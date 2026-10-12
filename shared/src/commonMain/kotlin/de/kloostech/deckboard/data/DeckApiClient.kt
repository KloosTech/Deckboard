package de.kloostech.deckboard.data

import de.kloostech.deckboard.i18n.tr
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.contentOrNull

/** Thrown when the Deck server responds with anything other than 2xx. */
class DeckApiException(val statusCode: Int, message: String) : Exception(message)

@Serializable
private data class ReorderCardBody(val order: Int, val stackId: Int)

@Serializable
private data class CreateBoardBody(val title: String, val color: String)

@Serializable
private data class UpdateBoardBody(val title: String, val color: String, val archived: Boolean)

@Serializable
private data class CreateCardBody(val title: String, val type: String = "plain", val order: Int = 0)

@Serializable
private data class CreateStackBody(val title: String, val order: Int)

@Serializable
private data class LabelIdBody(val labelId: Int)

@Serializable
private data class AclBody(
    val type: Int,
    val participant: String,
    val permissionEdit: Boolean,
    val permissionShare: Boolean,
    val permissionManage: Boolean,
)

@Serializable
private data class UserIdBody(val userId: String)

@Serializable
private data class CommentBody(val message: String)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Serializable
private data class CreateLabelBody(val title: String, val color: String)

@Serializable
private data class UpdateCardBody(
    val title: String,
    // Deck's PHP controller types both of these as non-nullable `string` params with no
    // default — a JSON `null` (not just a missing key) fails that type check server-side
    // with a bare, unlogged 400. `owner` also has no default at all, so it must always be
    // sent. duedate/done are fine nullable — those PHP params are untyped.
    val owner: String,
    val description: String,
    val type: String,
    val order: Int,
    val duedate: String?,
    val archived: Boolean,
    val done: String?,
)

@Serializable
private data class SetDoneStackBody(val boardId: Int, val isDone: Boolean)

class DeckApiClient(private val connection: DeckConnection) {

