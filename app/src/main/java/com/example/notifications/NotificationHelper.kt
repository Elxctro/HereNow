package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {

    const val EXTRA_PAIR_ID = "extra_pair_id"
    const val SERVICE_NOTIFICATION_ID = 1001

    private const val CHANNEL_ALERTS_ID = "partner_status_alerts_v2"
    private const val CHANNEL_ALERTS_NAME = "Room Status Alerts"
    private const val CHANNEL_ALERTS_DESC = "Instant high-priority notifications when your partner or family room member taps a status button"

    private const val CHANNEL_SYNC_ID = "herenow_live_sync_service"
    private const val CHANNEL_SYNC_NAME = "Live Background Sync"
    private const val CHANNEL_SYNC_DESC = "Keeps real-time room status updates active even when the app is closed"

    private const val GROUP_KEY_UPDATES = "com.aistudio.herenow.PARTNER_UPDATES"
    private val VIBRATION_PATTERN = longArrayOf(0L, 180L, 90L, 220L)

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (manager.getNotificationChannel(CHANNEL_ALERTS_ID) == null) {
                val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()

                val alertChannel = NotificationChannel(
                    CHANNEL_ALERTS_ID,
                    CHANNEL_ALERTS_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_ALERTS_DESC
                    enableVibration(true)
                    vibrationPattern = VIBRATION_PATTERN
                    enableLights(true)
                    setSound(defaultSound, audioAttributes)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(true)
                }
                manager.createNotificationChannel(alertChannel)
            }

            if (manager.getNotificationChannel(CHANNEL_SYNC_ID) == null) {
                val syncChannel = NotificationChannel(
                    CHANNEL_SYNC_ID,
                    CHANNEL_SYNC_NAME,
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = CHANNEL_SYNC_DESC
                    enableVibration(false)
                    setSound(null, null)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
                manager.createNotificationChannel(syncChannel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildLiveSyncNotification(context: Context, spaceCount: Int): Notification {
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SERVICE_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = when (spaceCount) {
            0 -> "Connected · Ready for live updates"
            1 -> "Listening to 1 private room in real time"
            else -> "Listening to $spaceCount private rooms in real time"
        }

        return NotificationCompat.Builder(context, CHANNEL_SYNC_ID)
            .setSmallIcon(R.drawable.ic_stat_beacon)
            .setContentTitle("HereNow Live Sync")
            .setContentText(subtitle)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun showPartnerUpdateNotification(
        context: Context,
        pairId: String,
        senderUid: String,
        spaceName: String,
        senderName: String,
        buttonLabel: String,
        customMessage: String,
        pingCount: Long
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val notificationId = ("$pairId:$senderUid:$pingCount").hashCode()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PAIR_ID, pairId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "$senderName ($spaceName) · $buttonLabel"
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_stat_beacon)
            .setContentTitle(title)
            .setContentText(customMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(customMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(VIBRATION_PATTERN)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_KEY_UPDATES)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission may have been revoked between check and notify
        }
    }

    fun showTestNotification(context: Context) {
        showPartnerUpdateNotification(
            context = context,
            pairId = "test_ping",
            senderUid = "test_sender",
            spaceName = "HereNow",
            senderName = "Test Alert",
            buttonLabel = "Live Sync Ready",
            customMessage = "Notifications are working! Partner and family room updates will pop up just like this.",
            pingCount = System.currentTimeMillis()
        )
    }
}
