package de.kloostech.deckboard

import de.kloostech.deckboard.i18n.tr
import de.kloostech.deckboard.data.ActivityEntry
import de.kloostech.deckboard.data.AssignedUser
import de.kloostech.deckboard.data.Board
import de.kloostech.deckboard.data.BoardAcl
import de.kloostech.deckboard.data.CardAttachment
import de.kloostech.deckboard.data.CardComment
import de.kloostech.deckboard.data.DeckApiClient
import de.kloostech.deckboard.data.DeckCard
import de.kloostech.deckboard.data.DeckConnection
import de.kloostech.deckboard.data.DeckUser
import de.kloostech.deckboard.data.DeckSettingsStore
import de.kloostech.deckboard.data.DefaultColumns
import de.kloostech.deckboard.data.Label
import de.kloostech.deckboard.data.MIN_POLL_INTERVAL_SECONDS
import de.kloostech.deckboard.data.PollingSettings
import de.kloostech.deckboard.data.Sharee
import de.kloostech.deckboard.data.Stack
import de.kloostech.deckboard.ui.BOARD_COLOR_PALETTE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Boards the server has archived or soft-deleted never belong in the sidebar. */
private fun List<Board>.visibleOnly(): List<Board> = filterNot { it.archived }.filter { it.deletedAt == 0L }

private fun randomBoardColor(): String = BOARD_COLOR_PALETTE.random()

data class AppUiState(
    val connection: DeckConnection = DeckConnection(),
    val sidebarExpanded: Boolean = true,
    val settingsOpen: Boolean = false,
    val boards: List<Board> = emptyList(),
    val boardsLoading: Boolean = false,
    val boardsError: String? = null,
    val selectedBoardId: Int? = null,
    /** Drives the "New Card" dialog. Lives here (not local Compose state) so the desktop menu
     * bar / keyboard shortcut — outside the board's composable subtree — can open it too. */
    val showAddCardDialog: Boolean = false,
    val stacks: List<Stack> = emptyList(),
    val stacksLoading: Boolean = false,
    val stacksError: String? = null,
    /** Set when a card move/edit/label toggle fails server-side after applying locally; the
     * local state then gets silently resynced from the server, so without this the user would
     * see their edit "disappear" on the next refresh with no indication why. */
    val actionError: String? = null,
    val pollingEnabled: Boolean = false,
    val pollingIntervalSeconds: Int = 30,
    /** On small screens, show a board's columns stacked vertically instead of side by side. */
    val verticalColumns: Boolean = false,
    /** Column titles auto-created, in order, on every new board. */
    val defaultColumnTitles: List<String> = emptyList(),
    /** True while a card detail dialog has its edit form open. The poll loop skips ticks while
     * this is set so a background refresh can't race an in-flight save with stale server data. */
    val isEditingCard: Boolean = false,
) {
    val selectedBoard: Board? get() = boards.find { it.id == selectedBoardId }

    /** Configured default columns the current board doesn't have yet. Only meaningful once a
     * board is selected and its stacks have actually loaded — check [stacksLoading] too, since
     * `stacks` is transiently empty while a different board's stacks are still in flight. */
    val missingDefaultColumns: List<String>
        get() = defaultColumnTitles.filter { title ->
            stacks.none { it.title.trim().equals(title.trim(), ignoreCase = true) }
        }
}