    /** `stack_api#update` (the plain `/index.php/...` route every other call here uses) silently
     * ignores `isDoneColumn` — toggling it only works through the newer OCS-mounted
     * `stack_ocs#setDoneStack` route, which lives under `/ocs/v2.php/...` instead. Verified
     * live: the plain route accepts the field and 200s without applying it; the OCS route
     * actually flips it. */
    private val ocsBaseUrl: String = connection.ocsApiBaseUrl

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Auth) {
            basic {
                credentials {
                    BasicAuthCredentials(username = connection.username, password = connection.appPassword)
                }
                sendWithoutRequest { true }
            }
        }
        defaultRequest {
            header("OCS-APIRequest", "true")
            header("Accept", "application/json")
        }
        expectSuccess = false
    }

    suspend fun getBoards(): List<Board> =
        get("${connection.apiBaseUrl}/boards?details=true").body()

    suspend fun getStacks(boardId: Int): List<Stack> =
        get("${connection.apiBaseUrl}/boards/$boardId/stacks").body()

    /**
     * Moves [cardId] to [order] inside [targetStackId]. The `{stackId}` URL segment is always
     * the *destination* stack — for both a same-column reorder (target == current stack) and a
     * cross-column move — never the card's source stack.
     */
    suspend fun reorderCard(boardId: Int, targetStackId: Int, cardId: Int, order: Int) {
        val response = client.put("${connection.apiBaseUrl}/boards/$boardId/stacks/$targetStackId/cards/$cardId/reorder") {
            contentType(ContentType.Application.Json)
            setBody(ReorderCardBody(order = order, stackId = targetStackId))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to move card ({code})", "code" to response.status.value))
        }
    }

    suspend fun createBoard(title: String, color: String): Board {
        val response = client.post("${connection.apiBaseUrl}/boards") {
            contentType(ContentType.Application.Json)
            setBody(CreateBoardBody(title = title, color = color))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to create board ({code})", "code" to response.status.value))
        }
        return response.body()
    }

    suspend fun createStack(boardId: Int, title: String, order: Int): Stack {
        val response = client.post("${connection.apiBaseUrl}/boards/$boardId/stacks") {
            contentType(ContentType.Application.Json)
            setBody(CreateStackBody(title = title, order = order))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to create column ({code})", "code" to response.status.value))
        }
        return response.body()
    }

    suspend fun createCard(boardId: Int, stackId: Int, title: String): DeckCard {
        val response = client.post("${connection.apiBaseUrl}/boards/$boardId/stacks/$stackId/cards") {
            contentType(ContentType.Application.Json)
            setBody(CreateCardBody(title = title))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to create card ({code})", "code" to response.status.value))
        }
        return response.body()
    }

    suspend fun updateCard(
        boardId: Int,
        stackId: Int,
        cardId: Int,
        title: String,
        description: String?,
        type: String,
        order: Int,
        duedate: String?,
        archived: Boolean,
        done: String?,
    ): DeckCard {
        val response = client.put("${connection.apiBaseUrl}/boards/$boardId/stacks/$stackId/cards/$cardId") {
            contentType(ContentType.Application.Json)
            setBody(
                UpdateCardBody(
                    title = title,
                    owner = connection.username,
                    description = description.orEmpty(),
                    type = type,
                    order = order,
                    duedate = duedate,
                    archived = archived,
                    done = done,
                ),
            )
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to update card ({code})", "code" to response.status.value))
        }
        return response.body()
    }

    /** Deck's board PUT requires `title` and `color` unconditionally (no partial update), so
     * callers must pass the board's current title/archived state alongside the new color. */
    suspend fun updateBoard(boardId: Int, title: String, color: String, archived: Boolean): Board {
        val response = client.put("${connection.apiBaseUrl}/boards/$boardId") {
            contentType(ContentType.Application.Json)
            setBody(UpdateBoardBody(title = title, color = color, archived = archived))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to update board ({code})", "code" to response.status.value))
        }
        return response.body()
    }

    /** Deck's public API only exposes a soft delete: this moves the board to trash (sets
     * `deletedAt`) rather than purging it. A second DELETE against an already-trashed board
     * 403s — Deck's own permission check doesn't consider a deleted board manageable — and
     * permanent purge isn't reachable over the API at all, only via server-side trash retention.
     * The app has no trash UI, so from here this is effectively "delete": [getBoards] still
     * returns the board with `deletedAt` set, which is why callers filter on that. */
    suspend fun deleteBoard(boardId: Int) {
        val response = client.delete("${connection.apiBaseUrl}/boards/$boardId")
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to delete board ({code})", "code" to response.status.value))
        }
    }

    /** Designates [stackId] as the board's "done" column (or unsets it). Setting it true
     * bulk-marks every undone card currently in that stack as done and clears the flag on any
     * other stack of the same board — both server-side, so callers should reload stacks
     * afterwards rather than trying to predict the diff locally. */
    suspend fun setDoneStack(boardId: Int, stackId: Int, isDone: Boolean) {
        val response = client.put("$ocsBaseUrl/stacks/$stackId/done") {
            contentType(ContentType.Application.Json)
            setBody(SetDoneStackBody(boardId = boardId, isDone = isDone))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to set done column ({code})", "code" to response.status.value))
        }
    }

    /** Like [deleteBoard], this is a soft delete server-side (sets `deletedAt`) rather than an
     * immediate purge — the app has no trash UI for cards either, so from here it's final. */
    suspend fun deleteCard(boardId: Int, stackId: Int, cardId: Int) {
        val response = client.delete("${connection.apiBaseUrl}/boards/$boardId/stacks/$stackId/cards/$cardId")
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to delete card ({code})", "code" to response.status.value))
        }
    }

    /** Creates a label on the board (labels belong to the board; cards then reference them). [color] is hex without `#`. */
    suspend fun createLabel(boardId: Int, title: String, color: String): Label {
        val response = client.post("${connection.apiBaseUrl}/boards/$boardId/labels") {
            contentType(ContentType.Application.Json)
            setBody(CreateLabelBody(title = title, color = color))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to create label ({code})", "code" to response.status.value))
        }
        return response.body()
    }

    /** Board with its members ([Board.users]) and shares ([Board.acl]). */
    suspend fun getBoard(boardId: Int): Board = get("${connection.apiBaseUrl}/boards/$boardId").body()

    /** Users and groups matching [query] that a board can be shared with (Nextcloud sharees lookup). */
    suspend fun searchSharees(query: String): List<Sharee> {
        val response = client.get("${connection.baseUrl}/ocs/v2.php/apps/files_sharing/api/v1/sharees") {
            url {
                parameters.append("format", "json")
                parameters.append("perPage", "20")
                parameters.append("search", query)
                parameters.append("itemType", "0")
                parameters.append("itemType", "1")
            }
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to search users ({code})", "code" to response.status.value))
        }
        val data = lenientJson.parseToJsonElement(response.bodyAsText()).jsonObject["ocs"]?.jsonObject?.get("data")?.jsonObject
            ?: return emptyList()
        fun entries(container: JsonObject?, key: String): List<Sharee> =
            container?.get(key)?.jsonArray.orEmpty().mapNotNull { e ->
                val o = e.jsonObject
                val value = o["value"]?.jsonObject ?: return@mapNotNull null
                Sharee(
                    label = o["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    shareType = value["shareType"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                    shareWith = value["shareWith"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                )
            }
        val exact = data["exact"]?.jsonObject
        return (entries(exact, "users") + entries(data, "users") + entries(exact, "groups") + entries(data, "groups"))
            .distinctBy { it.shareType to it.shareWith }
    }

    /** Shares the board with a user (type 0) or group (type 1). */
    suspend fun addAcl(
        boardId: Int,
        type: Int,
        participant: String,
        edit: Boolean = true,
        share: Boolean = false,
        manage: Boolean = false,
    ): BoardAcl {
        val response = client.post("${connection.apiBaseUrl}/boards/$boardId/acl") {
            contentType(ContentType.Application.Json)
            setBody(AclBody(type, participant, edit, share, manage))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to share board ({code})", "code" to response.status.value))
        }
        return response.body()
    }

    suspend fun updateAcl(boardId: Int, acl: BoardAcl, edit: Boolean, share: Boolean, manage: Boolean) {
        val response = client.put("${connection.apiBaseUrl}/boards/$boardId/acl/${acl.id}") {
            contentType(ContentType.Application.Json)
            setBody(AclBody(acl.type, acl.participant?.uid.orEmpty(), edit, share, manage))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to update sharing ({code})", "code" to response.status.value))
        }
    }

    suspend fun deleteAcl(boardId: Int, aclId: Int) {
        val response = client.delete("${connection.apiBaseUrl}/boards/$boardId/acl/$aclId")
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to remove share ({code})", "code" to response.status.value))
        }
    }

    suspend fun assignUser(boardId: Int, stackId: Int, cardId: Int, userId: String) {
        val response = client.put("${connection.apiBaseUrl}/boards/$boardId/stacks/$stackId/cards/$cardId/assignUser") {
            contentType(ContentType.Application.Json)
            setBody(UserIdBody(userId))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to assign user ({code})", "code" to response.status.value))
        }
    }

    suspend fun unassignUser(boardId: Int, stackId: Int, cardId: Int, userId: String) {
        val response = client.put("${connection.apiBaseUrl}/boards/$boardId/stacks/$stackId/cards/$cardId/unassignUser") {
            contentType(ContentType.Application.Json)
            setBody(UserIdBody(userId))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to unassign user ({code})", "code" to response.status.value))
        }
    }

    suspend fun getAttachments(boardId: Int, stackId: Int, cardId: Int): List<CardAttachment> =
        get("${connection.apiBaseUrl}/boards/$boardId/stacks/$stackId/cards/$cardId/attachments").body()

    /** Comments of a card, oldest first (Deck's OCS comments endpoint). */
    suspend fun getComments(cardId: Int): List<CardComment> {
        val response = client.get("${connection.ocsDeckBaseUrl}/cards/$cardId/comments") { url { parameters.append("limit", "100") } }
        if (!response.status.isSuccess()) {
            // 404 = the card has no comment thread yet on some versions
            if (response.status.value == 404) return emptyList()
            throw DeckApiException(response.status.value, tr("Failed to load comments ({code})", "code" to response.status.value))
        }
        return ocsData(response.bodyAsText())?.let { lenientJson.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(CardComment.serializer()), it) }
            .orEmpty().sortedBy { it.id }
    }

    suspend fun createComment(cardId: Int, message: String): CardComment {
        val response = client.post("${connection.ocsDeckBaseUrl}/cards/$cardId/comments") {
            contentType(ContentType.Application.Json)
            setBody(CommentBody(message))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to post comment ({code})", "code" to response.status.value))
        }
        val data = ocsData(response.bodyAsText()) ?: throw DeckApiException(500, tr("Empty comment response"))
        return lenientJson.decodeFromJsonElement(CardComment.serializer(), data)
    }

    suspend fun deleteComment(cardId: Int, commentId: Int) {
        val response = client.delete("${connection.ocsDeckBaseUrl}/cards/$cardId/comments/$commentId")
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to delete comment ({code})", "code" to response.status.value))
        }
    }

    /**
     * Recent Deck activity of the current user, newest first, limited to [boardId] and (if given) [cardId].
     * The activity app's own `object_id` filter does not work for Deck, so this reads the Deck stream in
     * pages and filters it here. Reads at most [maxPages] pages of [pageSize] to stay cheap.
     */
    suspend fun getActivities(boardId: Int, cardId: Int? = null, limit: Int = 30, pageSize: Int = 100, maxPages: Int = 5): List<ActivityEntry> {
        val found = mutableListOf<ActivityEntry>()
        var since: Long? = null
        repeat(maxPages) {
            val response = client.get("${connection.baseUrl}/ocs/v2.php/apps/activity/api/v2/activity/deck") {
                url {
                    parameters.append("format", "json")
                    parameters.append("limit", pageSize.toString())
                    since?.let { parameters.append("since", it.toString()) }
                }
            }
            // 304 = no (more) activities
            if (response.status.value == 304) return found
            if (!response.status.isSuccess()) {
                throw DeckApiException(response.status.value, tr("Failed to load activity ({code})", "code" to response.status.value))
            }
            val page = ocsData(response.bodyAsText())?.jsonArray ?: return found
            if (page.isEmpty()) return found
            page.forEach { e ->
                val o = e.jsonObject
                val rich = o["subject_rich"]?.jsonArray?.getOrNull(1)?.jsonObject
                val board = rich?.get("board")?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull
                val card = rich?.get("card")?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull
                if (board == boardId && (cardId == null || card == cardId)) {
                    found += ActivityEntry(
                        id = o["activity_id"]?.jsonPrimitive?.long ?: 0,
                        datetime = o["datetime"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        user = o["user"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        subject = o["subject"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        boardId = board,
                        cardId = card,
                    )
                }
            }
            if (found.size >= limit) return found.take(limit)
            since = page.last().jsonObject["activity_id"]?.jsonPrimitive?.long ?: return found
        }
        return found
    }

    private fun ocsData(body: String): JsonElement? =
        lenientJson.parseToJsonElement(body).jsonObject["ocs"]?.jsonObject?.get("data")

    suspend fun assignLabel(boardId: Int, stackId: Int, cardId: Int, labelId: Int) {
        val response = client.put("${connection.apiBaseUrl}/boards/$boardId/stacks/$stackId/cards/$cardId/assignLabel") {
            contentType(ContentType.Application.Json)
            setBody(LabelIdBody(labelId))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to assign label ({code})", "code" to response.status.value))
        }
    }

    suspend fun removeLabel(boardId: Int, stackId: Int, cardId: Int, labelId: Int) {
        val response = client.put("${connection.apiBaseUrl}/boards/$boardId/stacks/$stackId/cards/$cardId/removeLabel") {
            contentType(ContentType.Application.Json)
            setBody(LabelIdBody(labelId))
        }
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Failed to remove label ({code})", "code" to response.status.value))
        }
    }

    fun close() = client.close()

    private suspend fun get(url: String): HttpResponse {
        val response = client.get(url)
        if (!response.status.isSuccess()) {
            throw DeckApiException(response.status.value, tr("Request failed ({code}): {url}", "code" to response.status.value, "url" to url))
        }
        return response
    }
}
