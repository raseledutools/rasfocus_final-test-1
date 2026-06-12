// ============================================================
// RasFocusSettingsComplete.kt
// ONE FILE — Full Settings UI (Jetpack Compose) + All Blocking Logic
//
// SECTIONS:
//  A. Data / Prefs
//  B. Blocking Service (Accessibility)
//  C. All Blocking Handlers (inline)
//  D. Settings UI (Compose)
// ============================================================

package com.rasel.RasFocus.selfcontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.concurrent.TimeUnit
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.provider.Settings
import android.app.Activity
import android.content.Intent
import com.rasel.RasFocus.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning


// ════════════════════════════════════════════════════════════
// A. PREFS — সব toggle এর state এখানে
// ════════════════════════════════════════════════════════════

class BlockerPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("blocker_prefs", Context.MODE_PRIVATE)

    // ── Content Blocking ──
    var blockAdult: Boolean
        get() = prefs.getBoolean("adult", true)
        set(v) = prefs.edit().putBoolean("adult", v).apply()

    // adultsite.txt ফাইল থেকে লোড করা domain list দিয়ে ব্লক
    var blockAdultSiteList: Boolean
        get() = prefs.getBoolean("adult_site_list", false)
        set(v) = prefs.edit().putBoolean("adult_site_list", v).apply()

    var blockSearch: Boolean
        get() = prefs.getBoolean("search", false)
        set(v) = prefs.edit().putBoolean("search", v).apply()

    var blockReels: Boolean
        get() = prefs.getBoolean("reels", false)
        set(v) = prefs.edit().putBoolean("reels", v).apply()

    var blockInstaSearch: Boolean
        get() = prefs.getBoolean("insta_search", false)
        set(v) = prefs.edit().putBoolean("insta_search", v).apply()

    var blockYtShorts: Boolean
        get() = prefs.getBoolean("yt_shorts", true)
        set(v) = prefs.edit().putBoolean("yt_shorts", v).apply()

    var blockWaChannels: Boolean
        get() = prefs.getBoolean("wa_channels", false)
        set(v) = prefs.edit().putBoolean("wa_channels", v).apply()

    // ── নতুন: Block Reels/Shorts per-app prefs ──
    var blockInstaStories: Boolean
        get() = prefs.getBoolean("insta_stories", false)
        set(v) = prefs.edit().putBoolean("insta_stories", v).apply()

    var blockWaStatus: Boolean
        get() = prefs.getBoolean("wa_status", false)
        set(v) = prefs.edit().putBoolean("wa_status", v).apply()

    var blockWaBusinessStatus: Boolean
        get() = prefs.getBoolean("wa_biz_status", false)
        set(v) = prefs.edit().putBoolean("wa_biz_status", v).apply()

    var blockWaBusinessChannels: Boolean
        get() = prefs.getBoolean("wa_biz_channels", false)
        set(v) = prefs.edit().putBoolean("wa_biz_channels", v).apply()

    var blockSnapSpotlight: Boolean
        get() = prefs.getBoolean("snap_spotlight", false)
        set(v) = prefs.edit().putBoolean("snap_spotlight", v).apply()

    var blockSnapStories: Boolean
        get() = prefs.getBoolean("snap_stories", false)
        set(v) = prefs.edit().putBoolean("snap_stories", v).apply()

    var blockTikTok: Boolean
        get() = prefs.getBoolean("tiktok", false)
        set(v) = prefs.edit().putBoolean("tiktok", v).apply()

    var blockTikTokLive: Boolean
        get() = prefs.getBoolean("tiktok_live", false)
        set(v) = prefs.edit().putBoolean("tiktok_live", v).apply()

    // ── Advanced Blocking ──
    var blockUnsupported: Boolean
        get() = prefs.getBoolean("unsupported_browser", false)
        set(v) = prefs.edit().putBoolean("unsupported_browser", v).apply()

    var blockNewApps: Boolean
        get() = prefs.getBoolean("new_apps", false)
        set(v) = prefs.edit().putBoolean("new_apps", v).apply()

    var blockFbVideo: Boolean
        get() = prefs.getBoolean("fb_video", false)
        set(v) = prefs.edit().putBoolean("fb_video", v).apply()

    // ── Uninstall Protection ──
    var uninstallProtection: Boolean
        get() = prefs.getBoolean("uninstall_prot", false)
        set(v) = prefs.edit().putBoolean("uninstall_prot", v).apply()

    // ── Reboot / Power Protection (নতুন switches) ──
    var blockAdb: Boolean
        get() = prefs.getBoolean("block_adb", false)
        set(v) = prefs.edit().putBoolean("block_adb", v).apply()

    var blockPowerOff: Boolean
        get() = prefs.getBoolean("block_poweroff", false)
        set(v) = prefs.edit().putBoolean("block_poweroff", v).apply()

    var blockSafeMode: Boolean
        get() = prefs.getBoolean("block_safemode", false)
        set(v) = prefs.edit().putBoolean("block_safemode", v).apply()

    var blockReboot: Boolean
        get() = prefs.getBoolean("block_reboot", false)
        set(v) = prefs.edit().putBoolean("block_reboot", v).apply()

    var blockRecovery: Boolean
        get() = prefs.getBoolean("block_recovery", false)
        set(v) = prefs.edit().putBoolean("block_recovery", v).apply()

    // ── Customize Blocked Screen ──
    var blockedMessage: String
        get() = prefs.getString("blocked_msg", "This page is blocked.") ?: "This page is blocked."
        set(v) = prefs.edit().putString("blocked_msg", v).apply()

    var blockedCountdown: Int
        get() = prefs.getInt("blocked_countdown", 3)
        set(v) = prefs.edit().putInt("blocked_countdown", v).apply()

    var redirectUrl: String
        get() = prefs.getString("redirect_url", "https://www.google.com") ?: "https://www.google.com"
        set(v) = prefs.edit().putString("redirect_url", v).apply()

    // ── Focus Lock ──
    // mode: "none" | "self" | "parents" | "longtext"
    var focusLockMode: String
        get() = prefs.getString("focus_lock_mode", "none") ?: "none"
        set(v) = prefs.edit().putString("focus_lock_mode", v).apply()

    var focusLockActive: Boolean
        get() = prefs.getBoolean("focus_lock_active", false)
        set(v) = prefs.edit().putBoolean("focus_lock_active", v).apply()

    // Self mode: end time in millis (System.currentTimeMillis + duration)
    var focusLockEndTime: Long
        get() = prefs.getLong("focus_lock_end_time", 0L)
        set(v) = prefs.edit().putLong("focus_lock_end_time", v).apply()

    // Parents mode: hashed password (simple SHA-like, store as string)
    var focusLockPassword: String
        get() = prefs.getString("focus_lock_password", "") ?: ""
        set(v) = prefs.edit().putString("focus_lock_password", v).apply()

    // Long text mode: the required text (100-word passage)
    var focusLockLongText: String
        get() = prefs.getString("focus_lock_long_text", "") ?: ""
        set(v) = prefs.edit().putString("focus_lock_long_text", v).apply()

    // UI language: "bn" | "en"
    var focusLockLang: String
        get() = prefs.getString("focus_lock_lang", "bn") ?: "bn"
        set(v) = prefs.edit().putString("focus_lock_lang", v).apply()
}


// ════════════════════════════════════════════════════════════
// B. ACCESSIBILITY SERVICE — Main Entry Point
// ════════════════════════════════════════════════════════════

class RasFocusBlockingService : AccessibilityService() {

    companion object {
        var instance: RasFocusBlockingService? = null
    }

    private lateinit var prefs: BlockerPrefs
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private lateinit var windowManager: WindowManager

    // ════════════════════════════════════════════════════════
    // OVERLAY — Block কারণ সহ popup দেখায়, তারপর HOME
    // ════════════════════════════════════════════════════════

    // ── Motivational Quotes — feature অনুযায়ী আলাদা list ──
    // প্রতিটা entry: Pair(quote, author)
    private val QUOTES_DEFAULT = listOf(
        Pair("তোমার সময় সীমিত, তা অন্যের জীবন যাপনে নষ্ট করো না।", "স্টিভ জবস"),
        Pair("শৃঙ্খলাই স্বাধীনতার সেতু।", "জিম রোহন"),
        Pair("যে নিজেকে নিয়ন্ত্রণ করতে পারে, সে সবকিছু জয় করতে পারে।", "এপিকটেটাস"),
        Pair("মনোযোগ হলো নতুন যুগের মুদ্রা — তা সাবধানে ব্যয় করো।", "ক্যাল নিউপোর্ট"),
        Pair("ছোট ছোট অভ্যাসই বড় পরিবর্তন আনে।", "জেমস ক্লিয়ার"),
        Pair("প্রতিটি মুহূর্ত যা তুমি সোশ্যাল মিডিয়ায় কাটাও, সেটা তোমার স্বপ্নের কাছ থেকে দূরে সরে যাওয়া।", "RasFocus+"),
        Pair("নিজের উপর নিয়ন্ত্রণই সবচেয়ে বড় শক্তি।", "লাও জু"),
        Pair("একটা বিক্ষিপ্ত মন কখনো মহৎ কাজ করতে পারে না।", "রবীন্দ্রনাথ ঠাকুর")
    )

    private val QUOTES_BY_FEATURE: Map<String, List<Pair<String, String>>> = mapOf(

        "YouTube Shorts" to listOf(
            Pair("Shorts দেখে সময় নষ্ট করা মানে নিজের ভবিষ্যৎকে ছোট করা।", "RasFocus+"),
            Pair("তুমি কী consume করছ, সেটাই তোমাকে define করে।", "ক্যাল নিউপোর্ট"),
            Pair("একটানা short video দেখা মস্তিষ্কের মনোযোগ ধ্বংস করে।", "Andrew Huberman"),
            Pair("বড় স্বপ্ন দেখতে হলে ছোট distraction ছাড়তে হবে।", "জিম রোহন"),
            Pair("Screen time কমাও, dream time বাড়াও।", "RasFocus+"),
            Pair("যে ব্যক্তি নিজের সময়কে সম্মান করে, সে জীবনে এগিয়ে যায়।", "বেনজামিন ফ্র্যাংকলিন")
        ),

        "Instagram Reels" to listOf(
            Pair("Reels দেখছ? তোমার real life reel হয়ে যাচ্ছে।", "RasFocus+"),
            Pair("সোশ্যাল মিডিয়া তোমার মনোযোগ চুরি করার একটি মেশিন।", "Tristan Harris"),
            Pair("অন্যের highlight reel দেখে নিজের জীবন ম্লান করো না।", "RasFocus+"),
            Pair("প্রতিটি scroll তোমার একটা মূল্যবান মুহূর্ত নিয়ে যাচ্ছে।", "ক্যাল নিউপোর্ট"),
            Pair("যে নিজেকে নিয়ন্ত্রণ করতে পারে, সে সবকিছু জয় করতে পারে।", "এপিকটেটাস"),
            Pair("তোমার মনোযোগই তোমার সবচেয়ে মূল্যবান সম্পদ।", "Robin Sharma")
        ),

        "Facebook Reels" to listOf(
            Pair("Reels দেখছ? তোমার real life reel হয়ে যাচ্ছে।", "RasFocus+"),
            Pair("Facebook তোমার সময় চায়, তুমি কি দিতে রাজি?", "RasFocus+"),
            Pair("প্রতিটি scroll তোমার একটা মূল্যবান মুহূর্ত নিয়ে যাচ্ছে।", "ক্যাল নিউপোর্ট"),
            Pair("মনোযোগ ছাড়া কোনো সাফল্য নেই।", "Robin Sharma"),
            Pair("অভ্যাসই মানুষকে তৈরি করে।", "অ্যারিস্টটল")
        ),

        "Facebook Video" to listOf(
            Pair("একটা ভিডিও শেষ হলে আরেকটা শুরু হয় — এই চক্র ভাঙো।", "RasFocus+"),
            Pair("Deep work করতে হলে shallow distraction ছাড়তে হবে।", "ক্যাল নিউপোর্ট"),
            Pair("তোমার মস্তিষ্ক বিশ্রাম চাইছে, video নয়।", "Andrew Huberman"),
            Pair("সময় একবার চলে গেলে আর ফেরে না।", "ইমাম আল-গাজ্জালী"),
            Pair("যে নিজের সময়কে নষ্ট করে, সে নিজেকেই নষ্ট করে।", "RasFocus+")
        ),

        "Instagram Stories" to listOf(
            Pair("অন্যের story দেখার চেয়ে নিজের story লেখো।", "RasFocus+"),
            Pair("মনোযোগই তোমার সবচেয়ে দামি সম্পদ।", "Robin Sharma"),
            Pair("24 ঘণ্টায় story মিলিয়ে যায়, কিন্তু তোমার হারানো সময় ফেরে না।", "RasFocus+"),
            Pair("যা দেখছ তা তোমার চিন্তাকে প্রভাবিত করে।", "Marcus Aurelius"),
            Pair("Focus করো তোমার লক্ষ্যে, story তে নয়।", "RasFocus+")
        ),

        "WhatsApp Channels" to listOf(
            Pair("প্রয়োজনীয় যোগাযোগ করো, না হলে ফোন রাখো।", "RasFocus+"),
            Pair("তথ্যের অতিরিক্ত ভার মস্তিষ্ককে দুর্বল করে।", "ক্যাল নিউপোর্ট"),
            Pair("Channels scroll করা productive নয়, এটি distraction।", "RasFocus+"),
            Pair("কম পড়ো, বেশি চিন্তা করো।", "হেনরি ডেভিড থরো"),
            Pair("যা জানা দরকার তা শেখো, বাকি সব noise।", "RasFocus+")
        ),

        "WhatsApp Status" to listOf(
            Pair("অন্যের status দেখা সময়ের অপচয়।", "RasFocus+"),
            Pair("নিজের কাজে মনোযোগ দাও, অন্যের জীবনে নয়।", "Marcus Aurelius"),
            Pair("Status দেখার সময়টুকু নিজের জন্য ব্যবহার করো।", "RasFocus+"),
            Pair("যে অন্যের দিকে তাকিয়ে থাকে, সে নিজের পথ হারায়।", "লাও জু"),
            Pair("ছোট ছোট অভ্যাসই বড় পরিবর্তন আনে।", "জেমস ক্লিয়ার")
        ),

        "WA Business Status" to listOf(
            Pair("নিজের কাজে মনোযোগ দাও, অন্যের জীবনে নয়।", "Marcus Aurelius"),
            Pair("Status দেখার সময়টুকু নিজের জন্য ব্যবহার করো।", "RasFocus+"),
            Pair("সময় একবার চলে গেলে আর ফেরে না।", "ইমাম আল-গাজ্জালী"),
            Pair("ছোট ছোট অভ্যাসই বড় পরিবর্তন আনে।", "জেমস ক্লিয়ার")
        ),

        "WA Business Channels" to listOf(
            Pair("তথ্যের অতিরিক্ত ভার মস্তিষ্ককে দুর্বল করে।", "ক্যাল নিউপোর্ট"),
            Pair("কম পড়ো, বেশি চিন্তা করো।", "হেনরি ডেভিড থরো"),
            Pair("Channels scroll করা productive নয়, এটি distraction।", "RasFocus+"),
            Pair("শৃঙ্খলাই স্বাধীনতার সেতু।", "জিম রোহন")
        ),

        "Snapchat Spotlight" to listOf(
            Pair("Spotlight তোমার মনোযোগ কেড়ে নিচ্ছে।", "RasFocus+"),
            Pair("একটানা short video দেখা মস্তিষ্কের মনোযোগ ধ্বংস করে।", "Andrew Huberman"),
            Pair("বড় স্বপ্ন দেখতে হলে ছোট distraction ছাড়তে হবে।", "জিম রোহন"),
            Pair("তোমার সময় সীমিত, তা অন্যের বিনোদনে নষ্ট করো না।", "স্টিভ জবস"),
            Pair("মনোযোগ হলো নতুন যুগের মুদ্রা — তা সাবধানে ব্যয় করো।", "ক্যাল নিউপোর্ট")
        ),

        "Snapchat Stories" to listOf(
            Pair("অন্যের story দেখার চেয়ে নিজের story লেখো।", "RasFocus+"),
            Pair("24 ঘণ্টায় story মিলিয়ে যায়, কিন্তু তোমার হারানো সময় ফেরে না।", "RasFocus+"),
            Pair("নিজের কাজে মনোযোগ দাও, অন্যের জীবনে নয়।", "Marcus Aurelius"),
            Pair("Focus করো তোমার লক্ষ্যে, story তে নয়।", "RasFocus+"),
            Pair("যে অন্যের দিকে তাকিয়ে থাকে, সে নিজের পথ হারায়।", "লাও জু")
        ),

        "TikTok" to listOf(
            Pair("TikTok তোমার ঘণ্টার পর ঘণ্টা গ্রাস করছে — এখনই থামো।", "RasFocus+"),
            Pair("Dopamine trap থেকে বেরিয়ে আসো।", "Andrew Huberman"),
            Pair("তুমি কী consume করছ, সেটাই তোমাকে define করে।", "ক্যাল নিউপোর্ট"),
            Pair("Screen time কমাও, dream time বাড়াও।", "RasFocus+"),
            Pair("একটানা short video মস্তিষ্কের মনোযোগ শক্তি কমিয়ে দেয়।", "Andrew Huberman"),
            Pair("বড় স্বপ্ন দেখতে হলে ছোট distraction ছাড়তে হবে।", "জিম রোহন")
        ),

        "TikTok Live" to listOf(
            Pair("Live দেখে সময় নষ্ট করা মানে নিজের সুযোগ নষ্ট করা।", "RasFocus+"),
            Pair("তোমার মনোযোগই তোমার সবচেয়ে মূল্যবান সম্পদ।", "Robin Sharma"),
            Pair("অন্যের live দেখার বদলে নিজের জীবন নিয়ে কাজ করো।", "RasFocus+"),
            Pair("শৃঙ্খলাই স্বাধীনতার সেতু।", "জিম রোহন")
        ),

        "Adult Content" to listOf(
            Pair("আস্তাগফিরুল্লাহ! নিশ্চয়ই আল্লাহ সবকিছু দেখছেন।", "আল কুরআন"),
            Pair("চোখের জিনা থেকে নিজেকে রক্ষা করো, এটি অন্তরে অন্ধকার ডেকে আনে।", "আল হাদিস"),
            Pair("আল্লাহর ভয় অন্তরে রাখো, সাময়িক আনন্দ চিরস্থায়ী কষ্টের কারণ হতে পারে।", "RasFocus+"),
            Pair("যে ব্যক্তি নিজের প্রবৃত্তিকে নিয়ন্ত্রণ করে, জান্নাতই তার ঠিকানা।", "আল কুরআন"),
            Pair("মুমিনদেরকে বলুন, তারা যেন তাদের দৃষ্টি অবনত রাখে এবং তাদের যৌনাঙ্গের হেফাযত করে।", "সূরা আন-নূর: ৩০"),
            Pair("যিনা এর ধারে কাছেও যেও না, নিশ্চয়ই এটা অশ্লীল কাজ এবং নিকৃষ্ট পথ।", "সূরা বনী ইসরাঈল: ৩২"),
            Pair("চোখের ব্যভিচার হলো হারাম জিনিসের দিকে তাকানো।", "সহীহ বুখারী"),
            Pair("যে ব্যক্তি আল্লাহকে ভয় করে, আল্লাহ তার জন্য পথ বের করে দেন।", "সূরা আত্ব-ত্বালাক: ২"),
            Pair("শয়তানের পদাঙ্ক অনুসরণ করো না; নিশ্চয় সে তোমাদের প্রকাশ্য শত্রু।", "সূরা আল-বাকারাহ: ১৬৮"),
            Pair("অশ্লীলতা পরিহার করো, গোপন হোক বা প্রকাশ্য।", "সূরা আল-আনআম: ১৫১"),
            Pair("ক্ষণস্থায়ী পাপের জন্য চিরস্থায়ী জান্নাত হারিও না।", "RasFocus+"),
            Pair("আল্লাহর কাছে তওবা করো, নিশ্চয়ই তিনি ক্ষমাশীল ও পরম দয়ালু।", "আল কুরআন"),
            Pair("তোমার প্রবৃত্তি তোমাকে ধ্বংসের দিকে নিয়ে যাচ্ছে, এখনই ফিরে এসো আল্লাহর দিকে।", "RasFocus+"),
            Pair("দৃষ্টির হেফাজত করা ঈমানের পূর্ণতার অন্যতম প্রধান লক্ষণ।", "আল হাদিস"),
            Pair("যে ব্যক্তি নিজের কুপ্রবৃত্তিকে জয় করতে পারে, সে-ই প্রকৃত বীর।", "আল হাদিস"),
            Pair("নিজেকে সম্মান করো — তোমার মস্তিষ্ক এর চেয়ে ভালো কিছুর যোগ্য।", "RasFocus+"),
            Pair("মনকে পবিত্র রাখো, তাহলে জীবন সুন্দর হবে।", "ইমাম আল-গাজ্জালী")
        ),

        "Google Search" to listOf(
            Pair("Mindless browsing থেকে বেরিয়ে আসো।", "RasFocus+"),
            Pair("কম পড়ো, বেশি চিন্তা করো।", "হেনরি ডেভিড থরো"),
            Pair("তথ্যের অতিরিক্ত ভার মস্তিষ্ককে দুর্বল করে।", "ক্যাল নিউপোর্ট"),
            Pair("যা জানা দরকার তা শেখো, বাকি সব noise।", "RasFocus+")
        ),

        "Instagram Search" to listOf(
            Pair("Search করতে গেলে ঘণ্টার পর ঘণ্টা কোথায় হারিয়ে যাও জানো?", "RasFocus+"),
            Pair("Explore করার আগে নিজেকে জিজ্ঞেস করো — এটা কি সত্যিই দরকার?", "RasFocus+"),
            Pair("মনোযোগ হলো নতুন যুগের মুদ্রা — তা সাবধানে ব্যয় করো।", "ক্যাল নিউপোর্ট"),
            Pair("নিজের কাজে মনোযোগ দাও, অন্যের জীবনে নয়।", "Marcus Aurelius")
        ),

        "Unsupported Browser" to listOf(
            Pair("নিয়ম মানো, নিজেকে রক্ষা করো।", "RasFocus+"),
            Pair("শৃঙ্খলাই স্বাধীনতার সেতু।", "জিম রোহন"),
            Pair("নিজের উপর নিয়ন্ত্রণই সবচেয়ে বড় শক্তি।", "এপিকটেটাস"),
            Pair("ছোট ছোট অভ্যাসই বড় পরিবর্তন আনে।", "জেমস ক্লিয়ার")
        ),

        "App Install" to listOf(
            Pair("নতুন app মানে নতুন distraction।", "RasFocus+"),
            Pair("কম tools, বেশি focus।", "ক্যাল নিউপোর্ট"),
            Pair("প্রয়োজন না হলে install করো না।", "RasFocus+"),
            Pair("সরলতাই শ্রেষ্ঠত্বের চূড়ান্ত রূপ।", "লিওনার্দো দা ভিঞ্চি")
        ),

        "Uninstall Blocked" to listOf(
            Pair("তুমি এই সিদ্ধান্তটা ভবিষ্যতের নিজের জন্য নিয়েছিলে — এখন সম্মান করো।", "RasFocus+"),
            Pair("সংকল্পে অটল থাকো।", "Marcus Aurelius"),
            Pair("কঠিন সময়ে অভ্যাস ভাঙা সহজ — কিন্তু সেটাই তোমাকে পিছিয়ে দেবে।", "জেমস ক্লিয়ার"),
            Pair("নিজের উপর নিয়ন্ত্রণই সবচেয়ে বড় শক্তি।", "এপিকটেটাস")
        ),

        "Power/Reboot Blocked" to listOf(
            Pair("তোমার সংকল্প পরীক্ষা হচ্ছে — এখানেই জয় হয়।", "RasFocus+"),
            Pair("শৃঙ্খলাই স্বাধীনতার সেতু।", "জিম রোহন"),
            Pair("কঠিন সময়ে টিকে থাকাই আসল শক্তি।", "Marcus Aurelius"),
            Pair("নিজের উপর নিয়ন্ত্রণই সবচেয়ে বড় শক্তি।", "এপিকটেটাস")
        )
    )

