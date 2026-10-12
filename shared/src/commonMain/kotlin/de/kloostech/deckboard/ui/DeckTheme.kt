package de.kloostech.deckboard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.kloostech.deckboard.i18n.Language
import tech.kloos.kompound.i18n.KompoundStrings
import tech.kloos.kompound.theme.KompoundTheme

val DeckBackground = Color(0xFF14161C)
val DeckSurface = Color(0xFF1B1E27)
val DeckSurfaceRaised = Color(0xFF232733)
val DeckSurfaceHover = Color(0xFF2A2F3D)
val DeckBorder = Color(0xFF2E3340)
val DeckAccent = Color(0xFF5B8CFF)
val DeckAccentMuted = Color(0xFF3D5DB3)
val DeckTextPrimary = Color(0xFFE8EAF0)
val DeckTextSecondary = Color(0xFF9CA3B5)
val DeckTextTertiary = Color(0xFF6B7280)
val DeckError = Color(0xFFEF5A6F)
val DeckSuccess = Color(0xFF4CD97B)

/** Curated swatches: a new board's default color is picked from here at random, and the
 * "change color" picker on the board header only offers these — keeps every board's dot
 * visually consistent instead of opening up to an arbitrary hex/RGB picker. */
val BOARD_COLOR_PALETTE = listOf(
    "0082C9", "1CBC9B", "E9322D", "F1DB4B", "9B59B6", "BF678B", "E1A62F", "36A3D9",
)

private val DeckColorScheme = darkColorScheme(
    primary = DeckAccent,
    onPrimary = Color.White,
    primaryContainer = DeckAccentMuted,
    onPrimaryContainer = Color.White,
    secondary = DeckTextSecondary,
    background = DeckBackground,
    onBackground = DeckTextPrimary,
    surface = DeckSurface,
    onSurface = DeckTextPrimary,
    surfaceVariant = DeckSurfaceRaised,
    onSurfaceVariant = DeckTextSecondary,
    outline = DeckBorder,
    error = DeckError,
    onError = Color.White,
)

/** Kompound theme (which wraps [MaterialTheme]) with Deckboard's fixed dark color scheme. */
@Composable
fun DeckTheme(content: @Composable () -> Unit) {
    // Kompound's own labels (Cancel, Save, month names in pickers, ...) follow the app's language too.
    val strings = if (Language.isGerman) KompoundStrings.German else KompoundStrings.English
    KompoundTheme(colorScheme = DeckColorScheme, strings = strings, content = content)
}

@Composable
fun deckTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DeckSurfaceRaised,
    unfocusedContainerColor = DeckSurfaceRaised,
    focusedBorderColor = DeckAccent,
    unfocusedBorderColor = DeckBorder,
    focusedTextColor = DeckTextPrimary,
    unfocusedTextColor = DeckTextPrimary,
    focusedLabelColor = DeckAccent,
    unfocusedLabelColor = DeckTextSecondary,
    cursorColor = DeckAccent,
)

/** Readable text color on a label-colored background: dark text on light colors, white otherwise. */
fun onLabelColor(background: Color): Color =
    if (background.luminance() > 0.55f) Color(0xFF1B1E27) else Color.White

/** Stable per-board accent color parsed from the hex string Deck returns (e.g. "bf678b"). */
fun parseBoardColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return DeckAccent
    return runCatching {
        val clean = hex.removePrefix("#")
        Color(("FF$clean").toLong(16))
    }.getOrDefault(DeckAccent)
}
