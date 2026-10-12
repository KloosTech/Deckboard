package de.kloostech.deckboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.kloostech.deckboard.data.DeckUser
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import tech.kloos.kompound.avatar.KAvatar
import tech.kloos.kompound.avatar.KAvatarSize
import tech.kloos.kompound.text.KText

/** Name to show for a user: display name, else uid. */
val DeckUser.name: String get() = displayname?.takeIf { it.isNotBlank() } ?: uid ?: primaryKey ?: "?"

/** Initials avatar of a Nextcloud user (Kompound's [KAvatar]). */
@Composable
fun UserAvatar(user: DeckUser, modifier: Modifier = Modifier, size: KAvatarSize = KAvatarSize.Small) {
    KAvatar(name = user.name, modifier = modifier, size = size)
}

/** A row of overlapping-free avatars, with a "+N" when there are more than [max]. */
@Composable
fun AvatarRow(users: List<DeckUser>, modifier: Modifier = Modifier, max: Int = 3, size: KAvatarSize = KAvatarSize.Small) {
    if (users.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        users.take(max).forEach { UserAvatar(it, size = size) }
        if (users.size > max) KText("+${users.size - max}", style = Style { contentColor(DeckTextTertiary) })
    }
}

/**
 * `2026-10-12T12:34:56+00:00` → `2026-10-12 14:34` in the device's time zone. Anything that isn't a full timestamp
 * with an offset is shown as given, only trimmed to minutes when it looks like `YYYY-MM-DDTHH:MM…`.
 */
@OptIn(ExperimentalTime::class)
fun shortTime(iso: String, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    runCatching { Instant.parse(iso).toLocalDateTime(timeZone) }.getOrNull()?.let { t ->
        fun two(n: Int) = n.toString().padStart(2, '0')
        return "${t.year}-${two(t.month.number)}-${two(t.day)} ${two(t.hour)}:${two(t.minute)}"
    }
    return if (iso.length >= 16 && iso[10].let { it == 'T' || it == ' ' }) iso.take(16).replace('T', ' ') else iso
}

/** `922258` → `901 KB`. */
fun formatFileSize(bytes: Long?): String? {
    if (bytes == null) return null
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "${bytes * 10 / (1024 * 1024) / 10.0} MB"
        else -> "${bytes * 10 / (1024L * 1024 * 1024) / 10.0} GB"
    }
}