    /** feature নাম দিলে সেই feature-এর random quote দেয়, না পেলে default থেকে দেয় */
    private fun getRandomQuote(featureTitle: String): Pair<String, String> {
        val list = QUOTES_BY_FEATURE[featureTitle] ?: QUOTES_DEFAULT
        return list.random()
    }

    /**
     * featureTitle  → "YouTube Shorts", "Instagram Reels", "Adult Content" ইত্যাদি
     * reason        → কী কারণে block হলো সেটার ছোট বাংলা/English বার্তা
     */
    private var lastPopupTime = 0L

    private fun blockWithMessage(featureTitle: String, reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastPopupTime > 1500L) {   // debounce: 1.5s cooldown
            lastPopupTime = now
            // ১. আগে overlay দেখাও — instant
            mainHandler.post { showBlockOverlay(featureTitle, reason) }
            // ২. তারপর home যাও — overlay ইতিমধ্যে screen এ আছে
            mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 80L)
        }
    }

    private fun blockAndPopup(featureTitle: String, reason: String) = blockWithMessage(featureTitle, reason)

    private fun showBlockOverlay(featureTitle: String, reason: String) {
        removeOverlay()

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val ctx = this
        val dp = resources.displayMetrics.density

        // ── ১. উক্তি সংগ্রহ (১টি ইসলামিক, ১টি মনীষীদের) ──
        val islamicQuotes = listOf(
            Pair("আস্তাগফিরুল্লাহ! নিশ্চয়ই আল্লাহ সবকিছু দেখছেন।", "আল কুরআন"),
            Pair("মুমিনদেরকে বলুন, তারা যেন তাদের দৃষ্টি অবনত রাখে।", "সূরা আন-নূর: ৩০"),
            Pair("যিনা এর ধারে কাছেও যেও না, নিশ্চয়ই এটা অশ্লীল কাজ।", "সূরা বনী ইসরাঈল: ৩২"),
            Pair("দৃষ্টির হেফাজত করা ঈমানের পূর্ণতার অন্যতম প্রধান লক্ষণ।", "আল হাদিস"),
            Pair("ক্ষণস্থায়ী পাপের জন্য চিরস্থায়ী জান্নাত হারিও না।", "RasFocus+"),
            Pair("যে ব্যক্তি নিজের প্রবৃত্তিকে নিয়ন্ত্রণ করে, জান্নাতই তার ঠিকানা।", "আল কুরআন")
        )
        val wiseQuotes = listOf(
            Pair("নিজেকে সম্মান করো — তোমার মস্তিষ্ক এর চেয়ে ভালো কিছুর যোগ্য।", "RasFocus+"),
            Pair("যে নিজেকে নিয়ন্ত্রণ করতে পারে, সে সবকিছু জয় করতে পারে।", "এপিকটেটাস"),
            Pair("মনোযোগ হলো নতুন যুগের মুদ্রা — তা সাবধানে ব্যয় করো।", "ক্যাল নিউপোর্ট"),
            Pair("শৃঙ্খলাই স্বাধীনতার সেতু।", "জিম রোহন"),
            Pair("একটা বিক্ষিপ্ত মন কখনো মহৎ কাজ করতে পারে না।", "রবীন্দ্রনাথ ঠাকুর"),
            Pair("ছোট ছোট অভ্যাসই বড় পরিবর্তন আনে।", "জেমস ক্লিয়ার")
        )

        val q1 = islamicQuotes.random()
        val q2 = wiseQuotes.random()

        // ── ২. গ্লাসমরফিজম কালার প্যালেট ──
        val glassBg = android.graphics.Color.parseColor("#1AFFFFFF")
        val glassStroke = android.graphics.Color.parseColor("#4DFFFFFF")
        val textPrimary = android.graphics.Color.parseColor("#FFFFFF")
        val textSecondary = android.graphics.Color.parseColor("#B3FFFFFF")
        val accentNeon = android.graphics.Color.parseColor("#00F5C4")

        // ── ৩. মূল লেআউট (ফুল স্ক্রিন ডার্ক ট্রান্সলুসেন্ট) ──
        val rootWrap = android.widget.FrameLayout(ctx).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#FF0B0F19")) // FF = 100% opacity
        }

        // ── ৪. সেন্ট্রাল গ্লাস কার্ড — ফুল স্ক্রিন ──
        val glassCard = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = (24 * dp).toInt()
            setPadding(p, (48 * dp).toInt(), p, (32 * dp).toInt())

            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.TRANSPARENT)
                cornerRadius = 0f
            }

            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        // ── আইকন এবং টাইটেল ──
        val iconTv = android.widget.TextView(ctx).apply {
            text = "🛡️"
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * dp).toInt())
        }

        val titleTv = android.widget.TextView(ctx).apply {
            text = featureTitle.uppercase()
            textSize = 18f
            setTextColor(textPrimary)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        // ── ব্লক হওয়ার কারণ (Reason) ──
        val reasonBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor("#33000000"))
            cornerRadius = 12f * dp
            setStroke((1f * dp).toInt(), glassStroke)
        }

        val reasonTv = android.widget.TextView(ctx).apply {
            text = "ℹ $reason"
            textSize = 13f
            setTextColor(accentNeon)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
            background = reasonBg
            val p = (12 * dp).toInt()
            setPadding(p, p, p, p)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (24 * dp).toInt()) }
        }

        glassCard.addView(iconTv)
        glassCard.addView(titleTv)
        glassCard.addView(reasonTv)

        // ডিভাইডার লাইন
        val divLine = android.view.View(ctx).apply {
            setBackgroundColor(glassStroke)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (1f * dp).toInt()
            ).apply { setMargins(0, 0, 0, (20 * dp).toInt()) }
        }
        glassCard.addView(divLine)

        // ── ৫. প্রথম উক্তি: কুরআন / হাদিস ──
        val q1Header = android.widget.TextView(ctx).apply {
            text = "☪ কুরআন ও হাদিস থেকে"
            textSize = 12f
            setTextColor(accentNeon)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val q1Body = android.widget.TextView(ctx).apply {
            text = "\u201c${q1.first}\u201d\n— ${q1.second}"
            textSize = 14f
            setTextColor(textPrimary)
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            setLineSpacing(0f, 1.35f)
            setPadding(0, (6 * dp).toInt(), 0, (20 * dp).toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        glassCard.addView(q1Header)
        glassCard.addView(q1Body)

        // ── ৬. দ্বিতীয় উক্তি: মনীষীদের ──
        val q2Header = android.widget.TextView(ctx).apply {
            text = "💡 অনুপ্রেরণা"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#FFB800"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val q2Body = android.widget.TextView(ctx).apply {
            text = "\u201c${q2.first}\u201d\n— ${q2.second}"
            textSize = 14f
            setTextColor(textPrimary)
            setTypeface(typeface, android.graphics.Typeface.ITALIC)
            setLineSpacing(0f, 1.35f)
            setPadding(0, (6 * dp).toInt(), 0, (24 * dp).toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        glassCard.addView(q2Header)
        glassCard.addView(q2Body)

        // ── ৭. হোম বাটন ──
        val isAdult = featureTitle.contains("Adult", true)
        val homeBtn = android.widget.TextView(ctx).apply {
            text = if (isAdult) "⏳ অপেক্ষা করুন..." else "হোমে ফিরে যান"
            textSize = 15f
            setTextColor(android.graphics.Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER

            val btnBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isAdult) android.graphics.Color.GRAY else accentNeon)
                cornerRadius = 14f * dp
            }
            background = btnBg
            isEnabled = !isAdult

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (48 * dp).toInt()
            )

            setOnClickListener {
                removeOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)  // overlay সরানোর পরে HOME — browser দেখাবে না
            }

            if (isAdult) {
                var timeLeft = 5
                val timerRunnable = object : Runnable {
                    override fun run() {
                        timeLeft--
                        if (timeLeft > 0) {
                            text = "⏳ $timeLeft সেকেন্ড..."
                            mainHandler.postDelayed(this, 1000)
                        } else {
                            isEnabled = true
                            text = "বন্ধ করুন"
                            (background as android.graphics.drawable.GradientDrawable).setColor(accentNeon)
                            setTextColor(android.graphics.Color.BLACK)
                        }
                    }
                }
                mainHandler.postDelayed(timerRunnable, 1000)
            }
        }
        glassCard.addView(homeBtn)
        rootWrap.addView(glassCard)

        // ── ৮. উইন্ডো ম্যানেজার প্যারামিটার এবং ব্যাকগ্রাউন্ড ব্লার ──
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        // FLAG_NOT_FOCUSABLE সরানো হয়েছে — এখন overlay টা touch intercept করবে
        // ফলে overlay এর পেছনে browser touch করা যাবে না
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL   // শুধু overlay এর বাইরে touch block

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurBehindRadius = 50
            }
        }

        try {
            wm.addView(rootWrap, params)
            overlayView = rootWrap

            if (!isAdult) {
                mainHandler.postDelayed({ removeOverlay() }, 4000L)
            }
        } catch (_: Exception) {}
    }

    private fun removeOverlay() {
        overlayView?.let { v ->
            try { windowManager.removeView(v) } catch (_: Exception) {}
            overlayView = null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = BlockerPrefs(this)
        FirebaseKeywordSync.init(this)   // Firebase থেকে remote keywords load
        // XML config থেকে পড়া info কে extend করি — replace নয়
        // এটা না করলে canRetrieveWindowContent হারিয়ে যায়!
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        serviceInfo = info
        // ── Persistent notification: service যে চলছে সেটা status bar এ দেখায় ──
        showRunningNotification()
    }

    private fun showRunningNotification() {
        try {
            val channelId = "rasfocus_service_running"
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "RasFocus Active",
                    android.app.NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Extreme Block চলছে"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
            val settingsIntent = android.content.Intent(this, RasFocusSettingsActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pi = android.app.PendingIntent.getActivity(
                this, 3001, settingsIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(this, channelId)
                    .setContentTitle("RasFocus Extreme Block চলছে")
                    .setContentText("Blocking active — ট্যাপ করে settings খুলুন")
                    .setSmallIcon(R.drawable.ic_notification_shield)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(this)
                    .setContentTitle("RasFocus Extreme Block চলছে")
                    .setContentText("Blocking active")
                    .setSmallIcon(R.drawable.ic_notification_shield)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build()
            }
            nm.notify(3001, notification)
        } catch (_: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // ★ FAST PATH — tree scan ছাড়াই event.text থেকে instant block
        event ?: return
        val pkg0 = event.packageName?.toString() ?: return

        // নিজের package এবং system UI — skip করো (loop ও false positive এড়াতে)
        if (pkg0 == "com.rasel.RasFocus" ||           // ← fix: ছিল "com.rasel.RasFocus.selfcontrol"
            pkg0 == "com.rasfocus" ||
            pkg0 == "android" ||
            pkg0 == "com.android.systemui" ||
            pkg0 == "com.samsung.android.systemui" ||
            pkg0 == "com.miui.systemui") return

        if (fastAdultCheck(event, pkg0)) return
        // Custom blocked app — pkg0 দিয়েই check করো, tree scan ছাড়াই
        if (prefs.blockAdult && FirebaseKeywordSync.isCustomBlockedApp(pkg0)) {
            val msg = FirebaseKeywordSync.getAppBlockMessage(pkg0)
            blockWithMessage("App Blocked", msg)
            return
        }

        val root = rootInActiveWindow ?: return
        val pkg  = event.packageName?.toString() ?: run { root.recycle(); return }

        try {
            // handled flag — একটা event এ একবারই block হবে, multiple HOME action যাবে না
            var handled = false

            fun block() {
                if (!handled) {
                    handled = true
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }

            // ── Custom Block (Firebase remote) — সবার আগে ──
            if (!handled) handled = handleFirebaseCustomBlock(event, root, pkg)

            // ── Content Blocking ──
            if (!handled) handled = handleAdultContent(root, pkg)
            if (!handled) handled = handleUniversalScreenKeywordBlock(root, pkg)  // ← নতুন: screen text scan
            if (!handled) handleSafeSearch(root, pkg)          // silent inject, never sets handled
            if (!handled) handled = handleWebViewAdultBlock(root, pkg)
            if (!handled) handled = handleImageVideoSearch(root, pkg)
            if (!handled) handled = handleYouTubeShorts(root, pkg)
            if (!handled) handled = handleReels(root, pkg)
            if (!handled) handled = handleInstagramSearch(root, pkg)
            if (!handled) handled = handleWhatsAppChannels(root, pkg)
            if (!handled) handled = handleInstagramStories(root, pkg)
            if (!handled) handled = handleSnapchat(root, pkg)
            if (!handled) handled = handleWhatsAppStatus(root, pkg)
            if (!handled) handled = handleWaBusinessBlocking(root, pkg)
            if (!handled) handled = handleTikTok(root, pkg)
            if (!handled) handled = handleAppSearchKeyword(event, root, pkg)
            if (!handled && pkg == "com.facebook.katana") {
                handled = handleFacebookVideo(root, pkg)
                if (!handled) handled = handleFacebookFeedShortVideo(root, pkg)
            }

            // ── Advanced ──
            if (!handled) handled = handleUnsupportedBrowsers(root, pkg)
            if (!handled) handled = handleNewlyInstalledApps(root, pkg)

            // ── Protection ──
            if (!handled) handled = handleUninstallProtection(root, pkg)
            if (!handled) handleRebootProtection(root, pkg)

        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } finally {
            root.recycle()
        }
    }

    internal fun pauseFeedVideo(root: AccessibilityNodeInfo) {
        val pauseBtn = root
            .findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_pause_button")
            .firstOrNull { it.isVisibleToUser && it.isClickable }
        if (pauseBtn != null) {
            pauseBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            pauseBtn.recycle()
            return
        }
        val videoNode = root
            .findAccessibilityNodeInfosByViewId("com.facebook.katana:id/inline_video_player")
            .firstOrNull { it.isVisibleToUser }
        if (videoNode != null) {
            videoNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            videoNode.recycle()
            return
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        // Running notification cancel করো
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(3001)
        } catch (_: Exception) {}
        // ── SERVICE KILL GUARD: Service kill হলে 3 সেকেন্ড পরে restart করার alarm set করো ──
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val restartIntent = android.content.Intent(this, ServiceRestartReceiver::class.java)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                this, 1001, restartIntent,
                android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.set(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 3000L,
                pendingIntent
            )
        } catch (_: Exception) {}
        super.onDestroy()
    }

    // Switch ON হলে call হয় — already open সাইটও block করে
    // Switch ON হলে call হয় — already open window সব handler দিয়ে check করে
    fun checkCurrentWindow() {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: run { root.recycle(); return }
        // নিজের app বা system হলে skip
        if (pkg == "com.rasel.RasFocus" || pkg == "android" ||
            pkg == "com.android.systemui" || pkg == "com.samsung.android.systemui") {
            root.recycle(); return
        }
        try {
            var handled = false
            if (!handled) handled = handleFirebaseCustomAppBlock(pkg)
            if (!handled) handled = handleAdultContent(root, pkg)
            if (!handled) handled = handleWebViewAdultBlock(root, pkg)
            if (!handled) handled = handleImageVideoSearch(root, pkg)
            if (!handled) handled = handleYouTubeShorts(root, pkg)
            if (!handled) handled = handleReels(root, pkg)
            if (!handled) handled = handleInstagramSearch(root, pkg)
            if (!handled) handled = handleWhatsAppChannels(root, pkg)
            if (!handled) handled = handleInstagramStories(root, pkg)
            if (!handled) handled = handleSnapchat(root, pkg)
            if (!handled) handled = handleWhatsAppStatus(root, pkg)
            if (!handled) handled = handleWaBusinessBlocking(root, pkg)
            if (!handled) handled = handleTikTok(root, pkg)
            if (!handled) handled = handleUnsupportedBrowsers(root, pkg)
            if (!handled) handled = handleNewlyInstalledApps(root, pkg)
            if (!handled) handled = handleUninstallProtection(root, pkg)
            if (!handled) handleRebootProtection(root, pkg)
            if (pkg == "com.facebook.katana") {
                if (!handled) handled = handleFacebookVideo(root, pkg)
                if (!handled) handleFacebookFeedShortVideo(root, pkg)
            }
        } catch (_: Exception) {
        } finally {
            root.recycle()
        }
    }


    // ════════════════════════════════════════════════════════
    // C. ALL BLOCKING HANDLERS
    // ════════════════════════════════════════════════════════

    // ──────────────────────────────────────────────────────────
    // FIREBASE CUSTOM BLOCK HANDLERS
    // Firebase Console থেকে যেকোনো সময় keywords/domains/apps
    // add করলে সাথে সাথে সব user এ block হয়।
    // ──────────────────────────────────────────────────────────

    // Debounce timestamp — custom block এর জন্য
    private var lastCustomBlockTime = 0L

    /**
     * Browser URL বা screen text এ Firebase custom keyword/domain match হলে block।
     * সব app এ কাজ করে (browser + installed apps)।
     */
    private fun handleFirebaseCustomBlock(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo,
        pkg: String
    ): Boolean {
        if (!prefs.blockAdult) return false
        if (FirebaseKeywordSync.customKeywords.isEmpty() &&
            FirebaseKeywordSync.customDomains.isEmpty()) return false

        // Debounce — 1.5 সেকেন্ডে একবার
        val now = System.currentTimeMillis()
        if (now - lastCustomBlockTime < 1500L) return false

        // ── Browser হলে URL দিয়ে check করো ──
        if (pkg in ALL_BROWSER_PKGS) {
            val url = extractBrowserUrl(root, pkg)?.lowercase()?.trim() ?: ""
            val host = try {
                val raw = if (url.startsWith("http")) url else "https://$url"
                android.net.Uri.parse(raw).host?.lowercase()?.removePrefix("www.") ?: ""
            } catch (_: Exception) { "" }

            if (url.isNotBlank() && FirebaseKeywordSync.isCustomBlocked(url, host)) {
                lastCustomBlockTime = now
                val msg = FirebaseKeywordSync.getDomainBlockMessage(host)
                blockWithMessage("Site Blocked", msg)
                return true
            }
        }

        // ── যেকোনো app এ screen text check ──
        val screenText = collectAllText(root).lowercase()
        if (screenText.isNotBlank()) {
            val matchedKeyword = FirebaseKeywordSync.getMatchedCustomKeyword(screenText)
            if (matchedKeyword != null) {
                lastCustomBlockTime = now
                val msg = FirebaseKeywordSync.getKeywordBlockMessage(matchedKeyword)
                blockWithMessage("Content Blocked", msg)
                return true
            }
        }

        return false
    }

    /**
     * Package name Firebase custom apps list এ আছে কিনা check করো।
     * checkCurrentWindow() এ call হয় — app খুললেই তাৎক্ষণিক block।
     */
    private fun handleFirebaseCustomAppBlock(pkg: String): Boolean {
        if (!prefs.blockAdult) return false
        if (!FirebaseKeywordSync.isCustomBlockedApp(pkg)) return false
        val msg = FirebaseKeywordSync.getAppBlockMessage(pkg)
        blockWithMessage("App Blocked", msg)
        return true
    }

    // ── Helpers ──────────────────────────────────────────────

    // ── All supported browser packages for adult blocking ──
    private val ALL_BROWSER_PKGS = setOf(
        "com.android.chrome",
        "com.sec.android.app.sbrowser",          // Samsung Internet
        "org.mozilla.firefox",
        "com.microsoft.emmx",                    // Edge
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.UCMobile.intl",
        "com.yandex.browser",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser",
        "mark.via.gp",                           // Via Browser
        "com.duckduckgo.mobile.android",
        "com.mi.globalbrowser",                  // Mi Browser
        "com.hihonor.browser",
        "com.huawei.browser",
        "com.puffin.client.android"
    )

    // ── URL bar view IDs per browser package ──
    private val BROWSER_URL_BAR_IDS = mapOf(
        "com.android.chrome"               to listOf("com.android.chrome:id/url_bar", "com.android.chrome:id/omnibox_text", "com.android.chrome:id/location_bar_edit_text"),
        "com.sec.android.app.sbrowser"     to listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text", "com.sec.android.app.sbrowser:id/sb_urlbar_input"),
        "org.mozilla.firefox"              to listOf("org.mozilla.firefox:id/url_bar_title", "org.mozilla.firefox:id/mozac_browser_toolbar_url_view", "org.mozilla.firefox:id/url_edit_text"),
        "com.microsoft.emmx"               to listOf("com.microsoft.emmx:id/address_bar_edit_text", "com.microsoft.emmx:id/url_bar"),
        "com.brave.browser"                to listOf("com.brave.browser:id/url_bar", "com.brave.browser:id/omnibox_text"),
        "com.opera.browser"                to listOf("com.opera.browser:id/url_field"),
        "com.opera.mini.native"            to listOf("com.opera.mini.native:id/url_field"),
        "com.UCMobile.intl"                to listOf("com.UCMobile.intl:id/webview_tab_editurl"),
        "com.yandex.browser"               to listOf("com.yandex.browser:id/bro_urlbar_url"),
        "com.kiwibrowser.browser"          to listOf("com.kiwibrowser.browser:id/url_bar", "com.kiwibrowser.browser:id/omnibox_text"),
        "com.vivaldi.browser"              to listOf("com.vivaldi.browser:id/url_bar", "com.vivaldi.browser:id/omnibox_text"),
        "mark.via.gp"                      to listOf("mark.via.gp:id/cv"),
        "com.duckduckgo.mobile.android"    to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"),
        "com.mi.globalbrowser"             to listOf("com.mi.globalbrowser:id/url_address"),
        "com.hihonor.browser"              to listOf("com.hihonor.browser:id/url_edit_text"),
        "com.huawei.browser"               to listOf("com.huawei.browser:id/url_edit_text"),
        "com.puffin.client.android"        to listOf("com.puffin.client.android:id/url_edit_text")
    )

    private fun extractBrowserUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        // Try package-specific IDs first
        val ids = BROWSER_URL_BAR_IDS[pkg]
        if (ids != null) {
            for (id in ids) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                if (nodes.isNotEmpty()) {
                    val t = nodes[0].text?.toString()?.lowercase()
                        ?: nodes[0].contentDescription?.toString()?.lowercase()
                    if (!t.isNullOrBlank()) return t
                }
            }
        }
        // Fallback: tree scan for URL-shaped text
        return findNodeWithUrl(root)
    }

    // Keep old name as alias for backward compat with other handlers
    private fun extractChromeUrl(root: AccessibilityNodeInfo): String? =
        extractBrowserUrl(root, "com.android.chrome")

    private fun findNodeWithUrl(node: AccessibilityNodeInfo): String? {
        val t = node.text?.toString()?.lowercase()?.trim() ?: ""
        // proper URL pattern: http/https শুরু, অথবা www., অথবা known domain pattern
        if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("www.")) return t
        // domain-like: example.com/path — কিন্তু শুধু ".com" হলে skip (false positive)
        if (t.contains(".com/") || t.contains(".org/") || t.contains(".net/") ||
            t.contains(".xxx") || t.contains(".sex") || t.contains(".adult")) return t
        // TLD শেষে আছে এবং পুরোটা URL-like (space নেই, কমপক্ষে ৮ char)
        if (t.length >= 8 && !t.contains(" ") &&
            (t.endsWith(".com") || t.endsWith(".org") || t.endsWith(".net") ||
             t.endsWith(".xxx") || t.endsWith(".sex"))) return t
        for (i in 0 until node.childCount) {
            val r = findNodeWithUrl(node.getChild(i) ?: continue)
            if (r != null) return r
        }
        return null
    }

    private fun isTabActive(root: AccessibilityNodeInfo, name: String) =
        root.findAccessibilityNodeInfosByText(name)
            .any { it.isSelected || it.isChecked || it.isFocused }

    private val BLOCKED_BROWSERS = setOf(
        "com.opera.browser", "com.opera.mini.native", "com.UCMobile.intl",
        "org.mozilla.firefox", "com.brave.browser", "com.microsoft.emmx",
        "com.duckduckgo.mobile.android", "com.yandex.browser",
        "com.kiwibrowser.browser", "com.vivaldi.browser", "mark.via.gp",
        "com.puffin.client.android", "com.jawal.browser"
    )
    private val BLOCKED_BROWSER_NAMES = listOf(
        "Opera", "Opera Mini", "UC Browser", "Firefox", "Brave",
        "Edge", "DuckDuckGo", "Yandex Browser", "Kiwi", "Vivaldi",
        "Via Browser", "Puffin"
    )

    private val rasFocusPackage = "com.rasfocus"
    private val rasFocusName = "RasFocus"

    // ── Adult site domain list (assets/adultsite.txt থেকে lazy load) ──
    private var _adultDomainList: List<String>? = null
    private fun getAdultDomainList(): List<String> {
        if (_adultDomainList == null) {
            _adultDomainList = try {
                assets.open("adultsite.txt")
                    .bufferedReader()
                    .readLines()
                    .map { it.trim().trimStart('*', '.').lowercase() }
                    .filter { it.isNotEmpty() }
            } catch (e: Exception) {
                emptyList()
            }
        }
        return _adultDomainList!!
    }

    // ── 1. Adult Content ─────────────────────────────────────
    // ── Adult site keywords — phrase-based (false positive কমানো হয়েছে) ──
    // নোট: একক ছোট শব্দ (sex, hot, dick) সরানো হয়েছে,
    //       phrase বা site-name আকারে রাখা হয়েছে।
    private val adultSiteKeywords = listOf(

        // ════ EN — Hardcore (phrase / compound) ════
        "porn video", "porn site", "porn watch", "free porn", "watch porn",
        "xxx video", "xxx site", "xxx watch", "hd xxx",
        "nude video", "nude photo", "nude pic", "nude girl", "nude scene",
        "nsfw video", "nsfw content",
        "sexy video", "sexy girl", "sexy photo", "sexy scene",
        "hentai video", "hentai anime", "hentai watch",
        "rule34", "milf video", "milf porn",
        "blowjob video", "blowjob scene",
        "tits video", "boobs video", "boobs photo",
        "pussy video", "pussy photo",
        "cock video", "dick video", "dick photo",
        "escort service", "escort girl",
        "bdsm video", "bdsm porn",
        "fetish video", "fetish porn",
        "erotica video", "erotic video", "erotic scene",
        "dildo video",
        "camgirls live", "cam girls",
        "onlyfans video", "onlyfans content", "onlyfans leak",
        "chaturbate live",
        "mia khalifa", "sunny leone", "dani daniels", "johnny sins", "kendra lust",

        // ════ EN — Romantic/Soft (phrase) ════
        "hot dance video", "seductive dance",
        "kissing scene hot", "hot kissing",
        "sexy dance video", "sexy dance girl",
        "hot scene video", "hot scene movie",
        "romantic kiss scene", "bedroom scene hot",
        "bath scene hot", "rain dance sexy",
        "bold scene video", "semi nude video",
        "lingerie video", "lingerie photo",
        "erotic massage", "hot song video",
        "navel show video", "deep neck video",
        "short dress sexy", "unfaithful scene",
        "bikini video", "bikini photo", "swimsuit video",
        "cleavage video", "cleavage photo",
        "item song dance",

        // ════ Bangla — Hardcore (phrase / compound) ════
        "চটি গল্প", "চটি পড়",
        "পর্ণ ভিডিও", "পর্ণ দেখ", "পর্ণগ্রাফি দেখ",
        "সেক্স ভিডিও", "সেক্স করা", "সেক্স দেখ",
        "নগ্ন ভিডিও", "নগ্ন ছবি", "নগ্ন মেয়ে",
        "উলঙ্গ ভিডিও", "উলঙ্গ ছবি",
        "বেশ্যা ভিডিও", "মাগি ভিডিও",
        "খানকি ভিডিও", "খানকি ছবি",
        "যৌন ভিডিও", "যৌন মিলন ভিডিও", "যৌনাঙ্গ ভিডিও",
        "রেন্ডি ভিডিও",
        "চোদাচুতি ভিডিও",
        "গরম ভিডিও",
        "খারাপ ছবি দেখ", "খারাপ ভিডিও",
        "চুদো ভিডিও", "নগ্নতা ভিডিও",

        // ════ Romanized Bangla (Banglish) ════
        "bangla choti", "bangla chuda", "bangla xxx",
        "desi sex video", "desi bhabi sex", "desi nude",
        "bhabi sex", "bhabi nude", "bhabi video",
        "chudai video", "chudai scene",
        "panu video", "panu bd",
        "desi mms", "mms leak video",
        "khanki video", "magi video",
        "choda video", "chodachudi video",
        "randi video", "randi sex",
        "nengta video", "nangta video",
        "vodai video", "vodai photo",
        "bokachoda video",
        "kharap video", "kharap ছবি",

        // ════ Site names (domain-keyword, phrase safe) ════
        "pornhub", "xvideos", "xnxx", "xhamster",
        "redtube", "youporn", "tube8", "spankbang",
        "eporner", "drtuber", "txxx", "tnaflix",
        "brazzers", "realitykings", "bangbros",
        "onlyfans", "chaturbate", "stripchat",
        "slutload", "motherless", "redgifs",
        "hentaihaven", "gelbooru", "rule34",
        "javhd", "japanhdv",
        "playboy video", "playboy nude",
        "beeg video",
        "pornmd", "pornhd", "pornotube",
        "fakehub", "fakku", "hentaigasm",
        "camhub", "imlive", "luckycrush",
        "jerkmate", "jizzhut",
        "tubegalore", "cliphunter",
        "wankzvr", "vrporn", "vrcock",
        "watchmygf", "watchmyexgf",
        "hotsouthindiansex", "viewdesisex",
        "tranny video", "tranny porn",
        "twistysnetwork", "digitalplayground",
        "teamskeet", "nubiles",
        "theporndude", "toppornsites",

        // ════ Extra keywords from adultKeywords list ════
        "porn", "xxx", "sex", "nude", "nsfw", "sexy", "hentai",
        "milf", "blowjob", "tits", "boobs", "pussy", "dick", "cock",
        "escort", "bdsm", "fetish", "erotica", "dildo", "webcam",
        "camgirls", "webcam girl",

        // ════ Bangla keywords (single-word / short phrase) ════
        "চটি", "পর্ণ", "সেক্স", "নগ্ন", "উলঙ্গ",
        "বেশ্যা", "মাগি", "খানকি", "যৌন", "পর্ণগ্রাফি",
        "রেন্ডি", "চোদাচুতি", "গরম ভিডিও", "খারাপ ছবি",
        "যৌন মিলন", "যৌনাঙ্গ", "চুদো", "নগ্নতা",

        // ════ Extra Romanized Bangla (single-word / short) ════
        "bhabi", "chudai", "bangla choti", "panu", "desi bhabi",
        "mms", "magi", "choda", "chodachudi", "khanki", "besha",
        "randi", "nengta", "nangta", "baal", "vodai",
        "bokachoda", "kuttar bacha", "shuarer bacha",

        // ════ romanticKeywords list ════
        "hot dance", "seductive dance", "item song", "belly dance",
        "kissing scene", "bikini", "swimsuit", "sexy dance",
        "cleavage", "hot scene", "romantic kiss", "bedroom scene",
        "bath scene", "rain dance", "bold scene", "semi nude",
        "lingerie", "erotic", "hot song", "romantic video hot",
        "navel show", "deep neck", "short dress sexy", "unfaithful scene"
    )

    // Full domain set — exact host match
    private val adultDomains = setOf(
        "18videosz.com", "24porn.com", "3movs.com", "4tube.com",
        "adulttime.com", "allofgfs.com", "alohatube.com", "alotporn.com",
        "alphaporno.com", "anon-v.com", "anyshemale.com", "arabianchicks.com",
        "avn.com", "baberotica.com", "babes.com", "badoinkvr.com",
        "bang.com", "bangbrosnetwork.com", "bdsmstreak.com", "beeg.com",
        "bestpornbabes.com", "besttrannypornsites.com", "bestxxxsites.com", "bigtits.com",
        "blacked.com", "bobs-tube.com", "boysfood.com", "braincash.com",
        "brazzers.com", "brokestraightboys.com", "camhub.cc", "cams.com",
        "cliphunter.com", "clips4sale.com", "czechvr.com", "dansmovies.com",
        "daredorm.com", "ddfnetwork.com", "deviantclip.com", "digitalplayground.com",
        "dorcelclub.com", "drtuber.com", "eggporncomics.com", "empflix.com",
        "eporner.com", "eroxia.com", "evilangel.com", "extremetube.com",
        "fakehub.com", "fakku.net", "fantasti.cc", "fapster.xxx",
        "forhertube.com", "free18.net", "freepornfull.com", "fuq.com",
        "fux.com", "gayfuror.com", "gaymaletube.com", "gaytube.com",
        "gelbooru.com", "gfrevenge.com", "girlsway.com", "gotgayporn.com",
        "h2porn.com", "handjobhub.com", "hardsextube.com", "hclips.com",
        "helixstudios.net", "hentai-foundry.com", "hentaicore.org", "hentaigasm.com",
        "hentaihaven.org", "hentaipulse.com", "hotgoo.com", "hotsouthindiansex.com",
        "hustler.com", "iknowthatgirl.com", "imlive.com", "ixxx.com",
        "iyalc.com", "japanhdv.com", "javhd.com", "jerkmate.com",
        "jizzhut.com", "jizzonline.com", "justusboys.com", "keezmovies.com",
        "kinkyfamily.com", "kporno.com", "lesbian8.com", "letsjerk.is",
        "lovehomeporn.com", "lubetube.com", "luckycrush.live", "madthumbs.com",
        "manporn.xxx", "maxim.com", "maxiporn.com", "metaporn.com",
        "mofosex.com", "mogosnetwork.com", "motherless.com", "moviefap.com",
        "myporngay.com", "mythav.com", "netfapx.com", "newsensations.com",
        "nonktube.com", "nubiles.net", "nuvid.com", "orgasm.com",
        "perfectgirls.net", "perfectgonzo.com", "pervclips.com", "playboy.com",
        "porcore.com", "porn.com", "porn300.xxx", "porn7.xxx",
        "porndroids.com", "pornerbros.com", "pornfuror.com", "pornhd.com",
        "pornheed.com", "pornhost.com", "pornhub.com", "pornhubselect.com",
        "pornmate.com", "pornmd.com", "pornmilo.com", "pornotube.com",
        "pornoxo.com", "pornprosnetwork.com", "pornrabbit.com", "pornrox.com",
        "pornstarnetwork.com", "porntube.com", "pornxio.com", "proporn.com",
        "punishbang.com", "punishtube.com", "realitykings.com", "redgifs.com",
        "redporn.xxx", "redtube.com", "rk.com", "rockettube.com",
        "rude.com", "sankakucomplex.com", "sexlikereal.com", "sexvid.xxx",
        "shameless.com", "shemailhd.sex", "shooshtime.com", "slutload.com",
        "slutroulette.com", "spankbang.com", "spankwire.com", "stripchat.com",
        "submityourflicks.com", "submityourtapes.com", "sunporno.com", "teamskeet.com",
        "theporndude.com", "thumbzilla.com", "tiava.com", "tnaflix.com",
        "topfreepornvideos.com", "toppornsites.com", "tranny.one", "tube8.com",
        "tubegalore.com", "tubegals.com", "tubev.sex", "twilightsex.com",
        "twistysnetwork.com", "txxx.com", "videosz.com", "viewdesisex.com",
        "virtualtaboo.com", "vixen.com", "vporn.com", "vrcock.com",
        "vrcosplay.com", "vrporn.com", "vrsmash.com", "wankzvr.com",
        "watch-my-gf.com", "watch-my-gf.me", "watchindianporn.net", "watchmyexgf.net",
        "watchmygf.me", "watchmygf.tv", "xbabe.com", "xhamster.com",
        "xmoviesforyou.com", "xnxx.com", "xnxxhamster.net", "xpaja.net",
        "xtube.com", "xvideos.com", "xxvids.net", "xxx.com",
        "xxxaporn.com", "xxxbunker.com", "xxxvideos247.com", "youjizz.com",
        "youporn.com", "youporngay.com", "yuvutu.com", "zbporn.com",
        "zzcartoon.com", "zzgays.com"
    )

    // ── FAST PATH — event.text + source node text, tree scan ছাড়াই ──
    private fun fastAdultCheck(event: AccessibilityEvent, pkg: String): Boolean {
        if (pkg !in ALL_BROWSER_PKGS) return false
        if (!prefs.blockAdult && !prefs.blockAdultSiteList) return false

        // event.text — URL bar, page title, tab text সবকিছু
        val eventText = event.text.joinToString(" ").lowercase().trim()

        // source node text ও নাও (TYPE_WINDOW_STATE_CHANGED এ contentDescription তে URL/title আসে)
        val sourceText = try {
            val src = event.source
            val t = (src?.text?.toString() ?: src?.contentDescription?.toString() ?: "").lowercase()
            src?.recycle()
            t
        } catch (_: Exception) { "" }

        val combined = "$eventText $sourceText".trim()
        if (combined.isBlank()) return false

        // SafeSearch bypass attempt — fast block
        if (prefs.blockAdult && (combined.contains("safe=off") || combined.contains("safe=images"))) {
            blockAdultInBrowser(pkg)
            return true
        }

        if (isAdultUrl(combined)) {
            blockAdultInBrowser(pkg)
            return true
        }
        return false
    }

    /**
     * Browser-এ blocked content detect হলে:
     * 1. তাৎক্ষণিক blocking overlay দেখাও
     * 2. HOME-এ পাঠাও (overlay dismiss হলেও browser দেখা যাবে না)
     * 3. Tab close করো যাতে browser-এ ফিরে গেলে blocked tab না থাকে
     * 4. Safety net — 2s পরেও browser-এ থাকলে আবার HOME
     */
    private fun blockAdultInBrowser(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - lastPopupTime > 1200L) {
            lastPopupTime = now

            // 1. তাৎক্ষণিক blocking overlay দেখাও
            mainHandler.post {
                showBlockOverlay("Adult Content", "This page contains adult content and has been blocked.")
            }

            // 2. HOME — এখনই (overlay পেছনে browser যেন না দেখায়)
            mainHandler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_HOME)
            }, 120)

            // 3. Tab close — HOME যাওয়ার পরে background-এ tab বন্ধ করো
            //    এতে ব্যবহারকারী browser-এ ফিরে গেলে blocked tab পাবে না
            mainHandler.postDelayed({
                closeBrowserTab(pkg)
            }, 600)

            // 4. Safety net — 2.5s পরেও browser-এ থাকলে আবার HOME
            mainHandler.postDelayed({
                try {
                    val root2 = rootInActiveWindow
                    val currentPkg = root2?.packageName?.toString() ?: ""
                    root2?.recycle()
                    if (currentPkg in ALL_BROWSER_PKGS) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                } catch (_: Exception) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }, 2500)
        }
    }

    /**
     * Browser-এর current tab বন্ধ করার চেষ্টা করো।
     * Chrome/Chromium-based: close tab button খোঁজো।
     * Firefox/অন্যরা: BACK দিয়ে navigate করো।
     * যেকোনো browser-এই কাজ করে।
     */
    private fun closeBrowserTab(pkg: String) {
        try {
            val root2 = rootInActiveWindow ?: return
            val currentPkg = root2.packageName?.toString() ?: run { root2.recycle(); return }

            // Browser foreground-এ না থাকলে (HOME-এ আছি) — background-এ close করার চেষ্টা
            // Chromium-based browsers: close tab button এর view ID দিয়ে খোঁজো
            val closeTabIds = when (pkg) {
                "com.android.chrome" -> listOf(
                    "com.android.chrome:id/tab_close_button",
                    "com.android.chrome:id/close_button",
                    "com.android.chrome:id/action_close_tab"
                )
                "com.sec.android.app.sbrowser" -> listOf(
                    "com.sec.android.app.sbrowser:id/close_button",
                    "com.sec.android.app.sbrowser:id/tab_close_btn"
                )
                "com.brave.browser" -> listOf(
                    "com.brave.browser:id/tab_close_button",
                    "com.brave.browser:id/close_button"
                )
                "com.microsoft.emmx" -> listOf(
                    "com.microsoft.emmx:id/close_tab_button",
                    "com.microsoft.emmx:id/tab_close_button"
                )
                "com.opera.browser" -> listOf("com.opera.browser:id/close_tab_button")
                "com.kiwibrowser.browser" -> listOf(
                    "com.kiwibrowser.browser:id/tab_close_button",
                    "com.kiwibrowser.browser:id/close_button"
                )
                "com.vivaldi.browser" -> listOf(
                    "com.vivaldi.browser:id/tab_close_button",
                    "com.vivaldi.browser:id/close_button"
                )
                else -> emptyList()
            }

            // Browser foreground-এ থাকলে close tab button click করো
            if (currentPkg == pkg) {
                for (id in closeTabIds) {
                    val nodes = root2.findAccessibilityNodeInfosByViewId(id)
                    val closeBtn = nodes.firstOrNull { it.isVisibleToUser && it.isClickable }
                    if (closeBtn != null) {
                        closeBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        root2.recycle()
                        return
                    }
                }
                // Close button না পেলে: BACK দিয়ে navigate করো (previous safe page-এ যাবে)
                performGlobalAction(GLOBAL_ACTION_BACK)
            } else {
                // HOME-এ আছি — browser background-এ; Intent দিয়ে browser relaunch করো
                // এতে browser নতুন tab নিয়ে খুলবে, blocked tab active হবে না
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                        // Browser launch করো না — শুধু tab history clear করার জন্য
                        // background-এ থাকলে BACK action দিয়ে ফিরে যাও, তারপর HOME আবার
                    }
                } catch (_: Exception) {}
            }

            root2.recycle()
        } catch (_: Exception) {}
    }

    private fun isAdultUrl(text: String): Boolean {
        if (prefs.blockAdult) {
            if (adultSiteKeywords.any { text.contains(it) }) return true
        }
        val host = try {
            val raw = if (text.startsWith("http")) text.trim() else "https://${text.trim()}"
            android.net.Uri.parse(raw).host?.lowercase()?.removePrefix("www.") ?: ""
        } catch (e: Exception) { "" }
        if (host.isNotEmpty()) {
            // Check hardcoded domain set — only when blockAdult is ON
            if (prefs.blockAdult) {
                if (adultDomains.contains(host)) return true
            }
            // Check adultsite.txt domain list — only when blockAdultSiteList is ON
            if (prefs.blockAdultSiteList) {
                val domainList = getAdultDomainList()
                if (domainList.any { host == it || host.endsWith(".$it") }) return true
            }
        }
        // ── Firebase remote keywords + domains check ──────────
        // blockAdult অথবা blockAdultSiteList যেকোনো একটা ON হলে চলবে
        if (prefs.blockAdult || prefs.blockAdultSiteList) {
            if (FirebaseKeywordSync.isBlockedByRemote(text, host)) return true
        }
        return false
    }

    // ── SCREEN TEXT HELPER ──
    private fun collectAllText(node: AccessibilityNodeInfo?): String {
        node ?: return ""
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?) {
            n ?: return
            n.text?.let { sb.append(it).append(" ") }
            n.contentDescription?.let { sb.append(it).append(" ") }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(node)
        return sb.toString()
    }

    private fun handleAdultContent(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (pkg !in ALL_BROWSER_PKGS) return false
        if (!prefs.blockAdult && !prefs.blockAdultSiteList) return false

        // Layer 1: URL bar থেকে নাও (most reliable)
        val url = extractBrowserUrl(root, pkg)?.lowercase()?.trim() ?: ""

        // Layer 2: Page title / tab label থেকেও check করো
        val titleText = extractBrowserPageTitle(root, pkg)?.lowercase()?.trim() ?: ""

        // Layer 3: URL খালি হলে full screen text fallback
        val checkText = when {
            url.isNotBlank() -> url
            titleText.isNotBlank() -> titleText
            else -> collectAllText(root).lowercase()
        }

        if (checkText.isBlank()) return false

        // URL check
        if (url.isNotBlank() && isAdultUrl(url)) {
            blockAdultInBrowser(pkg)
            return true
        }

        // Title check — page title এ adult keyword থাকলে block
        if (titleText.isNotBlank() && isAdultUrl(titleText)) {
            blockAdultInBrowser(pkg)
            return true
        }

        // Fallback: screen text check (URL ও title না পেলে)
        if (url.isBlank() && titleText.isBlank() && isAdultUrl(checkText)) {
            blockAdultInBrowser(pkg)
            return true
        }

        return false
    }

    // Browser page title / tab label extract করো
    private fun extractBrowserPageTitle(root: AccessibilityNodeInfo, pkg: String): String? {
        val titleIds = mapOf(
            "com.android.chrome"            to listOf("com.android.chrome:id/title_bar", "com.android.chrome:id/tab_title", "com.android.chrome:id/url_bar"),
            "com.sec.android.app.sbrowser"  to listOf("com.sec.android.app.sbrowser:id/title", "com.sec.android.app.sbrowser:id/url_text"),
            "org.mozilla.firefox"           to listOf("org.mozilla.firefox:id/mozac_browser_toolbar_title_view"),
            "com.microsoft.emmx"            to listOf("com.microsoft.emmx:id/title"),
            "com.brave.browser"             to listOf("com.brave.browser:id/title_bar", "com.brave.browser:id/tab_title"),
            "com.duckduckgo.mobile.android" to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"),
            "com.opera.browser"             to listOf("com.opera.browser:id/title_bar"),
            "com.yandex.browser"            to listOf("com.yandex.browser:id/bro_urlbar_title")
        )
        val ids = titleIds[pkg] ?: return null
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                val t = nodes[0].text?.toString() ?: nodes[0].contentDescription?.toString()
                if (!t.isNullOrBlank()) return t
            }
        }
        return null
    }

    // ── 1b-EXTRA. Universal Screen Adult Keyword Block ─────────
    // যেকোনো app এ screen এ adult keyword দেখা গেলেই block
    // Browser, system UI, RasFocus নিজে — এগুলো skip করবে
    private val skipPkgsForScreenScan = setOf(
        "com.rasel.RasFocus.selfcontrol",
        "com.rasfocus",
        "android",
        "com.android.systemui",
        "com.samsung.android.systemui",
        "com.miui.systemui",
        "com.android.launcher",
        "com.android.launcher3",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.google.android.apps.nexuslauncher"
    )

    private var lastScreenScanBlockTime = 0L

    // যেসব app এর screen text adult keyword এর জন্য scan করা হবে
    private val ADULT_SCAN_TARGET_PKGS = setOf(
        // Telegram
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        // Facebook
        "com.facebook.katana",
        "com.facebook.lite",
        // YouTube
        "com.google.android.youtube",
        // Instagram
        "com.instagram.android",
        // Twitter / X
        "com.twitter.android",
        "com.x.android",
        // Reddit
        "com.reddit.frontpage",
        "com.reddit.redditisfun",
        // Snapchat
        "com.snapchat.android",
        // Pinterest
        "com.pinterest",
        // LinkedIn
        "com.linkedin.android"
    )

    private fun handleUniversalScreenKeywordBlock(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockAdult) return false

        // Browser এ আলাদা handler আছে — skip
        if (pkg in ALL_BROWSER_PKGS) return false

        // শুধু target apps এ scan করো — বাকি সব skip
        if (pkg !in ADULT_SCAN_TARGET_PKGS) return false

        // Debounce — 2 সেকেন্ডে একবারের বেশি fire হবে না
        val now = System.currentTimeMillis()
        if (now - lastScreenScanBlockTime < 2000L) return false

        // Screen এর সব visible text collect করো
        val screenText = collectAllText(root).lowercase()
        if (screenText.isBlank()) return false

        // Keyword match করো — local হার্ডকোড + Firebase remote উভয়
        val localMatch = adultSiteKeywords.firstOrNull { kw -> screenText.contains(kw) }
        val remoteMatch = if (localMatch == null) {
            FirebaseKeywordSync.isBlockedByRemote(screenText, "")
        } else false

        if (localMatch == null && !remoteMatch) return false

        // Match পেলে block
        lastScreenScanBlockTime = now

        // ১. Overlay screen তাৎক্ষণিক দেখাও
        mainHandler.post {
            showBlockOverlay("Adult Content", "Blocked keyword detected on screen.")
        }

        // ২. BACK করো — current page/screen থেকে বের হও
        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
        }, 120)

        // ৩. App অনুযায়ী নির্দিষ্ট navigation
        when {
            // Facebook → news feed homepage reload
            pkg.contains("facebook") -> {
                mainHandler.postDelayed({
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                            setPackage(pkg)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                     android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(intent)
                    } catch (_: Exception) { performGlobalAction(GLOBAL_ACTION_HOME) }
                }, 400)
            }
            // YouTube → home screen এ ফিরে যাও
            pkg == "com.google.android.youtube" -> {
                mainHandler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }, 350)
                mainHandler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }, 600)
            }
            // Instagram → home feed এ ফিরে যাও
            pkg == "com.instagram.android" -> {
                mainHandler.postDelayed({
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                            setPackage(pkg)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                     android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(intent)
                    } catch (_: Exception) { performGlobalAction(GLOBAL_ACTION_HOME) }
                }, 400)
            }
            // Telegram, Twitter/X, Reddit, Snapchat, Pinterest, LinkedIn → HOME
            else -> {
                mainHandler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }, 350)
            }
        }

        return true
    }

    // ── 1c. SafeSearch Force ─────────────────────────────────
    // blockAdult ON থাকলে Google search-এ safe=active inject করে
    // safe=off bypass attempt ও block করে
    private val safeSearchLastRedirect = HashMap<String, Long>()

    private fun handleSafeSearch(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockAdult) return false
        if (pkg !in ALL_BROWSER_PKGS) return false

        val url = extractBrowserUrl(root, pkg)?.lowercase() ?: return false
        if (!url.contains("google.") || !url.contains("/search")) return false

        // SafeSearch bypass attempt — block
        if (url.contains("safe=off") || url.contains("safe=images")) {
            blockAdultInBrowser(pkg)
            return true
        }

        // Already safe — skip
        if (url.contains("safe=active") || url.contains("safe=strict")) return false

        // Throttle: 1.5s cooldown per pkg to avoid redirect loop
        val now = System.currentTimeMillis()
        if ((now - (safeSearchLastRedirect[pkg] ?: 0L)) < 1500L) return false
        safeSearchLastRedirect[pkg] = now

        // Build safe URL and redirect
        val safeUrl = url + (if (url.contains("?")) "&" else "?") + "safe=active"
        mainHandler.postDelayed({
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(safeUrl)).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                             android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    setPackage(pkg)
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }, 300)
        return false  // don't mark handled — just redirect silently
    }

    // ── 1c. WebView Adult Block ─────────────────────────────
    // Any app using WebView to load adult content — block it
    private fun handleWebViewAdultBlock(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockAdult && !prefs.blockAdultSiteList) return false
        if (pkg in ALL_BROWSER_PKGS) return false  // already handled
        if (pkg.startsWith("com.android.") || pkg == "android") return false

        // Detect WebView presence
        if (!containsWebViewNode(root)) return false

        // WebView node থেকে URL বের করার চেষ্টা করো
        val urlText = findWebViewUrl(root)?.lowercase()?.trim()
            ?: findNodeWithUrl(root)?.lowercase()?.trim()
            ?: ""

        // URL পেলে সেটা check করো
        if (urlText.isNotBlank() && isAdultUrl(urlText)) {
            mainHandler.post {
                showBlockOverlay("Adult Content", "This page contains adult content and has been blocked.")
            }
            mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 150L)
            mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 800L)
            return true
        }

        // URL না পেলে screen text check করো — WebView এর visible text
        val screenText = collectAllText(root).lowercase()
        if (screenText.isNotBlank() && isAdultUrl(screenText)) {
            mainHandler.post {
                showBlockOverlay("Adult Content", "This page contains adult content and has been blocked.")
            }
            mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 150L)
            mainHandler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 800L)
            return true
        }

        return false
    }

    // WebView node এর contentDescription বা resource-id থেকে URL বের করো
    private fun findWebViewUrl(node: AccessibilityNodeInfo?): String? {
        node ?: return null
        val cn = node.className?.toString() ?: ""
        if (cn.contains("WebView") || cn.contains("XWalkView")) {
            // contentDescription এ URL থাকতে পারে কিছু browser/app এ
            val cd = node.contentDescription?.toString() ?: ""
            if (cd.startsWith("http") || cd.startsWith("www.")) return cd
            // text এ URL থাকতে পারে
            val t = node.text?.toString() ?: ""
            if (t.startsWith("http") || t.startsWith("www.")) return t
        }
        for (i in 0 until node.childCount) {
            val r = findWebViewUrl(node.getChild(i))
            if (r != null) return r
        }
        return null
    }

    private fun containsWebViewNode(node: AccessibilityNodeInfo?): Boolean {
        node ?: return false
        val cn = node.className?.toString() ?: ""
        if (cn.contains("WebView") || cn.contains("XWalkView")) return true
        for (i in 0 until node.childCount) {
            if (containsWebViewNode(node.getChild(i))) return true
        }
        return false
    }

    // ── 2. Image & Video Search ──────────────────────────────
    private fun handleImageVideoSearch(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockSearch) return false
        if (pkg != "com.android.chrome") return false
        val url = extractChromeUrl(root) ?: ""
        // URL-based block
        val urlBlocked = url.contains("tbm=isch") || url.contains("tbm=vid")
            || url.contains("google.com/images") || url.contains("google.com/videohp")
        // Tab-based block (URL না পেলেও tab active কিনা দেখো)
        val tabBlocked = url.isEmpty() && (isTabActive(root, "Images") || isTabActive(root, "Videos"))
        if (urlBlocked || tabBlocked) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return true
        }
        return false
    }

    // ── 3. YouTube Shorts ────────────────────────────────────
    private fun handleYouTubeShorts(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockYtShorts) return false
        if (pkg != "com.google.android.youtube") return false

        val tab = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar_item_label")
            .any { it.text?.toString()?.equals("Shorts", true) == true && (it.isSelected || it.parent?.isSelected == true) }
        if (tab) { performGlobalAction(GLOBAL_ACTION_HOME); return true }

        val player = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/shorts_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_player_page").isNotEmpty()
        if (player) { performGlobalAction(GLOBAL_ACTION_HOME); return true }

        val via = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/watch_while_layout").isNotEmpty()
            && root.findAccessibilityNodeInfosByText("Shorts").any { it.isSelected }
        if (via) { performGlobalAction(GLOBAL_ACTION_HOME); return true }

        val fb = root.findAccessibilityNodeInfosByText("Shorts")
            .any { it.isSelected || it.isChecked || it.parent?.isSelected == true }
        if (fb) { performGlobalAction(GLOBAL_ACTION_HOME); return true }

        return false
    }

    // ── 4. Reels ─────────────────────────────────────────────
    private fun handleReels(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockReels) return false
        when (pkg) {
            "com.instagram.android" -> {
                if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab").isNotEmpty()) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
                if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_container").isNotEmpty()) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
                if (root.findAccessibilityNodeInfosByText("Reels")
                        .any { it.contentDescription?.contains("Reels") == true && it.isSelected }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
                if (isTabActive(root, "Reels")) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            "com.facebook.katana" -> {
                // blockReels toggle — actual reels viewer বা reels section খোলা হলে block করো
                if (isFbReelsViewerOpen(root)) {
                    blockFacebookContent("Facebook Reels", "Facebook Reels is blocked.")
                    return true
                }
                // Watch tab-এ reels section খুলে থাকলেও block
                if (root.findAccessibilityNodeInfosByText("Reels")
                        .any { it.isVisibleToUser && (it.isSelected || it.isFocused || it.parent?.isSelected == true) }) {
                    blockFacebookContent("Facebook Reels", "Facebook Reels is blocked.")
                    return true
                }
                // নতুন FB-এ "Short videos" নামে থাকতে পারে
                if (root.findAccessibilityNodeInfosByText("Short videos")
                        .any { it.isVisibleToUser && (it.isSelected || it.parent?.isSelected == true) }
                    || root.findAccessibilityNodeInfosByText("Short Videos")
                        .any { it.isVisibleToUser && (it.isSelected || it.parent?.isSelected == true) }) {
                    blockFacebookContent("Facebook Reels", "Facebook Short Videos is blocked.")
                    return true
                }
            }
        }
        return false
    }

    // ── 5. Instagram Search ──────────────────────────────────
    private fun handleInstagramSearch(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockInstaSearch) return false
        if (pkg != "com.instagram.android") return false
        if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/search_tab").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/action_bar_search_edit_text")
                .any { it.isFocused || it.isAccessibilityFocused }) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        if (root.findAccessibilityNodeInfosByText("Search and explore").isNotEmpty()
            || root.findAccessibilityNodeInfosByText("Search").any { it.isSelected }) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        return false
    }

    // ── 6. WhatsApp Channels ─────────────────────────────────
    private fun handleWhatsAppChannels(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockWaChannels) return false
        if (pkg != "com.whatsapp") return false
        if (root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/updates_tab").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        if (root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/channels_home_content_layout").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        if (root.findAccessibilityNodeInfosByText("Channels").any { it.isSelected || it.isChecked }) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        if (root.findAccessibilityNodeInfosByText("Updates").any { it.isSelected || it.isChecked }) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        return false
    }

    // ── Facebook block helper — HOME-এ না পাঠিয়ে FB homepage reload করে + popup দেখায় ──
    private var fbBlockLastTime = 0L

    private fun blockFacebookContent(featureTitle: String, reason: String) {
        val now = System.currentTimeMillis()
        if (now - fbBlockLastTime < 1200L) return  // debounce
        fbBlockLastTime = now

        // 1. তাৎক্ষণিক blocking screen দেখাও
        mainHandler.post { showBlockOverlay(featureTitle, reason) }

        // 2. Facebook search box থাকলে clear করো
        mainHandler.postDelayed({
            try {
                val root2 = rootInActiveWindow ?: return@postDelayed
                val searchIds = listOf(
                    "com.facebook.katana:id/search_box",
                    "com.facebook.katana:id/search_input",
                    "com.facebook.katana:id/search_query_box",
                    "com.facebook.katana:id/action_bar_search_box_hint_text"
                )
                for (id in searchIds) {
                    val nodes = root2.findAccessibilityNodeInfosByViewId(id)
                    for (node in nodes) {
                        if (node.isEditable || node.isFocused) {
                            val args = android.os.Bundle().apply {
                                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                            }
                            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        }
                    }
                }
                root2.recycle()
            } catch (_: Exception) {}
        }, 150)

        // 3. Facebook news feed homepage reload
        mainHandler.postDelayed({
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                    setPackage("com.facebook.katana")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                             android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            } catch (_: Exception) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }, 400)
    }

    // ── Facebook: আসলেই video player খোলা হয়েছে কিনা detect করো ──
    // Feed-এ শুধু scroll করার সময় inline_video_player visible হয় — সেটা block করা উচিত না
    // কিন্তু fullscreen player বা reels viewer খুললে — block করো
    private fun isFbVideoPlayerActuallyOpen(root: AccessibilityNodeInfo): Boolean {
        // ── Layer 1: Fullscreen / dedicated video player ──
        val fullscreen =
            root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/fullscreen_video_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/fb_video_player").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_player_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_scrubber").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_control_overlay").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_controls_root").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/inline_video_controller").isNotEmpty()
            // নতুন FB version-এ নতুন ID
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_player_root").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_fullscreen_button").any { it.isVisibleToUser }
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/pip_button").any { it.isVisibleToUser }
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_player_progress_bar").any { it.isVisibleToUser }

        if (fullscreen) return true

        // ── Layer 2: Story video player ──
        val story =
            root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/story_viewer_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_story_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/story_video_player").isNotEmpty()

        if (story) return true

        // ── Layer 3: Mute/unmute + pause একসাথে = fullscreen video চলছে ──
        val hasScrubberOrControls =
            root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_unmute_button").any { it.isVisibleToUser }
            && root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_pause_button").any { it.isVisibleToUser }

        if (hasScrubberOrControls) return true

        return false
    }

    // ── Facebook Reels: feed tray নয়, actual reels viewer খোলা হয়েছে কিনা ──
    private fun isFbReelsViewerOpen(root: AccessibilityNodeInfo): Boolean {
        // ── Layer 1: Resource ID based (reliable for older FB versions) ──
        val byId =
            root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_viewer_root").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_viewer_fragment").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/short_video_player").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/clip_player_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/fb_shorts_container").isNotEmpty()
            // নতুন FB version-এ নতুন ID
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reel_player_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reel_viewer_content").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_player_root").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/vertical_video_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_channel_reels_container").isNotEmpty()

        if (byId) return true

        // ── Layer 2: Reels tab selected ──
        val tabSelected =
            root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_tab_button")
                .any { it.isSelected || it.isChecked || it.parent?.isSelected == true }
            // নতুন bottom nav এ reels tab
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/tab_reels")
                .any { it.isSelected || it.isChecked }
            || root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_nav_icon")
                .any { it.isSelected || it.parent?.isSelected == true }

        if (tabSelected) return true

        // ── Layer 3: tray + viewer একসাথে মানে reels এ ঢুকেছে ──
        val trayAndViewer =
            root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_tray_container").isNotEmpty()
            && root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/reels_viewer_root").isNotEmpty()

        if (trayAndViewer) return true

        // ── Layer 4: Text/contentDescription based (নতুন FB version-এ ID বদলালেও কাজ করে) ──
        // "Reels" text selected বা "Facebook Reels" title visible থাকলে reels viewer খোলা
        val reelsTextSelected =
            root.findAccessibilityNodeInfosByText("Reels")
                .any { node ->
                    (node.isSelected || node.isChecked || node.parent?.isSelected == true)
                    && node.isVisibleToUser
                }

        if (reelsTextSelected) return true

        // ── Layer 5: Fullscreen short video indicators ──
        // Reels-এ swipe করার সময় যে UI elements দেখা যায়
        val hasReelsUi =
            (root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_like_button").any { it.isVisibleToUser }
            && root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_comment_button").any { it.isVisibleToUser }
            && root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/video_share_button").any { it.isVisibleToUser })
            // Like + Comment + Share একসাথে vertical layout মানে reels UI

        if (hasReelsUi) return true

        return false
    }

    // ── 7. Facebook Video ────────────────────────────────────
    private fun handleFacebookVideo(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockFbVideo) return false

        // Watch tab — dedicated video section
        if (root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/watch_tab").isNotEmpty()
            || root.findAccessibilityNodeInfosByText("Watch").any { it.isSelected || it.parent?.isSelected == true }) {
            blockFacebookContent("Facebook Video", "Facebook Watch tab is blocked.")
            return true
        }

        // Actual video player open হয়েছে — block করো
        if (isFbVideoPlayerActuallyOpen(root)) {
            blockFacebookContent("Facebook Video", "Facebook video player is blocked.")
            return true
        }

        return false
    }

    // ── 8. Facebook Feed Reels ───────────────────────────────
    private fun handleFacebookFeedShortVideo(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockFbVideo && !prefs.blockReels) return false

        // Actual Reels viewer খোলা হয়েছে — block করো
        // Feed scroll করার সময় reels tray দেখা গেলে block করবে না
        if (isFbReelsViewerOpen(root)) {
            blockFacebookContent("Facebook Reels", "Facebook Reels is blocked.")
            return true
        }

        // blockFbVideo: autoplay video in feed (mute+pause একসাথে ছাড়া শুধু autoplay signal)
        if (prefs.blockFbVideo) {
            val autoplay = root.findAccessibilityNodeInfosByViewId("com.facebook.katana:id/auto_play_video")
                .any { it.isVisibleToUser }
            if (autoplay) {
                blockFacebookContent("Facebook Video", "Facebook auto-play video is blocked.")
                return true
            }
        }

        return false
    }


    // ── 8b. Instagram Stories Block ──────────────────────────
    private fun handleInstagramStories(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockInstaStories) return false
        if (pkg != "com.instagram.android") return false
        // L1: Stories tray / container
        if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/story_container_layout").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/stories_viewer_fragment").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/reel_header_overlay_fragment").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        // L2: Text-based detection
        if (root.findAccessibilityNodeInfosByText("Story").any { it.isVisibleToUser && it.isClickable }
            || root.findAccessibilityNodeInfosByText("Your story").any { it.isVisibleToUser }) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        // L3: Content description fallback
        if (root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_video_see_more_layout").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/story_viewer_container").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        return false
    }

    // ── 8c. Snapchat Spotlight & Stories Block ────────────────
    private fun handleSnapchat(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (pkg != "com.snapchat.android") return false

        // Spotlight block
        if (prefs.blockSnapSpotlight) {
            // L1: Spotlight tab/container
            if (root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/spotlight_tab").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/discover_feed_container").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Spotlight").any { it.isSelected || it.parent?.isSelected == true }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
            // L2: Content description / bottom nav
            if (root.findAccessibilityNodeInfosByText("Spotlight").any { it.isVisibleToUser && it.isClickable }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }

        // Stories block
        if (prefs.blockSnapStories) {
            // L1: Stories viewer
            if (root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/story_viewer_container").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/stories_feed_container").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("com.snapchat.android:id/top_snap_container").isNotEmpty()) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
            // L2: Friends stories row/tab
            if (root.findAccessibilityNodeInfosByText("Stories").any { it.isSelected || it.isChecked }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }

        return false
    }

    // ── 8d. WhatsApp Status Block ─────────────────────────────
    private fun handleWhatsAppStatus(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockWaStatus) return false
        if (pkg != "com.whatsapp") return false
        // L1: Status tab
        if (root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/status_tab").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/status_list").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/status_view_page_indicator").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        // L2: Text-based (Updates tab এ Status section)
        if (root.findAccessibilityNodeInfosByText("Status").any { it.isSelected || it.isChecked }) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        // L3: Status viewer
        if (root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/status_viewer_fragment_container").isNotEmpty()
            || root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/status_header_text_view").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK); return true
        }
        return false
    }

    // ── 8e. WhatsApp Business Status & Channels Block ─────────
    private fun handleWaBusinessBlocking(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (pkg != "com.whatsapp.w4b") return false

        if (prefs.blockWaBusinessStatus) {
            if (root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/status_tab").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/status_list").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Status").any { it.isSelected || it.isChecked }) {
                performGlobalAction(GLOBAL_ACTION_BACK); return true
            }
        }

        if (prefs.blockWaBusinessChannels) {
            if (root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/updates_tab").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/channels_home_content_layout").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Channels").any { it.isSelected || it.isChecked }
                || root.findAccessibilityNodeInfosByText("Updates").any { it.isSelected || it.isChecked }) {
                performGlobalAction(GLOBAL_ACTION_BACK); return true
            }
        }

        return false
    }

    // ── 8f. TikTok Block ──────────────────────────────────────
    private fun handleTikTok(root: AccessibilityNodeInfo, pkg: String): Boolean {
        val tiktokPkgs = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.tiktok.android")
        if (pkg !in tiktokPkgs) return false

        if (prefs.blockTikTok) {
            // L1: For You / Following feed (main screen)
            if (root.findAccessibilityNodeInfosByViewId("$pkg:id/tt_feed_item").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("$pkg:id/feed_container").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("$pkg:id/main_feed_page_video").isNotEmpty()) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
            // L2: Any TikTok screen (broad block)
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }

        if (prefs.blockTikTokLive) {
            if (root.findAccessibilityNodeInfosByViewId("$pkg:id/live_container").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("$pkg:id/live_room_fragment").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("LIVE").any { it.isVisibleToUser }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }

        return false
    }

    // ── 8g. App Search Keyword Block ─────────────────────────
    // যেকোনো supported app এর search এ adult keyword type করলে
    // typing শেষ হওয়ার আগেই block + search field clear
    private fun handleAppSearchKeyword(
        event: AccessibilityEvent?,
        root: AccessibilityNodeInfo,
        pkg: String
    ): Boolean {
        val targetPkgs = setOf(
            "org.telegram.messenger",        // Telegram
            "org.telegram.messenger.web",
            "com.whatsapp",                  // WhatsApp
            "com.whatsapp.w4b",              // WA Business
            "com.facebook.katana",           // Facebook
            "com.facebook.lite",             // FB Lite
            "com.google.android.youtube",    // YouTube search
            "com.instagram.android",         // Instagram search
            "com.twitter.android",           // Twitter / X
            "com.x.android",                 // X (new package)
            "com.reddit.frontpage",          // Reddit search
            "com.reddit.redditisfun",        // Reddit is Fun
            "com.snapchat.android",          // Snapchat search
            "com.pinterest",                 // Pinterest search
            "com.linkedin.android"           // LinkedIn search
        )
        if (pkg !in targetPkgs) return false
        if (!prefs.blockAdult) return false
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return false

        // event.text থেকে typed text নাও
        val typedText = event.text.joinToString(" ").lowercase().trim()
        if (typedText.isBlank()) return false

        // keyword match check
        val matched = adultSiteKeywords.any { kw -> typedText.contains(kw) }
        if (!matched) return false

        // ── Search field clear করো ──
        try {
            val source = event.source
            if (source != null) {
                // Method 1: ACTION_SET_TEXT দিয়ে empty করো
                val args = android.os.Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, ""
                    )
                }
                val cleared = source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (!cleared) {
                    // Method 2: select all + cut fallback
                    val sel = android.os.Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, typedText.length)
                    }
                    source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
                    source.performAction(AccessibilityNodeInfo.ACTION_CUT)
                }
                source.recycle()
            }
        } catch (e: Exception) {
            // clear fail হলেও block চলবে
        }

        // ── App অনুযায়ী নির্দিষ্ট action ──
        val now2 = System.currentTimeMillis()
        if (now2 - lastPopupTime > 1500L) {
            lastPopupTime = now2

            // Blocking screen তাৎক্ষণিক দেখাও
            mainHandler.post {
                showBlockOverlay("Adult Content", "Blocked keyword detected in search.")
            }

            when {
                // Facebook → news feed homepage এ ফিরে যাও
                pkg.contains("facebook") -> {
                    mainHandler.postDelayed({
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                                setPackage("com.facebook.katana")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            startActivity(intent)
                        } catch (_: Exception) { performGlobalAction(GLOBAL_ACTION_HOME) }
                    }, 400)
                }
                // Telegram → chat list (home) এ ফিরে যাও
                pkg.contains("telegram") -> {
                    mainHandler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }, 300)
                    mainHandler.postDelayed({
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                                setPackage(pkg)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            startActivity(intent)
                        } catch (_: Exception) { performGlobalAction(GLOBAL_ACTION_HOME) }
                    }, 600)
                }
                // WhatsApp → chats tab এ ফিরে যাও
                pkg.contains("whatsapp") -> {
                    mainHandler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }, 300)
                    mainHandler.postDelayed({
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                                setPackage(pkg)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            startActivity(intent)
                        } catch (_: Exception) { performGlobalAction(GLOBAL_ACTION_HOME) }
                    }, 600)
                }
                // YouTube → search clear করে home screen এ ফিরে যাও
                pkg == "com.google.android.youtube" -> {
                    mainHandler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }, 300)
                }
                // Instagram → home feed এ ফিরে যাও
                pkg == "com.instagram.android" -> {
                    mainHandler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }, 300)
                    mainHandler.postDelayed({
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                                setPackage(pkg)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            startActivity(intent)
                        } catch (_: Exception) { performGlobalAction(GLOBAL_ACTION_HOME) }
                    }, 600)
                }
                // Twitter / X → HOME
                pkg == "com.twitter.android" || pkg == "com.x.android" -> {
                    mainHandler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }, 300)
                    mainHandler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }, 600)
                }
                // Reddit → HOME
                pkg.contains("reddit") -> {
                    mainHandler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }, 300)
                    mainHandler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }, 600)
                }
                // Snapchat → launcher home এ ফিরে যাও
                pkg == "com.snapchat.android" -> {
                    mainHandler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }, 300)
                    mainHandler.postDelayed({
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }, 600)
                }
                // Pinterest, LinkedIn এবং বাকি সব → HOME
                else -> performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
        return true
    }

    // ── 9. Unsupported Browsers — 5 Layers ───────────────────
    private fun handleUnsupportedBrowsers(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockUnsupported) return false
        // L1: package match
        if (BLOCKED_BROWSERS.contains(pkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        // L2: recent apps
        val recentPkgs = setOf("com.android.systemui", "com.samsung.android.systemui", "com.miui.systemui")
        if (recentPkgs.contains(pkg)) {
            val inRecents = BLOCKED_BROWSER_NAMES.any { n -> root.findAccessibilityNodeInfosByText(n).any { it.isVisibleToUser } }
            if (inRecents) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        }
        // L3: Settings app info of blocked browser
        if (pkg == "com.android.settings") {
            val header = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/entity_header_title")
                .firstOrNull { it.isVisibleToUser }?.text?.toString() ?: ""
            if (BLOCKED_BROWSER_NAMES.any { header.contains(it, true) }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
            val defaultBrowserSec = root.findAccessibilityNodeInfosByText("Browser app").any { it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByText("Default browser").any { it.isVisibleToUser }
            if (defaultBrowserSec && BLOCKED_BROWSER_NAMES.any { n -> root.findAccessibilityNodeInfosByText(n).any { it.isClickable || it.isSelected } }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }
        // L4: intent chooser
        val chooserVisible = root.findAccessibilityNodeInfosByViewId("com.android.intentresolver:id/chooser_list").any { it.isVisibleToUser }
            || root.findAccessibilityNodeInfosByViewId("android:id/resolver_list").any { it.isVisibleToUser }
            || root.findAccessibilityNodeInfosByText("Open with").any { it.isVisibleToUser }
        if (chooserVisible && BLOCKED_BROWSER_NAMES.any { n -> root.findAccessibilityNodeInfosByText(n).any { it.isVisibleToUser } }) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        // L5: notification deep link
        val notif = root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/notification_panel").any { it.isVisibleToUser }
        if (notif && BLOCKED_BROWSER_NAMES.any { n -> root.findAccessibilityNodeInfosByText(n).any { it.isVisibleToUser } }) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        return false
    }

    // ── 10. New App Install — 5 Layers ───────────────────────
    private fun handleNewlyInstalledApps(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockNewApps) return false
        val installerPkgs = setOf("com.android.packageinstaller", "com.google.android.packageinstaller")
        // L1: stock installer
        if (installerPkgs.contains(pkg)) {
            val confirm = root.findAccessibilityNodeInfosByText("Do you want to install this app?").isNotEmpty()
                || root.findAccessibilityNodeInfosByText("Install").any { it.isClickable && it.isVisibleToUser }
            if (confirm) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            val panel = root.findAccessibilityNodeInfosByViewId("com.android.packageinstaller:id/install_confirm_panel")
                .any { it.isVisibleToUser }
            if (panel) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            val anyway = root.findAccessibilityNodeInfosByText("Install anyway").any { it.isClickable }
                || root.findAccessibilityNodeInfosByViewId("com.android.packageinstaller:id/install_button")
                    .any { it.isVisibleToUser }
            if (anyway) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        }
        // L2: Play Store install/update
        if (pkg == "com.android.vending") {
            val install = root.findAccessibilityNodeInfosByViewId("com.android.vending:id/buy_button")
                .any { it.isVisibleToUser && it.isClickable }
                || root.findAccessibilityNodeInfosByText("Install").any { it.isClickable && it.isVisibleToUser }
            if (install) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            val update = root.findAccessibilityNodeInfosByText("Update").any { it.isClickable && it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByViewId("com.android.vending:id/update_button")
                    .any { it.isVisibleToUser }
            if (update) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            val updateAll = root.findAccessibilityNodeInfosByText("Update all").any { it.isClickable }
            if (updateAll) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            // L3: post-install Open button
            val openAfter = root.findAccessibilityNodeInfosByText("Open").any { it.isClickable && it.isVisibleToUser }
                && root.findAccessibilityNodeInfosByText("Uninstall").any { it.isVisibleToUser }
            if (openAfter) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        }
        // L3b: package installer complete screen
        if (installerPkgs.contains(pkg)) {
            val done = root.findAccessibilityNodeInfosByText("App installed").isNotEmpty()
                || root.findAccessibilityNodeInfosByViewId("com.android.packageinstaller:id/install_success_text")
                    .any { it.isVisibleToUser }
            if (done) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        }
        // L4: Unknown sources / APK sideload in Settings
        // FIX: আগে `!it.isChecked` ছিল (OFF state block) — এটা ভুল ছিল।
        // Settings এ "Allow from this source" screen দেখলেই block করো — toggle state যাই হোক।
        if (pkg == "com.android.settings") {
            val unkScreen = root.findAccessibilityNodeInfosByText("Allow from this source").any { it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByText("Install unknown apps").any { it.isVisibleToUser }
            if (unkScreen) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }
        // L4b: APK from file managers
        val fileMgrs = setOf(
            "com.estrongs.android.pop", "com.google.android.apps.nbu.files",
            "com.sec.android.app.myfiles", "com.mi.android.globalFileexplorer",
            "com.asus.filemanager", "com.alphainventor.filemanager", "com.android.documentsui"
        )
        if (fileMgrs.contains(pkg)) {
            val apk = root.findAccessibilityNodeInfosByText(".apk").any { it.isVisibleToUser && it.isClickable }
                || root.findAccessibilityNodeInfosByText("Install").any { it.isClickable }
            if (apk) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
        }
        // L5: OEM stores
        val oemMap = mapOf(
            "com.sec.android.app.samsungapps" to "com.sec.android.app.samsungapps:id/btn_install",
            "com.xiaomi.mipicks"               to "com.xiaomi.mipicks:id/install_button",
            "com.huawei.appmarket"             to "com.huawei.appmarket:id/button_install"
        )
        for ((oemPkg, btnId) in oemMap) {
            if (pkg == oemPkg) {
                val btn = root.findAccessibilityNodeInfosByText("Install").any { it.isClickable && it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByViewId(btnId).any { it.isVisibleToUser }
                if (btn) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
        }
        val oemStores = setOf("com.oppo.market", "com.heytap.market", "com.vivo.appstore", "com.oneplus.store")
        if (oemStores.contains(pkg)) {
            if (root.findAccessibilityNodeInfosByText("Install").any { it.isClickable && it.isVisibleToUser }
                || root.findAccessibilityNodeInfosByText("Get").any { it.isClickable && it.isVisibleToUser }) {
                performGlobalAction(GLOBAL_ACTION_HOME); return true
            }
        }
        return false
    }

    // ── 11. Uninstall Protection — 10 Layers ─────────────────
    private fun handleUninstallProtection(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.uninstallProtection) return false
        try {
            // L1: Settings app info
            if (pkg == "com.android.settings") {
                if (root.findAccessibilityNodeInfosByViewId("com.android.settings:id/uninstall_button")
                        .any { it.isVisibleToUser }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
                val header = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/entity_header_title")
                    .any { it.text?.toString()?.contains(rasFocusName, true) == true }
                val unBtn = root.findAccessibilityNodeInfosByText("Uninstall").any { it.isClickable && it.isVisibleToUser }
                if (header && unBtn) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
                if (root.findAccessibilityNodeInfosByText("Do you want to uninstall this app?").isNotEmpty()
                    || root.findAccessibilityNodeInfosByText("Uninstall $rasFocusName?").isNotEmpty()) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            // L2: Play Store
            if (pkg == "com.android.vending") {
                if (root.findAccessibilityNodeInfosByViewId("com.android.vending:id/uninstall_button")
                        .any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Uninstall").any { it.isVisibleToUser && it.isClickable }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            // L3: Package Installer
            val instPkgs = setOf("com.android.packageinstaller", "com.google.android.packageinstaller")
            if (instPkgs.contains(pkg)) {
                if (root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Do you want to uninstall this app?").isNotEmpty()) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            // L4: Force Stop / Clear Data
            if (pkg == "com.android.settings") {
                if (root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }) {
                    if (root.findAccessibilityNodeInfosByViewId("com.android.settings:id/force_stop_button")
                            .any { it.isVisibleToUser && it.isEnabled }
                        || root.findAccessibilityNodeInfosByText("Force stop").any { it.isClickable }) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                    if (root.findAccessibilityNodeInfosByViewId("com.android.settings:id/clear_user_data_button")
                            .any { it.isVisibleToUser }) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
            }
            // L5: Third party file managers
            val fileMgrs5 = setOf(
                "com.estrongs.android.pop", "com.google.android.apps.nbu.files",
                "com.sec.android.app.myfiles", "com.mi.android.globalFileexplorer",
                "com.asus.filemanager", "com.alphainventor.filemanager"
            )
            if (fileMgrs5.contains(pkg)) {
                if (root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }
                    && root.findAccessibilityNodeInfosByText("Uninstall").any { it.isClickable }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            // L6: Home screen long press
            // FIX: "Remove" text false positive (widget remove, icon remove)।
            // "Remove" শুধু block করো যখন "Uninstall" dialog/text-ও দেখা যাচ্ছে — তাহলে নিশ্চিত app remove।
            val launchers = setOf(
                "com.google.android.apps.nexuslauncher", "com.samsung.android.launcher",
                "com.miui.home", "com.huawei.android.launcher", "com.oppo.launcher",
                "com.vivo.launcher", "com.android.launcher", "com.android.launcher3",
                "com.teslacoilsw.launcher", "org.zimmob.zimlx"
            )
            if (launchers.contains(pkg)) {
                if (root.findAccessibilityNodeInfosByText("Uninstall").any { it.isVisibleToUser }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
                // "Remove" শুধু block যদি app name-ও দেখা যায় — widget remove false positive এড়াতে
                val hasAppName = root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }
                val hasRemove  = root.findAccessibilityNodeInfosByText("Remove").any { it.isVisibleToUser && it.isClickable }
                if (hasAppName && hasRemove) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
            // L7: Game launchers
            val gameLaunchers = setOf(
                "com.samsung.android.game.gamehome", "com.garena.game.freefire",
                "com.mobile.legends", "com.tencent.ig", "com.dts.freefireth"
            )
            if (gameLaunchers.contains(pkg)) {
                val hasApp = root.findAccessibilityNodeInfosByText(rasFocusName)
                    .any { it.isChecked || it.isSelected || it.isVisibleToUser }
                val hasDel = root.findAccessibilityNodeInfosByText("Uninstall").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Delete").any { it.isVisibleToUser }
                if (hasApp && hasDel) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
            // L8: Device Admin
            if (pkg == "com.android.settings") {
                val adminScreen = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/device_admin_settings").isNotEmpty()
                    || root.findAccessibilityNodeInfosByText("Device admin apps").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Device Administrator").any { it.isVisibleToUser }
                if (adminScreen && root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isClickable || it.isVisibleToUser }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
                if (root.findAccessibilityNodeInfosByText("Deactivate").any { it.isVisibleToUser && it.isClickable }
                    || root.findAccessibilityNodeInfosByText("Deactivate this device admin app").any { it.isVisibleToUser }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            // L9: Accessibility service page
            // ⚠️ FIX: isChecked=true মানে service ON আছে — এটা block করা ঠিক না।
            // User যদি service OFF করতে চায় (Turn off / Stop) শুধু তখনই block করো।
            // Service ON করার সময় HOME করলে user কখনোই service চালু করতে পারবে না।
            if (pkg == "com.android.settings") {
                val accScreen = root.findAccessibilityNodeInfosByText("Accessibility").any { it.isVisibleToUser }
                if (accScreen && root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }) {
                    val tryingToTurnOff =
                        root.findAccessibilityNodeInfosByText("Turn off").any { it.isVisibleToUser }
                        || root.findAccessibilityNodeInfosByText("Stop").any { it.isClickable }
                        // switch unchecked (OFF) হওয়ার পরের confirmation dialog
                        || (root.findAccessibilityNodeInfosByViewId("com.android.settings:id/switch_widget")
                            .any { it.isVisibleToUser && !it.isChecked }
                            && root.findAccessibilityNodeInfosByText("OK").any { it.isVisibleToUser && it.isClickable })
                    if (tryingToTurnOff) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
            }
            // L10: Running services
            if (pkg == "com.android.settings") {
                val running = root.findAccessibilityNodeInfosByText("Running services").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Running apps").any { it.isVisibleToUser }
                if (running && root.findAccessibilityNodeInfosByText(rasFocusName).any { it.isVisibleToUser }) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }
            // Global fallback
            val uninstallTexts = listOf(
                "Uninstall $rasFocusName?", "Remove $rasFocusName?",
                "Delete $rasFocusName?",    "Do you want to uninstall $rasFocusName?"
            )
            for (txt in uninstallTexts) {
                if (root.findAccessibilityNodeInfosByText(txt).isNotEmpty()) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }

            // L11: itel Freezer Protection (package: com.itel)
            if (pkg == "com.itel") {
                val appNodes = root.findAccessibilityNodeInfosByText(rasFocusName)
                for (node in appNodes) {
                    if (node.isChecked || node.isSelected) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        return true
                    }
                    val parent = node.parent
                    if (parent != null && (parent.isChecked || parent.isSelected)) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        return true
                    }
                }
            }

            // ── BlockerHero smali থেকে port করা নতুন layers ──────────────
            //
            // L12: checkBox_show_freeze_badge — Island / App Manager freeze
            // BlockerHero D4/A.smali lines 264-279, reason code 14:
            //   v0_41.append(":id/checkBox_show_freeze_badge")
            //   p11.I = Integer.valueOf(14)
            // Island, App Manager, Shelter ইত্যাদি app-এর freeze checkbox এর view ID।
            // User যখন RasFocus-কে freeze করতে tap করে, এই view ID fire হয়।
            try {
                val freezeBadgeNodes = root.findAccessibilityNodeInfosByViewId(
                    "$pkg:id/checkBox_show_freeze_badge"
                )
                if (freezeBadgeNodes.any { it.isVisibleToUser }) {
                    // freeze checkbox-এ RasFocus selected আছে কিনা চেক করি
                    val hasRasFocus = root.findAccessibilityNodeInfosByText(rasFocusName)
                        .any { it.isVisibleToUser }
                    if (hasRasFocus || freezeBadgeNodes.any { it.isChecked }) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
            } catch (_: Exception) {}

            // L13: delete_icon_container — MIUI / Samsung app manager delete icon
            // BlockerHero D4/A.smali lines 249-262, reason code 13:
            //   v3_3.append(":id/delete_icon_container")
            //   p11.I = Integer.valueOf(13)
            // MIUI / Xiaomi / Samsung এর app manager-এ delete/remove icon-এর parent container।
            // User যখন RasFocus-এর delete icon-এ tap করে, parent node-এ এই view ID থাকে।
            try {
                val deleteContainerNodes = root.findAccessibilityNodeInfosByViewId(
                    "$pkg:id/delete_icon_container"
                )
                if (deleteContainerNodes.any { it.isVisibleToUser }) {
                    val hasRasFocus = root.findAccessibilityNodeInfosByText(rasFocusName)
                        .any { it.isVisibleToUser }
                    if (hasRasFocus) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
                // parent node দিয়েও চেক — source node-এর parent-এ delete_icon_container
                // BlockerHero D4/A.smali: p13.getSource().getParent().getViewIdResourceName()
                val allNodes = root.findAccessibilityNodeInfosByText(rasFocusName)
                for (node in allNodes) {
                    val parentId = node.parent?.viewIdResourceName ?: continue
                    if (parentId.endsWith(":id/delete_icon_container")) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
            } catch (_: Exception) {}

            // L14: txt_uninstall_main_title — OEM custom uninstall confirmation screen
            // BlockerHero D4/A.smali lines 227-236, reason code 12:
            //   D4.r.e(source, pkg + ":id/txt_uninstall_main_title")
            //   D4.A.c.a(text) → "Uninstall these|Uninstall the" match
            // কিছু OEM (Oppo, Vivo, Realme, OnePlus) এর custom app manager-এ
            // uninstall confirmation screen-এ এই view ID থাকে।
            try {
                val uninstallTitleNodes = root.findAccessibilityNodeInfosByViewId(
                    "$pkg:id/txt_uninstall_main_title"
                )
                for (titleNode in uninstallTitleNodes) {
                    val txt = titleNode.text?.toString() ?: continue
                    if (txt.contains("Uninstall", true) || txt.contains(rasFocusName, true)) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
            } catch (_: Exception) {}

            // L15: Transsion built-in freezer — Tecno / Infinix / itel phone manager
            // BlockerHero D4/A.smali lines 270-275, reason code 15:
            //   D4.A.b = new Y7.k("/|freezer")  → text pattern
            //   Y7.m.U(p14, "transsion", 0)      → Transsion device check
            // Tecno/Infinix/itel এর built-in phone manager-এ freeze option এর
            // text-এ "freezer" বা "/" থাকে। এই device গুলোতে extra check।
            try {
                val manufacturer = android.os.Build.MANUFACTURER.lowercase()
                val isTranssion = listOf("tecno", "infinix", "itel", "transsion")
                    .any { manufacturer.contains(it) }
                if (isTranssion) {
                    // Event text-এ "freezer" বা "/" আছে এবং RasFocus visible?
                    val allText = collectAllText(root)
                    val hasFreezerText = allText.contains("freezer", true) || allText.contains("/freeze", true)
                    val hasRasFocus   = allText.contains(rasFocusName, true)
                    if (hasFreezerText && hasRasFocus) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                }
            } catch (_: Exception) {}

            // L16: DeviceAdminAdd screen — অন্য app-এর admin revoke বা
            // settings থেকে RasFocus-এর admin disable করার চেষ্টা
            // BlockerHero MyAccessibilityService line 288:
            //   if (!Y7.m.U(v0_27, "DeviceAdminAdd", 0)) → BACK চেপে দেয়
            // className-এ "DeviceAdminAdd" থাকলে এবং RasFocus visible হলে block।
            // (RasFocus নিজে admin enable করার screen হলে allow করি।)
            try {
                if (pkg == "com.android.settings") {
                    // "Deactivate" button — Device Admin revoke confirmation
                    val deactivateBtn = root.findAccessibilityNodeInfosByText("Deactivate this device admin app")
                        .any { it.isVisibleToUser }
                    if (deactivateBtn && root.findAccessibilityNodeInfosByText(rasFocusName)
                            .any { it.isVisibleToUser }) {
                        performGlobalAction(GLOBAL_ACTION_HOME); return true
                    }
                    // "Device Admin" settings page-এ RasFocus row visible এবং clickable
                    val onAdminPage = root.findAccessibilityNodeInfosByText("Device admin apps")
                        .any { it.isVisibleToUser }
                        || root.findAccessibilityNodeInfosByText("Active admin").any { it.isVisibleToUser }
                    if (onAdminPage) {
                        val rasFocusRow = root.findAccessibilityNodeInfosByText(rasFocusName)
                            .any { it.isVisibleToUser && it.isClickable }
                        if (rasFocusRow) {
                            performGlobalAction(GLOBAL_ACTION_HOME); return true
                        }
                    }
                }
            } catch (_: Exception) {}

        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        return false
    }

    // ── 12. Reboot / Power / ADB Protection ──────────────────
    private fun handleRebootProtection(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!prefs.blockReboot && !prefs.blockPowerOff && !prefs.blockSafeMode
            && !prefs.blockRecovery && !prefs.blockAdb) return false
        try {
            // OEM systemui packages — Samsung, Xiaomi, MIUI, Huawei, OnePlus, Oppo, Vivo সব cover
            val powerMenuPkgs = setOf(
                "com.android.systemui",
                "com.samsung.android.systemui",
                "com.miui.systemui",
                "com.huawei.systemmanager",
                "com.oppo.systemui",
                "com.vivo.systemui",
                "com.oneplus.systemui",
                "com.coloros.systemui"
            )

            // L1: Power menu — Restart/Reboot
            if (prefs.blockReboot && powerMenuPkgs.contains(pkg)) {
                val reboot = root.findAccessibilityNodeInfosByText("Restart").any { it.isVisibleToUser && it.isClickable }
                    || root.findAccessibilityNodeInfosByText("Reboot").any { it.isVisibleToUser && it.isClickable }
                    || root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/restart_button").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByViewId("com.samsung.android.systemui:id/restart_button").any { it.isVisibleToUser }
                if (reboot) { performGlobalAction(GLOBAL_ACTION_HOME); return true }

                val pmVisible = root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/global_actions_grid_item").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/power_menu").any { it.isVisibleToUser }
                if (pmVisible && (root.findAccessibilityNodeInfosByText("Restart").any { it.isVisibleToUser }
                        || root.findAccessibilityNodeInfosByText("Reboot").any { it.isVisibleToUser })) {
                    performGlobalAction(GLOBAL_ACTION_HOME); return true
                }
            }

            // L2: Power off
            if (prefs.blockPowerOff && powerMenuPkgs.contains(pkg)) {
                val powerOff = root.findAccessibilityNodeInfosByText("Power off").any { it.isVisibleToUser && it.isClickable }
                    || root.findAccessibilityNodeInfosByText("Shut down").any { it.isVisibleToUser && it.isClickable }
                    || root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/power_off_button").any { it.isVisibleToUser }
                if (powerOff) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
                // Power off confirm dialog
                val confirm = root.findAccessibilityNodeInfosByText("Power off?").isNotEmpty()
                    || root.findAccessibilityNodeInfosByText("Shut down?").isNotEmpty()
                if (confirm) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }

            // L3: Safe mode
            if (prefs.blockSafeMode) {
                val safeMode = root.findAccessibilityNodeInfosByText("Safe mode").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Running in safe mode").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Restart to safe mode?").isNotEmpty()
                    || root.findAccessibilityNodeInfosByText("Reboot into safe mode?").isNotEmpty()
                    || root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/safe_mode_text").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByViewId("com.samsung.android.systemui:id/safe_mode_button").any { it.isVisibleToUser }
                if (safeMode) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }

            // L4: Recovery / Bootloader / Factory reset
            if (prefs.blockRecovery) {
                val recovery = root.findAccessibilityNodeInfosByText("Recovery mode").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Reboot to recovery").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Unlock bootloader").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Fastboot mode").any { it.isVisibleToUser }
                if (recovery) { performGlobalAction(GLOBAL_ACTION_HOME); return true }

                if (pkg == "com.android.settings") {
                    val factory = root.findAccessibilityNodeInfosByText("Factory data reset").any { it.isVisibleToUser }
                        || root.findAccessibilityNodeInfosByText("Erase all data").any { it.isVisibleToUser }
                        || root.findAccessibilityNodeInfosByText("Reset phone").any { it.isVisibleToUser }
                        || root.findAccessibilityNodeInfosByViewId("com.android.settings:id/eraseButton").any { it.isVisibleToUser }
                    if (factory) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
                }
            }

            // L5: ADB
            // FIX: আগে switch OFF থাকলে block করছিল — এটা ভুল ছিল।
            // ADB Settings screen দেখলেই সরাসরি HOME পাঠাও — toggle state check দরকার নেই।
            // কারণ: screen-এ ঢোকা মানেই enable করার attempt।
            if (prefs.blockAdb && pkg == "com.android.settings") {
                val adbDialog = root.findAccessibilityNodeInfosByText("Allow USB debugging?").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Allow wireless debugging?").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("RSA key fingerprint").any { it.isVisibleToUser }
                if (adbDialog) { performGlobalAction(GLOBAL_ACTION_HOME); return true }

                // ADB screen দেখলেই block — toggle state নির্বিশেষে
                val adbScreen = root.findAccessibilityNodeInfosByText("USB debugging").any { it.isVisibleToUser }
                    || root.findAccessibilityNodeInfosByText("Wireless debugging").any { it.isVisibleToUser }
                if (adbScreen) { performGlobalAction(GLOBAL_ACTION_HOME); return true }
            }
        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME); return true
        }
        return false
    }
}


// ════════════════════════════════════════════════════════════
// D. SETTINGS UI — Jetpack Compose
// Design: Dark industrial + neon accent — unique, unforgettable
// ════════════════════════════════════════════════════════════

class RasFocusSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RasFocusSettingsTheme {
                SettingsScreen()
            }
        }
    }
}

// ── Theme ─────────────────────────────────────────────────

private val BG_DEEP       = Color(0xFF0A0C10)
private val BG_CARD       = Color(0xFF111318)
private val BG_CARD2      = Color(0xFF161A22)
private val ACCENT        = Color(0xFF00F5C4)   // neon mint
private val ACCENT2       = Color(0xFF7B5CFA)   // electric violet
private val ACCENT_RED    = Color(0xFFFF3B5C)   // danger red
private val ACCENT_AMBER  = Color(0xFFFFB800)   // warning amber
private val TEXT_PRIMARY  = Color(0xFFEAEDF3)
private val TEXT_SEC      = Color(0xFF6B7280)
private val DIVIDER       = Color(0xFF1E222C)
private val SWITCH_ON     = ACCENT
private val SWITCH_OFF    = Color(0xFF2A2F3D)

@Composable
fun RasFocusSettingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BG_DEEP,
            surface = BG_CARD,
            primary = ACCENT,
            onBackground = TEXT_PRIMARY,
            onSurface = TEXT_PRIMARY
        ),
        content = content
    )
}

// ── Main Screen ───────────────────────────────────────────

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = remember { BlockerPrefs(ctx) }

    // State holders
    var blockAdult       by remember { mutableStateOf(prefs.blockAdult) }
    var blockAdultSiteList by remember { mutableStateOf(prefs.blockAdultSiteList) }
    var blockSearch      by remember { mutableStateOf(prefs.blockSearch) }
    var blockReels       by remember { mutableStateOf(prefs.blockReels) }
    var blockInstaSearch by remember { mutableStateOf(prefs.blockInstaSearch) }
    var blockYtShorts    by remember { mutableStateOf(prefs.blockYtShorts) }
    var blockWaChannels  by remember { mutableStateOf(prefs.blockWaChannels) }

    // ── নতুন Block Reels/Shorts per-app state ──
    var blockInstaStories      by remember { mutableStateOf(prefs.blockInstaStories) }
    var blockWaStatus          by remember { mutableStateOf(prefs.blockWaStatus) }
    var blockWaBusinessStatus  by remember { mutableStateOf(prefs.blockWaBusinessStatus) }
    var blockWaBusinessChannels by remember { mutableStateOf(prefs.blockWaBusinessChannels) }
    var blockSnapSpotlight     by remember { mutableStateOf(prefs.blockSnapSpotlight) }
    var blockSnapStories       by remember { mutableStateOf(prefs.blockSnapStories) }
    var blockTikTok            by remember { mutableStateOf(prefs.blockTikTok) }
    var blockTikTokLive        by remember { mutableStateOf(prefs.blockTikTokLive) }

    var blockUnsupported by remember { mutableStateOf(prefs.blockUnsupported) }
    var blockNewApps     by remember { mutableStateOf(prefs.blockNewApps) }
    var blockFbVideo     by remember { mutableStateOf(prefs.blockFbVideo) }

    // ── Admin helper ──────────────────────────────────────────────────────────
    val dpm = remember {
        ctx.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE)
            as android.app.admin.DevicePolicyManager
    }
    val adminComponent = remember {
        android.content.ComponentName(ctx, com.rasel.RasFocus.features.MyDeviceAdminReceiver::class.java)
    }
    fun isAdminActive() = dpm.isAdminActive(adminComponent)
    fun isAccessibilityActive(): Boolean {
        // Full component name check — শুধু package name নয়
        val fullName = "${ctx.packageName}/.selfcontrol.RasFocusBlockingService"
        val list = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val inSettings = list.contains(fullName, ignoreCase = true)
                      || list.contains(ctx.packageName, ignoreCase = true)
        // Instance alive কিনাও check করো
        val instanceAlive = RasFocusBlockingService.instance != null
        return inSettings && instanceAlive
    }

    var uninstallProt by remember { mutableStateOf(isAdminActive()) }
    var isServiceActive by remember { mutableStateOf(isAccessibilityActive()) }

    // ── Device Admin result launcher ───────────────────────────────────────────
    // Admin permission dialog ফেরত আসলে result দেখে UI update করি
    val adminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val adminNowActive = isAdminActive()
        uninstallProt = adminNowActive
        prefs.uninstallProtection = adminNowActive
        if (adminNowActive && !isAccessibilityActive()) {
            // Admin পেয়েছি — এখন Accessibility চাই
            android.widget.Toast.makeText(
                ctx,
                "✅ Device Admin দেওয়া হয়েছে! এখন Accessibility permission দিন।",
                android.widget.Toast.LENGTH_LONG
            ).show()
            try {
                ctx.startActivity(
                    android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) {}
        }
    }

    // ── ON_RESUME: admin/accessibility status re-sync ──────────────────────────
    // User settings screen থেকে ফিরে আসলে toggle state update করি
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val adminActive = isAdminActive()
                uninstallProt = adminActive
                prefs.uninstallProtection = adminActive
                isServiceActive = isAccessibilityActive()  // service status sync
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var blockAdb         by remember { mutableStateOf(prefs.blockAdb) }
    var blockPowerOff    by remember { mutableStateOf(prefs.blockPowerOff) }
    var blockSafeMode    by remember { mutableStateOf(prefs.blockSafeMode) }
    var blockReboot      by remember { mutableStateOf(prefs.blockReboot) }
    var blockRecovery    by remember { mutableStateOf(prefs.blockRecovery) }

    var blockedMsg       by remember { mutableStateOf(prefs.blockedMessage) }
    var redirectUrl      by remember { mutableStateOf(prefs.redirectUrl) }

    // ── Focus Lock state ──
    var focusLockActive   by remember { mutableStateOf(prefs.focusLockActive) }
    var focusLockMode     by remember { mutableStateOf(prefs.focusLockMode) }
    var focusLockEndTime  by remember { mutableStateOf(prefs.focusLockEndTime) }
    var showFocusSetupDialog  by remember { mutableStateOf(false) }
    var showFocusUnlockDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BG_DEEP)
    ) {
        // Subtle grid background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spacing = 40.dp.toPx()
            val cols = (size.width / spacing).toInt() + 1
            val rows = (size.height / spacing).toInt() + 1
            for (col in 0..cols) {
                drawLine(Color(0xFF131720), Offset(col * spacing, 0f), Offset(col * spacing, size.height), strokeWidth = 0.5f)
            }
            for (row in 0..rows) {
                drawLine(Color(0xFF131720), Offset(0f, row * spacing), Offset(size.width, row * spacing), strokeWidth = 0.5f)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(top = 8.dp) // no more reserved space for fixed bar
        ) {
            // ── Header ──
            Spacer(Modifier.height(8.dp))
            HeaderBar(isActive = isServiceActive)
            Spacer(Modifier.height(12.dp))

            // ── Focus Lock bar — header-এর নিচে, scroll content-এর অংশ ──
            FocusLockTopBar(
                isActive = focusLockActive,
                endTime = focusLockEndTime,
                onStartClick = { showFocusSetupDialog = true },
                onUnlockClick = { showFocusUnlockDialog = true }
            )
            Spacer(Modifier.height(20.dp))

            // ── Content Blocking ──
            SectionHeader(
                icon = "⊡",
                title = "Content Blocking",
                accentColor = ACCENT
            )
            Spacer(Modifier.height(8.dp))
            BlockingCard {
                RasSwitch(
                    label = "Block adult content",
                    sublabel = "Blocks adult URLs in Chrome",
                    checked = blockAdult,
                    accentColor = ACCENT_RED,
                    onCheckedChange = {
                        if (it || !focusLockActive) {
                            blockAdult = it
                            prefs.blockAdult = it
                            // Switch ON হলে already open সাইটও block
                            RasFocusBlockingService.instance?.checkCurrentWindow()
                        }
                    }
                )
                RasDivider()
                RasSwitch(
                    label = "Block image & video search",
                    sublabel = "Blocks Google image/video tab",
                    checked = blockSearch,
                    accentColor = ACCENT,
                    onCheckedChange = { if (it || !focusLockActive) { blockSearch = it; prefs.blockSearch = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                )
                RasDivider()
                RasSwitch(
                    label = "Block Instagram search",
                    sublabel = "Blocks Explore & search tab",
                    checked = blockInstaSearch,
                    accentColor = ACCENT,
                    onCheckedChange = { if (it || !focusLockActive) { blockInstaSearch = it; prefs.blockInstaSearch = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                )
                RasDivider()
                RasSwitch(
                    label = "Block WhatsApp channels",
                    sublabel = "Blocks Updates/Channels tab",
                    checked = blockWaChannels,
                    accentColor = ACCENT,
                    onCheckedChange = { if (it || !focusLockActive) { blockWaChannels = it; prefs.blockWaChannels = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Block Reels / Shorts — per-app breakdown ──
            SectionHeader(
                icon = "▶",
                title = "Block Reels / Shorts",
                accentColor = ACCENT
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Block distracting content like Shorts, Reels, and other in-app feeds to stay focused.",
                color = TEXT_SEC,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BG_CARD, RoundedCornerShape(10.dp))
                    .border(1.dp, DIVIDER, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Spacer(Modifier.height(8.dp))

            // YouTube
            AppReelsCard(
                appIcon = "▶",
                appName = "YouTube",
                appIconColor = Color(0xFFFF0000),
                rows = listOf(
                    AppReelsRow.Toggle(
                        label = "Shorts",
                        checked = blockYtShorts,
                        onCheckedChange = { if (it || !focusLockActive) { blockYtShorts = it; prefs.blockYtShorts = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                    )
                )
            )
            Spacer(Modifier.height(8.dp))

            // Instagram
            AppReelsCard(
                appIcon = "📷",
                appName = "Instagram",
                appIconColor = Color(0xFFE1306C),
                rows = listOf(
                    AppReelsRow.Toggle(
                        label = "Reels",
                        checked = blockReels,
                        onCheckedChange = { if (it || !focusLockActive) { blockReels = it; prefs.blockReels = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                    ),
                    AppReelsRow.Toggle(
                        label = "Stories",
                        checked = blockInstaStories,
                        onCheckedChange = { if (it || !focusLockActive) { blockInstaStories = it; prefs.blockInstaStories = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                    )
                )
            )
            Spacer(Modifier.height(8.dp))

            // WhatsApp
            AppReelsCard(
                appIcon = "💬",
                appName = "WhatsApp",
                appIconColor = Color(0xFF25D366),
                rows = listOf(
                    AppReelsRow.Toggle(
                        label = "Status",
                        checked = blockWaStatus,
                        onCheckedChange = { if (it || !focusLockActive) { blockWaStatus = it; prefs.blockWaStatus = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                    ),
                    AppReelsRow.Toggle(
                        label = "Channels",
                        checked = blockWaChannels,
                        onCheckedChange = { if (it || !focusLockActive) { blockWaChannels = it; prefs.blockWaChannels = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                    )
                )
            )
            Spacer(Modifier.height(8.dp))

            // Snapchat
            AppReelsCard(
                appIcon = "👻",
                appName = "Snapchat",
                appIconColor = Color(0xFFFFFC00),
                rows = listOf(
                    AppReelsRow.Toggle(
                        label = "Spotlight",
                        checked = blockSnapSpotlight,
                        onCheckedChange = { if (it || !focusLockActive) { blockSnapSpotlight = it; prefs.blockSnapSpotlight = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                    ),
                    AppReelsRow.Toggle(
                        label = "Stories",
                        checked = blockSnapStories,
                        onCheckedChange = { if (it || !focusLockActive) { blockSnapStories = it; prefs.blockSnapStories = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                    )
                )
            )
            Spacer(Modifier.height(8.dp))

            // WA Business
            AppReelsCard(
                appIcon = "💼",
                appName = "WA Business",
                appIconColor = Color(0xFF128C7E),
                rows = listOf(
                    AppReelsRow.Toggle(
                        label = "Status",
                        checked = blockWaBusinessStatus,
                        onCheckedChange = { if (it || !focusLockActive) { blockWaBusinessStatus = it; prefs.blockWaBusinessStatus = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                    ),
                    AppReelsRow.Toggle(
                        label = "Channels",
                        checked = blockWaBusinessChannels,
                        onCheckedChange = { if (it || !focusLockActive) { blockWaBusinessChannels = it; prefs.blockWaBusinessChannels = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                    )
                )
            )
            Spacer(Modifier.height(8.dp))

            // TikTok
            AppReelsCard(
                appIcon = "🎵",
                appName = "TikTok",
                appIconColor = Color(0xFF010101),
                rows = listOf(
                    AppReelsRow.Toggle(
                        label = "Feed / For You",
                        checked = blockTikTok,
                        onCheckedChange = { if (it || !focusLockActive) { blockTikTok = it; prefs.blockTikTok = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                    ),
                    AppReelsRow.Toggle(
                        label = "LIVE",
                        checked = blockTikTokLive,
                        onCheckedChange = { if (it || !focusLockActive) { blockTikTokLive = it; prefs.blockTikTokLive = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                    )
                )
            )
            SectionHeader(icon = "◈", title = "Advanced Blocking", accentColor = ACCENT2)
            Spacer(Modifier.height(8.dp))
            BlockingCard {
                RasSwitch(
                    label = "Block unsupported browsers",
                    sublabel = "Opera, Firefox, Brave, UC & more",
                    checked = blockUnsupported,
                    accentColor = ACCENT2,
                    onCheckedChange = { if (it || !focusLockActive) { blockUnsupported = it; prefs.blockUnsupported = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                )
                RasDivider()
                RasSwitch(
                    label = "Block newly installed apps",
                    sublabel = "Prevents all new installs & APK sideloads",
                    checked = blockNewApps,
                    accentColor = ACCENT2,
                    onCheckedChange = { if (it || !focusLockActive) { blockNewApps = it; prefs.blockNewApps = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                )
                RasDivider()
                RasSwitch(
                    label = "Block Facebook video",
                    sublabel = "Blocks Watch, Reels & inline videos",
                    checked = blockFbVideo,
                    accentColor = ACCENT2,
                    onCheckedChange = { if (it || !focusLockActive) { blockFbVideo = it; prefs.blockFbVideo = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Protection (Uninstall + Power) ──
            SectionHeader(icon = "⬡", title = "App Protection", accentColor = ACCENT_RED)
            Spacer(Modifier.height(8.dp))

            // Uninstall master toggle — highlighted
            // Toggle ON → Device Admin activation intent fire করে
            // Toggle OFF → Device Admin revoke করে
            MasterToggleCard(
                label = "Uninstall protection",
                sublabel = "10-layer block — Settings, Play Store, ADB, launchers & more",
                checked = uninstallProt,
                onCheckedChange = { newValue ->
                    if (newValue || !focusLockActive) {
                        if (newValue) {
                            // ── STEP 1: Device Admin permission ───────────────────────────────
                            // adminLauncher ব্যবহার করি যাতে result capture হয় এবং
                            // UI toggle সঙ্গে সঙ্গে update হয়।
                            if (isAdminActive()) {
                                // Admin আগেই active — সরাসরি accessibility check করো
                                uninstallProt = true
                                prefs.uninstallProtection = true
                                if (!isAccessibilityActive()) {
                                    android.widget.Toast.makeText(
                                        ctx,
                                        "⚠️ Uninstall protection-এর জন্য Accessibility permission দিন!",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    try {
                                        ctx.startActivity(
                                            android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    } catch (_: Exception) {}
                                }
                            } else {
                                // Admin নেই — system dialog খুলি, result adminLauncher-এ catch হবে
                                val intent = android.content.Intent(
                                    android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
                                ).apply {
                                    putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                    putExtra(
                                        android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                        "RasFocus-কে uninstall থেকে রক্ষা করতে Device Admin permission দরকার। " +
                                        "এটা চালু থাকলে কেউ app টা delete করতে পারবে না।"
                                    )
                                }
                                try {
                                    adminLauncher.launch(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(
                                        ctx, "Device Admin permission দিতে ব্যর্থ হয়েছে", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } else {
                            // ── Toggle OFF: Admin revoke ───────────────────────────────────────
                            try {
                                if (dpm.isAdminActive(adminComponent)) {
                                    dpm.removeActiveAdmin(adminComponent)
                                    // onDisabled() callback-এ prefs set হবে;
                                    // ON_RESUME observer UI sync করবে
                                } else {
                                    uninstallProt = false
                                    prefs.uninstallProtection = false
                                }
                            } catch (e: Exception) {
                                uninstallProt = false
                                prefs.uninstallProtection = false
                            }
                        }
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            // Power / System protection sub-switches
            BlockingCard {
                SubSwitchRow(
                    icon = "⏻",
                    label = "Block power off",
                    sublabel = "Prevents device shutdown",
                    checked = blockPowerOff,
                    accentColor = ACCENT_AMBER,
                    onCheckedChange = { if (it || !focusLockActive) { blockPowerOff = it; prefs.blockPowerOff = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                )
                RasDivider()
                SubSwitchRow(
                    icon = "↺",
                    label = "Block reboot",
                    sublabel = "Blocks restart from power menu & Settings",
                    checked = blockReboot,
                    accentColor = ACCENT_AMBER,
                    onCheckedChange = { if (it || !focusLockActive) { blockReboot = it; prefs.blockReboot = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                )
                RasDivider()
                SubSwitchRow(
                    icon = "⚠",
                    label = "Block safe mode",
                    sublabel = "Detects & exits safe mode boot",
                    checked = blockSafeMode,
                    accentColor = ACCENT_AMBER,
                    onCheckedChange = { if (it || !focusLockActive) { blockSafeMode = it; prefs.blockSafeMode = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                )
                RasDivider()
                SubSwitchRow(
                    icon = "⟳",
                    label = "Block recovery / factory reset",
                    sublabel = "Blocks recovery mode, bootloader, factory reset",
                    checked = blockRecovery,
                    accentColor = ACCENT_RED,
                    onCheckedChange = { if (it || !focusLockActive) { blockRecovery = it; prefs.blockRecovery = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                )
                RasDivider()
                SubSwitchRow(
                    icon = "⌁",
                    label = "Block ADB / USB debugging",
                    sublabel = "Blocks ADB authorization dialogs",
                    checked = blockAdb,
                    accentColor = ACCENT_RED,
                    onCheckedChange = { if (it || !focusLockActive) { blockAdb = it; prefs.blockAdb = it ; RasFocusBlockingService.instance?.checkCurrentWindow() } }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Customize Blocked Screen ──
            SectionHeader(icon = "✦", title = "Customize Blocked Screen", accentColor = TEXT_SEC)
            Spacer(Modifier.height(8.dp))
            BlockingCard {
                CustomizeRow(
                    label = "Blocked screen message",
                    value = blockedMsg,
                    placeholder = "This page is blocked.",
                    onValueChange = { blockedMsg = it; prefs.blockedMessage = it }
                )
                RasDivider()
                CustomizeRow(
                    label = "Redirect after closing (any URL)",
                    value = redirectUrl,
                    placeholder = "https://www.google.com",
                    onValueChange = { redirectUrl = it; prefs.redirectUrl = it }
                )
            }

            Spacer(Modifier.height(24.dp))

            Spacer(Modifier.height(32.dp))
        }

        // ── Setup Dialog ──
        if (showFocusSetupDialog) {
            FocusLockSetupDialog(
                prefs = prefs,
                onDismiss = { showFocusSetupDialog = false },
                onActivated = { mode, endMs ->
                    focusLockActive = true
                    focusLockMode = mode
                    focusLockEndTime = endMs
                    prefs.focusLockActive = true
                    prefs.focusLockMode = mode
                    prefs.focusLockEndTime = endMs
                    showFocusSetupDialog = false
                }
            )
        }

        // ── Unlock Dialog ──
        if (showFocusUnlockDialog) {
            FocusLockUnlockDialog(
                prefs = prefs,
                onDismiss = { showFocusUnlockDialog = false },
                onUnlocked = {
                    focusLockActive = false
                    focusLockMode = "none"
                    focusLockEndTime = 0L
                    prefs.focusLockActive = false
                    prefs.focusLockMode = "none"
                    prefs.focusLockEndTime = 0L
                    showFocusUnlockDialog = false
                }
            )
        }
    }
}

// ── UI Components ─────────────────────────────────────────

@Composable
fun HeaderBar(isActive: Boolean) {
    val ctx = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Logo mark
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        brush = Brush.linearGradientBrush(
                            listOf(ACCENT, ACCENT2),
                            start = Offset(0f, 0f),
                            end = Offset(40f, 40f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("RF", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "RasFocus",
                    color = TEXT_PRIMARY,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "Content Blocker",
                    color = TEXT_SEC,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
            Spacer(Modifier.weight(1f))
            // Dynamic status badge
            Box(
                modifier = Modifier
                    .background(
                        if (isActive) Color(0xFF1A2332) else Color(0xFF2A1515),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    if (isActive) "● ACTIVE" else "○ INACTIVE",
                    color = if (isActive) ACCENT else ACCENT_RED,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
        // Service inactive হলে enable banner দেখাও
        if (!isActive) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2A1515), RoundedCornerShape(12.dp))
                    .border(1.dp, ACCENT_RED.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ACCENT_RED,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Accessibility Service চালু নেই",
                        color = ACCENT_RED,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Blocking কাজ করছে না — তাপ করে চালু করুন",
                        color = TEXT_SEC,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .background(ACCENT_RED, RoundedCornerShape(8.dp))
                        .clickable {
                            try {
                                ctx.startActivity(
                                    android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: Exception) {}
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text("চালু করুন", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(icon: String, title: String, accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = accentColor, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            title.uppercase(),
            color = accentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(listOf(accentColor.copy(alpha = 0.3f), Color.Transparent))
                )
        )
    }
}

@Composable
fun BlockingCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BG_CARD, RoundedCornerShape(14.dp))
            .border(1.dp, DIVIDER, RoundedCornerShape(14.dp)),
        content = content
    )
}

@Composable
fun MasterToggleCard(
    label: String,
    sublabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val glowAlpha by animateFloatAsState(if (checked) 0.25f else 0f, tween(400))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (checked) Color(0xFF1A0A0E) else BG_CARD,
                RoundedCornerShape(14.dp)
            )
            .border(
                1.dp,
                if (checked) ACCENT_RED.copy(alpha = 0.5f) else DIVIDER,
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = TEXT_PRIMARY, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(sublabel, color = TEXT_SEC, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.width(12.dp))
            RasToggle(checked = checked, accentColor = ACCENT_RED, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun RasSwitch(
    label: String,
    sublabel: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TEXT_PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(sublabel, color = TEXT_SEC, fontSize = 11.sp)
        }
        Spacer(Modifier.width(12.dp))
        RasToggle(checked = checked, accentColor = accentColor, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SubSwitchRow(
    icon: String,
    label: String,
    sublabel: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 13.sp, color = accentColor)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = TEXT_PRIMARY, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(sublabel, color = TEXT_SEC, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        RasToggle(checked = checked, accentColor = accentColor, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun CustomizeRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(label, color = TEXT_SEC, fontSize = 11.sp, letterSpacing = 0.3.sp)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TEXT_PRIMARY,
                fontSize = 13.sp
            ),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BG_DEEP, RoundedCornerShape(8.dp))
                        .border(1.dp, DIVIDER, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    if (value.isEmpty()) Text(placeholder, color = TEXT_SEC, fontSize = 13.sp)
                    inner()
                }
            }
        )
    }
}

@Composable
fun RasToggle(checked: Boolean, accentColor: Color, onCheckedChange: (Boolean) -> Unit) {
    val trackColor by animateColorAsState(if (checked) accentColor.copy(alpha = 0.25f) else SWITCH_OFF, tween(250))
    val thumbColor by animateColorAsState(if (checked) accentColor else Color(0xFF4B5563), tween(250))
    val offset by animateDpAsState(if (checked) 20.dp else 2.dp, tween(250))

    Box(
        modifier = Modifier
            .width(44.dp)
            .height(26.dp)
            .background(trackColor, RoundedCornerShape(13.dp))
            .border(1.dp, if (checked) accentColor.copy(alpha = 0.5f) else Color(0xFF374151), RoundedCornerShape(13.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .padding(start = offset)
                .align(Alignment.CenterStart)
                .size(22.dp)
                .background(thumbColor, CircleShape)
                .then(if (checked) Modifier.shadow(4.dp, CircleShape, spotColor = accentColor) else Modifier)
        )
    }
}

// ── App Reels / Shorts Card ───────────────────────────────

sealed class AppReelsRow {
    data class Toggle(
        val label: String,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ) : AppReelsRow()
    data class Upgrade(val label: String) : AppReelsRow()
}

@Composable
fun AppReelsCard(
    appIcon: String,
    appName: String,
    appIconColor: Color,
    rows: List<AppReelsRow>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BG_CARD, RoundedCornerShape(14.dp))
            .border(1.dp, DIVIDER, RoundedCornerShape(14.dp))
    ) {
        // App header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(appIconColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .border(1.dp, appIconColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(appIcon, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(appName, color = TEXT_PRIMARY, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        // Dashed left border + rows
        rows.forEachIndexed { index, row ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp)
            ) {
                // Dashed vertical line
                Canvas(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (index == rows.lastIndex) 24.dp else 48.dp)
                        .align(Alignment.TopStart)
                ) {
                    val dashLen = 6.dp.toPx()
                    val gap = 4.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color(0xFF2A3040),
                            start = Offset(size.width / 2, y),
                            end = Offset(size.width / 2, minOf(y + dashLen, size.height)),
                            strokeWidth = 1.5f
                        )
                        y += dashLen + gap
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (row) {
                            is AppReelsRow.Toggle -> row.label
                            is AppReelsRow.Upgrade -> row.label
                        },
                        color = TEXT_PRIMARY,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    when (row) {
                        is AppReelsRow.Toggle -> {
                            RasToggle(
                                checked = row.checked,
                                accentColor = ACCENT,
                                onCheckedChange = row.onCheckedChange
                            )
                        }
                        is AppReelsRow.Upgrade -> {
                            UpgradeButton()
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun UpgradeButton() {
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .background(
                Brush.horizontalGradient(listOf(Color(0xFFFF8C00), Color(0xFFFFB800))),
                RoundedCornerShape(20.dp)
            )
            .clickable { /* TODO: navigate to upgrade screen */ }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👑", fontSize = 11.sp)
            Spacer(Modifier.width(5.dp))
            Text(
                "Upgrade",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp
            )
        }
    }
}

@Composable
fun RasDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(DIVIDER)
    )
}

@Composable
fun FocusModeButton() {
    var active by remember { mutableStateOf(false) }
    val pulse by rememberInfiniteTransition().animateFloat(
        0.85f, 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse)
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradientBrush(
                        if (active) listOf(ACCENT, ACCENT2) else listOf(Color(0xFF1E2330), Color(0xFF1E2330)),
                        start = Offset(0f, 0f),
                        end = Offset(200f, 60f)
                    ),
                    RoundedCornerShape(50.dp)
                )
                .border(
                    1.dp,
                    if (active) ACCENT.copy(0.5f) else Color(0xFF2A3040),
                    RoundedCornerShape(50.dp)
                )
                .clickable { active = !active }
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .then(if (active) Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse } else Modifier)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (active) "◉" else "◎", color = if (active) Color.Black else ACCENT, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (active) "Focus Mode ON" else "Focus Mode",
                    color = if (active) Color.Black else TEXT_PRIMARY,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// FOCUS LOCK — Top Bar, Setup Dialog, Unlock Dialog
// ══════════════════════════════════════════════════════════════════

// ── Focus Lock Mode Item ──────────────────────────────────────────
private data class ModeItem(val code: String, val icon: String, val title: String, val sub: String)

// ── Strings (Bilingual) ───────────────────────────────────────────
private object FL {
    fun str(lang: String, bn: String, en: String) = if (lang == "bn") bn else en
}

// ── Fixed Top Bar ─────────────────────────────────────────────────
@Composable
fun FocusLockTopBar(
    isActive: Boolean,
    endTime: Long,
    onStartClick: () -> Unit,
    onUnlockClick: () -> Unit
) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.96f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // Countdown timer display
    var remainingMs by remember { mutableStateOf(0L) }
    LaunchedEffect(isActive, endTime) {
        while (isActive && endTime > 0) {
            remainingMs = maxOf(0L, endTime - System.currentTimeMillis())
            if (remainingMs == 0L) break
            kotlinx.coroutines.delay(1000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .zIndex(10f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isActive)
                        Brush.horizontalGradient(listOf(Color(0xFF0D2B1F), Color(0xFF0D1A2B)))
                    else
                        Brush.horizontalGradient(listOf(Color(0xFF111827), Color(0xFF1A1F2E))),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    1.5.dp,
                    if (isActive) ACCENT.copy(0.6f) else Color(0xFF2A3040),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isActive) ACCENT.copy(0.15f) else Color(0xFF1E2330),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isActive) "🔒" else "🎯",
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isActive) "Focus Lock Active"
                        else "Start Focus",
                        color = if (isActive) ACCENT else TEXT_PRIMARY,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isActive && endTime > 0 && remainingMs > 0) {
                        val h = TimeUnit.MILLISECONDS.toHours(remainingMs)
                        val m = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60
                        val s = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60
                        Text(
                            "%02d:%02d:%02d".format(h, m, s),
                            color = ACCENT.copy(0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (isActive) {
                        Text(
                            "Active",
                            color = ACCENT.copy(0.7f),
                            fontSize = 11.sp
                        )
                    }
                }

                // Action button
                Box(
                    modifier = Modifier
                        .graphicsLayer { if (isActive) { scaleX = pulse; scaleY = pulse } }
                        .background(
                            if (isActive)
                                Brush.horizontalGradient(listOf(Color(0xFFFF3B5C), Color(0xFFFF6B35)))
                            else
                                Brush.horizontalGradient(listOf(ACCENT, ACCENT2)),
                            RoundedCornerShape(50.dp)
                        )
                        .clickable { if (isActive) onUnlockClick() else onStartClick() }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isActive) "Unlock"
                        else "Start",
                        color = if (isActive) Color.White else Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Setup Dialog ─────────────────────────────────────────────────
@Composable
fun FocusLockSetupDialog(
    prefs: BlockerPrefs,
    onDismiss: () -> Unit,
    onActivated: (mode: String, endMs: Long) -> Unit
) {
    // Step 0: mode → Step 1: config
    var step by remember { mutableStateOf(0) }  // 0=mode, 1=config
    val lang = "en"
    var selectedMode by remember { mutableStateOf("") }

    // Self mode fields
    var selfDays    by remember { mutableStateOf(0) }
    var selfHours   by remember { mutableStateOf(0) }
    var selfMinutes by remember { mutableStateOf(25) }

    // Parents mode
    var parentPass  by remember { mutableStateOf("") }
    var parentPass2 by remember { mutableStateOf("") }
    var passError   by remember { mutableStateOf("") }

    // Long text mode
    val longTextPassage = "Read carefully and type: Time is the most precious resource in our lives. Every moment that passes never returns. The person who respects their time moves forward in life. Distraction is our greatest enemy. Focus is power, discipline is freedom. Stay committed to your goals and make progress every day. Success does not come overnight; it is the fruit of patience and perseverance."
    var longTextInput by remember { mutableStateOf("") }
    val longTextWordCount = longTextInput.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .background(BG_CARD, RoundedCornerShape(20.dp))
                .border(1.dp, DIVIDER, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                // Dialog title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎯", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when(step) { 0 -> "Select Mode"; else -> "Configure" },
                        color = TEXT_PRIMARY,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text("✕", color = TEXT_SEC, fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(4.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Step 0: Mode selection ──
                if (step == 0) {
                    val modes = listOf(
                        ModeItem("self",     "⏱",  "Self Mode",     "Set day / hour / minute"),
                        ModeItem("parents",  "🔐", "Parents Mode",  "Password protected lock"),
                        ModeItem("longtext", "📝", "Long Text Mode", "Unlock by typing 100 words")
                    )
                    modes.forEach { modeItem ->
                    val code = modeItem.code; val icon = modeItem.icon; val title = modeItem.title; val sub = modeItem.sub
                        val sel = selectedMode == code
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .background(
                                    if (sel) ACCENT.copy(0.1f) else Color(0xFF161A22),
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.5.dp,
                                    if (sel) ACCENT else Color(0xFF2A3040),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedMode = code }
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = icon, fontSize = 22.sp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(text = title, color = if (sel) ACCENT else TEXT_PRIMARY, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = sub, color = TEXT_SEC, fontSize = 11.sp)
                                }
                                if (sel) {
                                    Spacer(Modifier.weight(1f))
                                    Text(text = "✓", color = ACCENT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FocusDialogButton("Cancel", true, modifier = Modifier.weight(1f)) { onDismiss() }
                        FocusDialogButton("Next →", selectedMode.isNotEmpty(), modifier = Modifier.weight(1f)) { step = 1 }
                    }
                }

                // ── Step 1: Config per mode ──
                if (step == 1) {
                    when (selectedMode) {

                        // SELF MODE
                        "self" -> {
                            Text(
                                "Set your focus duration:",
                                color = TEXT_SEC, fontSize = 12.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FocusNumberPicker(
                                    label = "Day",
                                    value = selfDays, min = 0, max = 30,
                                    onValueChange = { selfDays = it },
                                    modifier = Modifier.weight(1f)
                                )
                                FocusNumberPicker(
                                    label = "Hour",
                                    value = selfHours, min = 0, max = 23,
                                    onValueChange = { selfHours = it },
                                    modifier = Modifier.weight(1f)
                                )
                                FocusNumberPicker(
                                    label = "Min",
                                    value = selfMinutes, min = 0, max = 59,
                                    onValueChange = { selfMinutes = it },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            val totalMs = (selfDays * 86400L + selfHours * 3600L + selfMinutes * 60L) * 1000L
                            val valid = totalMs > 0
                            if (!valid) {
                                Text("Set at least 1 minute", color = ACCENT_RED, fontSize = 11.sp)
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FocusDialogButton("← Back", true, modifier = Modifier.weight(1f)) { step = 0 }
                                FocusDialogButton("🔒 Lock", valid, modifier = Modifier.weight(1f)) {
                                    onActivated("self", System.currentTimeMillis() + totalMs)
                                }
                            }
                        }

                        // PARENTS MODE
                        "parents" -> {
                            Text("Set a new password:", color = TEXT_SEC, fontSize = 12.sp)
                            Spacer(Modifier.height(10.dp))
                            FocusPasswordField(
                                value = parentPass,
                                placeholder = "Password",
                                onValueChange = { parentPass = it; passError = "" }
                            )
                            Spacer(Modifier.height(8.dp))
                            FocusPasswordField(
                                value = parentPass2,
                                placeholder = "Confirm password",
                                onValueChange = { parentPass2 = it; passError = "" }
                            )
                            if (passError.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(passError, color = ACCENT_RED, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FocusDialogButton("← Back", true, modifier = Modifier.weight(1f)) { step = 0 }
                                FocusDialogButton("🔒 Lock", parentPass.length >= 4, modifier = Modifier.weight(1f)) {
                                    when {
                                        parentPass.length < 4 -> passError = "Minimum 4 characters"
                                        parentPass != parentPass2 -> passError = "Passwords do not match"
                                        else -> {
                                            prefs.focusLockPassword = parentPass.hashCode().toString()
                                            onActivated("parents", 0L)
                                        }
                                    }
                                }
                            }
                        }

                        // LONG TEXT MODE
                        "longtext" -> {
                            Text(
                                "Remember this passage — you must type 100 words to unlock:",
                                color = TEXT_SEC, fontSize = 11.sp, lineHeight = 16.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0D1117), RoundedCornerShape(10.dp))
                                    .border(1.dp, ACCENT.copy(0.3f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(longTextPassage, color = ACCENT.copy(0.9f), fontSize = 11.sp, lineHeight = 17.sp)
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FocusDialogButton("← Back", true, modifier = Modifier.weight(1f)) { step = 0 }
                                FocusDialogButton("🔒 Lock", true, modifier = Modifier.weight(1f)) {
                                    prefs.focusLockLongText = longTextPassage
                                    onActivated("longtext", 0L)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Unlock Dialog ─────────────────────────────────────────────────
@Composable
fun FocusLockUnlockDialog(
    prefs: BlockerPrefs,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    val lang = "en"
    val mode = prefs.focusLockMode
    var error by remember { mutableStateOf("") }

    // Parents mode
    var passInput by remember { mutableStateOf("") }

    // Long text mode
    val requiredText = prefs.focusLockLongText
    var textInput by remember { mutableStateOf("") }
    val inputWordCount = textInput.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
    val requiredWordCount = requiredText.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size

    // Self mode — check if time is up
    val timeUp = mode == "self" && System.currentTimeMillis() >= prefs.focusLockEndTime

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .background(BG_CARD, RoundedCornerShape(20.dp))
                .border(1.5.dp, ACCENT_RED.copy(0.5f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔓", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Unlock Focus Lock",
                        color = TEXT_PRIMARY, fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text("✕", color = TEXT_SEC, fontSize = 14.sp,
                        modifier = Modifier.clickable { onDismiss() }.padding(4.dp))
                }

                Spacer(Modifier.height(16.dp))

                when (mode) {
                    "self" -> {
                        if (timeUp) {
                            Text(
                                "✅ Time's up! You can unlock now.",
                                color = ACCENT, fontSize = 13.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            FocusDialogButton("🔓 Unlock", true) { onUnlocked() }
                        } else {
                            val remaining = maxOf(0L, prefs.focusLockEndTime - System.currentTimeMillis())
                            val h = TimeUnit.MILLISECONDS.toHours(remaining)
                            val m = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
                            val s = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                            Text(
                                "⏳ %02d:%02d:%02d remaining. Cannot unlock until time is up.".format(h, m, s),
                                color = ACCENT_AMBER, fontSize = 12.sp, lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            FocusDialogButton("OK", true) { onDismiss() }
                        }
                    }

                    "parents" -> {
                        Text("Enter password:", color = TEXT_SEC, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        FocusPasswordField(
                            value = passInput,
                            placeholder = "Password",
                            onValueChange = { passInput = it; error = "" }
                        )
                        if (error.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(error, color = ACCENT_RED, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        FocusDialogButton("🔓 Unlock", passInput.isNotEmpty()) {
                            if (passInput.hashCode().toString() == prefs.focusLockPassword) {
                                onUnlocked()
                            } else {
                                error = "❌ Wrong password!"
                            }
                        }
                    }

                    "longtext" -> {
                        Text(
                            "Type the full passage below ($requiredWordCount words):",
                            color = TEXT_SEC, fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        // Show passage
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0D1117), RoundedCornerShape(10.dp))
                                .border(1.dp, ACCENT.copy(0.3f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                                .heightIn(max = 120.dp)
                        ) {
                            Text(requiredText, color = TEXT_SEC, fontSize = 10.sp, lineHeight = 15.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        // Input area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF111318), RoundedCornerShape(10.dp))
                                .border(
                                    1.dp,
                                    if (inputWordCount >= requiredWordCount) ACCENT else Color(0xFF2A3040),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp)
                                .heightIn(min = 80.dp, max = 140.dp)
                        ) {
                            BasicTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                textStyle = TextStyle(color = TEXT_PRIMARY, fontSize = 12.sp, lineHeight = 18.sp),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (textInput.isEmpty()) Text("Type here...", color = TEXT_SEC, fontSize = 12.sp)
                                    inner()
                                }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Text(
                                "Words: $inputWordCount / $requiredWordCount",
                                color = if (inputWordCount >= requiredWordCount) ACCENT else TEXT_SEC,
                                fontSize = 11.sp
                            )
                        }
                        if (error.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(error, color = ACCENT_RED, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        FocusDialogButton(
                            "🔓 Unlock",
                            inputWordCount >= requiredWordCount
                        ) {
                            // Simple text match check (trimmed, case-insensitive, whitespace-normalized)
                            val normalize: (String) -> String = { s ->
                                s.trim().replace("\\s+".toRegex(), " ").lowercase()
                            }
                            if (normalize(textInput) == normalize(requiredText)) {
                                onUnlocked()
                            } else {
                                error = "❌ Text doesn't match exactly!"
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Shared sub-components ─────────────────────────────────────────

@Composable
fun FocusDialogButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (enabled)
                    Brush.horizontalGradient(listOf(ACCENT, ACCENT2))
                else
                    Brush.horizontalGradient(listOf(Color(0xFF2A2F3D), Color(0xFF2A2F3D))),
                RoundedCornerShape(12.dp)
            )
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) Color.Black else TEXT_SEC,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun FocusPasswordField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111318), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF2A3040), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = TEXT_PRIMARY, fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = TEXT_SEC, fontSize = 14.sp)
                inner()
            }
        )
    }
}

@Composable
fun FocusNumberPicker(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF161A22), RoundedCornerShape(12.dp))
            .border(1.dp, DIVIDER, RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TEXT_SEC, fontSize = 10.sp, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(6.dp))
        // Up button
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF1E2330), CircleShape)
                .clickable { if (value < max) onValueChange(value + 1) },
            contentAlignment = Alignment.Center
        ) {
            Text("▲", color = ACCENT, fontSize = 10.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "%02d".format(value),
            color = TEXT_PRIMARY,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        // Down button
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF1E2330), CircleShape)
                .clickable { if (value > min) onValueChange(value - 1) },
            contentAlignment = Alignment.Center
        ) {
            Text("▼", color = ACCENT, fontSize = 10.sp)
        }
    }
}

// ── Brush helper (linearGradientBrush missing in some versions) ──
private fun Brush.Companion.linearGradientBrush(
    colors: List<Color>,
    start: Offset,
    end: Offset
): Brush = linearGradient(colors = colors, start = start, end = end)


// ════════════════════════════════════════════════════════════════════
// SERVICE RESTART RECEIVER
// Service kill হলে এই BroadcastReceiver টা user কে Accessibility settings
// এ নিয়ে যায় যাতে service আবার enable করা যায়।
//
// AndroidManifest.xml এ যোগ করতে হবে:
//
//  <receiver android:name=".selfcontrol.ServiceRestartReceiver"
//      android:exported="false">
//      <intent-filter>
//          <action android:name="com.rasel.RasFocus.RESTART_SERVICE"/>
//      </intent-filter>
//  </receiver>
// ════════════════════════════════════════════════════════════════════

class ServiceRestartReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return

        // Service এখনো alive কিনা check করো
        val isRunning = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE)
                    as android.app.ActivityManager
            @Suppress("DEPRECATION")
            am.getRunningServices(100).any {
                it.service.className == RasFocusBlockingService::class.java.name
            }
        } catch (_: Exception) { false }

        if (!isRunning) {
            // Service মরে গেছে — user কে Accessibility settings এ পাঠাও
            try {
                val settingsIntent = android.content.Intent(
                    android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
                ).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
            } catch (_: Exception) {}

            // Persistent notification দেখাও
            showServiceDeadNotification(context)
        }
    }

    private fun showServiceDeadNotification(context: Context) {
        try {
            val channelId = "rasfocus_service_dead"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as android.app.NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "RasFocus Service Alert",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Service restart required"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }

            val settingsIntent = android.content.Intent(
                android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pi = android.app.PendingIntent.getActivity(
                context, 2001, settingsIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, channelId)
                    .setContentTitle("⚠️ RasFocus বন্ধ হয়ে গেছে!")
                    .setContentText("Blocking বন্ধ। চালু করতে ট্যাপ করুন।")
                    .setSmallIcon(R.drawable.ic_notification_shield)
                    .setContentIntent(pi)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(context)
                    .setContentTitle("⚠️ RasFocus বন্ধ হয়ে গেছে!")
                    .setContentText("Blocking বন্ধ। চালু করতে ট্যাপ করুন।")
                    .setSmallIcon(R.drawable.ic_notification_shield)
                    .setContentIntent(pi)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .build()
            }

            nm.notify(9001, notification)
        } catch (_: Exception) {}
    }
}