package com.rasel.RasFocus.selfcontrol

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat

// ══════════════════════════════════════════════════════════════
//  CONSTANTS
// ══════════════════════════════════════════════════════════════
object C {
    const val PREFS            = "take_rest_prefs"
    const val KEY_ALLOWED      = "allowed_pkgs"
    const val KEY_BREAK_END    = "break_end_time"
    const val CHANNEL_ID       = "take_rest_channel"
    const val NOTIF_ID         = 9999

    // ── Lock system keys ──
    const val KEY_LOCK_MODE    = "lock_mode"          // "self" | "parents" | "longtext"
    const val KEY_PARENT_PASS  = "parent_password"
    const val LOCK_SELF        = "self"
    const val LOCK_PARENTS     = "parents"
    const val LOCK_LONGTEXT    = "longtext"

    // Long text যা টাইপ করতে হবে unlock করতে
    const val LONG_UNLOCK_TEXT =
        "I acknowledge that I set this focus session to improve my productivity. " +
        "Unlocking early means I am choosing distraction over my goals. " +
        "I understand that consistent focus builds habits and leads to long-term success. " +
        "I am committed to completing my session and will return to my work with full attention."

    // FIX ③: Phone & Messages সবসময় allow — এই packages গুলো সবসময় whitelist এ থাকবে
    val DEFAULT_ALLOWED = setOf(
        "com.android.dialer",           // Stock Phone (AOSP)
        "com.google.android.dialer",    // Google Phone
        "com.samsung.android.dialer",   // Samsung Phone
        "com.android.phone",            // Fallback phone
        "com.android.mms",              // Stock Messages (AOSP)
        "com.google.android.apps.messaging", // Google Messages
        "com.samsung.android.messaging",     // Samsung Messages
        "com.android.messaging"         // Another stock messaging
    )
}

