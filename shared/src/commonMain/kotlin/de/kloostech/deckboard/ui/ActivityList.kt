package de.kloostech.deckboard.ui

import de.kloostech.deckboard.i18n.tr
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.kloostech.deckboard.data.ActivityEntry
import de.kloostech.deckboard.data.DeckUser
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.progress.KCircularProgress
import tech.kloos.kompound.state.KErrorState
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme

/**
 * Loads activity with [load] and shows it under sticky headers (Today, Yesterday, Last 7 days, ...). Handles loading,
 * error (with retry) and empty states. The list scrolls on its own up to [maxHeight], so the headers can stick.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActivityList(load: suspend () -> Result<List<ActivityEntry>>, modifier: Modifier = Modifier, maxHeight: Dp = 420.dp) {
    var attempt by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<Result<List<ActivityEntry>>?>(null) }
    LaunchedEffect(attempt) {
        result = null
        result = load()
    }
    val r = result
    when {
        r == null -> KCircularProgress(progress = null, size = 24.dp, strokeWidth = 2.dp)
        r.isFailure -> KErrorState(
            title = tr("Couldn't load activity"),
            description = r.exceptionOrNull()?.message,
            onRetry = { attempt++ },
        )
        r.getOrThrow().isEmpty() -> KText(tr("No recent activity."), style = Style { contentColor(DeckTextTertiary) })
        else -> {
            val groups = remember(r) { groupActivities(r.getOrThrow()) }
            val listState = rememberLazyListState()
            LaunchedEffect(groups) { listState.scrollToItem(0) }
            LazyColumn(
                modifier.fillMaxWidth().heightIn(max = maxHeight),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                groups.forEach { group ->
                    stickyHeader(key = "header-${group.title}") { ActivityHeader(group.title) }
                    items(group.entries, key = { it.id }) { ActivityRow(it) }
                }
            }
        }
    }
}

/** Opaque so rows scroll underneath it while it sticks to the top of the list. */
@Composable
private fun ActivityHeader(title: String) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 6.dp)) {
        KText(title, textStyle = MaterialTheme.typography.labelLarge, style = Style { contentColor(DeckTextSecondary) })
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        UserAvatar(DeckUser(uid = entry.user, displayname = entry.user))
        Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KText(entry.subject)
            KText(shortTime(entry.datetime), style = Style { contentColor(DeckTextTertiary) })
        }
    }
}

/** Board-level activity. */
@Composable
fun ActivityDialog(boardTitle: String, load: suspend () -> Result<List<ActivityEntry>>, onDismiss: () -> Unit) {
    KDialog(
        onDismissRequest = onDismiss,
        title = tr("Activity · {board}", "board" to boardTitle),
        actions = { KButton(onClick = onDismiss, variant = KButtonVariant.Text) { KText(KompoundTheme.strings.close) } },
    ) {
        ActivityList(load)
    }
}
