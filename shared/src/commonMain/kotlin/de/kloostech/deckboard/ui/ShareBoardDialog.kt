package de.kloostech.deckboard.ui

import de.kloostech.deckboard.i18n.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.kloostech.deckboard.data.Board
import de.kloostech.deckboard.data.BoardAcl
import de.kloostech.deckboard.data.DeckUser
import de.kloostech.deckboard.data.Sharee
import kotlinx.coroutines.delay
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.chip.KChip
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.divider.KDivider
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.icon.KIcons
import tech.kloos.kompound.list.KListItem
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField
import tech.kloos.kompound.theme.KompoundTheme

private const val TYPE_USER = 0
private const val TYPE_GROUP = 1

/**
 * Who has access to a board, and (when the current user may manage the board) a search to share it with
 * more Nextcloud users or groups. Each share has Edit / Share / Manage switches; changes go to the server
 * right away and the list refreshes from it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareBoardDialog(
    board: Board,
    onSearch: suspend (query: String) -> List<Sharee>,
    onAdd: (Sharee) -> Unit,
    onUpdate: (acl: BoardAcl, edit: Boolean, share: Boolean, manage: Boolean) -> Unit,
    onRemove: (BoardAcl) -> Unit,
    onDismiss: () -> Unit,
) {
    val canManage = board.permissions?.manage == true
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Sharee>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // Debounced search; people already on the board are left out.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        searching = true
        val taken = board.acl.mapNotNull { a -> a.participant?.uid?.let { a.type to it } }.toSet() +
            setOfNotNull(board.owner?.uid?.let { TYPE_USER to it })
        results = onSearch(query).filter { (it.shareType to it.shareWith) !in taken }
        searching = false
    }

    KDialog(
        onDismissRequest = onDismiss,
        title = tr("Share \"{board}\"", "board" to board.title),
        actions = { KButton(onClick = onDismiss, variant = KButtonVariant.Text) { KText(KompoundTheme.strings.close) } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            board.owner?.let { owner ->
                KListItem(
                    headline = owner.name,
                    supporting = tr("Owner"),
                    leading = { UserAvatar(owner) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            board.acl.forEach { acl ->
                val who = acl.participant ?: DeckUser(uid = "?", displayname = tr("Unknown"))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KListItem(
                        headline = who.name,
                        supporting = if (acl.type == TYPE_GROUP) tr("Group") else who.uid,
                        leading = { UserAvatar(who) },
                        trailing = if (canManage) {
                            { KIconButton(onClick = { onRemove(acl) }, contentDescription = tr("Remove {name}", "name" to who.name)) { KIcon(KIcons.Close, contentDescription = null) } }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        KChip(tr("Edit"), onClick = { onUpdate(acl, !acl.permissionEdit, acl.permissionShare, acl.permissionManage) }, selected = acl.permissionEdit, enabled = canManage)
                        KChip(tr("Share"), onClick = { onUpdate(acl, acl.permissionEdit, !acl.permissionShare, acl.permissionManage) }, selected = acl.permissionShare, enabled = canManage)
                        KChip(tr("Manage"), onClick = { onUpdate(acl, acl.permissionEdit, acl.permissionShare, !acl.permissionManage) }, selected = acl.permissionManage, enabled = canManage)
                    }
                }
            }

            if (board.acl.isEmpty()) {
                KText(tr("Not shared with anyone yet."), style = Style { contentColor(DeckTextTertiary) })
            }

            if (canManage) {
                KDivider()
                KTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = tr("Add a user or group"),
                    placeholder = tr("Search by name"),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (searching) {
                    KText(tr("Searching…"), style = Style { contentColor(DeckTextTertiary) })
                } else if (query.isNotBlank() && results.isEmpty()) {
                    KText(tr("No matching users or groups."), style = Style { contentColor(DeckTextTertiary) })
                }
                results.forEach { sharee ->
                    KListItem(
                        headline = sharee.label,
                        supporting = if (sharee.shareType == TYPE_GROUP) tr("Group") else sharee.shareWith,
                        leading = { UserAvatar(DeckUser(uid = sharee.shareWith, displayname = sharee.label)) },
                        trailing = { KIcon(KIcons.Add, contentDescription = tr("Add {name}", "name" to sharee.label)) },
                        onClick = {
                            onAdd(sharee)
                            query = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                KText(tr("You can't change who this board is shared with."), style = Style { contentColor(DeckTextTertiary) })
            }
        }
    }
}