// ══════════════════════════════════════════════════════════════
//  TAKE REST ACTIVITY — Break সেট করার UI
// ══════════════════════════════════════════════════════════════
class TakeRestActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FIX ④: Permission popup — শুধু permission না থাকলেই যাও, থাকলে সরাসরি UI দেখাও
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // FIX ②: App list background thread এ load হবে, তাই UI সাথে সাথে দেখাবে
        val root = buildUI()
        setContentView(root)
    }

    private fun buildUI(): View {
        val ctx = this

        // ── State ──
        var selectedDays    = 0
        var selectedHours   = 0
        var selectedMinutes = 20
        var isBreakMode     = true   // true=Break, false=Pomodoro

        // ── Root: dark bg bottom sheet style ──
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1117"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ── Drag handle ──
        val handle = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(4)).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.topMargin = dp(12)
                it.bottomMargin = dp(16)
            }
            background = android.graphics.drawable.GradientDrawable().also {
                it.setColor(Color.parseColor("#3D4450"))
                it.cornerRadius = dp(2).toFloat()
            }
        }
        val handleWrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        handleWrapper.addView(handle)
        root.addView(handleWrapper)

        // ── Header row: X + Title ──
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val closeBtn = makeTv("✕", 20f, color = Color.parseColor("#8B949E")).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            gravity = Gravity.CENTER
        }
        closeBtn.setOnClickListener { finish() }
        val titleCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins(dp(12), 0, 0, 0)
            }
        }
        titleCol.addView(makeTv("Button Phone Mode", 18f, Typeface.BOLD, Color.WHITE))
        titleCol.addView(makeTv("Session শুরু করো", 13f, color = Color.parseColor("#8B949E")))
        headerRow.addView(closeBtn)
        headerRow.addView(titleCol)
        root.addView(headerRow)

        // ── Divider ──
        root.addView(View(ctx).apply {
            setBackgroundColor(Color.parseColor("#21262D"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        })

        // ── Scrollable content ──
        val scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(content)
        root.addView(scroll)

        // ── Tab Row: Break | Pomodoro ──
        val tabRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = android.graphics.drawable.GradientDrawable().also {
                it.setColor(Color.parseColor("#161B22"))
                it.cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(20) }
        }

        fun makeTabBtn(label: String, emoji: String, active: Boolean): LinearLayout {
            val bg = if (active) Color.parseColor("#1B6B5A") else Color.TRANSPARENT
            val tab = object : LinearLayout(ctx) {
                private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
                private val rect = RectF()
                override fun dispatchDraw(c: Canvas) {
                    if (active) {
                        rect.set(0f, 0f, width.toFloat(), height.toFloat())
                        c.drawRoundRect(rect, dp(10).toFloat(), dp(10).toFloat(), bgPaint)
                    }
                    super.dispatchDraw(c)
                }
            }.apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setWillNotDraw(false)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            tab.addView(makeTv(emoji, 16f, grav = Gravity.CENTER).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = dp(6) }
            })
            val textColor = if (active) Color.WHITE else Color.parseColor("#6E7681")
            tab.addView(makeTv(label, 14f, if (active) Typeface.BOLD else Typeface.NORMAL, textColor, grav = Gravity.CENTER))
            return tab
        }

        // Time display TextView (needs ref for updates)
        val timeTv = makeTv("20m", 56f, Typeface.BOLD, Color.parseColor("#2DD4AA"), grav = Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        fun updateTimeDisplay() {
            val d = selectedDays
            val h = selectedHours
            val m = selectedMinutes
            val text = buildString {
                if (d > 0) append("${d}d ")
                if (h > 0) append("${h}h ")
                if (m > 0 || (d == 0 && h == 0)) append("${m}m")
            }.trim()
            timeTv.text = text.ifEmpty { "0m" }
        }

        var breakTab: LinearLayout? = null
        var pomTab: LinearLayout? = null

        breakTab = makeTabBtn("Break", "☕", true)
        pomTab   = makeTabBtn("Pomodoro", "🍅", false)

        breakTab.setOnClickListener {
            isBreakMode = true
            tabRow.removeAllViews()
            tabRow.addView(makeTabBtn("Break", "☕", true).also {
                it.setOnClickListener { /* already active */ }
            })
            tabRow.addView(makeTabBtn("Pomodoro", "🍅", false).also {
                it.setOnClickListener {
                    isBreakMode = false
                    tabRow.removeAllViews()
                    tabRow.addView(makeTabBtn("Break", "☕", false))
                    tabRow.addView(makeTabBtn("Pomodoro", "🍅", true))
                }
            })
        }
        pomTab.setOnClickListener {
            isBreakMode = false
            tabRow.removeAllViews()
            tabRow.addView(makeTabBtn("Break", "☕", false).also {
                it.setOnClickListener {
                    isBreakMode = true
                    tabRow.removeAllViews()
                    tabRow.addView(makeTabBtn("Break", "☕", true))
                    tabRow.addView(makeTabBtn("Pomodoro", "🍅", false))
                }
            })
            tabRow.addView(makeTabBtn("Pomodoro", "🍅", true).also {
                it.setOnClickListener { /* already active */ }
            })
        }
        tabRow.addView(breakTab)
        tabRow.addView(pomTab)
        content.addView(tabRow)

        // ── Big Time Display Card ──
        val timeCard = object : FrameLayout(ctx) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#161B22") }
            private val rect = RectF()
            override fun dispatchDraw(c: Canvas) {
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, dp(16).toFloat(), dp(16).toFloat(), bgPaint)
                super.dispatchDraw(c)
            }
        }.apply {
            setWillNotDraw(false)
            setPadding(dp(20), dp(24), dp(20), dp(24))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(20) }
        }
        timeCard.addView(timeTv)
        content.addView(timeCard)

        // ── Quick Select Label ──
        // ── Time Section — Self Control এ শুধু দেখাবে ──
        val timeSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        timeSection.addView(makeTv("Quick Select", 13f, color = Color.parseColor("#8B949E")).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(10) }
        })

        // ── Quick Select Chips ──
        val quickRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(24) }
        }
        val quickOptions = listOf(Pair("5m", 5), Pair("15m", 15), Pair("25m", 25), Pair("30m", 30), Pair("1h", 60))

        fun makeQuickChip(label: String, mins: Int, isSelected: Boolean): TextView {
            val bgColor = if (isSelected) Color.parseColor("#1B6B5A") else Color.parseColor("#21262D")
            val textColor = if (isSelected) Color.WHITE else Color.parseColor("#8B949E")
            return object : TextView(ctx) {
                private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
                private val rect = RectF()
                override fun onDraw(c: Canvas) {
                    rect.set(0f, 0f, width.toFloat(), height.toFloat())
                    c.drawRoundRect(rect, height / 2f, height / 2f, bgPaint)
                    super.onDraw(c)
                }
            }.apply {
                text = label; textSize = 13f
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(textColor)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setWillNotDraw(false)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
                    it.setMargins(0, 0, dp(6), 0)
                }
            }
        }

        var selectedQuick = 20
        val quickChips = mutableListOf<TextView>()

        fun refreshQuickChips() {
            quickRow.removeAllViews()
            quickChips.clear()
            for ((lbl, mins) in quickOptions) {
                val chip = makeQuickChip(lbl, mins, mins == selectedQuick)
                chip.setOnClickListener {
                    selectedQuick   = mins
                    selectedMinutes = if (mins < 60) mins else 0
                    selectedHours   = if (mins == 60) 1 else 0
                    selectedDays    = 0
                    // Update sliders will be done via lambdas below
                    updateTimeDisplay()
                    refreshQuickChips()
                }
                quickChips.add(chip)
                quickRow.addView(chip)
            }
        }
        refreshQuickChips()
        timeSection.addView(quickRow)

        // ── Custom Duration Label ──
        timeSection.addView(makeTv("Custom Duration", 13f, color = Color.parseColor("#8B949E")).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(16) }
        })

        // Helper: make a labeled slider row
        fun makeSliderRow(label: String, max: Int, initVal: Int, onChanged: (Int) -> Unit): LinearLayout {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(16) }
            }
            val labelTv = makeTv(label, 14f, color = Color.parseColor("#C9D1D9")).apply {
                layoutParams = LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val valueTv = makeTv(initVal.toString(), 14f, Typeface.BOLD, Color.parseColor("#C9D1D9"), grav = Gravity.END).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val seekBar = SeekBar(ctx).apply {
                this.max = max
                progress = initVal
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
                    it.setMargins(dp(12), 0, dp(12), 0)
                }
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2DD4AA"))
                thumbTintList    = android.content.res.ColorStateList.valueOf(Color.parseColor("#2DD4AA"))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, v: Int, fromUser: Boolean) {
                        valueTv.text = v.toString()
                        onChanged(v)
                        updateTimeDisplay()
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            }
            row.addView(labelTv)
            row.addView(seekBar)
            row.addView(valueTv)
            return row
        }

        timeSection.addView(makeSliderRow("Days",    7,  selectedDays)    { selectedDays = it })
        timeSection.addView(makeSliderRow("Hours",   23, selectedHours)   { selectedHours = it })
        timeSection.addView(makeSliderRow("Minutes", 59, selectedMinutes) { selectedMinutes = it })

        // ── Spacer ──
        timeSection.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8))
        })

        content.addView(timeSection)

        // ── Allowed Apps Section — button দিয়ে open করো ──
        val allowedPkgs = C.DEFAULT_ALLOWED.toMutableSet()

        // Badge label যেটা button এ দেখাবে
        val allowedBtnLabel = makeTv("📱  Apps Select করো  ›", 15f, Typeface.BOLD, Color.WHITE, grav = Gravity.CENTER)

        fun refreshAllowedBtn() {
            val extraCount = allowedPkgs.count { it !in C.DEFAULT_ALLOWED }
            allowedBtnLabel.text = if (extraCount > 0)
                "📱  Apps Selected: $extraCount  ✓"
            else
                "📱  Apps Select করো  ›"
            allowedBtnLabel.setTextColor(
                if (extraCount > 0) Color.parseColor("#2DD4AA") else Color.WHITE
            )
        }

        val allowedBtn = object : LinearLayout(ctx) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#161B22") }
            private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2DD4AA"); style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat()
            }
            private val rect = RectF()
            override fun dispatchDraw(c: Canvas) {
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, dp(14).toFloat(), dp(14).toFloat(), bgPaint)
                c.drawRoundRect(rect, dp(14).toFloat(), dp(14).toFloat(), borderPaint)
                super.dispatchDraw(c)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setWillNotDraw(false)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(8) }
        }
        allowedBtn.addView(allowedBtnLabel)
        content.addView(makeTv("Break এ কোন apps ব্যবহার করতে পারবে?", 13f, color = Color.parseColor("#8B949E"), bot = dp(10)))
        content.addView(allowedBtn)
        content.addView(makeTv("📞 Phone & Messages সবসময় allow থাকবে", 11f, color = Color.parseColor("#4D5566"), bot = dp(4)))

        allowedBtn.setOnClickListener {
            showAllowListDialog(ctx, allowedPkgs) { refreshAllowedBtn() }
        }

        content.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20))
        })

        // ── Lock Mode Section ──────────────────────────────────
        content.addView(makeTv("Lock System", 13f, color = android.graphics.Color.parseColor("#8B949E")).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(10) }
        })

        var selectedLockMode = C.LOCK_SELF
        var parentPassInput  = ""

        // Lock mode option cards container
        val lockModeContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Password input field (only visible when Parents Control selected)
        val passField = android.widget.EditText(ctx).apply {
            hint = "Unlock password দাও"
            setHintTextColor(android.graphics.Color.parseColor("#5C6BC0"))
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#21262D"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, dp(10), 0, 0) }
            visibility = android.view.View.GONE
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { parentPassInput = s?.toString() ?: "" }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }

        data class LockOption(val mode: String, val emoji: String, val title: String, val sub: String)
        val lockOptions = listOf(
            LockOption(C.LOCK_SELF,     "🔓", "Self Control",    "নিজেই unlock করতে পারবে"),
            LockOption(C.LOCK_PARENTS,  "🔐", "Parents Control", "শুধু parents password দিয়ে unlock"),
            LockOption(C.LOCK_LONGTEXT, "✍️", "Long Text",       "~200 words টাইপ করলে unlock হবে")
        )

        fun buildLockOptions() {
            lockModeContainer.removeAllViews()
            for (opt in lockOptions) {
                val isSelected = selectedLockMode == opt.mode
                val card = object : LinearLayout(ctx) {
                    private val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (isSelected) android.graphics.Color.parseColor("#1B6B5A")
                                else android.graphics.Color.parseColor("#161B22")
                    }
                    private val rect = android.graphics.RectF()
                    override fun dispatchDraw(c: Canvas) {
                        rect.set(0f, 0f, width.toFloat(), height.toFloat())
                        c.drawRoundRect(rect, dp(14).toFloat(), dp(14).toFloat(), bgPaint)
                        super.dispatchDraw(c)
                    }
                }.apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    setWillNotDraw(false)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = dp(8) }
                    setOnClickListener {
                        selectedLockMode = opt.mode
                        passField.visibility = if (opt.mode == C.LOCK_PARENTS) android.view.View.VISIBLE else android.view.View.GONE
                        // Self Control এ time section দেখাবে, বাকিতে hide
                        timeSection.visibility = if (opt.mode == C.LOCK_SELF) android.view.View.VISIBLE else android.view.View.GONE
                        buildLockOptions()
                    }
                }
                val emojiTv = makeTv(opt.emoji, 22f, grav = Gravity.CENTER).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                val textCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .also { it.setMargins(dp(12), 0, 0, 0) }
                }
                textCol.addView(makeTv(opt.title, 14f, Typeface.BOLD, if (isSelected) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#C9D1D9")))
                textCol.addView(makeTv(opt.sub, 11f, color = if (isSelected) android.graphics.Color.parseColor("#A7F3D0") else android.graphics.Color.parseColor("#6E7681")))
                if (isSelected) {
                    val checkTv = makeTv("✓", 16f, Typeface.BOLD, android.graphics.Color.parseColor("#2DD4AA"), grav = Gravity.CENTER).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                    card.addView(emojiTv); card.addView(textCol); card.addView(checkTv)
                } else {
                    card.addView(emojiTv); card.addView(textCol)
                }
                lockModeContainer.addView(card)
            }
            lockModeContainer.addView(passField)
        }
        buildLockOptions()
        content.addView(lockModeContainer)

        content.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16))
        })

        // ── Start Button (outside scroll, pinned at bottom) ──
        val startBtn = object : TextView(ctx) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B6B5A") }
            private val rect = RectF()
            init { setWillNotDraw(false) }
            override fun onDraw(c: Canvas) {
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, dp(16).toFloat(), dp(16).toFloat(), bgPaint)
                super.onDraw(c)
            }
        }.apply {
            text = "☕  Break শুরু করো"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(18), dp(32), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(dp(20), dp(12), dp(20), dp(28)) }
        }

        startBtn.setOnClickListener {
            // Self Control → time validation দরকার
            // Parents / Long Text → time set করার দরকার নেই, indefinite block
            val totalMinutes = selectedDays * 24 * 60 + selectedHours * 60 + selectedMinutes
            val endTime: Long
            if (selectedLockMode == C.LOCK_SELF) {
                if (totalMinutes < 1) {
                    Toast.makeText(ctx, "কমপক্ষে ১ মিনিট দাও", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                endTime = System.currentTimeMillis() + totalMinutes * 60_000L
            } else {
                // Parents / Long Text — manually unlock না করা পর্যন্ত block থাকবে
                // 100 বছর পরে expire হবে (effectively indefinite)
                endTime = System.currentTimeMillis() + 100L * 365 * 24 * 60 * 60_000L
            }

            // FIX ③: DEFAULT_ALLOWED সবসময় allowedPkgs এ add করো save করার আগে
            allowedPkgs.addAll(C.DEFAULT_ALLOWED)

            // Parents Control — password validation
            if (selectedLockMode == C.LOCK_PARENTS && parentPassInput.trim().isEmpty()) {
                Toast.makeText(ctx, "Parents password দাও!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val prefs = getSharedPreferences(C.PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(C.KEY_BREAK_END, endTime)
                .putString(C.KEY_ALLOWED, allowedPkgs.joinToString(","))
                .putString(C.KEY_LOCK_MODE, selectedLockMode)
                .putString(C.KEY_PARENT_PASS, if (selectedLockMode == C.LOCK_PARENTS) parentPassInput.trim() else "")
                .apply()

            val svcIntent = Intent(ctx, BlockingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svcIntent)
            } else {
                startService(svcIntent)
            }

            Toast.makeText(ctx, "Break শুরু! ${totalMinutes} মিনিটের জন্য block চলছে", Toast.LENGTH_LONG).show()
            finish()
        }
        root.addView(startBtn)

        return root
    }

    // ── Allow List Dialog — fullscreen overlay ────────────
    private fun showAllowListDialog(
        ctx: Context,
        allowedPkgs: MutableSet<String>,
        onDone: () -> Unit
    ) {
        val dialog = android.app.Dialog(ctx, android.R.style.Theme_DeviceDefault_NoActionBar)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1117"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // ── Header ──
        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#161B22"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val backBtn = makeTv("←", 22f, color = Color.parseColor("#8B949E"), grav = Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            gravity = Gravity.CENTER
            setOnClickListener { dialog.dismiss(); onDone() }
        }
        val headerTitle = makeTv("Apps Allow List", 17f, Typeface.BOLD, Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.setMargins(dp(12), 0, 0, 0) }
        }
        val selectedCountTv = makeTv("", 12f, color = Color.parseColor("#2DD4AA"), grav = Gravity.END).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        fun updateCount() {
            val extra = allowedPkgs.count { it !in C.DEFAULT_ALLOWED }
            selectedCountTv.text = if (extra > 0) "$extra selected" else ""
        }
        updateCount()
        headerRow.addView(backBtn)
        headerRow.addView(headerTitle)
        headerRow.addView(selectedCountTv)
        root.addView(headerRow)

        // ── Info strip ──
        root.addView(makeTv("📞 Phone & Messages — সবসময় allow (এখানে দেখাবে না)", 11f, color = Color.parseColor("#4D5566")).apply {
            setBackgroundColor(Color.parseColor("#161B22"))
            setPadding(dp(20), dp(6), dp(20), dp(10))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        // ── Divider ──
        root.addView(View(ctx).apply {
            setBackgroundColor(Color.parseColor("#21262D"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        })

        // ── Search bar ──
        val searchEt = android.widget.EditText(ctx).apply {
            hint = "🔍  Search apps..."
            setHintTextColor(Color.parseColor("#6E7681"))
            setTextColor(Color.parseColor("#C9D1D9"))
            setBackgroundColor(Color.parseColor("#161B22"))
            setPadding(dp(20), dp(14), dp(20), dp(14))
            textSize = 14f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(searchEt)

        // ── App list container ──
        val listContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val listScroll = android.widget.ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        listScroll.addView(listContainer)
        root.addView(listScroll)

        // ── Loading state ──
        val loadingTv = makeTv("Apps লোড হচ্ছে...", 13f, color = Color.parseColor("#5C6BC0"), bot = 0, grav = Gravity.CENTER).apply {
            setPadding(0, dp(40), 0, 0)
        }
        listContainer.addView(loadingTv)

        // ── Done button ──
        val doneBtn = object : TextView(ctx) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B6B5A") }
            private val rect = RectF()
            init { setWillNotDraw(false) }
            override fun onDraw(c: Canvas) {
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, dp(16).toFloat(), dp(16).toFloat(), bgPaint)
                super.onDraw(c)
            }
        }.apply {
            text = "✓  Done"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(16), dp(32), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.setMargins(dp(20), dp(12), dp(20), dp(24)) }
            setOnClickListener { dialog.dismiss(); onDone() }
        }
        root.addView(doneBtn)

        dialog.setContentView(root)
        dialog.show()

        // ── Load apps in background ──
        var allApps: List<android.content.pm.ApplicationInfo> = emptyList()

        fun rebuildList(query: String) {
            listContainer.removeAllViews()
            val filtered = if (query.isBlank()) allApps
                else allApps.filter {
                    packageManager.getApplicationLabel(it).toString()
                        .contains(query, ignoreCase = true)
                }

            if (filtered.isEmpty()) {
                listContainer.addView(makeTv(
                    if (allApps.isEmpty()) "Apps লোড হচ্ছে..." else "কোনো app পাওয়া যায়নি",
                    13f, color = Color.parseColor("#5C6BC0"), bot = 0, grav = Gravity.CENTER
                ).apply { setPadding(0, dp(40), 0, 0) })
                return
            }

            val pm = packageManager
            filtered.forEach { appInfo ->
                val pkg = appInfo.packageName
                val label = pm.getApplicationLabel(appInfo).toString()
                val isSelected = pkg in allowedPkgs

                val itemRow = object : LinearLayout(ctx) {
                    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.TRANSPARENT }
                    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0D2B22") }
                    private val rect = RectF()
                    override fun dispatchDraw(c: Canvas) {
                        rect.set(0f, 0f, width.toFloat(), height.toFloat())
                        c.drawRect(rect, if (pkg in allowedPkgs) selPaint else bgPaint)
                        super.dispatchDraw(c)
                    }
                }.apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(20), dp(14), dp(20), dp(14))
                    setWillNotDraw(false)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                // Icon
                val iconView = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).also { it.marginEnd = dp(14) }
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                try { iconView.setImageDrawable(pm.getApplicationIcon(pkg)) }
                catch (_: Exception) { iconView.setImageDrawable(pm.defaultActivityIcon) }

                // App name column
                val nameCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                nameCol.addView(makeTv(label, 14f, Typeface.NORMAL,
                    if (isSelected) Color.WHITE else Color.parseColor("#C9D1D9")).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                })
                nameCol.addView(makeTv(pkg, 10f, color = Color.parseColor("#484F58")).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                })

                // Checkbox indicator
                val checkTv = makeTv(if (isSelected) "✓" else "", 18f, Typeface.BOLD,
                    Color.parseColor("#2DD4AA"), grav = Gravity.CENTER).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                    gravity = Gravity.CENTER
                }

                itemRow.addView(iconView)
                itemRow.addView(nameCol)
                itemRow.addView(checkTv)

                itemRow.setOnClickListener {
                    if (pkg in allowedPkgs) {
                        allowedPkgs.remove(pkg)
                        checkTv.text = ""
                        itemRow.invalidate()
                    } else {
                        allowedPkgs.add(pkg)
                        checkTv.text = "✓"
                        itemRow.invalidate()
                    }
                    updateCount()
                    doneBtn.text = run {
                        val extra = allowedPkgs.count { it !in C.DEFAULT_ALLOWED }
                        if (extra > 0) "✓  Done  ($extra selected)" else "✓  Done"
                    }
                }

                // Divider
                listContainer.addView(itemRow)
                listContainer.addView(View(ctx).apply {
                    setBackgroundColor(Color.parseColor("#21262D"))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                        .also { it.setMargins(dp(74), 0, 0, 0) }
                })
            }
        }

        Thread {
            val pm = packageManager
            allApps = try {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter {
                        pm.getLaunchIntentForPackage(it.packageName) != null &&
                        it.packageName != packageName &&
                        it.packageName !in C.DEFAULT_ALLOWED
                    }
                    .sortedWith(compareByDescending<android.content.pm.ApplicationInfo> {
                        it.packageName in allowedPkgs
                    }.thenBy { pm.getApplicationLabel(it).toString().lowercase() })
            } catch (_: Exception) { emptyList() }

            Handler(Looper.getMainLooper()).post {
                rebuildList("")
                updateCount()
                // Search listener
                searchEt.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                    override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { rebuildList(s?.toString() ?: "") }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
            }
        }.start()
    }

    // ── Allow list selector (পুরনো inline chips — এখন আর ব্যবহার নেই) ────
    private fun buildAllowedAppsSelectorAsync(
        ctx: Context,
        container: LinearLayout,
        allowedPkgs: MutableSet<String>
    ) {
        val loadingTv = makeTv("Apps লোড হচ্ছে...", 12f, color = Color.parseColor("#5C6BC0"), bot = dp(8), grav = Gravity.CENTER)
        container.addView(loadingTv)

        Thread {
            val pm = packageManager
            val launchableApps = try {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter {
                        pm.getLaunchIntentForPackage(it.packageName) != null &&
                        it.packageName != packageName &&
                        it.packageName !in C.DEFAULT_ALLOWED  // Phone/Messages আলাদা দেখাব না
                    }
                    .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            } catch (e: Exception) { emptyList() }

            Handler(Looper.getMainLooper()).post {
                container.removeView(loadingTv)
                buildChipsOnMainThread(ctx, container, launchableApps, allowedPkgs)
            }
        }.start()
    }

    private fun buildChipsOnMainThread(
        ctx: Context,
        container: LinearLayout,
        launchableApps: List<android.content.pm.ApplicationInfo>,
        allowedPkgs: MutableSet<String>
    ) {
        val pm = packageManager
        var row: LinearLayout? = null

        launchableApps.forEachIndexed { idx, appInfo ->
            if (idx % 3 == 0) {
                row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(0, 0, 0, dp(8)) }
                }
                container.addView(row)
            }

            val pkg = appInfo.packageName
            val label = pm.getApplicationLabel(appInfo).toString()
            var selected = false

            val chip = object : LinearLayout(ctx) {
                private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                private val rect = RectF()
                init {
                    bgPaint.color = Color.parseColor("#1E2E8F")
                    setWillNotDraw(false)
                }
                override fun dispatchDraw(c: Canvas) {
                    rect.set(0f, 0f, width.toFloat(), height.toFloat())
                    c.drawRoundRect(rect, dp(20).toFloat(), dp(20).toFloat(), bgPaint)
                    super.dispatchDraw(c)
                }
            }.apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
                    it.setMargins(if (idx % 3 == 0) 0 else dp(6), 0, 0, 0)
                }
            }

            val iconView = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            try { iconView.setImageDrawable(pm.getApplicationIcon(pkg)) } catch (_: Exception) {}

            val nameView = TextView(ctx).apply {
                text = label
                textSize = 10f
                setTextColor(Color.parseColor("#C5CAE9"))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            chip.addView(iconView)
            chip.addView(nameView)

            chip.setOnClickListener {
                selected = !selected
                if (selected) {
                    allowedPkgs.add(pkg)
                    chip.alpha = 1f
                    nameView.setTextColor(Color.WHITE)
                } else {
                    allowedPkgs.remove(pkg)
                    chip.alpha = 0.45f
                    nameView.setTextColor(Color.parseColor("#7986CB"))
                }
            }
            chip.alpha = 0.45f
            row?.addView(chip)
        }

        val rem = launchableApps.size % 3
        if (rem != 0) {
            repeat(3 - rem) {
                row?.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f).also {
                        it.setMargins(dp(6), 0, 0, 0)
                    }
                })
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun makeTv(
        text: String, size: Float, style: Int = Typeface.NORMAL,
        color: Int = Color.WHITE, bot: Int = 0, grav: Int = Gravity.NO_GRAVITY
    ) = TextView(this).apply {
        this.text = text; textSize = size
        setTypeface(null, style); setTextColor(color)
        setPadding(0, 0, 0, bot)
        if (grav != Gravity.NO_GRAVITY) gravity = grav
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

// ══════════════════════════════════════════════════════════════
//  BLOCKING SERVICE
// ══════════════════════════════════════════════════════════════
class BlockingService : Service() {

    private var thread: Thread? = null
    @Volatile private var running = false
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    @Volatile private var isOverlayShowing = false

    // FIX ①: Remaining time overlay তে দেখানোর জন্য TextView reference রাখো
    private var remainingTimeTv: TextView? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateRemainingTimeDisplay()
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(C.NOTIF_ID, buildNotification())
        if (!running) {
            running = true
            thread = Thread(::trackLoop).also { it.start() }
        }
        return START_STICKY
    }

    // ── Helper: Notification ──────────────────────────────
    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(C.CHANNEL_ID, "Break Blocker", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, C.CHANNEL_ID)
            .setContentTitle("Break চলছে 😴")
            .setContentText("Apps blocked — বিশ্রাম নাও")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    // ── Helper: Break সক্রিয় কিনা ───────────────────────
    private fun isBreakActive(): Boolean {
        val endTime = getSharedPreferences(C.PREFS, MODE_PRIVATE).getLong(C.KEY_BREAK_END, 0L)
        return System.currentTimeMillis() < endTime
    }

    // ── Helper: Remaining time (mm:ss বা hh:mm:ss) ───────
    private fun getRemainingTimeString(): String {
        val endTime = getSharedPreferences(C.PREFS, MODE_PRIVATE).getLong(C.KEY_BREAK_END, 0L)
        val remaining = maxOf(0L, endTime - System.currentTimeMillis())
        val totalSecs = remaining / 1000
        val hours = totalSecs / 3600
        val mins  = (totalSecs % 3600) / 60
        val secs  = totalSecs % 60
        return if (hours > 0) {
            "%02d:%02d:%02d".format(hours, mins, secs)
        } else {
            "%02d:%02d".format(mins, secs)
        }
    }

    // FIX ①: Timer TextView আপডেট করো প্রতি সেকেন্ডে
    private fun updateRemainingTimeDisplay() {
        remainingTimeTv?.text = getRemainingTimeString()
    }

    // ── Helper: Allowed app কিনা ─────────────────────────
    private fun isAllowed(pkg: String): Boolean {
        if (pkg == packageName) return true
        // FIX ③: DEFAULT_ALLOWED সবসময় allow
        if (pkg in C.DEFAULT_ALLOWED) return true
        val raw = getSharedPreferences(C.PREFS, MODE_PRIVATE).getString(C.KEY_ALLOWED, "") ?: ""
        val allowed = raw.split(",").filter { it.isNotEmpty() }.toSet()
        return pkg in allowed
    }

    // ── Helper: Foreground package ────────────────────────
    private fun getForegroundPackage(usm: UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 3000, now)
        val event = UsageEvents.Event()
        var lastPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPkg = event.packageName
            }
        }
        return lastPkg
    }

    // ── Core tracking loop ────────────────────────────────
    private fun trackLoop() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        while (running) {
            try {
                if (!isBreakActive()) {
                    // FIX ①: prefs clear করো
                    getSharedPreferences(C.PREFS, MODE_PRIVATE).edit()
                        .putLong(C.KEY_BREAK_END, 0L)
                        .apply()

                    // FIX ①: stopForeground আগে, তারপর stopSelf — এতে session সত্যিই বন্ধ হবে
                    Handler(Looper.getMainLooper()).post {
                        timerHandler.removeCallbacks(timerRunnable)
                        removeOverlay()
                        stopForeground(true)
                        stopSelf()
                    }
                    break
                }

                val pkg = getForegroundPackage(usm)
                if (pkg != null) {
                    val blocked = !isAllowed(pkg)
                    if (blocked && !isOverlayShowing) {
                        Handler(Looper.getMainLooper()).post { showOverlay() }
                    } else if (!blocked && isOverlayShowing) {
                        Handler(Looper.getMainLooper()).post { removeOverlay() }
                    }
                }
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                break
            } catch (_: Exception) {
                Thread.sleep(300)
            }
        }
    }

    // ── Overlay show ──────────────────────────────────────
    private fun showOverlay() {
        if (isOverlayShowing || !Settings.canDrawOverlays(this)) return

        val view = buildOverlayUI()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        @Suppress("DEPRECATION")
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type, flags, PixelFormat.TRANSLUCENT
        )

        windowManager.addView(view, params)
        overlayView = view
        isOverlayShowing = true

        // FIX ①: Timer শুরু করো overlay দেখানোর সাথে সাথে
        timerHandler.post(timerRunnable)
    }

    // ── Overlay remove ────────────────────────────────────
    private fun removeOverlay() {
        if (!isOverlayShowing) return
        timerHandler.removeCallbacks(timerRunnable)
        remainingTimeTv = null
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        isOverlayShowing = false
    }

    // ── Overlay UI ────────────────────────────────────────
    private fun buildOverlayUI(): View {
        val context = ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_NoActionBar)

        // ── Root scroll — deep dark background ──
        val scroll = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#060B14"))
            isVerticalScrollBarEnabled = false
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK }
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(52), dp(20), dp(48))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(root)

        // ─────────────────────────────────────────
        // ① STATUS BAR — top pill: "📵 Focus Mode"
        // ─────────────────────────────────────────
        val statusPill = object : LinearLayout(context) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0E1E35") }
            private val r = RectF()
            override fun dispatchDraw(c: Canvas) {
                r.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(r, height / 2f, height / 2f, p)
                super.dispatchDraw(c)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setWillNotDraw(false)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.gravity = Gravity.CENTER_HORIZONTAL; it.bottomMargin = dp(28) }
        }
        // pulsing dot
        val dotView = object : View(context) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2DD4AA") }
            override fun onDraw(c: Canvas) { c.drawCircle(width/2f, height/2f, width/2f, p) }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
                .also { it.marginEnd = dp(8); it.gravity = Gravity.CENTER_VERTICAL }
            setWillNotDraw(false)
        }
        statusPill.addView(dotView)
        statusPill.addView(blockTv(context, "FOCUS MODE  ACTIVE", 11f, Typeface.BOLD, Color.parseColor("#2DD4AA"), grav = Gravity.CENTER).apply {
            letterSpacing = 0.12f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        root.addView(statusPill)

        // ─────────────────────────────────────────
        // ② CLOCK — বড় time
        // ─────────────────────────────────────────
        val clockTv = blockTv(context, java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
            72f, Typeface.BOLD, Color.WHITE, bot = dp(4), grav = Gravity.CENTER).apply {
            letterSpacing = -0.02f
        }
        val dateTv = blockTv(context,
            java.text.SimpleDateFormat("EEEE, dd MMMM", java.util.Locale.getDefault()).format(java.util.Date()),
            13f, color = Color.parseColor("#3D5068"), bot = dp(36), grav = Gravity.CENTER)

        // clock প্রতি মিনিটে update
        timerHandler.post(object : Runnable {
            override fun run() {
                clockTv.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                dateTv.text  = java.text.SimpleDateFormat("EEEE, dd MMMM", java.util.Locale.getDefault()).format(java.util.Date())
                timerHandler.postDelayed(this, 60_000)
            }
        })
        root.addView(clockTv)
        root.addView(dateTv)

        // ─────────────────────────────────────────
        // ③ TIMER CARD — glassy dark card
        // ─────────────────────────────────────────
        val timerCard = object : FrameLayout(context) {
            private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B1628") }
            private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1A3A5C"); style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat()
            }
            private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0A2540"); style = Paint.Style.FILL
            }
            private val rect = RectF()
            override fun dispatchDraw(c: Canvas) {
                val r = dp(24).toFloat()
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, r, r, bgPaint)
                c.drawRoundRect(rect, r, r, rimPaint)
                super.dispatchDraw(c)
            }
        }.apply {
            setWillNotDraw(false)
            setPadding(dp(24), dp(28), dp(24), dp(28))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(24) }
        }

        val timerInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        // label row
        val labelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(12) }
        }
        labelRow.addView(blockTv(context, "⏳", 14f, grav = Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.marginEnd = dp(6) }
        })
        labelRow.addView(blockTv(context, "REMAINING TIME", 11f, Typeface.BOLD, Color.parseColor("#2D4A6B"), grav = Gravity.CENTER).apply {
            letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        timerInner.addView(labelRow)

        // big timer
        val timeTv = TextView(context).apply {
            text = getRemainingTimeString()
            textSize = 52f
            setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL), Typeface.NORMAL)
            setTextColor(Color.parseColor("#E8F4FF"))
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(6) }
        }
        remainingTimeTv = timeTv
        timerInner.addView(timeTv)

        // thin separator line
        timerInner.addView(object : View(context) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.LinearGradient(0f, 0f, 500f, 0f,
                    intArrayOf(Color.TRANSPARENT, Color.parseColor("#1A4A7A"), Color.TRANSPARENT),
                    null, android.graphics.Shader.TileMode.CLAMP)
            }
            override fun onDraw(c: Canvas) { c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p) }
        }.apply {
            setWillNotDraw(false)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                .also { it.setMargins(dp(24), dp(4), dp(24), dp(10)) }
        })

        timerInner.addView(blockTv(context, "session শেষ হওয়ার আগ পর্যন্ত apps blocked", 11f,
            color = Color.parseColor("#2D4A6B"), grav = Gravity.CENTER))
        timerCard.addView(timerInner)
        root.addView(timerCard)

        // ─────────────────────────────────────────
        // ④ ALLOWED APPS SECTION
        // ─────────────────────────────────────────
        root.addView(blockTv(context, "ALLOWED APPS", 10f, Typeface.BOLD,
            Color.parseColor("#2D4A6B"), bot = dp(12), grav = Gravity.CENTER).apply {
            letterSpacing = 0.15f
        })

        val appsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(appsContainer)
        loadAllowedAppsToView(context, appsContainer)

        // ─────────────────────────────────────────
        // ⑤ UNLOCK BUTTON
        // ─────────────────────────────────────────
        addUnlockButtonToOverlay(context, root)

        return scroll
    }

    // ── Unlock button overlay তে যোগ করো ──────────────────
    private fun addUnlockButtonToOverlay(context: Context, root: LinearLayout) {
        root.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28))
        })

        // lock mode label
        val prefs    = getSharedPreferences(C.PREFS, MODE_PRIVATE)
        val lockMode = prefs.getString(C.KEY_LOCK_MODE, C.LOCK_SELF) ?: C.LOCK_SELF
        val lockHint = when (lockMode) {
            C.LOCK_PARENTS  -> "🔐  Parents password দিয়ে unlock"
            C.LOCK_LONGTEXT -> "✍️  Long text টাইপ করে unlock"
            else            -> "🔓  Unlock করতে চাইলে tap করো"
        }
        root.addView(blockTv(context, lockHint, 11f, color = Color.parseColor("#243550"), bot = dp(10), grav = Gravity.CENTER))

        val unlockBtn = object : LinearLayout(context) {
            private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B1628") }
            private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1A3A5C"); style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat()
            }
            private val rect = RectF()
            override fun dispatchDraw(c: Canvas) {
                val r = dp(14).toFloat()
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, r, r, bgPaint)
                c.drawRoundRect(rect, r, r, rimPaint)
                super.dispatchDraw(c)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
            setWillNotDraw(false)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val lockIcon = blockTv(context, "🔓", 18f, grav = Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.marginEnd = dp(10) }
        }
        val unlockLabel = blockTv(context, "Unlock", 14f, Typeface.NORMAL, Color.parseColor("#3D6080"), grav = Gravity.CENTER).apply {
            letterSpacing = 0.05f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        unlockBtn.addView(lockIcon)
        unlockBtn.addView(unlockLabel)
        unlockBtn.setOnClickListener { showUnlockDialog(context) }
        root.addView(unlockBtn)
    }

    // ── Unlock dialog — lock type অনুযায়ী ────────────────
    private fun showUnlockDialog(context: Context) {
        val prefs    = getSharedPreferences(C.PREFS, MODE_PRIVATE)
        val lockMode = prefs.getString(C.KEY_LOCK_MODE, C.LOCK_SELF) ?: C.LOCK_SELF

        when (lockMode) {
            C.LOCK_PARENTS -> {
                // Parents Control — password input dialog
                showParentsUnlockDialog(context)
            }
            C.LOCK_LONGTEXT -> {
                // Long Text — ~200 words টাইপ করতে হবে
                showLongTextUnlockOverlay(context)
            }
            else -> {
                // Self Control — সরাসরি confirm করো
                val dialogCtx = android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar)
                val dialog = android.app.AlertDialog.Builder(dialogCtx)
                    .setTitle("Break শেষ করবে?")
                    .setMessage("Focus session বন্ধ করে unlock করতে চাও?")
                    .setPositiveButton("হ্যাঁ, Unlock করো") { _, _ -> doUnlock() }
                    .setNegativeButton("না, থাকো", null)
                    .create()
                dialog.window?.setType(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                )
                dialog.show()
            }
        }
    }

    // ── Parents unlock overlay ────────────────────────────
    private fun showParentsUnlockDialog(context: Context) {
        val savedPass = getSharedPreferences(C.PREFS, MODE_PRIVATE).getString(C.KEY_PARENT_PASS, "") ?: ""

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        val overlayRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1117"))
            setPadding(dp(28), dp(80), dp(28), dp(40))
            gravity = Gravity.CENTER_VERTICAL
        }

        var overlayView: android.view.View? = overlayRoot

        fun dismiss() {
            overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
            overlayView = null
        }

        // ── Lock icon ──
        overlayRoot.addView(blockTv(context, "🔐", 52f, grav = Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(16) }
        })

        // ── Title ──
        overlayRoot.addView(blockTv(context, "Parents Unlock", 22f, Typeface.BOLD, Color.WHITE, bot = dp(8), grav = Gravity.CENTER))
        overlayRoot.addView(blockTv(context, "Parents এর password দিয়ে unlock করো", 13f, color = Color.parseColor("#6E7681"), bot = dp(32), grav = Gravity.CENTER))

        // ── Password input ──
        val inputEt = android.widget.EditText(context).apply {
            hint = "Password লিখুন"
            setHintTextColor(Color.parseColor("#484F58"))
            setTextColor(Color.WHITE)
            textSize = 16f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setBackgroundColor(Color.parseColor("#161B22"))
            setPadding(dp(20), dp(16), dp(20), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12) }
        }
        overlayRoot.addView(inputEt)

        // ── Error message (hidden by default) ──
        val errorTv = blockTv(context, "❌  ভুল পাসওয়ার্ড! আবার চেষ্টা করো।", 13f,
            color = Color.parseColor("#F85149"), bot = dp(16)).apply {
            visibility = android.view.View.GONE
        }
        overlayRoot.addView(errorTv)

        // ── Unlock button ──
        val unlockBtn = object : TextView(context) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B6B5A") }
            private val rect = RectF()
            init { setWillNotDraw(false) }
            override fun onDraw(c: Canvas) {
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, dp(14).toFloat(), dp(14).toFloat(), bgPaint)
                super.onDraw(c)
            }
        }.apply {
            text = "🔓  Unlock করো"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(18), dp(24), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12) }
        }
        unlockBtn.setOnClickListener {
            val entered = inputEt.text.toString()
            if (entered == savedPass) {
                dismiss()
                doUnlock()
            } else {
                errorTv.visibility = android.view.View.VISIBLE
                inputEt.text.clear()
            }
        }
        overlayRoot.addView(unlockBtn)

        // ── Back button ──
        val backBtn = blockTv(context, "← ফিরে যাও", 14f, color = Color.parseColor("#484F58"), bot = 0, grav = Gravity.CENTER)
        backBtn.setOnClickListener { dismiss() }
        overlayRoot.addView(backBtn)

        windowManager.addView(overlayRoot, params)
    }

    // ── Long text unlock overlay ─────────────────────────
    private fun showLongTextUnlockOverlay(context: Context) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        val unlockRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1117"))
            setPadding(dp(24), dp(56), dp(24), dp(32))
        }

        unlockRoot.addView(blockTv(context, "Unlock করতে নিচের text হুবহু টাইপ করো:", 14f, Typeface.BOLD, Color.parseColor("#4DD0E1"), bot = dp(12)))

        val targetScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120))
                .also { it.bottomMargin = dp(16) }
            setBackgroundColor(Color.parseColor("#161B22"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        targetScroll.addView(blockTv(context, C.LONG_UNLOCK_TEXT, 11f, color = Color.parseColor("#8B949E")))
        unlockRoot.addView(targetScroll)

        val inputEt = android.widget.EditText(context).apply {
            hint = "এখানে টাইপ করুন..."
            setHintTextColor(Color.parseColor("#546E7A"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#161B22"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minLines = 4
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(16) }
        }
        unlockRoot.addView(inputEt)

        var unlockOverlayView: android.view.View? = unlockRoot
        windowManager.addView(unlockRoot, params)

        val verifyBtn = object : TextView(context) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B6B5A") }
            private val rect = RectF()
            init { setWillNotDraw(false) }
            override fun onDraw(c: Canvas) {
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, dp(14).toFloat(), dp(14).toFloat(), bgPaint)
                super.onDraw(c)
            }
        }.apply {
            text = "Verify & Unlock"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(10) }
        }
        verifyBtn.setOnClickListener {
            if (inputEt.text.toString().trim() == C.LONG_UNLOCK_TEXT.trim()) {
                unlockOverlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
                unlockOverlayView = null
                doUnlock()
            } else {
                Toast.makeText(context, "লেখা মিলছে না! হুবহু টাইপ করুন।", Toast.LENGTH_SHORT).show()
            }
        }
        unlockRoot.addView(verifyBtn)

        val backBtn = blockTv(context, "← ফিরে যাও", 14f, color = Color.parseColor("#546E7A"), bot = 0, grav = Gravity.CENTER)
        backBtn.setOnClickListener {
            unlockOverlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
            unlockOverlayView = null
        }
        unlockRoot.addView(backBtn)
    }

    // ── Unlock সম্পন্ন করো ───────────────────────────────
    private fun doUnlock() {
        getSharedPreferences(C.PREFS, MODE_PRIVATE).edit()
            .putLong(C.KEY_BREAK_END, 0L)
            .putString(C.KEY_LOCK_MODE, C.LOCK_SELF)
            .putString(C.KEY_PARENT_PASS, "")
            .apply()
        removeOverlay()
        stopForeground(true)
        stopSelf()
    }

    private fun loadAllowedAppsToView(context: Context, container: LinearLayout) {
        val raw = getSharedPreferences(C.PREFS, MODE_PRIVATE).getString(C.KEY_ALLOWED, "") ?: ""
        // FIX ③: DEFAULT_ALLOWED সবসময় দেখাও, prefs এ না থাকলেও
        val pkgs = (raw.split(",").filter { it.isNotEmpty() } + C.DEFAULT_ALLOWED).distinct()
        val pm = packageManager

        val allPkgs = pkgs.filter { pkg ->
            try { pm.getApplicationInfo(pkg, 0); true } catch (_: Exception) { false }
        }

        if (allPkgs.isEmpty()) {
            container.addView(blockTv(context, "কোনো app allow করা হয়নি", 13f, color = Color.parseColor("#5C6BC0"), bot = 0, grav = Gravity.CENTER))
            return
        }

        var row: LinearLayout? = null
        allPkgs.forEachIndexed { idx, pkg ->
            if (idx % 2 == 0) {
                row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .also { it.setMargins(0, 0, 0, dp(10)) }
                }
                container.addView(row)
            }

            val chip = buildAppChip(context, pkg, pm)
            chip.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins(if (idx % 2 == 0) 0 else dp(8), 0, 0, 0)
            }
            chip.setOnClickListener {
                // FIX ③: App launch এর আগে overlay সরাও — launch fast হবে
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    removeOverlay()  // আগে overlay সরাও
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(context, "App খোলা যাচ্ছে না", Toast.LENGTH_SHORT).show()
                }
            }
            row?.addView(chip)
        }

        if (allPkgs.size % 2 != 0) {
            row?.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.setMargins(dp(8), 0, 0, 0) }
            })
        }
    }

    private fun buildAppChip(context: Context, pkg: String, pm: PackageManager): LinearLayout {
        val chip = object : LinearLayout(context) {
            private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B1628") }
            private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#152240"); style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat()
            }
            private val rect = RectF()
            override fun dispatchDraw(c: Canvas) {
                val r = dp(16).toFloat()
                rect.set(0f, 0f, width.toFloat(), height.toFloat())
                c.drawRoundRect(rect, r, r, bgPaint)
                c.drawRoundRect(rect, r, r, rimPaint)
                super.dispatchDraw(c)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(16), dp(12))
            setWillNotDraw(false)
        }

        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).also { it.marginEnd = dp(10) }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        try { iconView.setImageDrawable(pm.getApplicationIcon(pkg)) }
        catch (_: Exception) { iconView.setImageDrawable(pm.defaultActivityIcon) }

        val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                    catch (_: Exception) { pkg.substringAfterLast(".") }

        val nameView = TextView(context).apply {
            text = label
            textSize = 12f
            setTypeface(null, Typeface.NORMAL)
            setTextColor(Color.parseColor("#8BAFD4"))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        chip.addView(iconView)
        chip.addView(nameView)
        return chip
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun blockTv(
        context: Context, text: String, size: Float,
        style: Int = Typeface.NORMAL, color: Int = Color.WHITE,
        bot: Int = 0, grav: Int = Gravity.NO_GRAVITY
    ) = TextView(context).apply {
        this.text = text; textSize = size
        setTypeface(null, style); setTextColor(color)
        setPadding(0, 0, 0, bot)
        if (grav != Gravity.NO_GRAVITY) gravity = grav
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroy() {
        running = false
        thread?.interrupt()
        timerHandler.removeCallbacks(timerRunnable)
        Handler(Looper.getMainLooper()).post { removeOverlay() }
    }
}