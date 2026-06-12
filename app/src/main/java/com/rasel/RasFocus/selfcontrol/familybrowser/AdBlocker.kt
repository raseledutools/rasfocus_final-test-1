package com.rasel.RasFocus.selfcontrol.familybrowser

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

// ─── ১. মূল ফিল্টারিং এবং লজিক ক্লাস (AdBlocker) ──────────────────────────────
class AdBlocker(private val context: Context) {

    companion object {
        // ── Adult URL Keywords (Moved here so WebViewClient can access it for JS Scanner) ──
        val ADULT_URL_KEYWORDS = setOf(
            "xnxx", "xvideos", "pornhub", "xhamster", "chaturbate", "onlyfans",
            "brazzers", "spankbang", "redtube", "youporn", "stripchat",
            "livejasmin", "bongacams", "myfreecams", "streamate",
            "porn", "hentai", "nsfw", "camgirl", "camshow", "webcamshow",
            "desi-sex", "bangla-sex", "hindi-sex", "indian-sex",
            "desiporn", "desixxx", "banglaporn", "nude", "boobs", "tits", 
            "milf", "onlyfans-leak", "fappening", "rule34", "jav", "deepfake"
        )

        // ── Layer 7: Expanded TLDs (নতুন এক্সটেনশন ব্লকার) ──
        private val ADULT_TLDS = setOf(".xxx", ".porn", ".adult", ".sex", ".cam", ".tube")

        // ── Remote Blocklist prefs keys ──
        private const val REMOTE_LIST_URL =
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts"
        private const val PREF_REMOTE_DOMAINS   = "remote_adult_domains"
        private const val PREF_LAST_UPDATE_TIME = "remote_list_last_update"

        // ─── Ad Network Domains ───────────────────────────────────────────────
        private val AD_DOMAINS = setOf(
            "doubleclick.net", "googlesyndication.com", "adservice.google.com",
            "googleadservices.com", "pagead2.googlesyndication.com", "tpc.googlesyndication.com",
            "securepubads.g.doubleclick.net", "stats.g.doubleclick.net", "cm.g.doubleclick.net",
            "ad.doubleclick.net", "googleads.g.doubleclick.net", "imasdk.googleapis.com",
            "static.doubleclick.net", "www.googleadservices.com", "amazon-adsystem.com",
            "adsystem.amazon.com", "fls-na.amazon.com", "an.facebook.com", "connect.facebook.net",
            "adnxs.com", "ib.adnxs.com", "secure.adnxs.com", "acdn.adnxs.com", "rubiconproject.com",
            "pixel.rubiconproject.com", "pubmatic.com", "ads.pubmatic.com", "simage2.pubmatic.com",
            "openx.net", "criteo.com", "criteo.net", "adsrvr.org", "advertising.com", "appnexus.com",
            "bidswitch.net", "casalemedia.com", "indexexchange.com", "lijit.com", "sovrn.com",
            "yieldmo.com", "media.net", "mathtag.com", "pixel.mathtag.com", "adsafeprotected.com",
            "eyeota.net", "moatads.com", "pixel.moatads.com", "taboola.com", "cdn.taboola.com",
            "trc.taboola.com", "outbrain.com", "revcontent.com", "mgid.com", "zergnet.com",
            "adblade.com", "ads.twitter.com", "static.ads-twitter.com", "analytics.twitter.com",
            "bat.bing.com", "hotjar.com", "mouseflow.com", "fullstory.com", "logrocket.com",
            "scorecardresearch.com", "quantserve.com", "semasio.net", "exelate.com", "bluekai.com",
            "demdex.net", "turn.com", "agkn.com", "segment.io", "banner.siteimprove.com"
        )

        // ─── Tracker Domains ──────────────────────────────────────────────────
        private val TRACKER_DOMAINS = setOf(
            "google-analytics.com", "googletagmanager.com", "googletagservices.com",
            "analytics.google.com", "ssl.google-analytics.com", "www.google-analytics.com",
            "stats.wp.com", "pixel.wp.com", "bat.bing.com", "analytics.twitter.com",
            "t.co", "connect.facebook.net", "graph.facebook.com", "analytics.yahoo.com",
            "beacon.yahoo.com", "clicks.beap.bc.yahoo.com", "piwik.org", "matomo.org",
            "statcounter.com", "clicktale.net", "clicktale.com", "crazyegg.com", "trackjs.com",
            "raygun.io", "bugsnag.com", "newrelic.com", "nr-data.net", "amplitude.com",
            "api.amplitude.com", "cdn.amplitude.com", "mixpanel.com", "cdn4.mxpnl.com",
            "segment.com", "cdn.segment.com", "api.segment.io", "cdn.heapanalytics.com",
            "heapanalytics.com", "rollbar.com", "sentry.io", "ingest.sentry.io",
            "browser.sentry-cdn.com", "intercom.io", "widget.intercom.io", "nexus.ensighten.com"
        )

        // ─── Adult Content Domains ────────────────────────────────────────────
        private val ADULT_DOMAINS = setOf(
            "xnxx.com", "xnxx.tv", "xnxx.net", "xnxx.org", "xvideos.com", "xvideos.net", "xvideos.red",
            "xhamster.com", "xhamster.desi", "xhamster.one", "xhamster.xxx", "pornhub.com", "pornhub.net",
            "pornhub.org", "onlyfans.com", "brazzers.com", "redtube.com", "youporn.com", "spankbang.com",
            "livejasmin.com", "chaturbate.com", "stripchat.com", "camsoda.com", "cam4.com", "bongacams.com",
            "myfreecams.com", "streamate.com", "ifsa.tv", "hclips.com", "hdzog.com", "tnaflix.com", "tube8.com",
            "extremetube.com", "keezmovies.com", "slutload.com", "beeg.com", "drtuber.com", "hardsextube.com",
            "fuq.com", "vjav.com", "porntrex.com", "empflix.com", "4tube.com", "porntube.com", "ah-me.com",
            "txxx.com", "xbabe.com", "xcafe.com", "wetplace.com", "sunporno.com", "fapster.com", "ok.xxx",
            "xxxbunker.com", "desixnxx.net", "desitvforum.net", "indianpornvideos.com", "18videosz.com",
            "24porn.com", "3movs.com", "adulttime.com", "allofgfs.com", "alohatube.com", "alotporn.com",
            "alphaporno.com", "anon-v.com", "anyshemale.com", "arabianchicks.com", "avn.com", "baberotica.com",
            "babes.com", "badoinkvr.com", "bang.com", "bangbrosnetwork.com", "bdsmstreak.com", "bestpornbabes.com",
            "besttrannypornsites.com", "bestxxxsites.com", "bigtits.com", "blacked.com", "bobs-tube.com",
            "boysfood.com", "braincash.com", "brokestraightboys.com", "camhub.cc", "cams.com", "cliphunter.com",
            "clips4sale.com", "czechvr.com", "dansmovies.com", "daredorm.com", "ddfnetwork.com", "deviantclip.com",
            "digitalplayground.com", "dorcelclub.com", "eggporncomics.com", "eporner.com", "eroxia.com",
            "evilangel.com", "fakehub.com", "fakku.net", "fantasti.cc", "fapster.xxx", "forhertube.com",
            "free18.net", "freepornfull.com", "fux.com", "gayfuror.com", "gaymaletube.com", "gaytube.com",
            "gelbooru.com", "gfrevenge.com", "girlsway.com", "gotgayporn.com", "h2porn.com", "handjobhub.com",
            "helixstudios.net", "hentai-foundry.com", "hentaicore.org", "hentaigasm.com", "hentaihaven.org",
            "hentaipulse.com", "hotgoo.com", "hotsouthindiansex.com", "hustler.com", "iknowthatgirl.com",
            "imlive.com", "ixxx.com", "iyalc.com", "japanhdv.com", "javhd.com", "jerkmate.com", "jizzhut.com",
            "jizzonline.com", "justusboys.com", "kinkyfamily.com", "kporno.com", "lesbian8.com", "letsjerk.is",
            "lovehomeporn.com", "lubetube.com", "luckycrush.live", "madthumbs.com", "manporn.xxx", "maxim.com",
            "maxiporn.com", "metaporn.com", "mofosex.com", "mogosnetwork.com", "motherless.com", "moviefap.com",
            "myporngay.com", "mythav.com", "netfapx.com", "newsensations.com", "nonktube.com", "nubiles.net",
            "nuvid.com", "orgasm.com", "perfectgirls.net", "perfectgonzo.com", "pervclips.com", "playboy.com",
            "porcore.com", "porn.com", "porn300.xxx", "porn7.xxx", "porndroids.com", "pornerbros.com",
            "pornfuror.com", "pornhd.com", "pornheed.com", "pornhost.com", "pornhubselect.com", "pornmate.com",
            "pornmd.com", "pornmilo.com", "pornotube.com", "pornoxo.com", "pornprosnetwork.com", "pornrabbit.com",
            "pornrox.com", "pornstarnetwork.com", "pornxio.com", "proporn.com", "punishbang.com", "punishtube.com",
            "realitykings.com", "redgifs.com", "redporn.xxx", "rk.com", "rockettube.com", "rude.com",
            "sankakucomplex.com", "sexlikereal.com", "sexvid.xxx", "shameless.com", "shemailhd.sex",
            "shooshtime.com", "slutroulette.com", "spankwire.com", "submityourflicks.com", "submityourtapes.com",
            "teamskeet.com", "theporndude.com", "thumbzilla.com", "tiava.com", "topfreepornvideos.com",
            "toppornsites.com", "tranny.one", "tubegalore.com", "tubegals.com", "tubev.sex", "twilightsex.com",
            "twistysnetwork.com", "videosz.com", "viewdesisex.com", "virtualtaboo.com", "vixen.com", "vporn.com",
            "vrcock.com", "vrcosplay.com", "vrporn.com", "vrsmash.com", "wankzvr.com", "watch-my-gf.com",
            "watch-my-gf.me", "watchindianporn.net", "watchmyexgf.net", "watchmygf.me", "watchmygf.tv",
            "xmoviesforyou.com", "xnxxhamster.net", "xpaja.net", "xtube.com", "xxvids.net", "xxx.com",
            "xxxaporn.com", "xxxvideos247.com", "youjizz.com", "youporngay.com", "yuvutu.com", "zbporn.com",
            "zzcartoon.com", "zzgays.com", "pornpics.com", "imagefap.com", "erome.com", "hqporner.com",
            "cyberdrop.me", "porntn.com", "sex.com", "twistys.com", "heavy-r.com", "fapello.com", "coomer.party",
            "coomer.su", "kemono.party", "kemono.su", "rule34.xxx", "e621.net", "nudevista.com", "scrolller.com",
            "xgifer.com", "babepedia.com", "vipergirls.to", "thefappeningblog.com", "nudecelebforum.com",
            "mrdeepfakes.com", "bdsmlr.com", "luscious.net", "nhentai.net", "multiporn.net", "hdporn.net",
            "yespornplease.com", "porn-plus.com", "anysex.com", "pornflip.com", "pictoa.com", "imgbox.com"
        )

        fun buildBlockedPage(url: String, reason: BlockReason): String {
            val (icon, title, subtitle, color) = when (reason) {
                BlockReason.ADULT    -> Quadruple("🔒", "Site Blocked",    "This site contains adult content and has been blocked for safe browsing.", "#E53E3E")
                BlockReason.AD       -> Quadruple("🛡️", "Ad Blocked",      "An advertisement or tracker was blocked.",                                 "#38A169")
                BlockReason.TRACKER  -> Quadruple("👁️", "Tracker Blocked", "A tracking script was prevented from loading.",                            "#3182CE")
                BlockReason.KIDS_MODE-> Quadruple("👶", "Not Allowed",     "This site is not on the approved list for Kids Mode.",                     "#805AD5")
            }
            return """
                <!DOCTYPE html><html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0">
                <style>
                  * { margin:0; padding:0; box-sizing:border-box; }
                  html, body { width: 100%; height: 100vh; overflow: hidden; }
                  body { font-family: -apple-system, sans-serif; background: #F7FAFC;
                         display:flex; align-items:center; justify-content:center; padding:24px; }
                  .card { background:white; border-radius:20px; padding:40px 32px;
                          text-align:center; max-width:400px; width:100%;
                          box-shadow: 0 10px 30px rgba(0,0,0,0.1); }
                  .icon { font-size:64px; margin-bottom:20px; }
                  h1 { font-size:26px; font-weight:700; color:#1A202C; margin-bottom:12px; }
                  p { color:#718096; font-size:16px; line-height:1.6; margin-bottom:24px; }
                  .url { background:#EDF2F7; border-radius:8px; padding:12px;
                         font-size:13px; color:#A0AEC0; word-break:break-all; margin-bottom:24px; }
                  .badge { display:inline-block; background:$color; color:white;
                           border-radius:20px; padding:8px 18px; font-size:14px; font-weight:600; }
                  .back-btn { display:block; margin-top:24px; padding:16px;
                              background:#E2E8F0; border-radius:12px; color:#4A5568;
                              font-size:16px; font-weight:600; text-decoration:none; }
                </style></head><body>
                <div class="card">
                  <div class="icon">$icon</div>
                  <h1>$title</h1>
                  <p>$subtitle</p>
                  <div class="url">$url</div>
                  <span class="badge">Family Browser Protection</span>
                  <a href="javascript:history.back()" class="back-btn">← Go Back</a>
                </div></body></html>
            """.trimIndent()
        }

        // ── Internal: domain match helper (Updated with TLDs) ──
        private fun isAdultHost(host: String): Boolean {
            if (ADULT_TLDS.any { host.endsWith(it) }) return true
            return ADULT_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }
        }

