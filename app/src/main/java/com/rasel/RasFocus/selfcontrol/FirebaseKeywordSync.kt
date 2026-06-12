// ============================================================
// FirebaseKeywordSync.kt  — v3
// Firebase Realtime Database থেকে তিন ধরনের data load করে:
//
//  1. Adult block  (আগের মতোই)
//     keyword_data/
//       adult_keywords:   ["porn", "xxx", ...]
//       adult_domains:    ["pornhub.com", ...]
//       allowed_keywords: ["sex education", ...]  ← whitelist
//
//  2. Custom block  (v2 থেকে)
//     keyword_data/
//       custom_blocks/
//         keywords: ["bet365", "gambling", ...]
//         domains:  ["bet365.com", "1xbet.com", ...]
//         apps:     ["com.betway.android", ...]
//
//  3. Custom messages  (v3 নতুন — প্রতিটা entry-র জন্য আলাদা বার্তা)
//     keyword_data/
//       custom_blocks/
//         app_messages/
//           com.betway.android: "Betting app blocked for focus"
//           com.facebook.katana: "Facebook blocked"
//         domain_messages/
//           bet365.com: "Gambling site blocked"
//           1xbet.com: "Betting site blocked"
//         keyword_messages/
//           gambling: "Gambling content blocked"
//           bet365: "Betting content blocked"
//
// HOW TO USE (Firebase Console):
//   keyword_data → custom_blocks → apps → Add → "com.betway.android"
//   keyword_data → custom_blocks → app_messages → Add → key: "com.betway.android", value: "Betting app blocked"
//   সাথে সাথে সব user এর ফোনে custom message সহ block হয়ে যাবে।
//
// Message fallback chain:
//   Firebase custom message → default message
// ============================================================

