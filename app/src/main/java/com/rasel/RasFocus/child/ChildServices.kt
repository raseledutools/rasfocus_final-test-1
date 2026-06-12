package com.rasel.RasFocus.child

// ============================================================
//  RasFocus+ — Child Background Services
//  Services  : ScreenCaptureService, LocationTracker,
//               FirebaseCommandListener
//  Author    : RasFocus+ Architecture Team
// ============================================================

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.gms.location.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private const val SERVICES_TAG = "RasFocus_ChildSvc"

// ─────────────────────────────────────────────────────────────────────────────
// Cloudinary helper functions
// initCloudinary — MediaManager একবারই init হয়, duplicate init crash করে
// uploadScreenshotToCloudinary — file upload করে public URL return করে
// ─────────────────────────────────────────────────────────────────────────────

private var cloudinaryInitialized = false

fun initCloudinary(context: Context) {
    if (cloudinaryInitialized) return
    try {
        val config = mapOf(
            "cloud_name" to "rasfocus",   // ← তোমার Cloudinary cloud name
            "api_key"    to "",            // public upload-এ api_key optional
            "api_secret" to ""
        )
        MediaManager.init(context, config)
        cloudinaryInitialized = true
        Log.i(SERVICES_TAG, "Cloudinary initialized")
    } catch (e: Exception) {
        Log.e(SERVICES_TAG, "Cloudinary init failed: ${e.message}")
    }
}