        // ── Public helper — FloatingWindowService + YoutubeFloatingWindowService ব্যবহার করে ──
        fun isAdultSite(url: String): Boolean {
            return try {
                val host = android.net.Uri.parse(url).host?.lowercase()
                    ?.removePrefix("www.") ?: return false
                isAdultHost(host)
            } catch (e: Exception) { false }
        }
    }

    // ─── Remote Blocklist (StevenBlack Adult List) ────────────────────────────
    private val remoteDomainSet = mutableSetOf<String>()

    var remoteListDomainCount: Int = 0
        private set
    var remoteListLastUpdated: Long = 0L
        private set

    // App start হলে call করো — BrowserViewModel এর init{} এ
    fun initRemoteBlocklist() {
        loadCachedDomainsFromDisk()
        CoroutineScope(Dispatchers.IO).launch {
            val lastUpdate = prefs.getLong(PREF_LAST_UPDATE_TIME, 0L)
            val now = System.currentTimeMillis()
            if (now - lastUpdate > TimeUnit.DAYS.toMillis(7)) {
                fetchAndCacheRemoteList()
            }
        }
    }

    // Settings থেকে "Update Now" button এ call করো
    fun forceUpdateRemoteBlocklist(onDone: (success: Boolean, count: Int) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = fetchAndCacheRemoteList()
            withContext(Dispatchers.Main) {
                onDone(success, remoteListDomainCount)
            }
        }
    }

    private suspend fun fetchAndCacheRemoteList(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val conn = URL(REMOTE_LIST_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout    = 30_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connect()
            if (conn.responseCode != 200) return@withContext false

            val domains = mutableSetOf<String>()
            conn.inputStream.bufferedReader().forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val domain = parts[1].lowercase().trim()
                    if (domain.isNotEmpty() && domain != "localhost" &&
                        domain != "0.0.0.0" && domain != "broadcasthost" &&
                        !domain.startsWith("#")) {
                        domains.add(domain)
                    }
                }
            }
            conn.disconnect()
            if (domains.isEmpty()) return@withContext false

            remoteDomainSet.clear()
            remoteDomainSet.addAll(domains)
            remoteListDomainCount = domains.size
            remoteListLastUpdated = System.currentTimeMillis()

            prefs.edit()
                .putString(PREF_REMOTE_DOMAINS, domains.joinToString("\n"))
                .putLong(PREF_LAST_UPDATE_TIME, remoteListLastUpdated)
                .apply()
            true
        } catch (e: Exception) { false }
    }

    private fun loadCachedDomainsFromDisk() {
        val saved = prefs.getString(PREF_REMOTE_DOMAINS, null) ?: return
        val domains = saved.split("\n").filter { it.isNotBlank() }.toSet()
        remoteDomainSet.clear()
        remoteDomainSet.addAll(domains)
        remoteListDomainCount = domains.size
        remoteListLastUpdated = prefs.getLong(PREF_LAST_UPDATE_TIME, 0L)
    }

    private fun isInRemoteBlocklist(host: String): Boolean {
        if (remoteDomainSet.isEmpty()) return false
        if (remoteDomainSet.contains(host)) return true
        val parent = host.split(".").drop(1).joinToString(".")
        return parent.isNotEmpty() && remoteDomainSet.contains(parent)
    }

    // ─── State ────────────────────────────────────────────────────────────────
    var isAdBlockEnabled: Boolean = true
    var isTrackerBlockEnabled: Boolean = true
    var isAdultBlockEnabled: Boolean = true
    var isKeywordBlockEnabled: Boolean = true
    var isDohEnabled: Boolean = true
    var isSafeSearchEnabled: Boolean = true

    @get:JvmName("getAdultBlockPinValue")
    @set:JvmName("setAdultBlockPinValue")
    var adultBlockPin: String = ""

    var trackerBlockCount: Int = 0
        private set
    var adBlockCount: Int = 0
        private set

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context, "adblocker_prefs", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("adblocker_prefs", Context.MODE_PRIVATE)
        }
    }

    init { loadSettings(); initRemoteBlocklist() }

    // ─── Navigation Block (shouldOverrideUrlLoading এ call করো) ─────────────
    fun shouldBlockNavigation(url: String): String? {
        if (!isAdultBlockEnabled) return null
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return null
            val lowerUrl = url.lowercase()

            if (isAdultHost(host) || isInRemoteBlocklist(host)) return buildBlockedPage(url, BlockReason.ADULT)

            if (isKeywordBlockEnabled) {
                val keywordBlocked = ADULT_URL_KEYWORDS.any { keyword -> lowerUrl.contains(keyword) }
                if (keywordBlocked) return buildBlockedPage(url, BlockReason.ADULT)
            }

            null
        } catch (e: Exception) { null }
    }

    // ─── Main Intercept (shouldInterceptRequest এ call করো) ──────────────────
    fun shouldBlock(
        request: WebResourceRequest,
        isKidsMode: Boolean = false,
        kidsWhitelist: Set<String> = emptySet()
    ): WebResourceResponse? {
        val url = request.url?.toString() ?: return null
        val host = request.url?.host?.lowercase() ?: return null
        val lowerUrl = url.lowercase()
        val isMainFrame = request.isForMainFrame

        if (isKidsMode && isMainFrame) {
            val allowed = kidsWhitelist.any { host.endsWith(it) || host == it }
            if (!allowed) return blockedPageResponse(url, BlockReason.KIDS_MODE)
        }

        if (isAdultBlockEnabled) {
            if (isAdultHost(host) || isInRemoteBlocklist(host)) {
                return if (isMainFrame) blockedPageResponse(url, BlockReason.ADULT) else emptyResponse()
            }
        }

        if (isAdultBlockEnabled && isKeywordBlockEnabled && isMainFrame) {
            if (ADULT_URL_KEYWORDS.any { keyword -> lowerUrl.contains(keyword) }) {
                return blockedPageResponse(url, BlockReason.ADULT)
            }
        }

        if (isAdBlockEnabled && AD_DOMAINS.any { domain -> host.endsWith(domain) || host == domain }) {
            adBlockCount++
            return emptyResponse()
        }

        if (isTrackerBlockEnabled && TRACKER_DOMAINS.any { host.contains(it) }) {
            trackerBlockCount++
            return emptyResponse()
        }

        return null
    }

    // ─── Safe Search ──────────────────────────────────────────────────────────
    fun applySafeSearch(webView: WebView, url: String): Boolean {
        if (!isSafeSearchEnabled) return false
        val safeUrl = buildSafeSearchUrl(url) ?: return false
        if (safeUrl == url) return false
        webView.post { webView.loadUrl(safeUrl) }
        return true
    }

    fun buildSafeSearchUrl(url: String): String? {
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return null

            if (host.contains("google.") && url.contains("/search")) {
                return when {
                    url.contains("safe=strict") -> null
                    url.contains("safe=")        -> url.replace(Regex("safe=(off|images|moderate|active)"), "safe=strict")
                    else                         -> url + (if (url.contains("?")) "&" else "?") + "safe=strict"
                }
            }
            if (host.contains("bing.com") && url.contains("/search")) {
                return when {
                    url.contains("adlt=strict") -> null
                    url.contains("adlt=")        -> url.replace(Regex("adlt=(off|moderate)"), "adlt=strict")
                    else                         -> url + (if (url.contains("?")) "&" else "?") + "adlt=strict"
                }
            }
            if (host.contains("duckduckgo.com") && url.contains("q=")) {
                return when {
                    url.contains("kp=1")  -> null
                    url.contains("kp=")   -> url.replace(Regex("kp=(-2|-1|0)"), "kp=1")
                    else                  -> url + (if (url.contains("?")) "&" else "?") + "kp=1"
                }
            }
            null
        } catch (e: Exception) { null }
    }

    // ─── Configuration & Persistence ──────────────────────────────────────────
    fun setAdultBlockPin(pin: String) {
        adultBlockPin = pin
        prefs.edit().putString("adult_pin", pin).apply()
    }
    fun verifyPin(pin: String): Boolean = pin == adultBlockPin
    fun disableAdultBlockWithPin(pin: String): Boolean {
        return if (verifyPin(pin)) { isAdultBlockEnabled = false; saveSettings(); true } else false
    }

    fun saveSettings() {
        prefs.edit().putBoolean("ad_block", isAdBlockEnabled)
            .putBoolean("tracker_block", isTrackerBlockEnabled)
            .putBoolean("adult_block", isAdultBlockEnabled)
            .putBoolean("keyword_block", isKeywordBlockEnabled)
            .putBoolean("doh_enabled", isDohEnabled)
            .putBoolean("safe_search", isSafeSearchEnabled)
            .putString("adult_pin", adultBlockPin).apply()
    }

    private fun loadSettings() {
        isAdBlockEnabled = prefs.getBoolean("ad_block", true)
        isTrackerBlockEnabled = prefs.getBoolean("tracker_block", true)
        isAdultBlockEnabled = prefs.getBoolean("adult_block", true)
        isKeywordBlockEnabled = prefs.getBoolean("keyword_block", true)
        isDohEnabled = prefs.getBoolean("doh_enabled", true)
        isSafeSearchEnabled = prefs.getBoolean("safe_search", true)
        adultBlockPin = prefs.getString("adult_pin", "") ?: ""
    }

    fun resetCounts() { adBlockCount = 0; trackerBlockCount = 0 }
    fun getDohSetupInstructions(): String = "Settings → Network → Private DNS → Hostname: family.cloudflare-dns.com"
    fun isPrivateDnsLikelyEnabled(): Boolean = false

    private fun blockedPageResponse(url: String, reason: BlockReason): WebResourceResponse {
        val html = buildBlockedPage(url, reason)
        return WebResourceResponse("text/html", "UTF-8", 200, "OK", mapOf("Content-Type" to "text/html"), ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)))
    }
    private fun emptyResponse(): WebResourceResponse = WebResourceResponse("text/plain", "UTF-8", 200, "OK", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}