package com.rasel.RasFocus.selfcontrol

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object FirebaseKeywordSync {

    private const val TAG = "FBKeywordSync"
    private const val DB_PATH  = "keyword_data"
    private const val CACHE_PREFS = "fb_keyword_cache"

    // ── Cache keys ──────────────────────────────────────────
    private const val KEY_ADULT_KW      = "cached_keywords"
    private const val KEY_ADULT_DM      = "cached_domains"
    private const val KEY_ALLOWED       = "cached_allowed"
    private const val KEY_CUSTOM_KW     = "cached_custom_keywords"
    private const val KEY_CUSTOM_DM     = "cached_custom_domains"
    private const val KEY_CUSTOM_AP     = "cached_custom_apps"
    // v3: messages
    private const val KEY_APP_MSGS      = "cached_app_messages"
    private const val KEY_DOMAIN_MSGS   = "cached_domain_messages"
    private const val KEY_KEYWORD_MSGS  = "cached_keyword_messages"

    // ── In-memory — adult ────────────────────────────────────
    @Volatile private var remoteKeywords:  Set<String> = emptySet()
    @Volatile private var remoteDomains:   Set<String> = emptySet()
    @Volatile private var allowedKeywords: Set<String> = emptySet()

    // ── In-memory — custom block ─────────────────────────────
    @Volatile var customKeywords: Set<String> = emptySet()
        private set
    @Volatile var customDomains:  Set<String> = emptySet()
        private set
    @Volatile var customApps:     Set<String> = emptySet()
        private set

    // ── In-memory — custom messages (v3 নতুন) ───────────────
    // key = package/domain/keyword (lowercase), value = message string
    @Volatile var appMessages:     Map<String, String> = emptyMap()
        private set
    @Volatile var domainMessages:  Map<String, String> = emptyMap()
        private set
    @Volatile var keywordMessages: Map<String, String> = emptyMap()
        private set

    @Volatile var isLoaded: Boolean = false

    private var appContext: Context? = null

    // ────────────────────────────────────────────────────────
    // init — service start এ call করো
    // ────────────────────────────────────────────────────────
    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromCache()      // offline/restart এ আগের data তাৎক্ষণিক দেয়
        fetchFromFirebase()  // background এ fresh data নিয়ে আসো
    }

    // ────────────────────────────────────────────────────────
    // Adult block check
    // ────────────────────────────────────────────────────────
    fun isBlockedByRemote(rawText: String, host: String): Boolean {
        if (!isLoaded && remoteKeywords.isEmpty() && remoteDomains.isEmpty()) return false

        if (allowedKeywords.any { rawText.contains(it) }) return false

        if (host.isNotEmpty()) {
            if (remoteDomains.contains(host)) return true
            if (remoteDomains.any { host.endsWith(".$it") }) return true
        }
        if (remoteKeywords.any { rawText.contains(it) }) return true
        return false
    }

    // ────────────────────────────────────────────────────────
    // Custom block checks
    // ────────────────────────────────────────────────────────

    /** Browser URL বা screen text এ custom keyword / domain match আছে কিনা */
    fun isCustomBlocked(rawText: String, host: String): Boolean {
        if (customKeywords.isEmpty() && customDomains.isEmpty()) return false

        if (host.isNotEmpty()) {
            if (customDomains.contains(host)) return true
            if (customDomains.any { host.endsWith(".$it") }) return true
        }
        if (customKeywords.any { rawText.contains(it) }) return true
        return false
    }

    /** Package name টা custom blocked apps এ আছে কিনা */
    fun isCustomBlockedApp(packageName: String): Boolean {
        if (customApps.isEmpty()) return false
        return customApps.contains(packageName.lowercase())
    }

    /**
     * rawText-এ custom keyword list থেকে যেই keyword টা প্রথম match হয় সেটা return করো,
     * কোনোটাই match না হলে null।
     */
    fun getMatchedCustomKeyword(rawText: String): String? {
        if (customKeywords.isEmpty()) return null
        return customKeywords.firstOrNull { rawText.contains(it) }
    }

    // ────────────────────────────────────────────────────────
    // Message getters (v3) — Firebase message না থাকলে default দেয়
    // ────────────────────────────────────────────────────────

    /**
     * App block এর message নাও।
     * Firebase-এ app_messages/com.betway.android = "Betting app blocked" থাকলে সেটা দেবে,
     * না থাকলে default message দেবে।
     */
    fun getAppBlockMessage(packageName: String, default: String = "This app is blocked by admin."): String {
        return appMessages[packageName.lowercase()]?.takeIf { it.isNotBlank() } ?: default
    }

    /**
     * Domain block এর message নাও।
     * Firebase-এ domain_messages/bet365.com = "Gambling site blocked" থাকলে সেটা দেবে।
     */
    fun getDomainBlockMessage(host: String, default: String = "This website is blocked by admin."): String {
        // exact match
        val exact = domainMessages[host.lowercase()]
        if (!exact.isNullOrBlank()) return exact
        // subdomain fallback: cdn.bet365.com → bet365.com
        val parent = host.substringAfter(".", "")
        val parentMsg = domainMessages[parent.lowercase()]
        if (!parentMsg.isNullOrBlank()) return parentMsg
        return default
    }

    /**
     * Keyword block এর message নাও।
     * Firebase-এ keyword_messages/gambling = "Gambling content blocked" থাকলে সেটা দেবে।
     * rawText-এ যেকোনো একটা keyword match হলে সেটার message নেয়।
     */
    fun getKeywordBlockMessage(rawText: String, default: String = "Blocked content detected."): String {
        val matchedKw = customKeywords.firstOrNull { rawText.contains(it) } ?: return default
        return keywordMessages[matchedKw]?.takeIf { it.isNotBlank() } ?: default
    }

    // ── Firebase fetch ───────────────────────────────────────
    private fun fetchFromFirebase() {
        try {
            val ref = FirebaseDatabase.getInstance().getReference(DB_PATH)

            ref.addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        Log.w(TAG, "Firebase '$DB_PATH' empty or missing")
                        return
                    }

                    // ── Adult ──
                    val keywords = parseStringList(snapshot.child("adult_keywords"))
                    val domains  = parseStringList(snapshot.child("adult_domains"))
                    val allowed  = parseStringList(snapshot.child("allowed_keywords"))

                    remoteKeywords  = keywords.toSet()
                    remoteDomains   = domains.toSet()
                    allowedKeywords = allowed.toSet()

                    // ── Custom block ──
                    val customSnap = snapshot.child("custom_blocks")
                    val ckw = parseStringList(customSnap.child("keywords"))
                    val cdm = parseStringList(customSnap.child("domains"))
                    val cap = parseStringList(customSnap.child("apps"))

                    customKeywords = ckw.toSet()
                    customDomains  = cdm.toSet()
                    customApps     = cap.toSet()

                    // ── Custom messages (v3) ──
                    val appMsgs     = parseStringMap(customSnap.child("app_messages"))
                    val domainMsgs  = parseStringMap(customSnap.child("domain_messages"))
                    val keywordMsgs = parseStringMap(customSnap.child("keyword_messages"))

                    appMessages     = appMsgs
                    domainMessages  = domainMsgs
                    keywordMessages = keywordMsgs

                    isLoaded = true

                    Log.d(TAG, "✅ Adult: ${keywords.size}kw ${domains.size}dm | " +
                               "Custom: ${ckw.size}kw ${cdm.size}dm ${cap.size}apps | " +
                               "Messages: ${appMsgs.size}app ${domainMsgs.size}domain ${keywordMsgs.size}kw")

                    saveToCache(
                        keywords, domains, allowed,
                        ckw, cdm, cap,
                        appMsgs, domainMsgs, keywordMsgs
                    )
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Firebase cancelled: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init error: ${e.message}")
        }
    }

    // ── Parse helpers ────────────────────────────────────────

    /** Firebase list/array → List<String> (key-value or array উভয় format সামলায়) */
    private fun parseStringList(snap: DataSnapshot): List<String> {
        val result = mutableListOf<String>()
        if (!snap.exists()) return result
        for (child in snap.children) {
            val v = child.getValue(String::class.java)?.trim()?.lowercase()
            if (!v.isNullOrEmpty()) result.add(v)
        }
        return result
    }

    /**
     * Firebase map → Map<String, String>
     * Structure: messages/key: "value"
     * উদাহরণ: app_messages/com.betway.android: "Betting app blocked"
     * key lowercase হবে, value original case রাখা হবে (message)।
     */
    private fun parseStringMap(snap: DataSnapshot): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!snap.exists()) return result
        for (child in snap.children) {
            val k = child.key?.trim()?.lowercase() ?: continue
            val v = child.getValue(String::class.java)?.trim() ?: continue
            if (k.isNotEmpty() && v.isNotEmpty()) result[k] = v
        }
        return result
    }

    // ── Disk cache ───────────────────────────────────────────

    /**
     * Map<String, String> → "key1=value1\nkey2=value2" format এ serialize করে।
     * Key-এ '=' থাকলে প্রথম '=' দিয়েই split হবে।
     */
    private fun mapToString(map: Map<String, String>): String =
        map.entries.joinToString("\n") { (k, v) -> "$k=$v" }

    /**
     * "key1=value1\nkey2=value2" → Map<String, String>
     */
    private fun stringToMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (line in raw.split("\n")) {
            val idx = line.indexOf('=')
            if (idx > 0) {
                val k = line.substring(0, idx).trim()
                val v = line.substring(idx + 1).trim()
                if (k.isNotEmpty() && v.isNotEmpty()) result[k] = v
            }
        }
        return result
    }

    private fun saveToCache(
        kw: List<String>, dm: List<String>, al: List<String>,
        ckw: List<String>, cdm: List<String>, cap: List<String>,
        appMsgs: Map<String, String>,
        domainMsgs: Map<String, String>,
        keywordMsgs: Map<String, String>
    ) {
        val ctx = appContext ?: return
        try {
            ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ADULT_KW,    kw.joinToString("\n"))
                .putString(KEY_ADULT_DM,    dm.joinToString("\n"))
                .putString(KEY_ALLOWED,     al.joinToString("\n"))
                .putString(KEY_CUSTOM_KW,   ckw.joinToString("\n"))
                .putString(KEY_CUSTOM_DM,   cdm.joinToString("\n"))
                .putString(KEY_CUSTOM_AP,   cap.joinToString("\n"))
                .putString(KEY_APP_MSGS,    mapToString(appMsgs))
                .putString(KEY_DOMAIN_MSGS, mapToString(domainMsgs))
                .putString(KEY_KEYWORD_MSGS,mapToString(keywordMsgs))
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Cache save failed: ${e.message}")
        }
    }

    private fun loadFromCache() {
        val ctx = appContext ?: return
        try {
            val p = ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

            fun loadSet(key: String): Set<String> =
                p.getString(key, null)
                    ?.split("\n")
                    ?.filter { it.isNotEmpty() }
                    ?.toSet()
                    ?: emptySet()

            val kw  = loadSet(KEY_ADULT_KW)
            val dm  = loadSet(KEY_ADULT_DM)
            val al  = loadSet(KEY_ALLOWED)
            val ckw = loadSet(KEY_CUSTOM_KW)
            val cdm = loadSet(KEY_CUSTOM_DM)
            val cap = loadSet(KEY_CUSTOM_AP)
            val ams = stringToMap(p.getString(KEY_APP_MSGS, null))
            val dms = stringToMap(p.getString(KEY_DOMAIN_MSGS, null))
            val kms = stringToMap(p.getString(KEY_KEYWORD_MSGS, null))

            if (kw.isNotEmpty() || dm.isNotEmpty() || ckw.isNotEmpty() || cdm.isNotEmpty()) {
                remoteKeywords  = kw
                remoteDomains   = dm
                allowedKeywords = al
                customKeywords  = ckw
                customDomains   = cdm
                customApps      = cap
                appMessages     = ams
                domainMessages  = dms
                keywordMessages = kms
                isLoaded = true
                Log.d(TAG, "📦 Cache: adult ${kw.size}kw ${dm.size}dm | " +
                           "custom ${ckw.size}kw ${cdm.size}dm ${cap.size}apps | " +
                           "msgs: ${ams.size}app ${dms.size}domain ${kms.size}kw")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cache load failed: ${e.message}")
        }
    }

    // ── Manual refresh ───────────────────────────────────────
    fun forceRefresh() {
        isLoaded = false
        fetchFromFirebase()
    }
}