package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Restarts real-time partner sync automatically after device reboot or app update
 * when the user is already authenticated.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (Firebase.auth.currentUser != null) {
                PartnerSyncService.start(context)
            }
        }
    }
}
