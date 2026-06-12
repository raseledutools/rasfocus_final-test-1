package com.rasel.RasFocus

import android.app.Application

/**
 * FIX: Custom Application class ensures DataManager.init() is called
 * before any Service (AccessibilityService, BootReceiver, etc.) runs,
 * preventing UninitializedPropertyAccessException crashes.
 */
class RasFocusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DataManager.init(this)
    }
}
