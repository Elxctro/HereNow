package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.HereNowRepository
import com.example.notifications.NotificationHelper
import com.example.notifications.PartnerSyncService
import com.example.ui.AuthScreen
import com.example.ui.HereNowViewModel
import com.example.ui.HomeScreen
import com.example.ui.theme.HereNowTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {

    private val pendingNotificationPairId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannels(this)
        handleNotificationIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            HereNowTheme {
                AppNavigation(
                    pendingPairIdFlow = pendingNotificationPairId,
                    onConsumePendingPairId = { pendingNotificationPairId.value = null },
                    onOpenAppSettings = {
                        val settingsIntent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                        startActivity(settingsIntent)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (Firebase.auth.currentUser != null) {
            PartnerSyncService.start(this)
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val pairId = intent?.getStringExtra(NotificationHelper.EXTRA_PAIR_ID)
        if (!pairId.isNullOrBlank() && pairId != "test_ping") {
            pendingNotificationPairId.value = pairId
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(
    pendingPairIdFlow: MutableStateFlow<String?>,
    onConsumePendingPairId: () -> Unit,
    onOpenAppSettings: () -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val context = LocalContext.current
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        LaunchedEffect(Unit) {
            PartnerSyncService.stop(context)
        }
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedHereNowApp(
                user = user,
                pendingPairIdFlow = pendingPairIdFlow,
                onConsumePendingPairId = onConsumePendingPairId,
                onOpenAppSettings = onOpenAppSettings
            )
        }
    }
}

@Composable
private fun AuthenticatedHereNowApp(
    user: FirebaseUser,
    pendingPairIdFlow: MutableStateFlow<String?>,
    onConsumePendingPairId: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotifPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    val isLiveSyncRunning by PartnerSyncService.isRunning.collectAsStateWithLifecycle()
    val pendingPairId by pendingPairIdFlow.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPermission = NotificationHelper.hasNotificationPermission(context)
                PartnerSyncService.start(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotifPermission = granted
            PartnerSyncService.start(context)
        }
    )

    LaunchedEffect(user.uid) {
        PartnerSyncService.start(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val viewModel: HereNowViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                HereNowViewModel(
                    appContext = app.applicationContext,
                    repository = HereNowRepository(db),
                    currentUserId = user.uid,
                    currentUserEmail = user.email,
                    isEmailVerified = user.isEmailVerified,
                    initialDisplayName = user.displayName ?: "Partner"
                )
            }
        }
    )

    LaunchedEffect(pendingPairId) {
        val targetId = pendingPairId
        if (!targetId.isNullOrBlank()) {
            viewModel.selectSpace(targetId)
            onConsumePendingPairId()
        }
    }

    // Fallback collector in case foreground service isn't active yet; deduplicated by PingStateStore
    LaunchedEffect(viewModel) {
        viewModel.incomingPings.collect { ping ->
            NotificationHelper.showPartnerUpdateNotification(
                context = context,
                pairId = ping.pairId,
                senderUid = ping.senderUid,
                spaceName = ping.spaceName,
                senderName = ping.senderName,
                buttonLabel = ping.buttonLabel,
                customMessage = ping.message,
                pingCount = ping.pingCount
            )
        }
    }

    HomeScreen(
        viewModel = viewModel,
        isLiveSyncRunning = isLiveSyncRunning,
        hasNotificationPermission = hasNotifPermission,
        onRequestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onRestartLiveSync = { PartnerSyncService.start(context) },
        onSendTestNotification = { NotificationHelper.showTestNotification(context) },
        onOpenAppSettings = onOpenAppSettings
    )
}
