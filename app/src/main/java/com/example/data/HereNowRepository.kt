package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

class HereNowRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    // --- Real-time Observation Flows ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.GET, "users/$userId", userId)
                throw error
            }

    fun observeMySpaces(userId: String): Flow<List<PairingRoom>> =
        db.collection("pairings")
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    PairingRoom::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, "pairings", userId)
                throw error
            }

    fun observePendingEmailInvites(userId: String, email: String): Flow<List<PairingRoom>> {
        val cleanEmail = email.trim().lowercase()
        val legacyFlow = db.collection("pairings")
            .whereEqualTo("invitedEmail", cleanEmail)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    PairingRoom::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, "pairings", userId)
                emit(emptyList())
            }

        val multiEmailFlow = db.collection("pairings")
            .whereArrayContains("invitedEmails", cleanEmail)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    PairingRoom::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logStreamErrorIfActive(error, OperationType.LIST, "pairings", userId)
                emit(emptyList())
            }

        return combine(legacyFlow, multiEmailFlow) { legacyRooms, groupRooms ->
            (legacyRooms + groupRooms)
                .distinctBy { it.id }
                .filter { room ->
                    !room.memberUids.contains(userId) && room.hasOpenSlots()
                }
        }
    }

    fun observeButtons(pairId: String, userId: String): Flow<List<StatusButton>> =
        db.collection("pairings").document(pairId).collection("buttons")
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    StatusButton::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.sortOrder }
            }
            .catch { error ->
                logStreamErrorIfActive(
                    error,
                    OperationType.LIST,
                    "pairings/$pairId/buttons",
                    userId
                )
                throw error
            }

    fun observeStatus(pairId: String, targetUserId: String, currentUserId: String): Flow<PartnerStatus?> =
        db.collection("pairings").document(pairId).collection("statuses").document(targetUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    PartnerStatus::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                logStreamErrorIfActive(
                    error,
                    OperationType.GET,
                    "pairings/$pairId/statuses/$targetUserId",
                    currentUserId
                )
                throw error
            }

    // --- Profile Mutations ---

    suspend fun ensureUserProfile(
        suggestedUsername: String,
        displayName: String,
        email: String?
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeUsername = sanitizeUsername(suggestedUsername)
                val safeDisplay = displayName.trim().ifBlank { safeUsername }.take(60)
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("username", safeUsername)
                    put("displayName", safeDisplay)
                    email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it.length <= 120 }?.let {
                        put("email", it)
                    }
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUsername(newUsername: String): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val clean = sanitizeUsername(newUsername)
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            docRef.update(
                mapOf(
                    "username" to clean,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setActivePairId(pairId: String?): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val updateValue: Any = if (pairId.isNullOrBlank()) FieldValue.delete() else pairId
            docRef.update(
                mapOf(
                    "activePairId" to updateValue,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Private Rooms (1-on-1 Direct & Multi-Member Family/Group) ---

    suspend fun createSpace(
        spaceName: String,
        myUsername: String,
        isGroupRoom: Boolean,
        invitedEmails: List<String>
    ): Result<PairingRoom> {
        var path = "pairings"
        return try {
            val uid = requireUserId()
            val cleanSpaceName = spaceName.trim().take(30)
            require(cleanSpaceName.isNotEmpty()) { "Room name cannot be empty." }
            val cleanUsername = sanitizeUsername(myUsername)
            val roomType = if (isGroupRoom) "group" else "direct"
            val maxMembers = if (isGroupRoom) 12 else 2
            val cleanEmails = invitedEmails
                .map { it.trim().lowercase() }
                .filter { it.length in 3..120 && it.contains("@") }
                .distinct()
                .take(if (isGroupRoom) 12 else 1)
            val inviteCode = generateInviteCode()

            val pairRef = db.collection("pairings").document()
            path = pairRef.path
            val pairPayload = buildMap<String, Any> {
                put("ownerUid", uid)
                put("roomType", roomType)
                put("maxMembers", maxMembers)
                put("memberUids", listOf(uid))
                put("memberNames", mapOf(uid to cleanUsername))
                put("spaceName", cleanSpaceName)
                put("inviteCode", inviteCode)
                if (cleanEmails.isNotEmpty()) {
                    put("invitedEmails", cleanEmails)
                    if (!isGroupRoom) {
                        put("invitedEmail", cleanEmails.first())
                    }
                }
                put("ownerUsername", cleanUsername)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            pairRef.set(pairPayload).await()

            val inviteRef = db.collection("pairInvites").document(inviteCode)
            path = inviteRef.path
            inviteRef.set(
                mapOf(
                    "pairId" to pairRef.id,
                    "creatorUid" to uid,
                    "creatorUsername" to cleanUsername,
                    "spaceName" to cleanSpaceName,
                    "roomType" to roomType,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            seedStarterButtons(pairRef.id, uid, listOf(uid))
            setActivePairId(pairRef.id)

            Result.success(
                PairingRoom(
                    id = pairRef.id,
                    ownerUid = uid,
                    roomType = roomType,
                    maxMembers = maxMembers,
                    memberUids = listOf(uid),
                    memberNames = mapOf(uid to cleanUsername),
                    spaceName = cleanSpaceName,
                    inviteCode = inviteCode,
                    invitedEmail = if (!isGroupRoom) cleanEmails.firstOrNull() else null,
                    invitedEmails = cleanEmails,
                    ownerUsername = cleanUsername
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinSpaceByCode(rawCode: String, myUsername: String): Result<String> {
        var path = "pairInvites"
        return try {
            val uid = requireUserId()
            val code = rawCode.trim().uppercase()
            require(code.length in 6..16 && code.matches(Regex("^[A-Z0-9]+$"))) {
                "Invite code must be 6-16 letters/numbers."
            }
            val cleanUsername = sanitizeUsername(myUsername)

            val inviteRef = db.collection("pairInvites").document(code)
            path = inviteRef.path
            val inviteSnap = inviteRef.get().await()
            val invite = inviteSnap.toObject(PairInvite::class.java)
                ?: throw IllegalArgumentException("Invite code not found or already used.")
            require(invite.creatorUid != uid) { "You created this room already." }

            val pairRef = db.collection("pairings").document(invite.pairId)
            path = pairRef.path
            val updates = mutableMapOf<String, Any>(
                "memberUids" to FieldValue.arrayUnion(uid),
                "memberNames.$uid" to cleanUsername,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (invite.roomType != "group") {
                updates["partnerUsername"] = cleanUsername
            }
            pairRef.update(updates).await()

            // For 1-on-1 direct rooms, consume the single-use invite code.
            // For group/family rooms, keep the invite code active so other family members can join.
            if (invite.roomType != "group") {
                try {
                    inviteRef.delete().await()
                } catch (_: Exception) {
                    // Non-fatal if already consumed
                }
            }

            val updatedRoom = pairRef.get().await().toObject(
                PairingRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val updatedMembers = updatedRoom?.memberUids?.takeIf { it.isNotEmpty() }
                ?: listOf(invite.creatorUid, uid)

            seedStarterButtons(invite.pairId, uid, updatedMembers)
            setActivePairId(invite.pairId)
            Result.success(invite.pairId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun acceptEmailInvite(room: PairingRoom, myUsername: String): Result<String> {
        val path = "pairings/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.id.isNotBlank()) { "Invalid room." }
            require(!room.memberUids.contains(uid)) { "You are already in this room." }
            require(room.hasOpenSlots()) { "This room is already full." }
            val cleanUsername = sanitizeUsername(myUsername)

            val pairRef = db.collection("pairings").document(room.id)
            val updates = mutableMapOf<String, Any>(
                "memberUids" to FieldValue.arrayUnion(uid),
                "memberNames.$uid" to cleanUsername,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!room.isGroupRoom()) {
                updates["partnerUsername"] = cleanUsername
            }
            pairRef.update(updates).await()

            if (!room.isGroupRoom() && room.inviteCode.isNotBlank()) {
                try {
                    db.collection("pairInvites").document(room.inviteCode).delete().await()
                } catch (_: Exception) {
                    // Optional cleanup
                }
            }

            val updatedRoom = pairRef.get().await().toObject(
                PairingRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val updatedMembers = updatedRoom?.memberUids?.takeIf { it.isNotEmpty() }
                ?: (room.memberUids + uid).distinct()

            seedStarterButtons(room.id, uid, updatedMembers)
            setActivePairId(room.id)
            Result.success(room.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun inviteEmailToRoom(room: PairingRoom, rawEmail: String): Result<Unit> {
        val path = "pairings/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.ownerUid == uid) { "Only the room creator can send email invites." }
            val cleanEmail = rawEmail.trim().lowercase()
            require(cleanEmail.length in 3..120 && cleanEmail.contains("@")) {
                "Please enter a valid Google email address."
            }
            require(room.allInvitedEmails().size < 12) {
                "You can invite up to 12 email addresses per room."
            }

            val updates = mutableMapOf<String, Any>(
                "invitedEmails" to FieldValue.arrayUnion(cleanEmail),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!room.isGroupRoom()) {
                updates["invitedEmail"] = cleanEmail
            }
            db.collection("pairings").document(room.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSpaceDetails(
        room: PairingRoom,
        newSpaceName: String,
        myNewUsername: String
    ): Result<Unit> {
        val path = "pairings/${room.id}"
        return try {
            val uid = requireUserId()
            val cleanSpace = newSpaceName.trim().take(30)
            require(cleanSpace.isNotEmpty()) { "Room name cannot be empty." }
            val cleanUsername = sanitizeUsername(myNewUsername)
            val updates = mutableMapOf<String, Any>(
                "spaceName" to cleanSpace,
                "memberNames.$uid" to cleanUsername,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (room.ownerUid == uid) {
                updates["ownerUsername"] = cleanUsername
            } else if (!room.isGroupRoom()) {
                updates["partnerUsername"] = cleanUsername
            }
            db.collection("pairings").document(room.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveSpace(room: PairingRoom): Result<Unit> {
        val path = "pairings/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.ownerUid != uid) { "Room creators delete the room instead of leaving." }

            // Clean up user's own buttons & live status while still a member
            deleteMyItemsInRoom(room.id, uid)

            db.collection("pairings").document(room.id).update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(uid),
                    "memberNames.$uid" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSpace(room: PairingRoom): Result<Unit> {
        val path = "pairings/${room.id}"
        return try {
            val uid = requireUserId()
            deleteMyItemsInRoom(room.id, uid)

            if (room.inviteCode.isNotBlank()) {
                try {
                    db.collection("pairInvites").document(room.inviteCode).delete().await()
                } catch (_: Exception) {
                }
            }
            db.collection("pairings").document(room.id).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun deleteMyItemsInRoom(pairId: String, uid: String) {
        val buttonsSnap = db.collection("pairings").document(pairId)
            .collection("buttons")
            .whereArrayContains("memberUids", uid)
            .get()
            .await()
        val batch = db.batch()
        buttonsSnap.documents.forEach { doc ->
            if (doc.getString("ownerUid") == uid) {
                batch.delete(doc.reference)
            }
        }
        batch.delete(
            db.collection("pairings").document(pairId)
                .collection("statuses").document(uid)
        )
        batch.commit().await()
    }

    // --- Button & Live Status Mutations ---

    suspend fun syncMemberUidsOnMyItems(
        pairId: String,
        targetMemberUids: List<String>,
        myButtons: List<StatusButton>,
        myStatus: PartnerStatus?
    ): Result<Unit> {
        val path = "pairings/$pairId"
        return try {
            val uid = requireUserId()
            val buttonsToSync = myButtons.filter { it.ownerUid == uid && it.memberUids != targetMemberUids }
            val statusNeedsSync = myStatus != null && myStatus.userId == uid && myStatus.memberUids != targetMemberUids
            if (buttonsToSync.isEmpty() && !statusNeedsSync) return Result.success(Unit)

            val batch = db.batch()
            val pairDoc = db.collection("pairings").document(pairId)
            buttonsToSync.forEach { btn ->
                batch.update(
                    pairDoc.collection("buttons").document(btn.id),
                    mapOf(
                        "memberUids" to targetMemberUids,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            if (statusNeedsSync) {
                batch.update(
                    pairDoc.collection("statuses").document(uid),
                    mapOf(
                        "memberUids" to targetMemberUids,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveButton(
        pairId: String,
        memberUids: List<String>,
        existingButton: StatusButton?,
        label: String,
        message: String,
        iconKey: String,
        sortOrder: Long
    ): Result<Unit> {
        var path = "pairings/$pairId/buttons"
        return try {
            val uid = requireUserId()
            val cleanLabel = label.trim().take(30)
            val cleanMessage = message.trim().take(160)
            require(cleanLabel.isNotEmpty()) { "Button label cannot be empty." }
            require(cleanMessage.isNotEmpty()) { "Notification message cannot be empty." }
            val safeOrder = sortOrder.coerceIn(0L, 10000L)
            val buttonsCol = db.collection("pairings").document(pairId).collection("buttons")

            if (existingButton == null) {
                val docRef = buttonsCol.document()
                path = docRef.path
                docRef.set(
                    mapOf(
                        "ownerUid" to uid,
                        "pairId" to pairId,
                        "memberUids" to memberUids,
                        "label" to cleanLabel,
                        "message" to cleanMessage,
                        "iconKey" to iconKey,
                        "sortOrder" to safeOrder,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val docRef = buttonsCol.document(existingButton.id)
                path = docRef.path
                docRef.update(
                    mapOf(
                        "label" to cleanLabel,
                        "message" to cleanMessage,
                        "iconKey" to iconKey,
                        "sortOrder" to safeOrder,
                        "memberUids" to memberUids,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteButton(pairId: String, buttonId: String): Result<Unit> {
        val path = "pairings/$pairId/buttons/$buttonId"
        return try {
            requireUserId()
            require(buttonId.isNotBlank())
            db.collection("pairings").document(pairId)
                .collection("buttons").document(buttonId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun activateButton(
        pairId: String,
        memberUids: List<String>,
        senderName: String,
        button: StatusButton,
        currentMyStatus: PartnerStatus?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
        val path = "pairings/$pairId/statuses/${uid ?: "unauthenticated"}"
        return try {
            val verifiedUid = requireUserId()
            val cleanSender = sanitizeUsername(senderName)
            val statusRef = db.collection("pairings").document(pairId)
                .collection("statuses").document(verifiedUid)

            val docExists = if (currentMyStatus != null) {
                true
            } else {
                statusRef.get().await().exists()
            }

            if (!docExists) {
                statusRef.set(
                    mapOf(
                        "userId" to verifiedUid,
                        "pairId" to pairId,
                        "memberUids" to memberUids,
                        "senderName" to cleanSender,
                        "activeButtonId" to button.id,
                        "buttonLabel" to button.label,
                        "message" to button.message,
                        "iconKey" to button.iconKey,
                        "pingCount" to 1L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                statusRef.update(
                    mapOf(
                        "senderName" to cleanSender,
                        "activeButtonId" to button.id,
                        "buttonLabel" to button.label,
                        "message" to button.message,
                        "iconKey" to button.iconKey,
                        "pingCount" to FieldValue.increment(1L),
                        "memberUids" to memberUids,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun seedStarterButtons(
        pairId: String,
        ownerUid: String,
        memberUids: List<String>
    ) {
        val buttonsCol = db.collection("pairings").document(pairId).collection("buttons")
        val starters = listOf(
            Triple("Gym", "Just got to the gym!", "gym"),
            Triple("College", "At college right now.", "college"),
            Triple("Home", "Made it back home safely.", "home"),
            Triple("Coffee", "Grabbing a coffee break.", "coffee")
        )
        val batch = db.batch()
        starters.forEachIndexed { index, (label, msg, icon) ->
            val docRef = buttonsCol.document()
            batch.set(
                docRef,
                mapOf(
                    "ownerUid" to ownerUid,
                    "pairId" to pairId,
                    "memberUids" to memberUids,
                    "label" to label,
                    "message" to msg,
                    "iconKey" to icon,
                    "sortOrder" to index.toLong(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
    }

    private fun logStreamErrorIfActive(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    private fun sanitizeUsername(raw: String): String {
        val trimmed = raw.trim().replace(Regex("\\s+"), "_").take(24)
        return if (trimmed.length >= 2) trimmed else "partner"
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return buildString(8) {
            repeat(8) { append(chars[random.nextInt(chars.length)]) }
        }
    }
}
