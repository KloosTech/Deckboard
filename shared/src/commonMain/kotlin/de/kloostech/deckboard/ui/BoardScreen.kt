package de.kloostech.deckboard.ui

import de.kloostech.deckboard.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.kloostech.deckboard.data.Board
import de.kloostech.deckboard.data.DeckCard
import de.kloostech.deckboard.data.Stack
import androidx.compose.foundation.style.Style
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.menu.KMenu
import tech.kloos.kompound.menu.KMenuItem
import tech.kloos.kompound.dnd.KDragAndDropContainer
import tech.kloos.kompound.dnd.KDragAndDropState
import tech.kloos.kompound.dnd.drag.KDropStrategy
import tech.kloos.kompound.dnd.drop.kDropTargetFallback
import tech.kloos.kompound.dnd.reorder.kReorderableItem
import tech.kloos.kompound.dnd.rememberKDragAndDropState
import tech.kloos.kompound.dnd.scroll.kDragAutoScroll

private val MAX_STACK_WIDTH = 300.dp
private val COMPACT_STACKS_BREAKPOINT = 500.dp

/** Vertically stacked columns stay this wide at most, so cards don't stretch across a tablet. */
private val MAX_VERTICAL_STACK_WIDTH = 640.dp

@Composable
fun BoardScreen(
    board: Board?,
    stacks: List<Stack>,
    loading: Boolean,
    error: String?,
    onCardClick: (DeckCard) -> Unit,
    onMoveCardLocally: (card: DeckCard, targetStackId: Int, beforeCardId: Int?) -> Unit,
    onCommitCardMove: (card: DeckCard) -> Unit,
    /** Requests the "New Card" dialog be shown; the dialog itself (and the actual create call)
     * lives above this composable so it can also be opened from outside the board (desktop
     * menu bar / keyboard shortcut). */
    onAddCard: () -> Unit,
    onDeleteBoard: (boardId: Int) -> Unit = {},
    onChangeBoardColor: (boardId: Int, color: String) -> Unit = { _, _ -> },
    /** Sets (or unsets) [Stack.isDoneColumn]. Cards dropped into the done column get marked
     * done automatically server-side — this just designates which column that is. */
    onSetDoneColumn: (stackId: Int, isDone: Boolean) -> Unit = { _, _ -> },
    actionError: String? = null,
    onDismissActionError: () -> Unit = {},
    /** Configured default columns this board doesn't have yet. Non-empty shows a small "+ Add
     * missing columns" affordance under the header. */
    missingColumns: List<String> = emptyList(),
    onAddMissingColumns: () -> Unit = {},
    /** Shows a back arrow in the header; used in single-pane (phone) layout to return to the
     * board list. Null hides it — desktop/tablet keep the sidebar always visible instead. */
    onBack: (() -> Unit)? = null,
    /** Lays the columns out below each other (one vertically scrolling page) instead of side by
     * side. Meant for small screens; the caller decides when it applies. */
    stackVertically: Boolean = false,
    /** Reloads the board list and the open board from the server (board menu → Refresh). */
    onRefresh: () -> Unit = {},
    /** Opens the sharing dialog for this board (board menu → Share…). */
    onShareBoard: () -> Unit = {},
    /** Opens the board's activity (board menu → Activity). */
    onShowActivity: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (board == null) {
        EmptyBoardState(modifier)
        return
    }

    val dndState = rememberKDragAndDropState<DeckCard>(reorderHysteresisDistance = 20.dp)
    val rowState = rememberLazyListState()
    val pageState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize().background(DeckBackground)) {
        BoardHeader(board, stacks, onBack, onDeleteBoard, onChangeBoardColor, onRefresh, onShareBoard, onShowActivity)

        if (actionError != null) {
            ActionErrorBanner(actionError, onDismissActionError)
        }

        if (!loading && missingColumns.isNotEmpty()) {
            MissingColumnsBanner(missingColumns, onAddMissingColumns)
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // On a phone, one column dominates the width with a peek of the next (Trello-style
            // horizontal paging); on wider screens columns keep a fixed, more compact width.
            val columnWidth = if (stackVertically) {
                // Full width minus the page's 20dp padding on each side, capped on wide screens.
                (maxWidth - 40.dp).coerceAtMost(MAX_VERTICAL_STACK_WIDTH)
            } else if (maxWidth < COMPACT_STACKS_BREAKPOINT) {
                (maxWidth - 56.dp).coerceAtLeast(220.dp)
            } else {
                MAX_STACK_WIDTH
            }

            when {
                loading && stacks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DeckAccent)
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error, color = DeckError)
                }
                stacks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tr("No columns on this board"), color = DeckTextTertiary)
                }
                else -> KDragAndDropContainer(state = dndState, modifier = Modifier.fillMaxSize()) {
                    if (stackVertically) {
                        LazyColumn(
                            state = pageState,
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .kDragAutoScroll(state = dndState, lazyListState = pageState),
                        ) {
                            itemsIndexed(stacks, key = { _, stack -> stack.id }) { index, stack ->
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                StackColumn(
                                    stack = stack,
                                    isFirstColumn = index == 0,
                                    columnWidth = columnWidth,
                                    vertical = true,
                                    dndState = dndState,
                                    onCardClick = onCardClick,
                                    onMoveCardLocally = onMoveCardLocally,
                                    onCommitCardMove = onCommitCardMove,
                                    onAddCard = onAddCard,
                                    onSetDoneColumn = onSetDoneColumn,
                                )
                                }
                            }
                        }
                    } else {
                        LazyRow(
                            state = rowState,
                            contentPadding = PaddingValues(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .kDragAutoScroll(state = dndState, lazyListState = rowState),
                        ) {
                            itemsIndexed(stacks, key = { _, stack -> stack.id }) { index, stack ->
                                StackColumn(
                                    stack = stack,
                                    isFirstColumn = index == 0,
                                    columnWidth = columnWidth,
                                    vertical = false,
                                    dndState = dndState,
                                    onCardClick = onCardClick,
                                    onMoveCardLocally = onMoveCardLocally,
                                    onCommitCardMove = onCommitCardMove,
                                    onAddCard = onAddCard,
                                    onSetDoneColumn = onSetDoneColumn,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyBoardState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(DeckBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Dashboard, contentDescription = null, tint = DeckTextTertiary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(tr("Select a board to get started"), color = DeckTextTertiary)
        }
    }
}

@Composable
private fun ActionErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeckError.copy(alpha = 0.15f))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            color = DeckError,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = tr("Dismiss"), tint = DeckError, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MissingColumnsBanner(missingColumns: List<String>, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeckAccent.copy(alpha = 0.12f))
            .clickable(onClick = onAdd)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = DeckAccent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (missingColumns.size > 1) tr("Add missing columns: {list}", "list" to missingColumns.joinToString(", "))
            else tr("Add missing column: {list}", "list" to missingColumns.joinToString(", ")),
            color = DeckAccent,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BoardHeader(
    board: Board,
    stacks: List<Stack>,
    onBack: (() -> Unit)?,
    onDeleteBoard: (Int) -> Unit,
    onChangeBoardColor: (Int, String) -> Unit,
    onRefresh: () -> Unit,
    onShareBoard: () -> Unit,
    onShowActivity: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var colorPickerExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = DeckBorder)
            .padding(start = if (onBack != null) 8.dp else 24.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back to boards"), tint = DeckTextSecondary)
                }
                Spacer(Modifier.width(4.dp))
            }
            Box {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(parseBoardColor(board.color))
                        .clickable { colorPickerExpanded = true },
                )
                DropdownMenu(expanded = colorPickerExpanded, onDismissRequest = { colorPickerExpanded = false }) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        BOARD_COLOR_PALETTE.forEach { hex ->
                            val selected = hex.equals(board.color, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(parseBoardColor(hex))
                                    .then(
                                        if (selected) {
                                            Modifier.border(width = 2.dp, color = DeckTextPrimary, shape = CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable {
                                        colorPickerExpanded = false
                                        onChangeBoardColor(board.id, hex)
                                    },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                board.title,
                color = DeckTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = tr("Board options"), tint = DeckTextSecondary)
                }
                KMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    KMenuItem(
                        text = tr("Refresh"),
                        leading = { KIcon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = { menuExpanded = false; onRefresh() },
                    )
                    KMenuItem(
                        text = tr("Share"),
                        leading = { KIcon(Icons.Filled.PersonAdd, contentDescription = null) },
                        onClick = { menuExpanded = false; onShareBoard() },
                    )
                    KMenuItem(
                        text = tr("Activity"),
                        leading = { KIcon(Icons.Filled.History, contentDescription = null) },
                        onClick = { menuExpanded = false; onShowActivity() },
                    )
                    KMenuItem(
                        text = tr("Delete board"),
                        leading = { KIcon(Icons.Filled.Delete, contentDescription = null) },
                        style = Style { contentColor(DeckError) },
                        onClick = { menuExpanded = false; showDeleteConfirm = true },
                    )
                }
            }
        }
        val cardCount = stacks.sumOf { it.cards.size }
        Text(
            listOf(
                if (stacks.size == 1) tr("1 column") else tr("{count} columns", "count" to stacks.size),
                if (cardCount == 1) tr("1 card") else tr("{count} cards", "count" to cardCount),
            ).joinToString(" · "),
            color = DeckTextTertiary,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            maxLines = 1,
            modifier = Modifier.padding(start = if (onBack != null) 42.dp else 26.dp, top = 2.dp),
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = tr("Delete board?"),
            message = tr(
                "\"{title}\" will be removed from your board list. It's moved to Nextcloud Deck's trash, " +
                    "not permanently deleted — restore it from Deck's web UI if needed.",
                "title" to board.title,
            ),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDeleteBoard(board.id)
            },
        )
    }
}

