package com.rasel.RasFocus.features

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            // Accessibility Service সিস্টেম নিজে থেকেই রিস্টার্ট করে দেয়
            // DataManager dependency সরিয়ে দেওয়া হয়েছে
        }
    }
}
