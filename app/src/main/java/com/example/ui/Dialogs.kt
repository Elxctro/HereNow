package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ButtonIconOption
import com.example.data.PairingRoom
import com.example.data.StatusButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditButtonDialog(
    existingButton: StatusButton?,
    onDismiss: () -> Unit,
    onSave: (label: String, message: String, iconKey: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var label by remember(existingButton) { mutableStateOf(existingButton?.label ?: "") }
    var message by remember(existingButton) { mutableStateOf(existingButton?.message ?: "") }
    var selectedIconKey by remember(existingButton) {
        mutableStateOf(existingButton?.iconKey ?: ButtonIconOption.GYM.key)
    }

    val isValid = label.trim().isNotEmpty() && message.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingButton == null) "New Status Button" else "Edit Button",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { if (it.length <= 30) label = it },
                    label = { Text("Button title (e.g. Gym, College)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("button_label_input")
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 160) message = it },
                    label = { Text("Custom notification message") },
                    placeholder = { Text("I'm at the gym right now!") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("button_message_input")
                )

                Text(
                    text = "Choose an icon",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ButtonIconOption.entries.forEach { option ->
                        val selected = option.key == selectedIconKey
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedIconKey = option.key }
                                .testTag("icon_option_${option.key}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = option.title,
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                if (existingButton != null && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("delete_button_action")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete this button")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(label.trim(), message.trim(), selectedIconKey) },
                enabled = isValid,
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                modifier = Modifier.testTag("save_button_confirm")
            ) {
                Text(if (existingButton == null) "Add Button" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateOrJoinSpaceDialog(
    defaultUsername: String,
    initialTabIsJoin: Boolean = false,
    onDismiss: () -> Unit,
    onCreateSpace: (spaceName: String, myUsername: String, isGroupRoom: Boolean, invitedEmails: List<String>) -> Unit,
    onJoinByCode: (inviteCode: String, myUsername: String) -> Unit
) {
    var isJoinMode by remember(initialTabIsJoin) { mutableStateOf(initialTabIsJoin) }
    var isGroupRoom by remember { mutableStateOf(false) }
    var spaceName by remember { mutableStateOf("") }
    var myUsername by remember(defaultUsername) { mutableStateOf(defaultUsername) }
    var useEmailInvite by remember { mutableStateOf(false) }
    var emailsInput by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }

    val parsedEmails = remember(emailsInput) {
        emailsInput.split(",", ";", "\n", " ")
            .map { it.trim().lowercase() }
            .filter { it.length in 3..120 && it.contains("@") }
            .distinct()
            .take(12)
    }

    val canSubmit = if (!isJoinMode) {
        spaceName.trim().isNotEmpty() && myUsername.trim().length >= 2 &&
            (!useEmailInvite || parsedEmails.isNotEmpty())
    } else {
        inviteCode.trim().length in 6..16 && myUsername.trim().length >= 2
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Private Room",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = !isJoinMode,
                        onClick = { isJoinMode = false },
                        label = { Text("Create Room") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_create_space")
                    )
                    FilterChip(
                        selected = isJoinMode,
                        onClick = { isJoinMode = true },
                        label = { Text("Join Code") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_join_space")
                    )
                }

                if (!isJoinMode) {
                    Text(
                        text = "Room Type",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = !isGroupRoom,
                            onClick = { isGroupRoom = false },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("1-on-1") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("room_type_direct")
                        )
                        FilterChip(
                            selected = isGroupRoom,
                            onClick = { isGroupRoom = true },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Groups,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Group / Family") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("room_type_group")
                        )
                    }

                    Text(
                        text = if (isGroupRoom) {
                            "Private group room (up to 12 people). Tap one button to notify everyone inside at once. Members cannot see any of your other private rooms."
                        } else {
                            "Strictly private 1-on-1 room (max 2 people). Nobody else can join or see anything inside this room."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = spaceName,
                        onValueChange = { if (it.length <= 30) spaceName = it },
                        label = {
                            Text(
                                if (isGroupRoom) "Room name (e.g. Family, Home)"
                                else "Room name (e.g. Partner, Mom)"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("space_name_input")
                    )
                    OutlinedTextField(
                        value = myUsername,
                        onValueChange = { if (it.length <= 24) myUsername = it },
                        label = { Text("Your private username") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("space_username_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !useEmailInvite,
                            onClick = { useEmailInvite = false },
                            label = { Text("Secret Code") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        FilterChip(
                            selected = useEmailInvite,
                            onClick = { useEmailInvite = true },
                            label = { Text("Google Email") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    if (useEmailInvite) {
                        OutlinedTextField(
                            value = emailsInput,
                            onValueChange = { if (it.length <= 400) emailsInput = it },
                            label = {
                                Text(
                                    if (isGroupRoom) "Google emails (comma-separated)"
                                    else "Partner's Google email"
                                )
                            },
                            placeholder = {
                                Text(
                                    if (isGroupRoom) "mom@gmail.com, dad@gmail.com"
                                    else "partner@gmail.com"
                                )
                            },
                            minLines = if (isGroupRoom) 2 else 1,
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("partner_email_input")
                        )
                    } else {
                        Text(
                            text = if (isGroupRoom) {
                                "Maximum privacy: we'll create a secret 8-character code you can share with your family members to join."
                            } else {
                                "Maximum privacy: we'll generate a secret 8-character code to share privately with your partner."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Enter the secret 8-character invite code shared by your partner or family member.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { if (it.length <= 16) inviteCode = it.uppercase() },
                        label = { Text("Secret invite code") },
                        placeholder = { Text("HN7K4M2P") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_code_input")
                    )
                    OutlinedTextField(
                        value = myUsername,
                        onValueChange = { if (it.length <= 24) myUsername = it },
                        label = { Text("Your private username") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("join_username_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isJoinMode) {
                        onCreateSpace(
                            spaceName.trim(),
                            myUsername.trim(),
                            isGroupRoom,
                            if (useEmailInvite) parsedEmails else emptyList()
                        )
                    } else {
                        onJoinByCode(inviteCode.trim(), myUsername.trim())
                    }
                },
                enabled = canSubmit,
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                modifier = Modifier.testTag("space_dialog_confirm")
            ) {
                Text(if (!isJoinMode) "Create Room" else "Join Room")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpaceSettingsDialog(
    room: PairingRoom,
    currentUserId: String,
    defaultUsername: String,
    onDismiss: () -> Unit,
    onUpdateSpace: (newSpaceName: String, myNewUsername: String) -> Unit,
    onInviteEmail: (email: String) -> Unit,
    onLeaveSpace: () -> Unit,
    onDeleteSpace: () -> Unit,
    onCopiedCode: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var spaceName by remember(room) { mutableStateOf(room.spaceName) }
    var myUsername by remember(room) {
        mutableStateOf(room.myUsernameInSpace(currentUserId, defaultUsername))
    }
    var newInviteEmail by remember { mutableStateOf("") }
    var confirmDestructive by remember { mutableStateOf(false) }

    val isOwner = room.ownerUid == currentUserId
    val isGroup = room.isGroupRoom()
    val canDeleteRoom = isOwner || !isGroup
    val pendingEmails = remember(room) { room.allInvitedEmails() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Room Settings", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Privacy & Member Summary
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isGroup) {
                                "Private Group Room · ${room.memberUids.size}/${room.effectiveMaxMembers()} members"
                            } else {
                                "1-on-1 Private Room · ${room.memberUids.size}/2 members"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            room.memberUids.forEach { uid ->
                                val name = room.memberDisplayName(uid)
                                val suffix = when {
                                    uid == currentUserId -> " (you)"
                                    uid == room.ownerUid -> " (host)"
                                    else -> ""
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(8.dp)
                                    )
                                ) {
                                    Text(
                                        text = "@$name$suffix",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Invite Code Card (shown whenever the room still has open slots)
                if (room.hasOpenSlots()) {
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
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isGroup) "Group Invite Code (share with family)" else "Secret Invite Code",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = room.inviteCode,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(room.inviteCode))
                                    onCopiedCode()
                                },
                                modifier = Modifier.testTag("copy_invite_code_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy invite code",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Owner can invite additional people by Google email
                    if (isOwner) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newInviteEmail,
                                onValueChange = { if (it.length <= 120) newInviteEmail = it },
                                label = { Text("Add member by Gmail") },
                                placeholder = { Text("member@gmail.com") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_invite_email_input")
                            )
                            Button(
                                onClick = {
                                    val emailToSend = newInviteEmail.trim().lowercase()
                                    if (emailToSend.contains("@")) {
                                        onInviteEmail(emailToSend)
                                        newInviteEmail = ""
                                    }
                                },
                                enabled = newInviteEmail.trim().contains("@") && newInviteEmail.trim().length >= 5,
                                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                                modifier = Modifier.testTag("settings_invite_email_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonAdd,
                                    contentDescription = "Send email invite",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (pendingEmails.isNotEmpty()) {
                            Text(
                                text = "Invited emails: ${pendingEmails.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        text = if (isGroup) "Room is at capacity (${room.memberUids.size}/${room.effectiveMaxMembers()} members)."
                        else "Locked private 1-on-1 room (2/2 members).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = spaceName,
                    onValueChange = { if (it.length <= 30) spaceName = it },
                    label = { Text("Room name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = myUsername,
                    onValueChange = { if (it.length <= 24) myUsername = it },
                    label = { Text("Your username in this room") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (!confirmDestructive) {
                    TextButton(
                        onClick = { confirmDestructive = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("room_destructive_action")
                    ) {
                        Icon(
                            imageVector = if (canDeleteRoom) Icons.Rounded.DeleteOutline else Icons.AutoMirrored.Rounded.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (canDeleteRoom) "Delete this room" else "Leave this room")
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (canDeleteRoom) {
                                    "Delete \"${room.spaceName}\" for everyone in this room?"
                                } else {
                                    "Leave \"${room.spaceName}\"? You will stop receiving updates from this room."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = if (canDeleteRoom) onDeleteSpace else onLeaveSpace,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)
                                ) {
                                    Text(if (canDeleteRoom) "Confirm Delete" else "Confirm Leave")
                                }
                                TextButton(onClick = { confirmDestructive = false }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpdateSpace(spaceName.trim(), myUsername.trim()) },
                enabled = spaceName.trim().isNotEmpty() && myUsername.trim().length >= 2,
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun LiveSyncStatusDialog(
    isLiveSyncRunning: Boolean,
    hasNotificationPermission: Boolean,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onRestartSync: () -> Unit,
    onSendTestAlert: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Live Notifications & Sync", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = if (isLiveSyncRunning && hasNotificationPermission) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when {
                                !hasNotificationPermission -> "Notifications are disabled"
                                isLiveSyncRunning -> "Live Background Sync Active"
                                else -> "Background Sync Paused"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isLiveSyncRunning && hasNotificationPermission) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                        Text(
                            text = when {
                                !hasNotificationPermission ->
                                    "Allow notification permission so partner and family room updates alert you immediately."
                                isLiveSyncRunning ->
                                    "HereNow is listening in real time across all your private rooms so alerts arrive in foreground, background, and even when closed."
                                else ->
                                    "Tap Restart Sync below to resume instant background alerts."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLiveSyncRunning && hasNotificationPermission) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }

                Text(
                    text = "Tip for Samsung, Xiaomi, or OnePlus phones: set Battery usage to Unrestricted in Android App Settings so the OS never pauses live sync when the screen is off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!hasNotificationPermission) {
                    Button(
                        onClick = onRequestPermission,
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_request_notif_permission")
                    ) {
                        Text("Allow Notifications")
                    }
                } else {
                    Button(
                        onClick = onSendTestAlert,
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_test_notification")
                    ) {
                        Text("Send Test Notification")
                    }
                }

                if (!isLiveSyncRunning) {
                    Button(
                        onClick = onRestartSync,
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_restart_sync")
                    ) {
                        Text("Start Live Background Sync")
                    }
                }

                TextButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_open_app_settings")
                ) {
                    Text("Open Android App & Battery Settings")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