@Composable
private fun StackColumn(
    stack: Stack,
    isFirstColumn: Boolean,
    columnWidth: Dp,
    vertical: Boolean,
    dndState: KDragAndDropState<DeckCard>,
    onCardClick: (DeckCard) -> Unit,
    onMoveCardLocally: (card: DeckCard, targetStackId: Int, beforeCardId: Int?) -> Unit,
    onCommitCardMove: (card: DeckCard) -> Unit,
    onAddCard: () -> Unit,
    onSetDoneColumn: (stackId: Int, isDone: Boolean) -> Unit,
) {
    // Only meaningful for the done column — collapses its card list into a single "N Done"
    // summary. Not persisted: resets to expanded on app restart.
    var doneCardsHidden by remember { mutableStateOf(false) }
    val collapseDone = stack.isDoneColumn && doneCardsHidden

    // Catch-all for the column's empty space and gaps, so a drop there still reaches onDrop
    // instead of the shadow flying back.
    val dropFallback = Modifier.kDropTargetFallback(
        key = "stack-${stack.id}",
        state = dndState,
        onDrop = { s ->
            onMoveCardLocally(s.data, stack.id, null)
            onCommitCardMove(s.data)
        },
    )

    Column(
        modifier = (if (vertical) Modifier.widthIn(max = MAX_VERTICAL_STACK_WIDTH).fillMaxWidth() else Modifier.width(columnWidth).fillMaxHeight())
            .clip(RoundedCornerShape(12.dp))
            .background(DeckSurface),
    ) {
        StackHeader(
            stack = stack,
            doneCardsHidden = doneCardsHidden,
            onToggleDoneCardsHidden = { doneCardsHidden = !doneCardsHidden },
            onSetDoneColumn = onSetDoneColumn,
        )

        if (vertical) {
            // The page itself scrolls (the caller's LazyColumn), so the cards are laid out in a
            // plain column that grows with its content.
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    // Keeps an empty column a usable drop target now that it shows no placeholder.
                    .heightIn(min = 56.dp)
                    .then(dropFallback)
                    .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
            ) {
                if (isFirstColumn) AddCardTile(onClick = onAddCard)
                if (collapseDone) {
                    DoneSummary(stack.cards.size) { doneCardsHidden = false }
                } else {
                    stack.cards.forEach { card ->
                        key(card.id) {
                            StackCard(card, stack.id, columnWidth, dndState, onCardClick, onMoveCardLocally, onCommitCardMove)
                        }
                    }
                }
            }
        } else {
            val colState = rememberLazyListState()
            LazyColumn(
                state = colState,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .kDragAutoScroll(state = dndState, lazyListState = colState)
                    .then(dropFallback),
            ) {
                if (isFirstColumn) {
                    item(key = "add-card-${stack.id}") {
                        AddCardTile(onClick = onAddCard)
                    }
                }

                if (collapseDone) {
                    item(key = "done-summary-${stack.id}") {
                        DoneSummary(stack.cards.size) { doneCardsHidden = false }
                    }
                } else {
                    items(stack.cards, key = { it.id }) { card ->
                        StackCard(
                            card, stack.id, columnWidth, dndState, onCardClick, onMoveCardLocally, onCommitCardMove,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                item { Spacer(Modifier.height(6.dp)) }
            }
        }
    }
}

@Composable
private fun StackHeader(
    stack: Stack,
    doneCardsHidden: Boolean,
    onToggleDoneCardsHidden: () -> Unit,
    onSetDoneColumn: (stackId: Int, isDone: Boolean) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stack.isDoneColumn) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DeckSuccess, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                stack.title.uppercase(),
                color = DeckTextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            CountBadge(stack.cards.size)
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = tr("Column options"), tint = DeckTextSecondary)
                }
                KMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    KMenuItem(
                        text = if (stack.isDoneColumn) tr("Unset as done column") else tr("Set as done column"),
                        onClick = { menuExpanded = false; onSetDoneColumn(stack.id, !stack.isDoneColumn) },
                    )
                    if (stack.isDoneColumn) {
                        KMenuItem(
                            text = if (doneCardsHidden) tr("Show done cards") else tr("Hide done cards"),
                            onClick = { menuExpanded = false; onToggleDoneCardsHidden() },
                        )
                    }
                }
            }
        }
}