// ─── SafeSearchEnforcer — top-level object (fully qualified access) ───────────
object SafeSearchEnforcer {
    fun enforceIfNeeded(url: String): String? {
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return null
            when {
                host.contains("google.") && url.contains("/search") -> when {
                    url.contains("safe=strict") -> null
                    url.contains("safe=") -> url.replace(Regex("safe=(off|images|moderate|active)"), "safe=strict")
                    else -> url + (if (url.contains("?")) "&" else "?") + "safe=strict"
                }
                host.contains("bing.com") && url.contains("/search") -> when {
                    url.contains("adlt=strict") -> null
                    url.contains("adlt=") -> url.replace(Regex("adlt=(off|moderate)"), "adlt=strict")
                    else -> url + (if (url.contains("?")) "&" else "?") + "adlt=strict"
                }
                host.contains("duckduckgo.com") && url.contains("q=") -> when {
                    url.contains("kp=1") -> null
                    url.contains("kp=") -> url.replace(Regex("kp=(-2|-1|0)"), "kp=1")
                    else -> url + (if (url.contains("?")) "&" else "?") + "kp=1"
                }
                else -> null
            }
        } catch (e: Exception) { null }
    }
}


// ─── ২. WebViewClient ক্লাস (JavaScript Injector সহ) ────────────────────────
class FamilyWebViewClient(private val adBlocker: AdBlocker) : WebViewClient() {

