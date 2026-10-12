package de.kloostech.deckboard.ui

import de.kloostech.deckboard.i18n.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.Style
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import de.kloostech.deckboard.data.ActivityEntry
import de.kloostech.deckboard.data.CardAttachment
import de.kloostech.deckboard.data.CardComment
import de.kloostech.deckboard.data.DeckUser
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import tech.kloos.kompound.accordion.KExpandable
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.icon.KIcons
import tech.kloos.kompound.list.KListItem
import tech.kloos.kompound.markdown.KMarkdown
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField

/**
 * What the card detail dialog needs beyond the card itself: the board's members to assign from, and
 * on-demand loading of comments, attachments and activity. Implemented by the app on top of the API.
 */
interface CardExtras {
    /** Nextcloud user id of the signed-in user (to recognise one's own comments). */
    val currentUser: String

    /** Everyone who can be assigned to the card (the board's members). */
    val boardUsers: List<DeckUser>

    /** The card in Nextcloud's web UI, where attachments can be opened. Null when unknown. */
    val cardWebUrl: String?

    fun setAssignee(user: DeckUser, assigned: Boolean)
    suspend fun comments(): Result<List<CardComment>>
    suspend fun postComment(message: String): Result<CardComment>
    suspend fun deleteComment(commentId: Int): Result<Unit>
    suspend fun attachments(): Result<List<CardAttachment>>
    suspend fun activities(): Result<List<ActivityEntry>>
}

@Composable
private fun SectionTitle(text: String) {
    KText(text, textStyle = androidx.compose.material3.MaterialTheme.typography.titleSmall, style = Style { contentColor(DeckTextSecondary) })
}

/** Files attached to the card. Renders nothing while loading, on failure, or when there are none. */
@Composable
fun AttachmentsSection(extras: CardExtras) {
    var attachments by remember { mutableStateOf<List<CardAttachment>>(emptyList()) }
    LaunchedEffect(Unit) { attachments = extras.attachments().getOrDefault(emptyList()) }
    if (attachments.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(tr("Attachments ({count})", "count" to attachments.size))
        attachments.forEach { a ->
            val details = listOfNotNull(
                formatFileSize(a.extendedData?.filesize),
                a.createdBy?.let { tr("by {user}", "user" to it) },
                a.createdAt?.takeIf { it > 0 }?.let { ts -> shortTime(epochSecondsToIso(ts)) },
            ).joinToString(" · ")
            KListItem(
                headline = a.data.ifBlank { tr("Attachment {id}", "id" to a.id) },
                supporting = details.ifBlank { null },
                leading = { KIcon(Icons.Filled.AttachFile, contentDescription = null) },
                // Files live in Nextcloud, so open the card there instead of downloading in-app.
                onClick = extras.cardWebUrl?.let { url -> { runCatching { uriHandler.openUri(url) } } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Comment thread of the card with a box to write a new comment; own comments can be deleted. */
@Composable
fun CommentsSection(extras: CardExtras) {
    var comments by remember { mutableStateOf<List<CardComment>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        extras.comments()
            .onSuccess { comments = it }
            .onFailure { error = it.message ?: tr("Couldn't load comments") }
        loaded = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(if (loaded) tr("Comments ({count})", "count" to comments.size) else tr("Comments"))
        error?.let { KText(it, style = Style { contentColor(DeckError) }) }
        if (loaded && comments.isEmpty() && error == null) {
            KText(tr("No comments yet."), style = Style { contentColor(DeckTextTertiary) })
        }
        comments.forEach { c ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                UserAvatar(DeckUser(uid = c.actorId, displayname = c.actorDisplayName ?: c.actorId))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KText(c.actorDisplayName ?: c.actorId)
                        KText(shortTime(c.creationDateTime), style = Style { contentColor(DeckTextTertiary) })
                    }
                    KMarkdown(c.message, selectable = false, modifier = Modifier.fillMaxWidth())
                }
                if (c.actorId == extras.currentUser) {
                    KIconButton(
                        onClick = {
                            scope.launch {
                                extras.deleteComment(c.id)
                                    .onSuccess { comments = comments.filterNot { it.id == c.id } }
                                    .onFailure { error = it.message ?: tr("Couldn't delete comment") }
                            }
                        },
                        contentDescription = tr("Delete comment"),
                    ) { KIcon(KIcons.Delete, contentDescription = null, tint = DeckError) }
                }
            }
        }

        KTextField(
            value = draft,
            onValueChange = { draft = it },
            label = tr("Write a comment"),
            singleLine = false,
            minLines = 2,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )
        KButton(
            onClick = {
                val text = draft.trim()
                if (text.isEmpty() || posting) return@KButton
                posting = true
                error = null
                scope.launch {
                    extras.postComment(text)
                        .onSuccess { comments = comments + it; draft = "" }
                        .onFailure { error = it.message ?: tr("Couldn't post comment") }
                    posting = false
                }
            },
            enabled = draft.isNotBlank(),
            loading = posting,
            variant = KButtonVariant.Tonal,
        ) { KText(tr("Comment")) }
    }
}

/** Collapsed by default; loads the card's activity only when opened. */
@Composable
fun ActivitySection(extras: CardExtras) {
    var expanded by remember { mutableStateOf(false) }
    KExpandable(title = tr("Activity"), expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = 8.dp)) {
            ActivityList(load = { extras.activities() })
        }
    }
}

/** Unix seconds → ISO-8601 (UTC), which [shortTime] trims to minutes. */
@OptIn(ExperimentalTime::class)
private fun epochSecondsToIso(seconds: Long): String = Instant.fromEpochSeconds(seconds).toString()