@Composable
private fun StackCard(
    card: DeckCard,
    stackId: Int,
    columnWidth: Dp,
    dndState: KDragAndDropState<DeckCard>,
    onCardClick: (DeckCard) -> Unit,
    onMoveCardLocally: (card: DeckCard, targetStackId: Int, beforeCardId: Int?) -> Unit,
    onCommitCardMove: (card: DeckCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDragging = dndState.isDragging(card.id)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isDragging) 0f else 1f }
            .clickable(onClick = { onCardClick(card) })
            .kReorderableItem(
                key = card.id,
                data = card,
                state = dndState,
                dropStrategy = KDropStrategy.CenterDistance,
                dragAfterLongPress = true,
                // Drop is delivered on release; the drag shadow then glides straight to
                // where the card ended up instead of to the hovered slot and snapping.
                landOnDroppedItem = true,
                onDragEnter = { s -> onMoveCardLocally(s.data, stackId, card.id) },
                onDrop = { s -> onCommitCardMove(s.data) },
                draggableContent = {
                    CardTileContent(card, modifier = Modifier.width(columnWidth - 20.dp))
                },
            ),
    ) {
        CardTileContent(card)
    }
}

@Composable
private fun DoneSummary(count: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DeckSuccess, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            tr("{count} Done", "count" to count),
            color = DeckSuccess,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
        )
    }
}

