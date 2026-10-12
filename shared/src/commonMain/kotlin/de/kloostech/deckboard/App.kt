package de.kloostech.deckboard

import de.kloostech.deckboard.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.kloostech.deckboard.data.DeckCard
import de.kloostech.deckboard.data.DeckSettingsStore
import de.kloostech.deckboard.data.DeckUser
import de.kloostech.deckboard.ui.ActivityDialog
import de.kloostech.deckboard.ui.CardExtras
import de.kloostech.deckboard.ui.ShareBoardDialog
import de.kloostech.deckboard.ui.BoardScreen
import de.kloostech.deckboard.ui.CardDetailDialog
import de.kloostech.deckboard.ui.DeckBackground
import de.kloostech.deckboard.ui.DeckTheme
import de.kloostech.deckboard.ui.SettingsScreen
import de.kloostech.deckboard.ui.Sidebar
import de.kloostech.deckboard.ui.TextInputDialog

/** Below this width there's no room for sidebar + board side by side — go single-pane. */
private val COMPACT_WIDTH_BREAKPOINT = 600.dp

@Composable
@Preview
fun App() {
    val scope = rememberCoroutineScope()
    val viewModel = remember { AppViewModel(scope, DeckSettingsStore()) }
    App(viewModel)
}

/**
 * Overload taking an externally-owned [AppViewModel]. Lets a platform entry point (e.g. the
 * desktop app's window chrome) share one instance with UI it hosts outside this composable,
 * such as a native menu bar.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    DeckTheme {
        val state by viewModel.state.collectAsState()
        // Only the id is kept; the card itself is looked up from state so the dialog always sees the
        // current version (labels added or changed since it was opened, a poll refresh, ...).
        var selectedCardId by remember { mutableStateOf<Int?>(null) }
        var showNewBoardDialog by remember { mutableStateOf(false) }
        var showShareDialog by remember { mutableStateOf(false) }
        var showActivityDialog by remember { mutableStateOf(false) }
        // Single-pane (phone) navigation: which pane is showing. Irrelevant in wide layout,
        // where both panes are always visible side by side.
        var showingBoardOnCompact by remember { mutableStateOf(false) }

        // background() before safeDrawingPadding() so the dark background still bleeds full
        // edge-to-edge under the system bars — only the content inside gets inset from them.
        BoxWithConstraints(modifier = modifier.fillMaxSize().background(DeckBackground).safeDrawingPadding()) {
            val compact = maxWidth < COMPACT_WIDTH_BREAKPOINT

            BackHandler(enabled = compact && showingBoardOnCompact) {
                showingBoardOnCompact = false
            }

            if (compact) {
                if (showingBoardOnCompact) {
                    BoardScreen(
                        board = state.selectedBoard,
                        stacks = state.stacks,
                        loading = state.stacksLoading,
                        error = state.stacksError,
                        onCardClick = { selectedCardId = it.id },
                        onMoveCardLocally = viewModel::moveCardLocally,
                        onCommitCardMove = viewModel::commitCardMove,
                        onAddCard = viewModel::openAddCardDialog,
                        onDeleteBoard = { boardId ->
                            viewModel.deleteBoard(boardId)
                            showingBoardOnCompact = false
                        },
                        onChangeBoardColor = viewModel::updateBoardColor,
                        onSetDoneColumn = viewModel::setDoneColumn,
                        actionError = state.actionError,
                        onDismissActionError = viewModel::dismissActionError,
                        missingColumns = state.missingDefaultColumns,
                        onAddMissingColumns = viewModel::addMissingColumns,
                        onBack = { showingBoardOnCompact = false },
                        stackVertically = state.verticalColumns,
                        onRefresh = viewModel::refresh,
                        onShareBoard = { showShareDialog = true },
                        onShowActivity = { showActivityDialog = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Sidebar(
                        expanded = true,
                        compact = true,
                        boards = state.boards,
                        selectedBoardId = state.selectedBoardId,
                        loading = state.boardsLoading,
                        error = state.boardsError,
                        onToggle = {},
                        onSelectBoard = { id ->
                            viewModel.selectBoard(id)
                            showingBoardOnCompact = true
                        },
                        onOpenSettings = viewModel::openSettings,
                        onRefresh = viewModel::refresh,
                        onCreateBoard = { showNewBoardDialog = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(
                        expanded = state.sidebarExpanded,
                        boards = state.boards,
                        selectedBoardId = state.selectedBoardId,
                        loading = state.boardsLoading,
                        error = state.boardsError,
                        onToggle = viewModel::toggleSidebar,
                        onSelectBoard = viewModel::selectBoard,
                        onOpenSettings = viewModel::openSettings,
                        onRefresh = viewModel::refresh,
                        onCreateBoard = { showNewBoardDialog = true },
                    )

                    BoardScreen(
                        board = state.selectedBoard,
                        stacks = state.stacks,
                        loading = state.stacksLoading,
                        error = state.stacksError,
                        onCardClick = { selectedCardId = it.id },
                        onMoveCardLocally = viewModel::moveCardLocally,
                        onCommitCardMove = viewModel::commitCardMove,
                        onAddCard = viewModel::openAddCardDialog,
                        onDeleteBoard = viewModel::deleteBoard,
                        onChangeBoardColor = viewModel::updateBoardColor,
                        onSetDoneColumn = viewModel::setDoneColumn,
                        actionError = state.actionError,
                        onDismissActionError = viewModel::dismissActionError,
                        missingColumns = state.missingDefaultColumns,
                        onAddMissingColumns = viewModel::addMissingColumns,
                        stackVertically = state.verticalColumns,
                        onRefresh = viewModel::refresh,
                        onShareBoard = { showShareDialog = true },
                        onShowActivity = { showActivityDialog = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (showNewBoardDialog) {
            TextInputDialog(
                title = tr("New Board"),
                label = tr("Board title"),
                onDismiss = { showNewBoardDialog = false },
                onConfirm = { title ->
                    viewModel.createBoard(title)
                    showNewBoardDialog = false
                },
            )
        }

        if (state.showAddCardDialog) {
            TextInputDialog(
                title = tr("New Card"),
                label = tr("Card title"),
                onDismiss = viewModel::dismissAddCardDialog,
                onConfirm = { title ->
                    viewModel.createCard(title)
                    viewModel.dismissAddCardDialog()
                },
            )
        }

        if (state.settingsOpen) {
            SettingsScreen(
                connection = state.connection,
                canDismiss = state.connection.isConfigured,
                onSave = viewModel::saveConnection,
                onDismiss = viewModel::dismissSettings,
                pollingEnabled = state.pollingEnabled,
                pollingIntervalSeconds = state.pollingIntervalSeconds,
                onSavePolling = viewModel::updatePollingSettings,
                defaultColumnTitles = state.defaultColumnTitles,
                onSaveDefaultColumns = viewModel::updateDefaultColumns,
                verticalColumns = state.verticalColumns,
                onSaveVerticalColumns = viewModel::updateVerticalColumns,
            )
        }

        val boardForDialogs = state.selectedBoard
        if (showShareDialog && boardForDialogs != null) {
            ShareBoardDialog(
                board = boardForDialogs,
                onSearch = viewModel::searchSharees,
                onAdd = { viewModel.addBoardShare(boardForDialogs.id, it) },
                onUpdate = { acl, edit, share, manage -> viewModel.updateBoardShare(boardForDialogs.id, acl, edit, share, manage) },
                onRemove = { viewModel.removeBoardShare(boardForDialogs.id, it) },
                onDismiss = { showShareDialog = false },
            )
        }
        if (showActivityDialog && boardForDialogs != null) {
            ActivityDialog(
                boardTitle = boardForDialogs.title,
                load = { viewModel.loadActivities() },
                onDismiss = { showActivityDialog = false },
            )
        }

        val selectedCard = selectedCardId?.let { id -> state.stacks.firstNotNullOfOrNull { st -> st.cards.firstOrNull { it.id == id } } }
        selectedCard?.let { card ->
            CardDetailDialog(
                card = card,
                boardLabels = state.selectedBoard?.labels.orEmpty(),
                onDismiss = {
                    selectedCardId = null
                    // Safety net: if the dialog was closed (X button, scrim tap, system back)
                    // while mid-edit rather than via Save/Cancel, make sure polling resumes.
                    viewModel.setEditingCard(false)
                },
                onSave = { title, description, duedate, done ->
                    viewModel.updateCard(card, title, description, duedate, done)
                },
                onDelete = { viewModel.deleteCard(card.id) },
                onSetLabel = { label, assigned -> viewModel.setCardLabel(card.id, label, assigned) },
                onCreateLabel = viewModel::createLabel,
                extras = remember(card.id, state.selectedBoard?.id, state.selectedBoard?.users, state.connection) {
                    object : CardExtras {
                        override val currentUser = state.connection.username
                        override val boardUsers = state.selectedBoard?.users.orEmpty()
                        override val cardWebUrl = state.selectedBoard?.let { "${state.connection.baseUrl}/index.php/apps/deck/#/board/${it.id}/card/${card.id}" }
                        override fun setAssignee(user: DeckUser, assigned: Boolean) = viewModel.setCardAssignee(card.id, user, assigned)
                        override suspend fun comments() = viewModel.loadComments(card.id)
                        override suspend fun postComment(message: String) = viewModel.postComment(card.id, message)
                        override suspend fun deleteComment(commentId: Int) = viewModel.deleteComment(card.id, commentId)
                        override suspend fun attachments() = viewModel.loadAttachments(card.id)
                        override suspend fun activities() = viewModel.loadActivities(card.id)
                    }
                },
                onEditingChange = viewModel::setEditingCard,
            )
        }
    }
}
