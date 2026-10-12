package de.kloostech.deckboard.ui

import de.kloostech.deckboard.i18n.tr
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.kloostech.deckboard.data.DeckCard
import de.kloostech.deckboard.data.Label
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.chip.KChip
import tech.kloos.kompound.date.KDateField
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.icon.KIcons
import tech.kloos.kompound.markdown.KMarkdown
import tech.kloos.kompound.markdown.KMarkdownField
import tech.kloos.kompound.selection.KCheckbox
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField
import tech.kloos.kompound.theme.KompoundTheme

/** Which field the user clicked to start editing, so that field gets the keyboard focus. */
private enum class EditFocus { Title, Description, None }

@OptIn(ExperimentalLayoutApi::class, ExperimentalTime::class, ExperimentalFoundationApi::class)
@Composable
fun CardDetailDialog(
    card: DeckCard,
    boardLabels: List<Label>,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String?, duedate: String?, done: String?) -> Unit,
    onDelete: () -> Unit,
    /** Assigns ([assigned] = true) or removes a label; must be idempotent. */
    onSetLabel: (label: Label, assigned: Boolean) -> Unit,
    /** Creates a new label on the board; the callback gets it once the server assigned an id. */
    onCreateLabel: (title: String, color: String, onCreated: (Label) -> Unit) -> Unit = { _, _, _ -> },
    /** Board members, comments, attachments and activity. Null hides those parts. */
    extras: CardExtras? = null,
    /** Reports edit-mode transitions so the caller can pause background polling while a save
     * could otherwise race a poll's stale response. */
    onEditingChange: (Boolean) -> Unit = {},
) {
    var isEditing by remember(card.id) { mutableStateOf(false) }
    var focusTarget by remember(card.id) { mutableStateOf(EditFocus.None) }
    LaunchedEffect(isEditing) { onEditingChange(isEditing) }

    // The dialog's own idea of the card's current fields — seeded from `card`, then kept in
    // sync locally on save so edits show immediately without waiting for a fresh `card` prop.
    var title by remember(card.id) { mutableStateOf(card.title) }
    var description by remember(card.id) { mutableStateOf(card.description) }
    var duedate by remember(card.id) { mutableStateOf(card.duedate) }
    var done by remember(card.id) { mutableStateOf(card.done) }
    var labels by remember(card.id) { mutableStateOf(card.labels.distinctBy { it.id }) }
    var assignees by remember(card.id) { mutableStateOf(card.assignedUsers.mapNotNull { it.participant }.distinctBy { it.uid }) }

    var editTitle by remember(card.id) { mutableStateOf(card.title) }
    var editDescription by remember(card.id) { mutableStateOf(card.description.orEmpty()) }
    var editDuedate by remember(card.id) { mutableStateOf(card.duedate.orEmpty()) }
    var editDone by remember(card.id) { mutableStateOf(card.done != null) }
    var editLabelIds by remember(card.id) { mutableStateOf(card.labels.map { it.id }.toSet()) }
    var editAssigneeIds by remember(card.id) { mutableStateOf(assignees.mapNotNull { it.uid }.toSet()) }
    var showDeleteConfirm by remember(card.id) { mutableStateOf(false) }
    var creatingLabel by remember(card.id) { mutableStateOf(false) }
    var newLabelTitle by remember(card.id) { mutableStateOf("") }
    var newLabelColor by remember(card.id) { mutableStateOf(BOARD_COLOR_PALETTE.first()) }

    val titleFocus = remember { FocusRequester() }
    val descriptionFocus = remember { FocusRequester() }

    fun startEditing(target: EditFocus) {
        editTitle = title
        editDescription = description.orEmpty()
        editDuedate = duedate.orEmpty()
        editDone = done != null
        editLabelIds = labels.map { it.id }.toSet()
        editAssigneeIds = assignees.mapNotNull { it.uid }.toSet()
        focusTarget = target
        isEditing = true
    }

    // Focus the field the user clicked once edit mode has composed it.
    LaunchedEffect(isEditing, focusTarget) {
        if (!isEditing) return@LaunchedEffect
        withFrameNanos { }
        when (focusTarget) {
            EditFocus.Title -> runCatching { titleFocus.requestFocus() }
            EditFocus.Description -> runCatching { descriptionFocus.requestFocus() }
            EditFocus.None -> Unit
        }
    }

    fun save() {
        val newTitle = editTitle.trim()
        val newDescription = editDescription.trim().ifBlank { null }
        val newDuedate = editDuedate.trim().ifBlank { null }
        val newDone = if (editDone) (done ?: Clock.System.now().toString()) else null

        (editLabelIds - labels.map { it.id }.toSet()).forEach { id ->
            boardLabels.find { it.id == id }?.let { onSetLabel(it, true) }
        }
        (labels.map { it.id }.toSet() - editLabelIds).forEach { id ->
            labels.find { it.id == id }?.let { onSetLabel(it, false) }
        }

        extras?.let { ex ->
            val before = assignees.mapNotNull { it.uid }.toSet()
            (editAssigneeIds - before).forEach { uid -> ex.boardUsers.find { it.uid == uid }?.let { ex.setAssignee(it, true) } }
            (before - editAssigneeIds).forEach { uid -> assignees.find { it.uid == uid }?.let { ex.setAssignee(it, false) } }
            assignees = ex.boardUsers.filter { it.uid in editAssigneeIds } +
                assignees.filter { it.uid in editAssigneeIds && ex.boardUsers.none { b -> b.uid == it.uid } }
        }

        title = newTitle
        description = newDescription
        duedate = newDuedate
        done = newDone
        labels = boardLabels.filter { it.id in editLabelIds }
        isEditing = false

        onSave(newTitle, newDescription, newDuedate, newDone)
    }

    KDialog(
        onDismissRequest = onDismiss,
        actions = if (isEditing) {
            {
                KButton(onClick = { isEditing = false }, variant = KButtonVariant.Text) { KText(KompoundTheme.strings.cancel) }
                KButton(onClick = { save() }, enabled = editTitle.isNotBlank()) { KText(KompoundTheme.strings.save) }
            }
        } else {
            null
        },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (isEditing) {
                KTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f).focusRequester(titleFocus),
                )
            } else {
                // Clicking the title switches to edit mode with the title focused.
                KText(
                    title,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { startEditing(EditFocus.Title) }
                        .padding(vertical = 4.dp),
                )
                KIconButton(onClick = { startEditing(EditFocus.None) }, contentDescription = tr("Edit card")) {
                    KIcon(KIcons.Edit, contentDescription = null)
                }
                KIconButton(onClick = { showDeleteConfirm = true }, contentDescription = tr("Delete card")) {
                    KIcon(KIcons.Delete, contentDescription = null, tint = DeckError)
                }
                KIconButton(onClick = onDismiss, contentDescription = KompoundTheme.strings.close) {
                    KIcon(KIcons.Close, contentDescription = null)
                }
            }
        }

        if (isEditing) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KCheckbox(checked = editDone, onCheckedChange = { editDone = it }, label = { KText(tr("Done")) })

                KDateField(
                    value = runCatching { Instant.parse(editDuedate).toEpochMilliseconds() }.getOrNull(),
                    onValueChange = { millis ->
                        editDuedate = millis?.let { Instant.fromEpochMilliseconds(it).toString() }.orEmpty()
                    },
                    label = tr("Due date"),
                    modifier = Modifier.fillMaxWidth(),
                )

                KMarkdownField(
                    value = editDescription,
                    onValueChange = { editDescription = it },
                    label = tr("Description (Markdown)"),
                    minLines = 6,
                    maxLines = 16,
                    modifier = Modifier.fillMaxWidth().focusRequester(descriptionFocus),
                )

                KText(tr("Labels"), style = Style { contentColor(DeckTextSecondary) })
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    boardLabels.forEach { label ->
                        val selected = editLabelIds.contains(label.id)
                        LabelChip(
                            label = label,
                            selected = selected,
                            onClick = {
                                editLabelIds = if (selected) editLabelIds - label.id else editLabelIds + label.id
                            },
                        )
                    }
                    // "+ New label" opens the inline creator below.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(width = 1.dp, color = DeckBorder, shape = RoundedCornerShape(6.dp))
                            .clickable { creatingLabel = !creatingLabel }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        KText(
                            if (creatingLabel) tr("Cancel new label") else tr("+ New label"),
                            textStyle = MaterialTheme.typography.labelSmall,
                            style = Style { contentColor(DeckAccent) },
                        )
                    }
                }

                if (creatingLabel) {
                    fun addLabel() {
                        val name = newLabelTitle.trim()
                        if (name.isEmpty()) return
                        onCreateLabel(name, newLabelColor) { created ->
                            // Select the new label for this card right away.
                            created.id?.let { editLabelIds = editLabelIds + it }
                        }
                        newLabelTitle = ""
                        creatingLabel = false
                    }
                    KTextField(
                        value = newLabelTitle,
                        onValueChange = { newLabelTitle = it },
                        label = tr("New label name"),
                        keyboardActions = KeyboardActions(onDone = { addLabel() }),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BOARD_COLOR_PALETTE.forEach { hex ->
                            val swatch = parseBoardColor(hex)
                            val chosen = hex == newLabelColor
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (chosen) 3.dp else 0.dp,
                                        color = if (chosen) DeckTextPrimary else Color.Transparent,
                                        shape = CircleShape,
                                    )
                                    .clickable { newLabelColor = hex },
                            )
                        }
                    }
                    KButton(onClick = { addLabel() }, enabled = newLabelTitle.isNotBlank(), variant = KButtonVariant.Tonal) {
                        KText(tr("Add label"))
                    }
                }

                val members = extras?.boardUsers.orEmpty()
                if (members.isNotEmpty()) {
                    KText(tr("Assigned to"), style = Style { contentColor(DeckTextSecondary) })
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        members.forEach { user ->
                            val uid = user.uid ?: return@forEach
                            val selected = uid in editAssigneeIds
                            KChip(
                                label = user.name,
                                selected = selected,
                                onClick = { editAssigneeIds = if (selected) editAssigneeIds - uid else editAssigneeIds + uid },
                                leading = { UserAvatar(user) },
                            )
                        }
                    }
                }
            }
        } else {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    duedate?.let { due ->
                        val overdue = (card.overdue ?: 0) > 0
                        MetaChip(KIcons.Calendar, due.take(10), if (overdue) DeckError else DeckTextSecondary)
                    }
                    if (done != null) {
                        MetaChip(KIcons.Check, tr("Done"), DeckSuccess)
                    }
                    card.owner?.displayname?.let { owner ->
                        KText(tr("Owner: {name}", "name" to owner), style = Style { contentColor(DeckTextTertiary) })
                    }
                }

                if (labels.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        labels.forEach { label -> LabelChip(label, selected = true, onClick = null) }
                    }
                }

                if (assignees.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        assignees.forEach { user ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                UserAvatar(user)
                                KText(user.name)
                            }
                        }
                    }
                }

                // Rendered as Markdown (Deck descriptions are Markdown). Clicking the text — but
                // not a link inside it — switches to edit mode with the description focused.
                val currentDescription = description
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { startEditing(EditFocus.Description) },
                ) {
                    if (!currentDescription.isNullOrBlank()) {
                        KMarkdown(currentDescription, selectable = false, modifier = Modifier.fillMaxWidth())
                    } else {
                        KText(tr("No description — click to add one"), style = Style { contentColor(DeckTextTertiary) })
                    }
                }

                if (extras != null) {
                    AttachmentsSection(extras)
                    CommentsSection(extras)
                    ActivitySection(extras)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = tr("Delete card?"),
            message = tr(
                "\"{title}\" will be removed from the board. It's moved to Nextcloud Deck's trash, " +
                    "not permanently deleted — restore it from Deck's web UI if needed.",
                "title" to title,
            ),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
                onDismiss()
            },
        )
    }
}

/** A label pill in the label's color: filled when [selected], outlined otherwise. Clickable when [onClick] is set. */
@Composable
private fun LabelChip(label: Label, selected: Boolean, onClick: (() -> Unit)?) {
    val color = parseBoardColor(label.color)
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) color else Color.Transparent)
            .border(width = 1.dp, color = color, shape = shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        KText(
            label.title?.ifBlank { null } ?: tr("(no name)"),
            textStyle = MaterialTheme.typography.labelSmall,
            style = Style { contentColor(if (selected) onLabelColor(color) else color) },
        )
    }
}

@Composable
private fun MetaChip(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        KIcon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        KText(text, textStyle = MaterialTheme.typography.bodySmall, style = Style { contentColor(color) })
    }
}
