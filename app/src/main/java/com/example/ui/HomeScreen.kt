package com.example.ui

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.signOut
import com.example.data.ButtonIconOption
import com.example.data.PairingRoom
import com.example.data.PartnerStatus
import com.example.data.StatusButton
import com.example.data.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HereNowViewModel,
    isLiveSyncRunning: Boolean,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRestartLiveSync: () -> Unit,
    onSendTestNotification: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val spacesState by viewModel.spacesState.collectAsStateWithLifecycle()
    val pendingInvites by viewModel.pendingEmailInvites.collectAsStateWithLifecycle()
    val selectedSpace by viewModel.selectedSpace.collectAsStateWithLifecycle()
    val activeBundleState by viewModel.activeSpaceBundle.collectAsStateWithLifecycle()
    val roomActiveCounts by viewModel.roomActiveCounts.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    var showSpaceDialog by remember { mutableStateOf(false) }
    var spaceDialogJoinTab by remember { mutableStateOf(false) }
    var showSpaceSettings by remember { mutableStateOf(false) }
    var showLiveSyncDialog by remember { mutableStateOf(false) }
    var editingButton by remember { mutableStateOf<StatusButton?>(null) }
    var showAddButtonDialog by remember { mutableStateOf(false) }

    val defaultUsername = userProfile?.username?.takeIf { it.isNotBlank() } ?: "partner"

    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 620.dp)
            ) {
                // Minimal Flat Brand Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Column {
                            Text(
                                text = "HereNow",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "@$defaultUsername",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isSyncHealthy = isLiveSyncRunning && hasNotificationPermission
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSyncHealthy) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(
                                    width = 1.dp,
                                    color = if (isSyncHealthy) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable { showLiveSyncDialog = true }
                                .testTag("live_sync_status_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSyncHealthy) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.NotificationsOff,
                                        contentDescription = "Notifications or sync paused",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    text = when {
                                        !hasNotificationPermission -> "Alerts Off"
                                        isLiveSyncRunning -> "Live"
                                        else -> "Sync Off"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSyncHealthy) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        IconButton(
                            onClick = { signOut(context, credentialManager, scope) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Sign out",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Isolated Rooms Switcher Strip (1-on-1 & Group/Family Rooms)
                val spaces = (spacesState as? UiState.Success)?.data.orEmpty()
                if (spaces.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        items(spaces, key = { it.id }) { room ->
                            val isSelected = selectedSpace?.id == room.id
                            val hasActiveStatus = (roomActiveCounts[room.id] ?: 0) > 0
                            SpacePill(
                                room = room,
                                currentUserId = viewModel.currentUserId,
                                isSelected = isSelected,
                                hasActiveHighlight = hasActiveStatus,
                                onClick = { viewModel.selectSpace(room.id) }
                            )
                        }
                        item {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable {
                                        spaceDialogJoinTab = false
                                        showSpaceDialog = true
                                    }
                                    .testTag("new_space_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Add new room",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "New Room",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Main Scrollable Content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (pendingInvites.isNotEmpty()) {
                        items(pendingInvites, key = { "invite_${it.id}" }) { inviteRoom ->
                            PendingEmailInviteCard(
                                room = inviteRoom,
                                onAccept = { viewModel.acceptEmailInvite(inviteRoom, defaultUsername) }
                            )
                        }
                    }

                    when (val state = spacesState) {
                        is UiState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        is UiState.Error -> {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }

                        is UiState.Success -> {
                            if (state.data.isEmpty()) {
                                item {
                                    EmptySpacesCard(
                                        onCreateSpace = {
                                            spaceDialogJoinTab = false
                                            showSpaceDialog = true
                                        },
                                        onJoinSpace = {
                                            spaceDialogJoinTab = true
                                            showSpaceDialog = true
                                        }
                                    )
                                }
                            } else {
                                when (val bundleState = activeBundleState) {
                                    is UiState.Loading -> {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 40.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }

                                    is UiState.Error -> {
                                        item {
                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = bundleState.message,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    is UiState.Success -> {
                                        val bundle = bundleState.data
                                        if (bundle != null) {
                                            item {
                                                RoomStatusHeroCard(
                                                    bundle = bundle,
                                                    currentUserId = viewModel.currentUserId,
                                                    onCopyCode = { code ->
                                                        clipboard.setText(AnnotatedString(code))
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar("Copied invite code: $code")
                                                        }
                                                    },
                                                    onOpenSettings = { showSpaceSettings = true }
                                                )
                                            }

                                            item {
                                                val otherCount = bundle.room.otherMemberUids(viewModel.currentUserId).size
                                                val subtitleText = when {
                                                    bundle.room.isGroupRoom() && otherCount > 0 ->
                                                        "Tap to notify all $otherCount ${if (otherCount == 1) "member" else "members"} in ${bundle.room.spaceName}"
                                                    bundle.room.isGroupRoom() ->
                                                        "Tap to broadcast to everyone in ${bundle.room.spaceName}"
                                                    else ->
                                                        "Tap to notify @${bundle.room.resolvedPartnerLabel(viewModel.currentUserId)}"
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Your Buttons",
                                                            style = MaterialTheme.typography.titleLarge,
                                                            color = MaterialTheme.colorScheme.onBackground
                                                        )
                                                        Text(
                                                            text = subtitleText,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    FilledTonalButton(
                                                        onClick = { showAddButtonDialog = true },
                                                        shape = RoundedCornerShape(14.dp),
                                                        colors = ButtonDefaults.filledTonalButtonColors(
                                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                            contentColor = MaterialTheme.colorScheme.primary
                                                        ),
                                                        elevation = ButtonDefaults.buttonElevation(
                                                            defaultElevation = 0.dp,
                                                            pressedElevation = 0.dp
                                                        ),
                                                        modifier = Modifier.testTag("add_button_action")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Add,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Add Button")
                                                    }
                                                }
                                            }

                                            if (bundle.myButtons.isEmpty()) {
                                                item {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.surface,
                                                        shape = RoundedCornerShape(18.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .border(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.outline,
                                                                RoundedCornerShape(18.dp)
                                                            )
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(24.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            Text(
                                                                text = "No quick buttons yet",
                                                                style = MaterialTheme.typography.titleMedium
                                                            )
                                                            Text(
                                                                text = "Add buttons like Gym, College, or Home with a custom message.",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                val rows = bundle.myButtons.chunked(2)
                                                items(rows) { rowButtons ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        rowButtons.forEach { button ->
                                                            val isMyActive = bundle.myStatus?.activeButtonId == button.id
                                                            MyStatusButtonCard(
                                                                button = button,
                                                                isActive = isMyActive,
                                                                onTap = { viewModel.sendStatusPing(bundle, button) },
                                                                onEdit = { editingButton = button },
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                        if (rowButtons.size == 1) {
                                                            Spacer(modifier = Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSpaceDialog) {
        CreateOrJoinSpaceDialog(
            defaultUsername = defaultUsername,
            initialTabIsJoin = spaceDialogJoinTab,
            onDismiss = { showSpaceDialog = false },
            onCreateSpace = { name, username, isGroupRoom, emails ->
                showSpaceDialog = false
                viewModel.createSpace(name, username, isGroupRoom, emails)
            },
            onJoinByCode = { code, username ->
                showSpaceDialog = false
                viewModel.joinSpaceByCode(code, username)
            }
        )
    }

    val activeSpaceForSettings = selectedSpace
    if (showSpaceSettings && activeSpaceForSettings != null) {
        SpaceSettingsDialog(
            room = activeSpaceForSettings,
            currentUserId = viewModel.currentUserId,
            defaultUsername = defaultUsername,
            onDismiss = { showSpaceSettings = false },
            onUpdateSpace = { newName, newUsername ->
                showSpaceSettings = false
                viewModel.updateSpaceDetails(activeSpaceForSettings, newName, newUsername)
            },
            onInviteEmail = { email ->
                viewModel.inviteEmailToRoom(activeSpaceForSettings, email)
            },
            onLeaveSpace = {
                showSpaceSettings = false
                viewModel.leaveSpace(activeSpaceForSettings)
            },
            onDeleteSpace = {
                showSpaceSettings = false
                viewModel.deleteSpace(activeSpaceForSettings)
            },
            onCopiedCode = {
                scope.launch {
                    snackbarHostState.showSnackbar("Copied invite code: ${activeSpaceForSettings.inviteCode}")
                }
            }
        )
    }

    val currentBundle = (activeBundleState as? UiState.Success)?.data
    if (showAddButtonDialog && currentBundle != null) {
        AddEditButtonDialog(
            existingButton = null,
            onDismiss = { showAddButtonDialog = false },
            onSave = { label, msg, iconKey ->
                showAddButtonDialog = false
                val nextOrder = (currentBundle.myButtons.maxOfOrNull { it.sortOrder } ?: -1L) + 1L
                viewModel.saveButton(
                    room = currentBundle.room,
                    existingButton = null,
                    label = label,
                    message = msg,
                    iconKey = iconKey,
                    nextSortOrder = nextOrder
                )
            }
        )
    }

    val buttonBeingEdited = editingButton
    if (buttonBeingEdited != null && currentBundle != null) {
        AddEditButtonDialog(
            existingButton = buttonBeingEdited,
            onDismiss = { editingButton = null },
            onSave = { label, msg, iconKey ->
                editingButton = null
                viewModel.saveButton(
                    room = currentBundle.room,
                    existingButton = buttonBeingEdited,
                    label = label,
                    message = msg,
                    iconKey = iconKey,
                    nextSortOrder = buttonBeingEdited.sortOrder
                )
            },
            onDelete = {
                editingButton = null
                viewModel.deleteButton(
                    pairId = currentBundle.room.id,
                    buttonId = buttonBeingEdited.id,
                    label = buttonBeingEdited.label
                )
            }
        )
    }

    if (showLiveSyncDialog) {
        LiveSyncStatusDialog(
            isLiveSyncRunning = isLiveSyncRunning,
            hasNotificationPermission = hasNotificationPermission,
            onDismiss = { showLiveSyncDialog = false },
            onRequestPermission = {
                onRequestNotificationPermission()
            },
            onRestartSync = {
                onRestartLiveSync()
                scope.launch {
                    snackbarHostState.showSnackbar("Live background sync started")
                }
            },
            onSendTestAlert = {
                onSendTestNotification()
                scope.launch {
                    snackbarHostState.showSnackbar("Test notification sent")
                }
            },
            onOpenAppSettings = {
                showLiveSyncDialog = false
                onOpenAppSettings()
            }
        )
    }
}

@Composable
private fun SpacePill(
    room: PairingRoom,
    currentUserId: String,
    isSelected: Boolean,
    hasActiveHighlight: Boolean,
    onClick: () -> Unit
) {
    val title = room.spaceName.ifBlank { room.resolvedPartnerLabel(currentUserId) }
    val isGroup = room.isGroupRoom()

    Surface(
        shape = RoundedCornerShape(50),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .testTag("space_pill_$title")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (hasActiveHighlight) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            if (isGroup) {
                Icon(
                    imageVector = Icons.Rounded.Groups,
                    contentDescription = "Group room",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = if (isGroup) "$title · ${room.memberUids.size}" else title,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PendingEmailInviteCard(
    room: PairingRoom,
    onAccept: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (room.isGroupRoom()) "Private Group Room Invite" else "Private 1-on-1 Invite",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "@${room.ownerUsername} invited you to \"${room.spaceName}\"",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onAccept,
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                modifier = Modifier.testTag("accept_email_invite_button")
            ) {
                Text("Join")
            }
        }
    }
}

@Composable
private fun EmptySpacesCard(
    onCreateSpace: () -> Unit,
    onJoinSpace: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_partner_pink_banner),
                contentDescription = "Private connection illustration",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "Create your first private room",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Start a private 1-on-1 room with your partner or a private group room for your family. Every room is strictly isolated with zero cross-visibility.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCreateSpace,
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("empty_create_space_button")
                ) {
                    Text("Create Room")
                }
                OutlinedButton(
                    onClick = onJoinSpace,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("empty_join_space_button")
                ) {
                    Text("Join Code")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomStatusHeroCard(
    bundle: SpaceBundle,
    currentUserId: String,
    onCopyCode: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val room = bundle.room
    val isGroup = room.isGroupRoom()
    val otherUids = room.otherMemberUids(currentUserId)
    val hasAnyActiveStatus = bundle.otherStatuses.values.any { it.activeButtonId.isNotBlank() }

    // Shared 30-second ticker so relative timestamps stay fresh without flicker
    var nowMillis by remember(room.id) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(room.id, bundle.otherStatuses) {
        nowMillis = System.currentTimeMillis()
        while (true) {
            delay(30_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (hasAnyActiveStatus) 1.5.dp else 1.dp,
                color = if (hasAnyActiveStatus) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(50)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isGroup) Icons.Rounded.Groups else Icons.Rounded.Lock,
                                contentDescription = if (isGroup) "Private group room" else "Private 1-on-1 room",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = room.spaceName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (isGroup) {
                        Text(
                            text = "${room.memberUids.size} ${if (room.memberUids.size == 1) "member" else "members"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    } else if (otherUids.isNotEmpty()) {
                        Text(
                            text = "@${room.resolvedPartnerLabel(currentUserId)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isGroup && room.hasOpenSlots()) {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("invite_group_member_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonAdd,
                                contentDescription = "Invite members to ${room.spaceName}",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("space_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Room settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (otherUids.isEmpty()) {
                // Waiting for first invitee to join
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onCopyCode(room.inviteCode) }
                        .testTag("waiting_invite_code_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isGroup) "Invite people to ${room.spaceName}" else "Waiting for partner to join",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val invitedEmails = room.allInvitedEmails()
                            Text(
                                text = if (invitedEmails.isNotEmpty()) {
                                    "Invited ${invitedEmails.size} email${if (invitedEmails.size == 1) "" else "s"} · Or share code ${room.inviteCode}"
                                } else {
                                    "Tap to copy secret code: ${room.inviteCode}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy code",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else if (!isGroup) {
                // 1-on-1 Direct Room Hero View
                val partnerName = room.resolvedPartnerLabel(currentUserId)
                val partnerStatus = bundle.primaryPartnerStatus(currentUserId)
                val partnerButtons = bundle.primaryPartnerButtons(currentUserId)

                if (partnerStatus == null) {
                    Text(
                        text = "@$partnerName hasn't tapped a button yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SingleMemberActiveBanner(
                        status = partnerStatus,
                        nowMillis = nowMillis
                    )
                }

                if (partnerButtons.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "$partnerName's Places",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            partnerButtons.forEach { partnerBtn ->
                                val isActivePlace = partnerStatus?.activeButtonId == partnerBtn.id
                                PartnerPlaceChip(
                                    button = partnerBtn,
                                    isActive = isActivePlace
                                )
                            }
                        }
                    }
                }
            } else {
                // Private Multi-Member Group / Family Room View
                val sortedMemberUids = remember(otherUids, bundle.otherStatuses) {
                    otherUids.sortedByDescending { uid ->
                        bundle.otherStatuses[uid]?.updatedAt?.toDate()?.time ?: Long.MIN_VALUE
                    }
                }
                var expandedMemberUid by remember(room.id) { mutableStateOf<String?>(null) }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    sortedMemberUids.forEach { memberUid ->
                        val memberName = room.memberDisplayName(memberUid)
                        val memberStatus = bundle.otherStatuses[memberUid]
                        val memberButtons = bundle.otherButtonsByMember[memberUid].orEmpty()
                        val isExpanded = expandedMemberUid == memberUid

                        GroupMemberStatusCard(
                            memberName = memberName,
                            status = memberStatus,
                            buttons = memberButtons,
                            isExpanded = isExpanded,
                            nowMillis = nowMillis,
                            onToggleExpand = {
                                if (memberButtons.isNotEmpty()) {
                                    expandedMemberUid = if (isExpanded) null else memberUid
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleMemberActiveBanner(
    status: PartnerStatus,
    nowMillis: Long
) {
    val activeIcon = ButtonIconOption.fromKey(status.iconKey).icon
    val timeAgo = status.updatedAt?.toDate()?.time?.let { millis ->
        val clampedNow = maxOf(nowMillis, millis)
        DateUtils.getRelativeTimeSpanString(
            millis,
            clampedNow,
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    } ?: "Just now"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = activeIcon,
                contentDescription = status.buttonLabel,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = status.buttonLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "· $timeAgo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = status.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupMemberStatusCard(
    memberName: String,
    status: PartnerStatus?,
    buttons: List<StatusButton>,
    isExpanded: Boolean,
    nowMillis: Long,
    onToggleExpand: () -> Unit
) {
    val isActive = status != null && status.activeButtonId.isNotBlank()
    val icon = if (status != null) {
        ButtonIconOption.fromKey(status.iconKey).icon
    } else {
        Icons.Rounded.Lock
    }
    val timeAgo = status?.updatedAt?.toDate()?.time?.let { millis ->
        val clampedNow = maxOf(nowMillis, millis)
        DateUtils.getRelativeTimeSpanString(
            millis,
            clampedNow,
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isActive) 1.dp else 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = buttons.isNotEmpty(), onClick = onToggleExpand)
            .testTag("group_member_row_$memberName")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = 1.dp,
                            color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(11.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = status?.buttonLabel ?: memberName,
                        tint = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "@$memberName",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (status != null) {
                            Text(
                                text = "· ${status.buttonLabel}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = if (status != null) {
                            val suffix = timeAgo?.let { " · $it" } ?: ""
                            "${status.message}$suffix"
                        } else {
                            "No status update yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status != null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isExpanded && buttons.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    buttons.forEach { memberBtn ->
                        PartnerPlaceChip(
                            button = memberBtn,
                            isActive = status?.activeButtonId == memberBtn.id
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartnerPlaceChip(
    button: StatusButton,
    isActive: Boolean
) {
    val icon = ButtonIconOption.fromKey(button.iconKey).icon
    val bgColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "partner_chip_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "partner_chip_fg"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("partner_button_${button.label}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = button.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun MyStatusButtonCard(
    button: StatusButton,
    isActive: Boolean,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val icon = ButtonIconOption.fromKey(button.iconKey).icon

    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        label = "my_btn_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
        label = "my_btn_border"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTap()
            }
            .testTag("my_status_button_${button.label}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = button.label,
                        tint = if (isActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Active status",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 2.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("edit_status_button_${button.label}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit ${button.label}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = button.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = button.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
