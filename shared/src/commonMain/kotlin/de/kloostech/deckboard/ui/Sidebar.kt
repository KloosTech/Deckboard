package de.kloostech.deckboard.ui

import de.kloostech.deckboard.i18n.tr
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.kloostech.deckboard.data.Board

private val EXPANDED_WIDTH = 260.dp
private val COLLAPSED_WIDTH = 64.dp

@Composable
fun Sidebar(
    expanded: Boolean,
    boards: List<Board>,
    selectedBoardId: Int?,
    loading: Boolean,
    error: String?,
    onToggle: () -> Unit,
    onSelectBoard: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onCreateBoard: () -> Unit,
    /** Full-screen single-pane mode (phone): fills its container instead of animating between
     * a fixed collapsed/expanded width, and drops the collapse toggle — there's no icon-rail
     * state on a screen this narrow. */
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(
        targetValue = if (expanded) EXPANDED_WIDTH else COLLAPSED_WIDTH,
        animationSpec = tween(220),
    )

    Column(
        modifier = (if (compact) modifier.fillMaxWidth() else modifier.width(width))
            .fillMaxHeight()
            .background(DeckSurface)
            .border(width = 1.dp, color = DeckBorder),
    ) {
        SidebarHeader(expanded, onToggle, onRefresh, loading, showToggle = !compact)

        NewBoardRow(expanded, onCreateBoard, compact)

        Spacer(Modifier.height(4.dp))

        Box(Modifier.weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(boards, key = { it.id }) { board ->
                    BoardRow(
                        board = board,
                        expanded = expanded,
                        compact = compact,
                        selected = board.id == selectedBoardId,
                        onClick = { onSelectBoard(board.id) },
                    )
                }
                if (!loading && boards.isEmpty() && error == null) {
                    item {
                        if (expanded) {
                            Text(
                                tr("No boards found"),
                                color = DeckTextTertiary,
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
                if (error != null && expanded) {
                    item {
                        Text(
                            error,
                            color = DeckError,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }

        SidebarFooter(expanded, onOpenSettings, compact)
    }
}

@Composable
private fun SidebarHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean,
    showToggle: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showToggle) {
            IconButton(onClick = onToggle) {
                Icon(Icons.Filled.Menu, contentDescription = tr("Toggle sidebar"), tint = DeckTextSecondary)
            }
        }
        if (expanded) {
            Text(
                "Deck",
                color = DeckTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                modifier = Modifier.weight(1f).padding(start = if (showToggle) 0.dp else 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = tr("Refresh boards"), tint = DeckTextSecondary)
            }
        }
    }
}

@Composable
private fun NewBoardRow(expanded: Boolean, onCreateBoard: () -> Unit, compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onCreateBoard)
            .padding(horizontal = if (expanded) 8.dp else 0.dp, vertical = if (compact) 12.dp else 8.dp),
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = tr("New board"), tint = DeckAccent, modifier = Modifier.size(18.dp))
        if (expanded) {
            Spacer(Modifier.width(10.dp))
            Text(tr("New board"), color = DeckAccent, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
        }
    }
}

@Composable
private fun SidebarFooter(expanded: Boolean, onOpenSettings: () -> Unit, compact: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = DeckBorder)
            .clickable(onClick = onOpenSettings)
            .padding(horizontal = 16.dp, vertical = if (compact) 16.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(Icons.Filled.Settings, contentDescription = tr("Settings"), tint = DeckTextSecondary)
        if (expanded) {
            Spacer(Modifier.width(12.dp))
            Text(tr("Settings"), color = DeckTextSecondary, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
        }
    }
}

@Composable
private fun BoardRow(board: Board, expanded: Boolean, compact: Boolean, selected: Boolean, onClick: () -> Unit) {
    val accent = parseBoardColor(board.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DeckSurfaceHover else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (expanded) 10.dp else 0.dp, vertical = if (compact) 14.dp else 10.dp),
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accent),
        )
        if (expanded) {
            Spacer(Modifier.width(10.dp))
            Text(
                board.title,
                color = if (selected) DeckTextPrimary else DeckTextSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
