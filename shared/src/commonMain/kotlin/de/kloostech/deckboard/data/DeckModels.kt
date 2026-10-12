package de.kloostech.deckboard.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeckUser(
    val primaryKey: String? = null,
    val uid: String? = null,
    val displayname: String? = null,
    val type: Int? = null,
)

@Serializable
data class BoardPermissions(
    @SerialName("PERMISSION_READ") val read: Boolean = false,
    @SerialName("PERMISSION_EDIT") val edit: Boolean = false,
    @SerialName("PERMISSION_MANAGE") val manage: Boolean = false,
    @SerialName("PERMISSION_SHARE") val share: Boolean = false,
)

@Serializable
data class Label(
    val id: Int? = null,
    val title: String? = null,
    val color: String? = null,
    val boardId: Int? = null,
)

@Serializable
data class Board(
    val id: Int,
    val title: String,
    val owner: DeckUser? = null,
    val color: String? = null,
    val archived: Boolean = false,
    val labels: List<Label> = emptyList(),
    val permissions: BoardPermissions? = null,
    val lastModified: Long? = null,
    val deletedAt: Long = 0,
    /** Explicit shares (users, groups, circles); the owner is not in here, see [users]. */
    val acl: List<BoardAcl> = emptyList(),
    /** Everyone who can see the board, owner included: the pool cards can be assigned from. */
    val users: List<DeckUser> = emptyList(),
)

/** One share of a board. [type]: 0 user, 1 group, 7 circle. */
@Serializable
data class BoardAcl(
    val id: Int,
    val participant: DeckUser? = null,
    val type: Int = 0,
    val boardId: Int? = null,
    val permissionEdit: Boolean = false,
    val permissionShare: Boolean = false,
    val permissionManage: Boolean = false,
    val owner: Boolean = false,
)

/** A user, group or circle that can be shared with, from the files_sharing "sharees" lookup. */
data class Sharee(val label: String, val shareType: Int, val shareWith: String)

/** A card comment (Nextcloud comments API). */
@Serializable
data class CardComment(
    val id: Int,
    val objectId: String? = null,
    val message: String = "",
    val actorId: String = "",
    val actorDisplayName: String? = null,
    val creationDateTime: String = "",
)

@Serializable
data class AttachmentExtendedData(
    val filesize: Long? = null,
    val mimetype: String? = null,
)

/** A file attached to a card. For `type == "file"`/`"deck_file"`, [data] is the file name. */
@Serializable
data class CardAttachment(
    val id: Int,
    val cardId: Int? = null,
    val type: String = "",
    val data: String = "",
    val createdBy: String? = null,
    val createdAt: Long? = null,
    val lastModified: Long? = null,
    val extendedData: AttachmentExtendedData? = null,
)

/** One entry of the Nextcloud activity stream, already rendered to text by the server. */
data class ActivityEntry(
    val id: Long,
    val datetime: String,
    val user: String,
    val subject: String,
    val boardId: Int?,
    val cardId: Int?,
)

@Serializable
data class DeckCard(
    val id: Int,
    val title: String,
    val description: String? = null,
    val color: String? = null,
    val stackId: Int? = null,
    val type: String? = null,
    val lastModified: Long? = null,
    val createdAt: Long? = null,
    val labels: List<Label> = emptyList(),
    val assignedUsers: List<AssignedUser> = emptyList(),
    val owner: DeckUser? = null,
    val order: Int? = null,
    val archived: Boolean = false,
    val done: String? = null,
    val duedate: String? = null,
    val overdue: Int? = null,
)

@Serializable
data class AssignedUser(
    val id: Int? = null,
    val participant: DeckUser? = null,
)

@Serializable
data class Stack(
    val id: Int,
    val title: String,
    val boardId: Int? = null,
    val order: Int? = null,
    val isDoneColumn: Boolean = false,
    val cards: List<DeckCard> = emptyList(),
)