suspend fun uploadScreenshotToCloudinary(file: File, childUid: String): String =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        try {
            MediaManager.get().upload(file.absolutePath)
                .option("folder", "rasfocus/$childUid/screenshots")
                .option("public_id", "ss_${System.currentTimeMillis()}")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val url = resultData["secure_url"]?.toString() ?: ""
                        Log.i(SERVICES_TAG, "Upload success: $url")
                        if (cont.isActive) cont.resume(url) {}
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        Log.e(SERVICES_TAG, "Upload error: ${error.description}")
                        if (cont.isActive) cont.resume("") {}
                    }
                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                        if (cont.isActive) cont.resume("") {}
                    }
                })
                .dispatch()
        } catch (e: Exception) {
            Log.e(SERVICES_TAG, "Upload exception: ${e.message}")
            if (cont.isActive) cont.resume("") {}
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// Notification Channel Ids
// ─────────────────────────────────────────────────────────────────────────────

private const val CHANNEL_SCREEN    = "rasfocus_child_screen"
private const val CHANNEL_LOCATION  = "rasfocus_child_location"
private const val CHANNEL_COMMANDS  = "rasfocus_child_commands"

private fun createChannel(context: Context, id: String, name: String, importance: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ch = NotificationChannel(id, name, importance).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}

private fun buildForegroundNotif(
    context: Context,
    channelId: String,
    title: String,
    text: String
): Notification {
    val pi = PendingIntent.getActivity(
        context, 0,
        context.packageManager.getLaunchIntentForPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE
    )
    return NotificationCompat.Builder(context, channelId)
        .setContentTitle(title)
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(pi)
        .build()
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. ScreenCaptureService
//    Parent "Screenshot নাও" command পেলে screen capture করে Cloudinary-তে
//    upload করে, URL Firebase-এ save করে।
//    MediaProjection token MainActivity থেকে Intent-এ আসে।
// ─────────────────────────────────────────────────────────────────────────────

class ScreenCaptureService : Service() {

    companion object {
        const val NOTIF_ID            = 9101
        const val EXTRA_RESULT_CODE   = "result_code"
        const val EXTRA_RESULT_DATA   = "result_data"

        /** Parent-এর screenshot command এলে MainActivity এই Intent fire করে */
        fun startCapture(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val db          by lazy { Firebase.database.reference }
    private val childUid    get() = Firebase.auth.currentUser?.uid ?: ""
    private val scope       = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay:  VirtualDisplay?  = null
    private var imageReader:     ImageReader?      = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this, CHANNEL_SCREEN, "RasFocus Screen Monitor", NotificationManager.IMPORTANCE_LOW)
        val notif = buildForegroundNotif(this, CHANNEL_SCREEN, "RasFocus+ Active", "Screen monitoring ready")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_RESULT_DATA)

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            takeScreenshot(resultCode, resultData)
        } else {
            Log.w(SERVICES_TAG, "ScreenCaptureService: no valid MediaProjection token, staying idle")
        }
        return START_STICKY
    }

    private fun takeScreenshot(resultCode: Int, data: Intent) {
        scope.launch {
            try {
                val metrics = DisplayMetrics()
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bounds = wm.currentWindowMetrics.bounds
                    metrics.widthPixels  = bounds.width()
                    metrics.heightPixels = bounds.height()
                    metrics.densityDpi   = resources.configuration.densityDpi
                } else {
                    @Suppress("DEPRECATION")
                    wm.defaultDisplay.getMetrics(metrics)
                }

                val width  = metrics.widthPixels
                val height = metrics.heightPixels
                val dpi    = metrics.densityDpi

                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = mpm.getMediaProjection(resultCode, data)

                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "RasFocusCapture",
                    width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader!!.surface, null, null
                )

                // Short delay — display needs a frame
                delay(500)

                val image: Image? = imageReader?.acquireLatestImage()
                if (image != null) {
                    val file = saveImageToFile(image, width, height)
                    image.close()
                    if (file != null && childUid.isNotEmpty()) {
                        initCloudinary(applicationContext)
                        val url = uploadScreenshotToCloudinary(file, childUid)
                        if (url.isNotEmpty()) {
                            val ts = System.currentTimeMillis()
                            db.child("children/$childUid/screenshots").push()
                                .setValue(mapOf("url" to url, "timestamp" to ts))
                            Log.i(SERVICES_TAG, "Screenshot uploaded: $url")
                        }
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.e(SERVICES_TAG, "Screenshot failed: ${e.message}", e)
            } finally {
                virtualDisplay?.release()
                mediaProjection?.stop()
                imageReader?.close()
                virtualDisplay  = null
                mediaProjection = null
                imageReader     = null
            }
        }
    }

    private fun saveImageToFile(image: Image, width: Int, height: Int): File? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride  = planes[0].pixelStride
            val rowStride    = planes[0].rowStride
            val rowPadding   = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle()

            val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(cacheDir, "screenshot_${ts}.jpg")
            FileOutputStream(file).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            croppedBitmap.recycle()
            file
        } catch (e: Exception) {
            Log.e(SERVICES_TAG, "Image save failed: ${e.message}", e)
            null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        super.onDestroy()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. LocationTracker
//    প্রতি 5 মিনিটে child-এর GPS location Firebase-এ update করে।
//    Parent real-time map-এ দেখতে পারবে।
// ─────────────────────────────────────────────────────────────────────────────

class LocationTracker : Service() {

    companion object {
        const val NOTIF_ID            = 9102
        private const val INTERVAL_MS = 5 * 60 * 1000L  // 5 minutes
        private const val FASTEST_MS  = 60 * 1000L       // 1 minute fastest
    }

    private val db       by lazy { Firebase.database.reference }
    private val childUid get() = Firebase.auth.currentUser?.uid ?: ""

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest:     LocationRequest
    private lateinit var locationCallback:    LocationCallback

    override fun onCreate() {
        super.onCreate()
        createChannel(this, CHANNEL_LOCATION, "RasFocus Location", NotificationManager.IMPORTANCE_LOW)
        val notif = buildForegroundNotif(
            this, CHANNEL_LOCATION,
            "RasFocus+ Location Active",
            "Sharing location with parent"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_MS)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    pushLocationToFirebase(loc.latitude, loc.longitude, loc.accuracy)
                }
            }
        }

        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            Log.i(SERVICES_TAG, "LocationTracker: updates started")
        } catch (e: SecurityException) {
            Log.e(SERVICES_TAG, "Location permission missing: ${e.message}")
            stopSelf()
        }
    }

    private fun pushLocationToFirebase(lat: Double, lng: Double, accuracy: Float) {
        if (childUid.isEmpty()) return
        val data = mapOf(
            "lat"       to lat,
            "lng"       to lng,
            "accuracy"  to accuracy,
            "timestamp" to System.currentTimeMillis()
        )
        db.child("children/$childUid/location").setValue(data)
        Log.d(SERVICES_TAG, "Location updated → lat=$lat, lng=$lng")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. FirebaseCommandListener
//    Parent-এর real-time commands শোনে:
//      • isLocked       → device lock/unlock
//      • takeScreenshot → ScreenCaptureService trigger করে
//      • blockedApps    → FocusAccessibilityService-এর blocked list update
//      • screenTimeLimit→ limit update
// ─────────────────────────────────────────────────────────────────────────────

class FirebaseCommandListener : Service() {

    companion object {
        const val NOTIF_ID = 9103

        // Intent extra — MainActivity screenshot permission result পাঠাবে
        const val ACTION_SCREENSHOT_RESULT = "com.rasel.RasFocus.SCREENSHOT_RESULT"
        const val EXTRA_RESULT_CODE        = "result_code"
        const val EXTRA_RESULT_DATA        = "result_data"
    }

    private val db          by lazy { Firebase.database.reference }
    private val childUid    get() = Firebase.auth.currentUser?.uid ?: ""

    private var commandListener:   ValueEventListener? = null
    private var deviceRef:         DatabaseReference?  = null

    // MediaProjection token — MainActivity থেকে broadcast এলে store করি
    private var projectionResultCode: Int?    = null
    private var projectionResultData: Intent? = null

    private val screenshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SCREENSHOT_RESULT) {
                projectionResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                projectionResultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                else
                    @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
                Log.i(SERVICES_TAG, "Screenshot permission received, ready to capture")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel(this, CHANNEL_COMMANDS, "RasFocus Parental Commands", NotificationManager.IMPORTANCE_LOW)
        val notif = buildForegroundNotif(
            this, CHANNEL_COMMANDS,
            "RasFocus+ Connected",
            "Listening for parent commands"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        // Screenshot permission broadcast dingi
        val filter = IntentFilter(ACTION_SCREENSHOT_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenshotReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenshotReceiver, filter)
        }

        attachFirebaseListener()
    }

    private fun attachFirebaseListener() {
        if (childUid.isEmpty()) {
            Log.w(SERVICES_TAG, "FirebaseCommandListener: no uid, retrying in 10s")
            Handler(Looper.getMainLooper()).postDelayed({ attachFirebaseListener() }, 10_000)
            return
        }

        // Child নিজের device id হিসেবে childUid use করে
        deviceRef = db.child("children/$childUid/commands")

        commandListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                handleCommands(snapshot)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(SERVICES_TAG, "Firebase command listener cancelled: ${error.message}")
            }
        }
        deviceRef!!.addValueEventListener(commandListener!!)

        // Online presence — child device online দেখাও
        db.child("children/$childUid/isOnline").setValue(true)
        db.child("children/$childUid/lastSeen").setValue(ServerValue.TIMESTAMP)
        Log.i(SERVICES_TAG, "FirebaseCommandListener attached for uid=$childUid")
    }

    private fun handleCommands(snapshot: DataSnapshot) {
        // ── Lock command ──────────────────────────────────────────────────────
        val isLocked = snapshot.child("isLocked").getValue(Boolean::class.java) ?: false
        if (isLocked) {
            lockDevice()
        }

        // ── Screenshot command ────────────────────────────────────────────────
        val takeScreenshot = snapshot.child("takeScreenshot").getValue(Boolean::class.java) ?: false
        if (takeScreenshot) {
            triggerScreenshot()
            // Command consume করো
            snapshot.ref.child("takeScreenshot").setValue(false)
        }

        // ── Screen time limit ─────────────────────────────────────────────────
        val limitMin = snapshot.child("screenTimeLimit").getValue(Long::class.java)
        if (limitMin != null) {
            // Broadcast to main app (ViewModel শুনবে)
            sendBroadcast(Intent("com.rasel.RasFocus.SCREEN_TIME_LIMIT").apply {
                putExtra("limitMinutes", limitMin.toInt())
                setPackage(packageName)
            })
        }

        // ── Emergency SOS reply ───────────────────────────────────────────────
        val sosReply = snapshot.child("sosReply").getValue(String::class.java)
        if (!sosReply.isNullOrEmpty()) {
            showParentReply(sosReply)
            snapshot.ref.child("sosReply").removeValue()
        }
    }

    private fun lockDevice() {
        // DevicePolicyManager থাকলে lock করো, না থাকলে HOME-এ পাঠাও
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        try {
            dpm.lockNow()
            Log.i(SERVICES_TAG, "Device locked by parent command")
        } catch (e: SecurityException) {
            // Device Admin নেই — Home screen-এ পাঠাও
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(home)
            Log.w(SERVICES_TAG, "Device admin not granted, sent to home instead")
        }
    }

    private fun triggerScreenshot() {
        val code = projectionResultCode
        val data = projectionResultData
        if (code != null && code == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.startCapture(this, code, data)
            Log.i(SERVICES_TAG, "Screenshot triggered by parent command")
        } else {
            // Permission নেই — parent-কে Firebase-এ জানাও
            if (childUid.isNotEmpty()) {
                db.child("children/$childUid/alerts").push()
                    .setValue(mapOf(
                        "type"      to "Screenshot permission required",
                        "message"   to "Open RasFocus app to grant screen capture permission",
                        "timestamp" to System.currentTimeMillis()
                    ))
            }
            Log.w(SERVICES_TAG, "Screenshot requested but no projection permission available")
        }
    }

    private fun showParentReply(message: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, CHANNEL_COMMANDS)
            .setContentTitle("💬 Message from Parent")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(9199, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        commandListener?.let { deviceRef?.removeEventListener(it) }
        if (childUid.isNotEmpty()) {
            db.child("children/$childUid/isOnline").setValue(false)
            db.child("children/$childUid/lastSeen").setValue(ServerValue.TIMESTAMP)
        }
        try { unregisterReceiver(screenshotReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }
}
