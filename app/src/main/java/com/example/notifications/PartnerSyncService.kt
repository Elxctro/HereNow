package com.example.notifications

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.HereNowRepository
import com.example.data.PairingRoom
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Persistent foreground service (foregroundServiceType = remoteMessaging) that maintains
 * real-time Firestore snapshot listeners across all isolated 1-on-1 and multi-member group/family
 * rooms so status alerts arrive immediately whether the app is in foreground, background, or closed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PartnerSyncService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observationJob: Job? = null
    private var activeUid: String? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val uid = firebaseAuth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            stopObserving()
            stopSelf()
        } else if (uid != activeUid) {
            startObserving(uid)
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        Firebase.auth.addAuthStateListener(authStateListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopObserving()
            stopSelf()
            return START_NOT_STICKY
        }

        val currentUser = Firebase.auth.currentUser
        if (currentUser == null || currentUser.uid.isBlank()) {
            stopObserving()
            stopSelf()
            return START_NOT_STICKY
        }

        promoteToForeground(spaceCount = 0)
        _isRunning.value = true

        if (activeUid != currentUser.uid || observationJob?.isActive != true) {
            startObserving(currentUser.uid)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _isRunning.value = false
        Firebase.auth.removeAuthStateListener(authStateListener)
        stopObserving()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground(spaceCount: Int) {
        val notification = NotificationHelper.buildLiveSyncNotification(this, spaceCount)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.SERVICE_NOTIFICATION_ID,
                notification,
                serviceType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to enter foreground for PartnerSyncService", e)
            stopSelf()
        }
    }

    private fun updateForegroundNotification(spaceCount: Int) {
        if (!NotificationHelper.hasNotificationPermission(this)) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NotificationHelper.SERVICE_NOTIFICATION_ID,
            NotificationHelper.buildLiveSyncNotification(this, spaceCount)
        )
    }

    private fun stopObserving() {
        observationJob?.cancel()
        observationJob = null
        activeUid = null
    }

    private fun startObserving(userId: String) {
        stopObserving()
        activeUid = userId
        val repository = HereNowRepository(applicationContext)

        observationJob = serviceScope.launch {
            var retryDelayMs = 2_000L
            while (true) {
                if (Firebase.auth.currentUser?.uid != userId) {
                    break
                }
                try {
                    val latestRooms = mutableMapOf<String, PairingRoom>()

                    repository.observeMySpaces(userId)
                        .map { rooms ->
                            latestRooms.clear()
                            rooms.forEach { latestRooms[it.id] = it }
                            updateForegroundNotification(rooms.size)
                            rooms.flatMap { room ->
                                room.otherMemberUids(userId).map { otherUid -> room.id to otherUid }
                            }
                        }
                        .distinctUntilChanged()
                        .flatMapLatest { memberTargets ->
                            if (memberTargets.isEmpty()) {
                                flowOf(Unit)
                            } else {
                                val statusFlows = memberTargets.map { (pairId, memberUid) ->
                                    repository.observeStatus(pairId, memberUid, userId)
                                        .map { status -> Triple(pairId, memberUid, status) }
                                        .catch { error ->
                                            val firestoreError = generateSequence(error) { it.cause }
                                                .filterIsInstance<FirebaseFirestoreException>()
                                                .firstOrNull()
                                            if (firestoreError == null && error is CancellationException) throw error
                                            Log.w(TAG, "Member status listener error for $pairId/$memberUid", firestoreError ?: error)
                                            emit(Triple(pairId, memberUid, null))
                                        }
                                }
                                combine(statusFlows) { snapshots ->
                                    snapshots.forEach { (pairId, memberUid, status) ->
                                        if (status == null) {
                                            PingStateStore.seedBaselineIfAbsent(
                                                context = applicationContext,
                                                userId = userId,
                                                pairId = pairId,
                                                senderUid = memberUid,
                                                baseline = 0L
                                            )
                                        } else {
                                            val updatedMs = status.updatedAt?.toDate()?.time
                                            val isNew = PingStateStore.consumeIfNew(
                                                context = applicationContext,
                                                userId = userId,
                                                pairId = pairId,
                                                senderUid = memberUid,
                                                pingCount = status.pingCount,
                                                updatedAtMillis = updatedMs
                                            )
                                            if (isNew) {
                                                val room = latestRooms[pairId]
                                                val spaceName = room?.spaceName?.ifBlank { "Private Room" }
                                                    ?: "Private Room"
                                                val senderName = status.senderName.ifBlank {
                                                    room?.memberDisplayName(memberUid) ?: "Member"
                                                }
                                                NotificationHelper.showPartnerUpdateNotification(
                                                    context = applicationContext,
                                                    pairId = pairId,
                                                    senderUid = memberUid,
                                                    spaceName = spaceName,
                                                    senderName = senderName,
                                                    buttonLabel = status.buttonLabel,
                                                    customMessage = status.message,
                                                    pingCount = status.pingCount
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .collect {
                            retryDelayMs = 2_000L
                        }
                } catch (e: Throwable) {
                    val firestoreError = generateSequence(e) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && e is CancellationException) throw e
                    Log.w(TAG, "Sync stream interrupted, retrying in ${retryDelayMs}ms", firestoreError ?: e)
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    companion object {
        private const val TAG = "PartnerSyncService"
        private const val ACTION_STOP = "com.example.notifications.ACTION_STOP_SYNC"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            if (Firebase.auth.currentUser == null) return
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, PartnerSyncService::class.java)
            try {
                ContextCompat.startForegroundService(appCtx, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start PartnerSyncService right now: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            try {
                appCtx.stopService(Intent(appCtx, PartnerSyncService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop PartnerSyncService: ${e.message}")
            }
            _isRunning.value = false
        }
    }
}
