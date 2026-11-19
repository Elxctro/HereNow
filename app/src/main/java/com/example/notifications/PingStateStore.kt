package com.example.notifications

import android.content.Context
import android.content.SharedPreferences

/**
 * Thread-safe persistent tracker of the last seen member pingCount per (userId, pairId, senderUid).
 * Prevents duplicate notifications between the background service and foreground ViewModel across
 * both 1-on-1 rooms and multi-member group/family rooms.
 */
object PingStateStore {
    private const val PREFS_NAME = "herenow_ping_state"
    private const val RECENT_THRESHOLD_MS = 45_000L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(userId: String, pairId: String, senderUid: String): String =
        "ping_${userId}_${pairId}_${senderUid}"

    /**
     * Records a baseline of 0 when a room member has no status document yet, so their
     * very first button tap (pingCount = 1) always triggers a notification.
     */
    @Synchronized
    fun seedBaselineIfAbsent(
        context: Context,
        userId: String,
        pairId: String,
        senderUid: String,
        baseline: Long = 0L
    ) {
        val sp = prefs(context)
        val k = key(userId, pairId, senderUid)
        if (!sp.contains(k)) {
            sp.edit().putLong(k, baseline).apply()
        }
    }

    /**
     * Atomically checks if [pingCount] represents a new status update from [senderUid] that has not
     * yet triggered a notification on this device. Returns true at most once per (userId, pairId, senderUid, pingCount).
     */
    @Synchronized
    fun consumeIfNew(
        context: Context,
        userId: String,
        pairId: String,
        senderUid: String,
        pingCount: Long,
        updatedAtMillis: Long?
    ): Boolean {
        if (pingCount <= 0L) return false
        val sp = prefs(context)
        val k = key(userId, pairId, senderUid)

        if (!sp.contains(k)) {
            sp.edit().putLong(k, pingCount).apply()
            val isFresh = updatedAtMillis != null &&
                (System.currentTimeMillis() - updatedAtMillis) in -10_000L..RECENT_THRESHOLD_MS
            return isFresh
        }

        val lastSeen = sp.getLong(k, 0L)
        return if (pingCount > lastSeen) {
            sp.edit().putLong(k, pingCount).apply()
            true
        } else {
            false
        }
    }

    @Synchronized
    fun clearSpace(context: Context, userId: String, pairId: String) {
        val sp = prefs(context)
        val prefix = "ping_${userId}_${pairId}_"
        val legacyKey = "ping_${userId}_${pairId}"
        val editor = sp.edit()
        sp.all.keys
            .filter { it == legacyKey || it.startsWith(prefix) }
            .forEach { editor.remove(it) }
        editor.apply()
    }
}
