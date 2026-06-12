package com.rasel.RasFocus.selfcontrol

// ============================================================
// BlockerPrefs.kt
// RasFocusBlockingService এর সব SharedPreferences একটি জায়গায়।
// UnifiedBlockerService, MyDeviceAdminReceiver এ ব্যবহার হয়।
// ============================================================

import android.content.Context
import android.content.SharedPreferences

class BlockerPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("RasFocusBlockerPrefs", Context.MODE_PRIVATE)

    // ── Adult Content ────────────────────────────────────────────────
    /** Keyword-based adult content detection চালু/বন্ধ */
    var blockAdult: Boolean
        get() = prefs.getBoolean("blockAdult", true)
        set(v) = prefs.edit().putBoolean("blockAdult", v).apply()

    /** adultsite.txt domain list দিয়ে site blocking চালু/বন্ধ */
    var blockAdultSiteList: Boolean
        get() = prefs.getBoolean("blockAdultSiteList", true)
        set(v) = prefs.edit().putBoolean("blockAdultSiteList", v).apply()

    // ── YouTube ──────────────────────────────────────────────────────
    /** YouTube Shorts ব্লক */
    var blockYtShorts: Boolean
        get() = prefs.getBoolean("blockYtShorts", true)
        set(v) = prefs.edit().putBoolean("blockYtShorts", v).apply()

    // ── Instagram ────────────────────────────────────────────────────
    /** Instagram / Facebook Reels ব্লক */
    var blockReels: Boolean
        get() = prefs.getBoolean("blockReels", true)
        set(v) = prefs.edit().putBoolean("blockReels", v).apply()

    /** Instagram Search ট্যাব ব্লক */
    var blockInstaSearch: Boolean
        get() = prefs.getBoolean("blockInstaSearch", true)
        set(v) = prefs.edit().putBoolean("blockInstaSearch", v).apply()

    /** Instagram Stories ব্লক */
    var blockInstaStories: Boolean
        get() = prefs.getBoolean("blockInstaStories", false)
        set(v) = prefs.edit().putBoolean("blockInstaStories", v).apply()

    // ── Snapchat ─────────────────────────────────────────────────────
    /** Snapchat Spotlight ব্লক */
    var blockSnapSpotlight: Boolean
        get() = prefs.getBoolean("blockSnapSpotlight", true)
        set(v) = prefs.edit().putBoolean("blockSnapSpotlight", v).apply()

    /** Snapchat Stories ব্লক */
    var blockSnapStories: Boolean
        get() = prefs.getBoolean("blockSnapStories", true)
        set(v) = prefs.edit().putBoolean("blockSnapStories", v).apply()

    // ── WhatsApp ─────────────────────────────────────────────────────
    /** WhatsApp Channels ব্লক */
    var blockWaChannels: Boolean
        get() = prefs.getBoolean("blockWaChannels", false)
        set(v) = prefs.edit().putBoolean("blockWaChannels", v).apply()

    /** WhatsApp Status ব্লক */
    var blockWaStatus: Boolean
        get() = prefs.getBoolean("blockWaStatus", false)
        set(v) = prefs.edit().putBoolean("blockWaStatus", v).apply()

    /** WhatsApp Business Status ব্লক */
    var blockWaBusinessStatus: Boolean
        get() = prefs.getBoolean("blockWaBusinessStatus", false)
        set(v) = prefs.edit().putBoolean("blockWaBusinessStatus", v).apply()

    /** WhatsApp Business Channels ব্লক */
    var blockWaBusinessChannels: Boolean
        get() = prefs.getBoolean("blockWaBusinessChannels", false)
        set(v) = prefs.edit().putBoolean("blockWaBusinessChannels", v).apply()

    // ── TikTok ───────────────────────────────────────────────────────
    /** TikTok সম্পূর্ণ ব্লক */
    var blockTikTok: Boolean
        get() = prefs.getBoolean("blockTikTok", true)
        set(v) = prefs.edit().putBoolean("blockTikTok", v).apply()

    /** TikTok Live ব্লক */
    var blockTikTokLive: Boolean
        get() = prefs.getBoolean("blockTikTokLive", true)
        set(v) = prefs.edit().putBoolean("blockTikTokLive", v).apply()

    // ── Facebook ─────────────────────────────────────────────────────
    /** Facebook Video / Watch ব্লক */
    var blockFbVideo: Boolean
        get() = prefs.getBoolean("blockFbVideo", false)
        set(v) = prefs.edit().putBoolean("blockFbVideo", v).apply()

    // ── Browser ──────────────────────────────────────────────────────
    /** Chrome Image/Video Search ব্লক */
    var blockSearch: Boolean
        get() = prefs.getBoolean("blockSearch", false)
        set(v) = prefs.edit().putBoolean("blockSearch", v).apply()

    /** Unsupported (non-allowlisted) browser ব্লক */
    var blockUnsupported: Boolean
        get() = prefs.getBoolean("blockUnsupported", true)
        set(v) = prefs.edit().putBoolean("blockUnsupported", v).apply()

    // ── App Control ──────────────────────────────────────────────────
    /** নতুন app install হলে back press করো */
    var blockNewApps: Boolean
        get() = prefs.getBoolean("blockNewApps", false)
        set(v) = prefs.edit().putBoolean("blockNewApps", v).apply()

    // ── System Protection ────────────────────────────────────────────
    /** Uninstall protection — DeviceAdmin দিয়ে সুরক্ষিত */
    var uninstallProtection: Boolean
        get() = prefs.getBoolean("uninstallProtection", false)
        set(v) = prefs.edit().putBoolean("uninstallProtection", v).apply()

    /** Device reboot ব্লক */
    var blockReboot: Boolean
        get() = prefs.getBoolean("blockReboot", false)
        set(v) = prefs.edit().putBoolean("blockReboot", v).apply()

    /** Power off ব্লক */
    var blockPowerOff: Boolean
        get() = prefs.getBoolean("blockPowerOff", false)
        set(v) = prefs.edit().putBoolean("blockPowerOff", v).apply()

    /** Safe mode ব্লক */
    var blockSafeMode: Boolean
        get() = prefs.getBoolean("blockSafeMode", false)
        set(v) = prefs.edit().putBoolean("blockSafeMode", v).apply()

    /** Recovery mode ব্লক */
    var blockRecovery: Boolean
        get() = prefs.getBoolean("blockRecovery", false)
        set(v) = prefs.edit().putBoolean("blockRecovery", v).apply()

    /** ADB / Developer Options ব্লক */
    var blockAdb: Boolean
        get() = prefs.getBoolean("blockAdb", false)
        set(v) = prefs.edit().putBoolean("blockAdb", v).apply()

    // ── Helpers ──────────────────────────────────────────────────────
    /** সব settings একসাথে reset করো (factory defaults এ) */
    fun resetAll() = prefs.edit().clear().apply()
}