class AppViewModel(
    private val scope: CoroutineScope,
    private val settingsStore: DeckSettingsStore,
) {
    private val _state = MutableStateFlow(AppUiState(connection = settingsStore.connection.value))
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var apiClient: DeckApiClient? = null
    private var loadJob: Job? = null
    private var pollingJob: Job? = null

    init {
        val connection = settingsStore.connection.value
        val polling = settingsStore.polling.value
        _state.update {
            it.copy(
                settingsOpen = !connection.isConfigured,
                pollingEnabled = polling.enabled,
                pollingIntervalSeconds = polling.intervalSeconds,
                defaultColumnTitles = settingsStore.defaultColumns.value.titles,
                verticalColumns = settingsStore.verticalColumns.value,
            )
        }
        if (connection.isConfigured) {
            rebuildClient(connection)
            loadBoards()
        }
        restartPolling()
    }

    fun toggleSidebar() {
        _state.update { it.copy(sidebarExpanded = !it.sidebarExpanded) }
    }

    fun openSettings() {
        _state.update { it.copy(settingsOpen = true) }
    }

    fun dismissSettings() {
        if (_state.value.connection.isConfigured) {
            _state.update { it.copy(settingsOpen = false) }
        }
    }

    fun saveConnection(connection: DeckConnection) {
        settingsStore.save(connection)
        _state.update {
            it.copy(
                connection = connection,
                settingsOpen = false,
                boards = emptyList(),
                stacks = emptyList(),
                selectedBoardId = null,
                boardsError = null,
            )
        }
        rebuildClient(connection)
        loadBoards()
    }

    fun selectBoard(boardId: Int) {
        if (_state.value.selectedBoardId == boardId) return
        _state.update { it.copy(selectedBoardId = boardId, stacks = emptyList(), stacksError = null) }
        loadStacks(boardId)
    }

    fun refresh() {
        loadBoards()
        _state.value.selectedBoardId?.let { loadStacks(it) }
    }

    /** Opens the "New Card" dialog. No-op with no board selected — the shortcut/menu item
     * should be disabled then, but guard here too in case it fires anyway. */
    fun openAddCardDialog() {
        if (_state.value.selectedBoardId == null) return
        _state.update { it.copy(showAddCardDialog = true) }
    }

    fun dismissAddCardDialog() {
        _state.update { it.copy(showAddCardDialog = false) }
    }

    fun dismissActionError() {
        _state.update { it.copy(actionError = null) }
    }

    fun updatePollingSettings(enabled: Boolean, intervalSeconds: Int) {
        val clamped = intervalSeconds.coerceAtLeast(MIN_POLL_INTERVAL_SECONDS)
        settingsStore.savePolling(PollingSettings(enabled, clamped))
        _state.update { it.copy(pollingEnabled = enabled, pollingIntervalSeconds = clamped) }
        restartPolling()
    }

    fun updateVerticalColumns(enabled: Boolean) {
        settingsStore.saveVerticalColumns(enabled)
        _state.update { it.copy(verticalColumns = enabled) }
    }

    /** Card detail dialog reports its edit-mode transitions here so the poll loop can pause. */
    fun setEditingCard(editing: Boolean) {
        _state.update { it.copy(isEditingCard = editing) }
    }

    fun updateDefaultColumns(titles: List<String>) {
        val normalized = titles.map { it.trim() }.filter { it.isNotEmpty() }
        settingsStore.saveDefaultColumns(DefaultColumns(normalized))
        _state.update { it.copy(defaultColumnTitles = normalized) }
    }

    /**
     * Live-updates local state while a card is being dragged: moves [draggedCard] into
     * [targetStackId], positioned just before [beforeCardId] (or at the end if null).
     * Purely local — no network call, safe to call on every hover-target change.
     */
    fun moveCardLocally(draggedCard: DeckCard, targetStackId: Int, beforeCardId: Int?) {
        // Hovering the dragged card's own slot is a no-op — must not fall through to
        // "beforeCardId not found" (which means append-at-end for a real empty-drop).
        if (beforeCardId == draggedCard.id) return

        _state.update { s ->
            val newStacks = s.stacks.map { stack ->
                if (stack.id != targetStackId) {
                    if (stack.cards.any { it.id == draggedCard.id }) {
                        stack.copy(cards = stack.cards.filterNot { it.id == draggedCard.id })
                    } else {
                        stack
                    }
                } else {
                    val withoutDragged = stack.cards.filterNot { it.id == draggedCard.id }.toMutableList()
                    val targetIndex = beforeCardId?.let { id -> withoutDragged.indexOfFirst { it.id == id } } ?: -1
                    val insertAt = if (targetIndex == -1) withoutDragged.size else targetIndex.coerceAtMost(withoutDragged.size)
                    withoutDragged.add(insertAt, draggedCard.copy(stackId = targetStackId))
                    stack.copy(cards = withoutDragged)
                }
            }
            s.copy(stacks = newStacks)
        }
    }

    /**
     * Persists wherever [originalCard] currently sits in local state (after any number of
     * [moveCardLocally] calls during a drag).
     */
    fun commitCardMove(originalCard: DeckCard) {
        val boardId = _state.value.selectedBoardId ?: return
        val client = apiClient ?: return
        val currentStack = _state.value.stacks.firstOrNull { st -> st.cards.any { it.id == originalCard.id } } ?: return
        val order = currentStack.cards.indexOfFirst { it.id == originalCard.id }
        if (order == -1) return

        scope.launch {
            runCatching {
                // The /reorder endpoint's {stackId} URL segment is always the *destination*
                // stack, for both a same-column reorder and a cross-column move.
                client.reorderCard(
                    boardId = boardId,
                    targetStackId = currentStack.id,
                    cardId = originalCard.id,
                    order = order,
                )
            }.onFailure { error ->
                _state.update { it.copy(actionError = tr("Failed to move card: {error}", "error" to error.message)) }
                loadStacks(boardId)
            }
        }
    }

    /** Edits [card]'s title, description, due date, and done status. */
    fun updateCard(card: DeckCard, title: String, description: String?, duedate: String?, done: String?) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return
        val boardId = _state.value.selectedBoardId ?: return
        val client = apiClient ?: return
        val stackId = card.stackId ?: return

        // Apply the edit to the card as it is in state *now*, not to the (possibly stale) [card]
        // the caller opened — otherwise labels assigned meanwhile would be overwritten locally.
        _state.update { s ->
            s.copy(
                stacks = s.stacks.map { stack ->
                    if (stack.id != stackId) stack
                    else stack.copy(
                        cards = stack.cards.map {
                            if (it.id == card.id) it.copy(title = trimmedTitle, description = description, duedate = duedate, done = done) else it
                        },
                    )
                },
            )
        }

        scope.launch {
            runCatching {
                client.updateCard(
                    boardId = boardId,
                    stackId = stackId,
                    cardId = card.id,
                    title = trimmedTitle,
                    description = description,
                    type = card.type ?: "plain",
                    order = card.order ?: 0,
                    duedate = duedate,
                    archived = card.archived,
                    done = done,
                )
            }.onFailure { error ->
                _state.update { it.copy(actionError = tr("Failed to save card: {error}", "error" to error.message)) }
                loadStacks(boardId)
            }
        }
    }

    /** Makes [label] assigned (or not) on the card. Idempotent: does nothing, and sends nothing to
     * the server, when the card already is in the requested state. */
    fun setCardLabel(cardId: Int, label: Label, assigned: Boolean) {
        val labelId = label.id ?: return
        val boardId = _state.value.selectedBoardId ?: return
        val client = apiClient ?: return
        val stack = _state.value.stacks.firstOrNull { st -> st.cards.any { it.id == cardId } } ?: return
        val currentCard = stack.cards.first { it.id == cardId }
        val hasLabel = currentCard.labels.any { it.id == label.id }
        if (hasLabel == assigned) return
        val updatedLabels = if (hasLabel) currentCard.labels.filterNot { it.id == label.id } else currentCard.labels + label

        _state.update { s ->
            s.copy(
                stacks = s.stacks.map { st ->
                    if (st.id != stack.id) st
                    else st.copy(cards = st.cards.map { if (it.id == cardId) it.copy(labels = updatedLabels) else it })
                },
            )
        }

        scope.launch {
            runCatching {
                if (hasLabel) {
                    client.removeLabel(boardId, stack.id, cardId, labelId)
                } else {
                    client.assignLabel(boardId, stack.id, cardId, labelId)
                }
            }.onFailure { error ->
                _state.update { it.copy(actionError = tr("Failed to update label: {error}", "error" to error.message)) }
                loadStacks(boardId)
            }
        }
    }

    /**
     * Creates a new label on the current board and adds it to the local board state. [onCreated]
     * receives it (with its server id) so the caller can select it right away; a label with the
     * same title (case-insensitive) is reused instead of creating a duplicate.
     */
    fun createLabel(title: String, color: String, onCreated: (Label) -> Unit = {}) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val boardId = _state.value.selectedBoardId ?: return
        val client = apiClient ?: return
        _state.value.selectedBoard?.labels
            ?.firstOrNull { it.title?.trim().equals(trimmed, ignoreCase = true) }
            ?.let { onCreated(it); return }

        scope.launch {
            runCatching { client.createLabel(boardId, trimmed, color) }
                .onSuccess { label ->
                    _state.update { s ->
                        s.copy(boards = s.boards.map { if (it.id == boardId) it.copy(labels = it.labels + label) else it })
                    }
                    onCreated(label)
                }
                .onFailure { error ->
                    _state.update { it.copy(actionError = tr("Failed to create label: {error}", "error" to error.message)) }
                }
        }
    }

    // --- Sharing -----------------------------------------------------------------------------

    /** Users and groups matching [query] that the board can be shared with; empty on any failure. */
    suspend fun searchSharees(query: String): List<Sharee> =
        apiClient?.let { c -> runCatching { c.searchSharees(query.trim()) }.getOrDefault(emptyList()) }.orEmpty()

    fun addBoardShare(boardId: Int, sharee: Sharee) = changeBoardShares(boardId, tr("Failed to share board")) { c ->
        c.addAcl(boardId, sharee.shareType, sharee.shareWith, edit = true)
    }

    fun updateBoardShare(boardId: Int, acl: BoardAcl, edit: Boolean, share: Boolean, manage: Boolean) =
        changeBoardShares(boardId, tr("Failed to update sharing")) { c -> c.updateAcl(boardId, acl, edit, share, manage) }

    fun removeBoardShare(boardId: Int, acl: BoardAcl) = changeBoardShares(boardId, tr("Failed to remove share")) { c -> c.deleteAcl(boardId, acl.id) }

    /** Runs a share change, then reloads the board's members and shares so the UI shows the server's truth. */
    private fun changeBoardShares(boardId: Int, failureMessage: String, change: suspend (DeckApiClient) -> Unit) {
        val client = apiClient ?: return
        scope.launch {
            runCatching {
                change(client)
                client.getBoard(boardId)
            }.onSuccess { fresh ->
                _state.update { s ->
                    s.copy(boards = s.boards.map { if (it.id == boardId) it.copy(acl = fresh.acl, users = fresh.users) else it })
                }
            }.onFailure { error ->
                _state.update { it.copy(actionError = "$failureMessage: ${error.message}") }
            }
        }
    }

    // --- Card assignees ----------------------------------------------------------------------

    /** Idempotently assigns ([assigned] = true) or unassigns [user]; optimistic like [setCardLabel]. */
    fun setCardAssignee(cardId: Int, user: DeckUser, assigned: Boolean) {
        val uid = user.uid ?: return
        val boardId = _state.value.selectedBoardId ?: return
        val client = apiClient ?: return
        val stack = _state.value.stacks.firstOrNull { st -> st.cards.any { it.id == cardId } } ?: return
        val currentCard = stack.cards.first { it.id == cardId }
        val has = currentCard.assignedUsers.any { it.participant?.uid == uid }
        if (has == assigned) return
        val updated = if (has) {
            currentCard.assignedUsers.filterNot { it.participant?.uid == uid }
        } else {
            currentCard.assignedUsers + AssignedUser(participant = user)
        }
        _state.update { s ->
            s.copy(
                stacks = s.stacks.map { st ->
                    if (st.id != stack.id) st
                    else st.copy(cards = st.cards.map { if (it.id == cardId) it.copy(assignedUsers = updated) else it })
                },
            )
        }
        scope.launch {
            runCatching {
                if (assigned) client.assignUser(boardId, stack.id, cardId, uid) else client.unassignUser(boardId, stack.id, cardId, uid)
            }.onFailure { error ->
                _state.update { it.copy(actionError = tr("Failed to update assignee: {error}", "error" to error.message)) }
                loadStacks(boardId)
            }
        }
    }

    // --- Card comments, attachments and activity (loaded on demand by the dialogs) ------------

    suspend fun loadComments(cardId: Int): Result<List<CardComment>> = apiResult { it.getComments(cardId) }

    suspend fun postComment(cardId: Int, message: String): Result<CardComment> = apiResult { it.createComment(cardId, message.trim()) }

    suspend fun deleteComment(cardId: Int, commentId: Int): Result<Unit> = apiResult { it.deleteComment(cardId, commentId) }

    suspend fun loadAttachments(cardId: Int): Result<List<CardAttachment>> {
        val boardId = _state.value.selectedBoardId ?: return Result.success(emptyList())
        val stackId = _state.value.stacks.firstOrNull { st -> st.cards.any { it.id == cardId } }?.id ?: return Result.success(emptyList())
        return apiResult { it.getAttachments(boardId, stackId, cardId) }
    }

    /** Activity of the open board, or of one card when [cardId] is given. */
    suspend fun loadActivities(cardId: Int? = null): Result<List<ActivityEntry>> {
        val boardId = _state.value.selectedBoardId ?: return Result.success(emptyList())
        return apiResult { it.getActivities(boardId, cardId) }
    }

    private suspend fun <T> apiResult(call: suspend (DeckApiClient) -> T): Result<T> {
        val client = apiClient ?: return Result.failure(IllegalStateException(tr("Not connected")))
        return runCatching { call(client) }.also { r ->
            // Never swallow cancellation: the caller's scope is going away.
            r.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
    }

    /** Designates [stackId] as the board's done column, or unsets it. No optimistic local
     * update — the server bulk-marks cards done and clears other stacks' flags as a side
     * effect, so a full reload afterward is simpler and safer than predicting that diff here. */
    fun setDoneColumn(stackId: Int, isDone: Boolean) {
        val boardId = _state.value.selectedBoardId ?: return
        val client = apiClient ?: return

        scope.launch {
            runCatching { client.setDoneStack(boardId, stackId, isDone) }
                .onSuccess { loadStacks(boardId) }
                .onFailure { error ->
                    _state.update { it.copy(actionError = tr("Failed to set done column: {error}", "error" to error.message)) }
                }
        }
    }

    /** Deletes [cardId] from whichever stack it's currently in. */
    fun deleteCard(cardId: Int) {
        val boardId = _state.value.selectedBoardId ?: return
        val client = apiClient ?: return
        val stack = _state.value.stacks.firstOrNull { st -> st.cards.any { it.id == cardId } } ?: return

        _state.update { s ->
            s.copy(
                stacks = s.stacks.map { st ->
                    if (st.id != stack.id) st else st.copy(cards = st.cards.filterNot { it.id == cardId })
                },
            )
        }

        scope.launch {
            runCatching { client.deleteCard(boardId, stack.id, cardId) }
                .onFailure { error ->
                    _state.update { it.copy(actionError = tr("Failed to delete card: {error}", "error" to error.message)) }
                    loadStacks(boardId)
                }
        }
    }

    /**
     * Creates a new board, adds it to the sidebar, then creates the configured default columns
     * on it (if any) and selects it so the user lands straight on the populated board.
     */
    fun createBoard(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val client = apiClient ?: return

        scope.launch {
            runCatching { client.createBoard(trimmed, randomBoardColor()) }
                .onSuccess { board ->
                    _state.update { it.copy(boards = it.boards + board, boardsError = null) }

                    val columnTitles = _state.value.defaultColumnTitles
                    if (columnTitles.isNotEmpty()) {
                        runCatching { createColumns(client, board.id, columnTitles, startOrder = 0) }
                            .onFailure { error ->
                                _state.update { it.copy(actionError = tr("Failed to create default columns: {error}", "error" to error.message)) }
                            }
                    }

                    selectBoard(board.id)
                }
                .onFailure { error ->
                    _state.update { it.copy(boardsError = error.message ?: tr("Failed to create board")) }
                }
        }
    }

    /** Creates on the current board whichever configured default columns it's still missing
     * (matched by title, case-insensitive) — the small "+ Add missing columns" affordance. */
    fun addMissingColumns() {
        val boardId = _state.value.selectedBoardId ?: return
        val client = apiClient ?: return
        val missing = _state.value.missingDefaultColumns
        if (missing.isEmpty()) return
        val startOrder = _state.value.stacks.size

        scope.launch {
            runCatching { createColumns(client, boardId, missing, startOrder) }
                .onSuccess { loadStacks(boardId) }
                .onFailure { error ->
                    _state.update { it.copy(actionError = tr("Failed to add columns: {error}", "error" to error.message)) }
                }
        }
    }

    private suspend fun createColumns(client: DeckApiClient, boardId: Int, titles: List<String>, startOrder: Int) {
        titles.forEachIndexed { index, title -> client.createStack(boardId, title, startOrder + index) }
    }

    /** Creates a card in the board's first (leftmost) stack, placed at the top of that stack. */
    fun createCard(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val boardId = _state.value.selectedBoardId ?: return
        val client = apiClient ?: return
        val firstStack = _state.value.stacks.firstOrNull() ?: return

        scope.launch {
            runCatching { client.createCard(boardId, firstStack.id, trimmed) }
                .onSuccess { card ->
                    _state.update { s ->
                        s.copy(
                            stacksError = null,
                            stacks = s.stacks.map { st ->
                                if (st.id == firstStack.id) st.copy(cards = listOf(card) + st.cards) else st
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(stacksError = error.message ?: tr("Failed to create card")) }
                }
        }
    }

    /** Deletes [boardId] on the server and drops it from the sidebar. Clears the current
     * selection (and its stacks) if the deleted board was the one open. */
    fun deleteBoard(boardId: Int) {
        val client = apiClient ?: return

        scope.launch {
            runCatching { client.deleteBoard(boardId) }
                .onSuccess {
                    _state.update { s ->
                        val wasSelected = s.selectedBoardId == boardId
                        s.copy(
                            boards = s.boards.filterNot { it.id == boardId },
                            selectedBoardId = if (wasSelected) null else s.selectedBoardId,
                            stacks = if (wasSelected) emptyList() else s.stacks,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(boardsError = error.message ?: tr("Failed to delete board")) }
                }
        }
    }

    /** Changes [boardId]'s color dot to one of [de.kloostech.deckboard.ui.BOARD_COLOR_PALETTE]. */
    fun updateBoardColor(boardId: Int, color: String) {
        val client = apiClient ?: return
        val board = _state.value.boards.firstOrNull { it.id == boardId } ?: return
        if (board.color.equals(color, ignoreCase = true)) return

        _state.update { s ->
            s.copy(boards = s.boards.map { if (it.id == boardId) it.copy(color = color) else it })
        }

        scope.launch {
            runCatching { client.updateBoard(boardId, board.title, color, board.archived) }
                .onFailure { error ->
                    _state.update { it.copy(boardsError = error.message ?: tr("Failed to change board color")) }
                    loadBoards()
                }
        }
    }

    private fun rebuildClient(connection: DeckConnection) {
        apiClient?.close()
        apiClient = DeckApiClient(connection)
    }

    private fun loadBoards() {
        val client = apiClient ?: return
        loadJob?.cancel()
        loadJob = scope.launch {
            _state.update { it.copy(boardsLoading = true, boardsError = null) }
            runCatching { client.getBoards().visibleOnly() }
                .onSuccess { boards ->
                    _state.update { it.copy(boards = boards, boardsLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(boardsLoading = false, boardsError = error.message ?: tr("Failed to load boards"))
                    }
                }
        }
    }

    /** (Re)starts the background poll loop; cancels any loop already running. Safe to call
     * whenever polling settings change so a new interval/toggle takes effect immediately
     * instead of waiting out the previous delay. */
    private fun restartPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (true) {
                delay(_state.value.pollingIntervalSeconds.coerceAtLeast(MIN_POLL_INTERVAL_SECONDS) * 1000L)
                val current = _state.value
                if (!current.pollingEnabled || current.isEditingCard) continue
                pollQuietly(current.selectedBoardId)
            }
        }
    }

    /** Background refresh: no loading spinners, no clearing of existing errors — a failed poll
     * just skips this tick rather than disturbing whatever's currently on screen. */
    private suspend fun pollQuietly(selectedBoardId: Int?) {
        val client = apiClient ?: return

        runCatching { client.getBoards().visibleOnly() }
            .onSuccess { boards -> _state.update { it.copy(boards = boards) } }

        if (selectedBoardId != null) {
            runCatching { client.getStacks(selectedBoardId) }
                .onSuccess { stacks ->
                    _state.update {
                        // isEditingCard could have flipped true while this request was in
                        // flight (or the user switched boards) — re-check before applying.
                        if (it.selectedBoardId == selectedBoardId && !it.isEditingCard) {
                            it.copy(stacks = stacks.sortedBy { s -> s.order ?: 0 })
                        } else {
                            it
                        }
                    }
                }
        }
    }

    private fun loadStacks(boardId: Int) {
        val client = apiClient ?: return
        scope.launch {
            _state.update { it.copy(stacksLoading = true, stacksError = null) }
            runCatching { client.getStacks(boardId) }
                .onSuccess { stacks ->
                    _state.update {
                        if (it.selectedBoardId == boardId) {
                            it.copy(stacks = stacks.sortedBy { s -> s.order ?: 0 }, stacksLoading = false)
                        } else it
                    }
                }
                .onFailure { error ->
                    _state.update {
                        if (it.selectedBoardId == boardId) {
                            it.copy(stacksLoading = false, stacksError = error.message ?: tr("Failed to load stacks"))
                        } else it
                    }
                }
        }
    }
}