    // ── Layer 2 & 3: URL Intercept & SafeSearch ──
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()

        // 1. Adult URL / Keyword Blocker
        val blockedHtml = adBlocker.shouldBlockNavigation(url)
        if (blockedHtml != null) {
            view.loadDataWithBaseURL(null, blockedHtml, "text/html", "UTF-8", null)
            return true
        }

        // 2. SafeSearch Enforcer
        if (adBlocker.applySafeSearch(view, url)) {
            return true
        }

        return super.shouldOverrideUrlLoading(view, request)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        return adBlocker.shouldBlock(request) ?: super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        injectMultiLayerScanner(view)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        injectMultiLayerScanner(view)
    }

    // ── Layer 4, 5, 6: Dynamic Content Scanner (Meta, Image, DOM) ──
    private fun injectMultiLayerScanner(view: WebView?) {
        if (view == null || !adBlocker.isAdultBlockEnabled) return

        // Kotlin-এর লিস্টকে জাভাস্ক্রিপ্ট অ্যারেতে কনভার্ট করা হচ্ছে
        val jsKeywordsArray = AdBlocker.ADULT_URL_KEYWORDS.joinToString("','", "['", "']")

        val jsScannerCode = """
            javascript:(function() {
                const badWords = $jsKeywordsArray;
                
                function executeBlock() {
                    window.stop();
                    document.head.innerHTML = '<meta name="viewport" content="width=device-width, initial-scale=1">';
                    document.body.innerHTML = `
                        <div style="display:flex; height:100vh; align-items:center; justify-content:center; background-color:#F7FAFC; font-family:-apple-system, sans-serif;">
                            <div style="text-align:center; padding:40px; background:white; border-radius:20px; box-shadow:0 10px 30px rgba(0,0,0,0.1); width: 85%; max-width: 400px;">
                                <div style="font-size:64px; margin-bottom:20px;">🔒</div>
                                <h1 style="color:#E53E3E; font-size:24px; margin-bottom:10px;">Content Blocked</h1>
                                <p style="color:#718096; font-size: 15px; margin-bottom: 24px;">Family Browser has blocked this page to ensure safe browsing.</p>
                                <button onclick="window.history.back()" style="padding:12px 24px; background:#E2E8F0; border:none; border-radius:12px; font-weight:600; color:#4A5568; width: 100%;">Go Back</button>
                            </div>
                        </div>
                    `;
                }

                function checkContent() {
                    let shouldBlock = false;

                    // ── Layer 5: Meta Tag & RTA Check ──
                    const metaRating = document.querySelector('meta[name="rating" i]');
                    const metaRTA = document.querySelector('meta[name="RATING" i]');
                    if ((metaRating && metaRating.content.toLowerCase() === 'adult') || 
                        (metaRTA && metaRTA.content.includes('RTA-5042'))) {
                        shouldBlock = true;
                    }

                    // ── Layer 4: DOM Text Scan ──
                    if (!shouldBlock) {
                        const titleText = document.title.toLowerCase();
                        // পারফরম্যান্সের জন্য শুধু প্রথম ৫০০০ ক্যারেক্টার চেক করা হচ্ছে
                        const bodyText = document.body ? document.body.innerText.substring(0, 5000).toLowerCase() : "";
                        const contentToScan = titleText + " " + bodyText;
                        
                        shouldBlock = badWords.some(word => {
                            const regex = new RegExp('\\b' + word + '\\b');
                            return regex.test(contentToScan);
                        });
                    }

                    // ── Layer 6: Image Source & Alt Text Scan ──
                    if (!shouldBlock) {
                        const images = document.getElementsByTagName('img');
                        // সর্বোচ্চ ১০০ টি ইমেজ স্ক্যান করা হচ্ছে ব্রাউজার ফাস্ট রাখার জন্য
                        const maxImages = Math.min(images.length, 100); 
                        for (let i = 0; i < maxImages; i++) {
                            const imgSrc = images[i].src ? images[i].src.toLowerCase() : "";
                            const imgAlt = images[i].alt ? images[i].alt.toLowerCase() : "";
                            
                            const hasBadImage = badWords.some(word => imgSrc.includes(word) || imgAlt.includes(word));
                            if (hasBadImage) {
                                shouldBlock = true;
                                break;
                            }
                        }
                    }

                    if (shouldBlock) {
                        executeBlock();
                    }
                }
                
                // সাথে সাথেই একবার চেক করুন
                checkContent();
                
                // ডাইনামিক (AJAX/React) পেজ লোডের জন্য MutationObserver 
                if (!window.hasFamilyBlockerObserver) {
                    window.hasFamilyBlockerObserver = true;
                    const observer = new MutationObserver(function(mutations) {
                        checkContent();
                    });
                    if (document.body) {
                        observer.observe(document.body, { childList: true, subtree: true });
                    }
                }
            })();
        """.trimIndent()

        view.evaluateJavascript(jsScannerCode, null)
    }
}

