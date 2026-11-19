package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HereNowRepository
import com.example.data.PairingRoom
import com.example.data.PartnerStatus
import com.example.data.StatusButton
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.notifications.PingStateStore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IncomingPartnerPing(
    val pairId: String,
    val senderUid: String,
    val spaceName: String,
    val senderName: String,
    val buttonLabel: String,
    val message: String,
    val pingCount: Long
)

data class SpaceBundle(
    val room: PairingRoom,
    val myButtons: List<StatusButton>,
    val otherButtonsByMember: Map<String, List<StatusButton>>,
    val myStatus: PartnerStatus?,
    val otherStatuses: Map<String, PartnerStatus>
) {
    fun primaryPartnerStatus(myUid: String): PartnerStatus? =
        room.partnerUid(myUid)?.let { otherStatuses[it] }

    fun primaryPartnerButtons(myUid: String): List<StatusButton> =
        room.partnerUid(myUid)?.let { otherButtonsByMember[it] }.orEmpty()
}

private data class ActiveSpaceKey(
    val pairId: String,
    val otherMemberUids: List<String>
)

@OptIn(ExperimentalCoroutinesApi::class)
class HereNowViewModel(
    private val appContext: Context,
    private val repository: HereNowRepository,
    val currentUserId: String,
    val currentUserEmail: String?,
    val isEmailVerified: Boolean,
    initialDisplayName: String
) : ViewModel() {

    private val _selectedSpaceId = MutableStateFlow<String?>(null)
    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _incomingPings = MutableSharedFlow<IncomingPartnerPing>(extraBufferCapacity = 32)
    val incomingPings: SharedFlow<IncomingPartnerPing> = _incomingPings.asSharedFlow()

    // Prevents duplicate syncMemberUidsOnMyItems batch writes during active emissions
    private val syncedMembershipKeys = mutableSetOf<String>()

    init {
        val suggestedUsername = currentUserEmail
            ?.substringBefore("@")
            ?.replace(Regex("[^a-zA-Z0-9_]"), "")
            ?.take(20)
            ?.takeIf { it.length >= 2 }
            ?: initialDisplayName.replace(Regex("[^a-zA-Z0-9_]"), "").take(20).ifBlank { "user" }

        viewModelScope.launch {
            repository.ensureUserProfile(
                suggestedUsername = suggestedUsername,
                displayName = initialDisplayName,
                email = currentUserEmail
            )
        }
    }

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            unwrapAndLog(error, "userProfile")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val spacesState: StateFlow<UiState<List<PairingRoom>>> = repository.observeMySpaces(currentUserId)
        .map<List<PairingRoom>, UiState<List<PairingRoom>>> { UiState.Success(it) }
        .catch { error ->
            val msg = unwrapAndLog(error, "spaces")
            emit(UiState.Error(msg))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val pendingEmailInvites: StateFlow<List<PairingRoom>> =
        if (isEmailVerified && !currentUserEmail.isNullOrBlank()) {
            repository.observePendingEmailInvites(currentUserId, currentUserEmail)
                .catch { error ->
                    unwrapAndLog(error, "pendingInvites")
                    emit(emptyList())
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000L),
                    initialValue = emptyList()
                )
        } else {
            MutableStateFlow(emptyList())
        }

    // Tracks count of active other members per room and emits deduplicated live alerts across ALL rooms
    val roomActiveCounts: StateFlow<Map<String, Int>> = spacesState
        .map { state ->
            val rooms = (state as? UiState.Success)?.data.orEmpty()
            rooms.flatMap { room ->
                room.otherMemberUids(currentUserId).map { otherUid -> room.id to otherUid }
            }
        }
        .distinctUntilChanged()
        .flatMapLatest { memberTargets ->
            if (memberTargets.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val statusFlows = memberTargets.map { (pairId, memberUid) ->
                    repository.observeStatus(pairId, memberUid, currentUserId)
                        .map { status -> Triple(pairId, memberUid, status) }
                        .catch { error ->
                            unwrapAndLog(error, "memberStatus_${pairId}_$memberUid")
                            emit(Triple(pairId, memberUid, null))
                        }
                }
                combine(statusFlows) { triples ->
                    val currentRooms = (spacesState.value as? UiState.Success)?.data.orEmpty()
                        .associateBy { it.id }
                    val activeCounts = mutableMapOf<String, Int>()

                    triples.forEach { (pairId, memberUid, status) ->
                        if (status == null) {
                            PingStateStore.seedBaselineIfAbsent(
                                context = appContext,
                                userId = currentUserId,
                                pairId = pairId,
                                senderUid = memberUid,
                                baseline = 0L
                            )
                        } else {
                            if (status.activeButtonId.isNotBlank()) {
                                activeCounts[pairId] = (activeCounts[pairId] ?: 0) + 1
                            }
                            val updatedMs = status.updatedAt?.toDate()?.time
                            val isNew = PingStateStore.consumeIfNew(
                                context = appContext,
                                userId = currentUserId,
                                pairId = pairId,
                                senderUid = memberUid,
                                pingCount = status.pingCount,
                                updatedAtMillis = updatedMs
                            )
                            if (isNew) {
                                val room = currentRooms[pairId]
                                _incomingPings.tryEmit(
                                    IncomingPartnerPing(
                                        pairId = pairId,
                                        senderUid = memberUid,
                                        spaceName = room?.spaceName?.ifBlank { "Private Room" } ?: "Private Room",
                                        senderName = status.senderName.ifBlank {
                                            room?.memberDisplayName(memberUid) ?: "Member"
                                        },
                                        buttonLabel = status.buttonLabel,
                                        message = status.message,
                                        pingCount = status.pingCount
                                    )
                                )
                            }
                        }
                    }
                    activeCounts.toMap()
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyMap()
        )

    val selectedSpace: StateFlow<PairingRoom?> = combine(
        spacesState,
        userProfile,
        _selectedSpaceId
    ) { state, profile, explicitId ->
        val rooms = (state as? UiState.Success)?.data.orEmpty()
        val chosenId = explicitId ?: profile?.activePairId
        rooms.firstOrNull { it.id == chosenId } ?: rooms.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = null
    )

    // Keyed strictly by (pairId, otherMemberUids) so room metadata edits never tear down Firestore listeners
    private val activeSpaceStreams: StateFlow<UiState<Triple<List<StatusButton>, PartnerStatus?, Map<String, PartnerStatus>>?>> =
        selectedSpace
            .map { room -> room?.let { ActiveSpaceKey(it.id, it.otherMemberUids(currentUserId)) } }
            .distinctUntilChanged()
            .flatMapLatest { key ->
                if (key == null) {
                    flowOf(UiState.Success(null))
                } else {
                    val buttonsFlow = repository.observeButtons(key.pairId, currentUserId)
                    val myStatusFlow = repository.observeStatus(key.pairId, currentUserId, currentUserId)
                        .catch { error ->
                            unwrapAndLog(error, "myStatus_${key.pairId}")
                            emit(null)
                        }
                    val otherStatusesFlow = if (key.otherMemberUids.isEmpty()) {
                        flowOf(emptyMap())
                    } else {
                        val flows = key.otherMemberUids.map { memberUid ->
                            repository.observeStatus(key.pairId, memberUid, currentUserId)
                                .map { status -> memberUid to status }
                                .catch { error ->
                                    unwrapAndLog(error, "activeMemberStatus_${key.pairId}_$memberUid")
                                    emit(memberUid to null)
                                }
                        }
                        combine(flows) { entries ->
                            buildMap {
                                entries.forEach { (uid, status) ->
                                    if (status != null) put(uid, status)
                                }
                            }
                        }
                    }

                    combine(buttonsFlow, myStatusFlow, otherStatusesFlow) { buttons, myStatus, otherStatuses ->
                        UiState.Success(Triple(buttons, myStatus, otherStatuses))
                            as UiState<Triple<List<StatusButton>, PartnerStatus?, Map<String, PartnerStatus>>?>
                    }.catch { error ->
                        val msg = unwrapAndLog(error, "activeSpaceStreams")
                        emit(UiState.Error(msg))
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val activeSpaceBundle: StateFlow<UiState<SpaceBundle?>> = combine(
        selectedSpace,
        activeSpaceStreams
    ) { room, streamState ->
        val nextState: UiState<SpaceBundle?> = when {
            room == null -> UiState.Success(null)
            streamState is UiState.Loading -> UiState.Loading
            streamState is UiState.Error -> streamState
            streamState is UiState.Success -> {
                val triple = streamState.data
                if (triple == null) {
                    UiState.Loading
                } else {
                    val (buttons, myStatus, otherStatuses) = triple
                    val myButtons = buttons.filter { it.ownerUid == currentUserId }
                    val otherButtonsByMember = buttons
                        .filter { it.ownerUid != currentUserId }
                        .groupBy { it.ownerUid }

                    // Keep memberUids synced on my items whenever room membership changes
                    val needsSync = myButtons.any { it.memberUids != room.memberUids } ||
                        (myStatus != null && myStatus.memberUids != room.memberUids)
                    if (needsSync) {
                        val syncKey = "${room.id}:${room.memberUids.joinToString(",")}:${myButtons.size}"
                        if (syncedMembershipKeys.add(syncKey)) {
                            viewModelScope.launch {
                                val result = repository.syncMemberUidsOnMyItems(
                                    pairId = room.id,
                                    targetMemberUids = room.memberUids,
                                    myButtons = myButtons,
                                    myStatus = myStatus
                                )
                                if (result.isFailure) {
                                    syncedMembershipKeys.remove(syncKey)
                                }
                            }
                        }
                    }

                    UiState.Success(
                        SpaceBundle(
                            room = room,
                            myButtons = myButtons,
                            otherButtonsByMember = otherButtonsByMember,
                            myStatus = myStatus,
                            otherStatuses = otherStatuses
                        )
                    )
                }
            }
            else -> UiState.Loading
        }
        nextState
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading
    )

    // --- User Actions ---

    fun selectSpace(pairId: String) {
        if (pairId.isBlank()) return
        _selectedSpaceId.value = pairId
        viewModelScope.launch {
            repository.setActivePairId(pairId)
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun createSpace(
        spaceName: String,
        myUsername: String,
        isGroupRoom: Boolean,
        invitedEmails: List<String>
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createSpace(spaceName, myUsername, isGroupRoom, invitedEmails)
            _isBusy.value = false
            result.onSuccess { room ->
                _selectedSpaceId.value = room.id
                val typeLabel = if (room.isGroupRoom()) "group room" else "private room"
                _feedbackMessage.value = "Created $typeLabel \"${room.spaceName}\" · Code: ${room.inviteCode}"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Could not create room."
            }
        }
    }

    fun joinSpaceByCode(inviteCode: String, myUsername: String) {
        val cleanCode = inviteCode.trim().uppercase()
        val existingRoom = (spacesState.value as? UiState.Success)?.data
            ?.firstOrNull { it.inviteCode.equals(cleanCode, ignoreCase = true) }
        if (existingRoom != null) {
            _selectedSpaceId.value = existingRoom.id
            _feedbackMessage.value = "You're already in \"${existingRoom.spaceName}\""
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.joinSpaceByCode(cleanCode, myUsername)
            _isBusy.value = false
            result.onSuccess { pairId ->
                _selectedSpaceId.value = pairId
                _feedbackMessage.value = "Joined private room!"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Invalid or full invite code."
            }
        }
    }

    fun acceptEmailInvite(room: PairingRoom, myUsername: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.acceptEmailInvite(room, myUsername)
            _isBusy.value = false
            result.onSuccess { pairId ->
                _selectedSpaceId.value = pairId
                _feedbackMessage.value = "Joined ${room.spaceName}!"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Could not accept invite."
            }
        }
    }

    fun inviteEmailToRoom(room: PairingRoom, email: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.inviteEmailToRoom(room, email)
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "Invited ${email.trim().lowercase()} to ${room.spaceName}"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Could not send invite."
            }
        }
    }

    fun updateSpaceDetails(room: PairingRoom, newSpaceName: String, myNewUsername: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateSpaceDetails(room, newSpaceName, myNewUsername)
            if (result.isSuccess) {
                repository.updateUsername(myNewUsername)
            }
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "Updated room settings"
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Failed to update settings."
            }
        }
    }

    fun leaveSpace(room: PairingRoom) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.leaveSpace(room)
            _isBusy.value = false
            result.onSuccess {
                PingStateStore.clearSpace(appContext, currentUserId, room.id)
                _selectedSpaceId.value = null
                _feedbackMessage.value = "Left \"${room.spaceName}\""
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Failed to leave room."
            }
        }
    }

    fun deleteSpace(room: PairingRoom) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteSpace(room)
            _isBusy.value = false
            result.onSuccess {
                PingStateStore.clearSpace(appContext, currentUserId, room.id)
                _selectedSpaceId.value = null
                _feedbackMessage.value = "Deleted room \"${room.spaceName}\""
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Failed to delete room."
            }
        }
    }

    fun saveButton(
        room: PairingRoom,
        existingButton: StatusButton?,
        label: String,
        message: String,
        iconKey: String,
        nextSortOrder: Long
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveButton(
                pairId = room.id,
                memberUids = room.memberUids,
                existingButton = existingButton,
                label = label,
                message = message,
                iconKey = iconKey,
                sortOrder = existingButton?.sortOrder ?: nextSortOrder
            )
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = if (existingButton == null) "Added \"$label\"" else "Updated \"$label\""
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Failed to save button."
            }
        }
    }

    fun deleteButton(pairId: String, buttonId: String, label: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteButton(pairId, buttonId)
            _isBusy.value = false
            result.onSuccess {
                _feedbackMessage.value = "Deleted \"$label\""
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Failed to delete button."
            }
        }
    }

    fun sendStatusPing(bundle: SpaceBundle, button: StatusButton) {
        val fallbackName = userProfile.value?.username ?: "me"
        val senderName = bundle.room.myUsernameInSpace(currentUserId, fallbackName)
        viewModelScope.launch {
            val result = repository.activateButton(
                pairId = bundle.room.id,
                memberUids = bundle.room.memberUids,
                senderName = senderName,
                button = button,
                currentMyStatus = bundle.myStatus
            )
            result.onSuccess {
                val otherCount = bundle.room.otherMemberUids(currentUserId).size
                _feedbackMessage.value = when {
                    otherCount == 0 ->
                        "Status set to ${button.label} (share code ${bundle.room.inviteCode} to invite people)"
                    bundle.room.isGroupRoom() ->
                        "Notified all $otherCount ${if (otherCount == 1) "member" else "members"} in ${bundle.room.spaceName}: \"${button.message}\""
                    else ->
                        "Notified ${bundle.room.resolvedPartnerLabel(currentUserId)}: \"${button.message}\""
                }
            }.onFailure { err ->
                _feedbackMessage.value = err.localizedMessage ?: "Could not send status."
            }
        }
    }

    private fun unwrapAndLog(error: Throwable, tagSuffix: String): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w("HereNowVM_$tagSuffix", "Error observing $tagSuffix", rootError)
        return rootError.localizedMessage ?: "Sync error"
    }
}