@Composable
private fun AddCardTile(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(width = 1.dp, color = DeckBorder, shape = RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = DeckAccent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(tr("Add card"), color = DeckAccent, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
    }
}

@Composable
private fun CountBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DeckSurfaceRaised)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(count.toString(), color = DeckTextTertiary, fontSize = MaterialTheme.typography.labelSmall.fontSize)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardTileContent(card: DeckCard, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DeckSurfaceRaised)
            .border(width = 1.dp, color = DeckBorder, shape = RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(
            card.title,
            color = DeckTextPrimary,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            fontWeight = FontWeight.Medium,
        )

        val hasLabels = card.labels.isNotEmpty()
        val hasDueDate = card.duedate != null
        val isDone = card.done != null

        if (hasLabels) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                card.labels.distinctBy { it.id }.forEach { label ->
                    val color = parseBoardColor(label.color)
                    val name = label.title?.trim().orEmpty()
                    if (name.isEmpty()) {
                        // Unnamed label: just its color, as a small bar.
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(28.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                name,
                                color = onLabelColor(color),
                                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        val assigned = card.assignedUsers.mapNotNull { it.participant }.distinctBy { it.uid }
        if (assigned.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            AvatarRow(assigned)
        }

        if (hasDueDate || isDone) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDone) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DeckSuccess, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("Done"), color = DeckSuccess, fontSize = MaterialTheme.typography.labelSmall.fontSize)
                } else if (hasDueDate) {
                    val overdue = (card.overdue ?: 0) > 0
                    val tint = if (overdue) DeckError else DeckTextTertiary
                    Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        card.duedate.orEmpty().take(10),
                        color = tint,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    )
                }
            }
        }
    }
}