// ─── ৩. ডেটা ক্লাস এবং এনাম (Data Class & Enum) ──────────────────────────────
enum class BlockReason { ADULT, AD, TRACKER, KIDS_MODE }
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


// ─── ৪. YouTube Ad Pruner — uBlock Origin json-prune approach ────────────────
/**
 * YouTubeAdPruner
 *
 * uBlock Origin এর exact approach:
 *   youtube.com##+js(json-prune, playerResponse.adPlacements playerResponse.playerAds adPlacements playerAds adSlots)
 *
 * এটা YouTube এর /youtubei/v1/player API response থেকে ad fields সরিয়ে দেয়।
 * YouTube player তখন মনে করে কোনো ad নেই — ad request-ই যায় না।
 *
 * দুই layer:
 *   1. shouldInterceptRequest → POST response intercept (background thread)
 *   2. JS inject → fetch/XHR intercept (page-side, real-time)
 */
object YouTubeAdPruner {

    // YouTube player API endpoints যেগুলো ad data বহন করে
    private val YT_PLAYER_ENDPOINTS = listOf(
        "/youtubei/v1/player",
        "/youtubei/v1/next",
        "/youtubei/v1/browse"
    )

    // uBlock Origin এর exact field list — json-prune target
    private val AD_FIELDS = listOf(
        "adPlacements",
        "playerAds",
        "adSlots",
        "adBreakHeartbeatParams",
        "auxiliaryUi",          // overlay ad
        "adMessagingConfig",
        "adVideoId"
    )

