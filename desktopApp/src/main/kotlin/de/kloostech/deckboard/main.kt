package de.kloostech.deckboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.kloostech.deckboard.data.DeckSettingsStore
import de.kloostech.deckboard.i18n.tr
import de.kloostech.deckboard.ui.AboutDialog
import de.kloostech.deckboard.ui.DeckBackground
import de.kloostech.deckboard.ui.DeckSurface
import de.kloostech.deckboard.ui.DeckTextPrimary
import de.kloostech.deckboard.ui.DeckTextTertiary
import de.kloostech.deckboard.ui.DeckTheme
import de.kloostech.deckboard.ui.appIconPainter

private val isMac = System.getProperty("os.name").contains("Mac", ignoreCase = true)

fun main() {
    // Must be set before AWT/Skiko initializes (i.e. before `application {}` runs) — this is
    // what the macOS global menu bar shows next to the Apple logo instead of "MainKt".
    System.setProperty("apple.awt.application.name", "Deckboard")

    application {
        val scope = rememberCoroutineScope()
        val viewModel = remember { AppViewModel(scope, DeckSettingsStore()) }
        val state by viewModel.state.collectAsState()
        var showAbout by remember { mutableStateOf(false) }
        val icon = appIconPainter()

        Window(
            onCloseRequest = ::exitApplication,
            title = "Deckboard",
            icon = icon,
        ) {
            if (isMac) {
                // Extends content under the native title bar and makes it transparent, so our
                // own dark background shows through instead of the OS's white/light chrome.
                LaunchedEffect(Unit) {
                    window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                    window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)

                }
            }

            MenuBar {
                Menu(tr("File"), mnemonic = tr("File").first()) {
                    Item(
                        tr("New Card"),
                        enabled = state.selectedBoardId != null,
                        onClick = viewModel::openAddCardDialog,
                        shortcut = KeyShortcut(Key.N, ctrl = true),
                    )
                    Item(tr("Refresh"), onClick = viewModel::refresh, shortcut = KeyShortcut(Key.R, ctrl = true))
                    Separator()
                    Item(tr("Settings…"), onClick = viewModel::openSettings, shortcut = KeyShortcut(Key.Comma, ctrl = true))
                    Separator()
                    Item(tr("Quit"), onClick = ::exitApplication, shortcut = KeyShortcut(Key.Q, ctrl = true), mnemonic = tr("Quit").first())
                }
                Menu(tr("View"), mnemonic = tr("View").first()) {
                    Item(tr("Toggle Sidebar"), onClick = viewModel::toggleSidebar, shortcut = KeyShortcut(Key.B, ctrl = true))
                }
                Menu(tr("Help"), mnemonic = tr("Help").first()) {
                    Item(tr("About Deckboard"), onClick = { showAbout = true })
                }
            }

            // Fills the whole window (including the area under the transparent title bar) with
            // the app's dark background, then insets the real content clear of the traffic lights.
            Box(Modifier.fillMaxSize().background(DeckBackground)) {
                App(viewModel, modifier = if (isMac) Modifier.padding(top = 28.dp) else Modifier)
            }

            if (showAbout) {
                DeckTheme { AboutDialog(onDismiss = { showAbout = false }) }
            }
        }
    }
}
