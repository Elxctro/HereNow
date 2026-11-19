package com.example.data

import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val email: String? = null,
    val activePairId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PairInvite(
    @DocumentId val id: String = "",
    val pairId: String = "",
    val creatorUid: String = "",
    val creatorUsername: String = "",
    val spaceName: String = "",
    val roomType: String = "direct",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PairingRoom(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val roomType: String = "direct",
    val maxMembers: Int = 2,
    val memberUids: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val spaceName: String = "",
    val inviteCode: String = "",
    val invitedEmail: String? = null,
    val invitedEmails: List<String> = emptyList(),
    val ownerUsername: String = "",
    val partnerUsername: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isGroupRoom(): Boolean =
        roomType == "group" || maxMembers > 2 || memberUids.size > 2

    fun effectiveMaxMembers(): Int =
        if (maxMembers >= 2) maxMembers else if (roomType == "group") 12 else 2

    fun hasOpenSlots(): Boolean = memberUids.size < effectiveMaxMembers()

    fun otherMemberUids(myUid: String): List<String> = memberUids.filter { it != myUid }

    fun partnerUid(myUid: String): String? = memberUids.firstOrNull { it != myUid }

    fun memberDisplayName(uid: String): String {
        memberNames[uid]?.takeIf { it.isNotBlank() }?.let { return it }
        if (uid == ownerUid && ownerUsername.isNotBlank()) return ownerUsername
        if (uid != ownerUid && !partnerUsername.isNullOrBlank()) return partnerUsername
        return "member"
    }

    fun resolvedPartnerLabel(myUid: String): String {
        if (isGroupRoom()) {
            return spaceName.ifBlank { "Group Room" }
        }
        val otherUid = partnerUid(myUid)
        if (otherUid != null) {
            memberNames[otherUid]?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return if (ownerUid == myUid) {
            partnerUsername?.takeIf { it.isNotBlank() } ?: spaceName.ifBlank { "Partner" }
        } else {
            ownerUsername.takeIf { it.isNotBlank() } ?: spaceName.ifBlank { "Partner" }
        }
    }

    fun myUsernameInSpace(myUid: String, fallback: String): String {
        memberNames[myUid]?.takeIf { it.isNotBlank() }?.let { return it }
        return if (ownerUid == myUid) {
            ownerUsername.ifBlank { fallback }
        } else {
            partnerUsername?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    fun allInvitedEmails(): List<String> {
        val combined = buildList {
            invitedEmail?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            invitedEmails.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.forEach { add(it) }
        }
        return combined.distinct()
    }
}

@Keep
@IgnoreExtraProperties
data class StatusButton(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val pairId: String = "",
    val memberUids: List<String> = emptyList(),
    val label: String = "",
    val message: String = "",
    val iconKey: String = "home",
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PartnerStatus(
    @DocumentId val id: String = "",
    val userId: String = "",
    val pairId: String = "",
    val memberUids: List<String> = emptyList(),
    val senderName: String = "",
    val activeButtonId: String = "",
    val buttonLabel: String = "",
    val message: String = "",
    val iconKey: String = "home",
    val pingCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ButtonIconOption(
    val key: String,
    val title: String,
    val icon: ImageVector
) {
    GYM("gym", "Gym", Icons.Rounded.FitnessCenter),
    COLLEGE("college", "College", Icons.Rounded.School),
    HOME("home", "Home", Icons.Rounded.Home),
    WORK("work", "Work", Icons.Rounded.Work),
    COFFEE("coffee", "Coffee", Icons.Rounded.LocalCafe),
    COMMUTE("commute", "Commute", Icons.Rounded.DirectionsCar),
    STUDY("study", "Study", Icons.Rounded.MenuBook),
    FOOD("food", "Food", Icons.Rounded.Restaurant),
    SHOPPING("shopping", "Errands", Icons.Rounded.ShoppingBag),
    OUTDOORS("outdoors", "Outdoors", Icons.Rounded.Park),
    SLEEP("sleep", "Resting", Icons.Rounded.Bedtime),
    HEART("heart", "Thinking", Icons.Rounded.Favorite);

    companion object {
        fun fromKey(key: String): ButtonIconOption =
            entries.firstOrNull { it.key == key } ?: HOME
    }
}