    /**
     * shouldInterceptRequest এ call করো।
     * YouTube player URL হলে response fetch করে ad fields prune করে return করে।
     * অন্য URL হলে null return করে (normal flow চলে)।
     */
    fun interceptPlayerResponse(
        request: android.webkit.WebResourceRequest
    ): android.webkit.WebResourceResponse? {
        val url = request.url?.toString() ?: return null

        // YouTube player endpoint কিনা দেখো
        val isPlayerEndpoint = YT_PLAYER_ENDPOINTS.any { url.contains(it) }
        if (!isPlayerEndpoint) return null

        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection

            // Original headers copy — cookie, auth সব দরকার
            request.requestHeaders.forEach { (key, value) ->
                try { connection.setRequestProperty(key, value) } catch (_: Exception) {}
            }

            // YouTube mostly GET করে player endpoint এ
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()

            if (connection.responseCode != 200) return null

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val pruned = pruneAdFields(body)

            android.webkit.WebResourceResponse(
                connection.contentType ?: "application/json",
                "UTF-8",
                pruned.byteInputStream(Charsets.UTF_8)
            )
        } catch (_: Exception) {
            // Network error বা JSON parse fail — normal load হতে দাও
            null
        }
    }

    /**
     * JSON string থেকে ad-related fields সরিয়ে দাও।
     * uBlock Origin এর json-prune scriptlet এর exact equivalent।
     */
    fun pruneAdFields(json: String): String {
        if (json.isBlank()) return json
        return try {
            val obj = org.json.JSONObject(json)
            removeAdFields(obj)
            obj.toString()
        } catch (_: Exception) {
            json // parse fail হলে original দাও
        }
    }

    private fun removeAdFields(obj: org.json.JSONObject) {
        // Top-level fields সরাও
        AD_FIELDS.forEach { field -> obj.remove(field) }

        // Nested playerResponse এ সরাও
        obj.optJSONObject("playerResponse")?.let { pr ->
            AD_FIELDS.forEach { field -> pr.remove(field) }
        }

        // contents array তে sponsored items সরাও
        obj.optJSONObject("contents")?.let { contents ->
            pruneContents(contents)
        }
    }

    private fun pruneContents(obj: org.json.JSONObject) {
        // twoColumnBrowseResultsRenderer → tabs → tabRenderer → content
        // এর মধ্যে promotedVideoRenderer, searchPyvRenderer সরাও
        val sponsoredKeys = listOf(
            "promotedVideoRenderer",
            "searchPyvRenderer",
            "promotedSparklesWebRenderer",
            "adSlotRenderer"
        )

        val iter = obj.keys()
        while (iter.hasNext()) {
            val key = iter.next()
            if (sponsoredKeys.contains(key)) {
                iter.remove()
                continue
            }
            when (val value = obj.opt(key)) {
                is org.json.JSONObject -> pruneContents(value)
                is org.json.JSONArray -> pruneArray(value)
            }
        }
    }

    private fun pruneArray(arr: org.json.JSONArray) {
        for (i in 0 until arr.length()) {
            when (val item = arr.opt(i)) {
                is org.json.JSONObject -> pruneContents(item)
                is org.json.JSONArray  -> pruneArray(item)
            }
        }
    }

    /**
     * JS inject করার script — page load এ একবার inject করলেই হয়।
     *
     * এটা fetch() এবং XMLHttpRequest দুটোই intercept করে।
     * YouTube যখন /player endpoint এ call করে, response এর আগেই
     * ad fields সরিয়ে দেওয়া হয়।
     *
     * uBlock Origin এর json-prune + nano-setInterval-logger approach।
     */
    fun getJsInjectScript(): String = """
(function() {
    if (window.__rasAdPrunerInstalled) return;
    window.__rasAdPrunerInstalled = true;

    // Ad fields যেগুলো সরাতে হবে — uBlock এর exact list
    var AD_FIELDS = [
        'adPlacements', 'playerAds', 'adSlots',
        'adBreakHeartbeatParams', 'auxiliaryUi',
        'adMessagingConfig', 'adVideoId'
    ];

    function pruneAdFields(json) {
        try {
            var obj = JSON.parse(json);
            removeFields(obj);
            return JSON.stringify(obj);
        } catch(e) {
            return json;
        }
    }

    function removeFields(obj) {
        if (!obj || typeof obj !== 'object') return;
        AD_FIELDS.forEach(function(f) { delete obj[f]; });
        // nested playerResponse
        if (obj.playerResponse) {
            AD_FIELDS.forEach(function(f) { delete obj.playerResponse[f]; });
        }
        // array items
        Object.keys(obj).forEach(function(key) {
            var val = obj[key];
            if (Array.isArray(val)) {
                val.forEach(function(item) { removeFields(item); });
            } else if (val && typeof val === 'object') {
                removeFields(val);
            }
        });
    }

    function isPlayerUrl(url) {
        return url && (
            url.includes('/youtubei/v1/player') ||
            url.includes('/youtubei/v1/next') ||
            url.includes('/youtubei/v1/browse')
        );
    }

    // ── 1. fetch() intercept ──────────────────────────────────────────────────
    var origFetch = window.fetch;
    window.fetch = function(input, init) {
        var url = (typeof input === 'string') ? input : (input && input.url) || '';
        return origFetch.call(this, input, init).then(function(response) {
            if (!isPlayerUrl(url)) return response;
            return response.clone().text().then(function(text) {
                var pruned = pruneAdFields(text);
                return new Response(pruned, {
                    status: response.status,
                    statusText: response.statusText,
                    headers: response.headers
                });
            });
        });
    };

    // ── 2. XMLHttpRequest intercept ───────────────────────────────────────────
    var origOpen = XMLHttpRequest.prototype.open;
    var origSend = XMLHttpRequest.prototype.send;

    XMLHttpRequest.prototype.open = function(method, url) {
        this._rasUrl = url;
        return origOpen.apply(this, arguments);
    };

    XMLHttpRequest.prototype.send = function() {
        if (isPlayerUrl(this._rasUrl)) {
            var xhr = this;
            var origOnReadyStateChange = xhr.onreadystatechange;
            Object.defineProperty(xhr, 'responseText', {
                get: function() {
                    var raw = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText');
                    var text = raw ? raw.get.call(xhr) : '';
                    if (xhr.readyState === 4 && isPlayerUrl(xhr._rasUrl)) {
                        return pruneAdFields(text);
                    }
                    return text;
                },
                configurable: true
            });
        }
        return origSend.apply(this, arguments);
    };

    // ── 3. Skip ad button auto-click (backup) ─────────────────────────────────
    function skipAds() {
        var skip = document.querySelector('.ytp-skip-ad-button, .ytp-ad-skip-button, .ytp-ad-skip-button-modern');
        if (skip) { skip.click(); return; }

        var video = document.querySelector('video');
        var adShowing = document.querySelector('.ad-showing, .ad-interrupting');
        if (video && adShowing && !video.paused) {
            video.currentTime = video.duration || 9999;
        }
    }

    // MutationObserver দিয়ে DOM change track করো
    var observer = new MutationObserver(function() {
        skipAds();
    });
    if (document.body) {
        observer.observe(document.body, { childList: true, subtree: true });
    }

    // Interval backup
    setInterval(skipAds, 500);

    console.log('[RasBrowser] YouTube ad pruner installed');
})();
""".trimIndent()
}