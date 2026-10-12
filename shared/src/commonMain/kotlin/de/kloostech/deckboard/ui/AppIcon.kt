package de.kloostech.deckboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import deckboard.shared.generated.resources.Res
import deckboard.shared.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource

/**
 * The app's brand icon. The generated resource accessor is internal to this module, so this
 * public wrapper is what platform-specific chrome (desktop window/taskbar icon, About dialogs)
 * outside `shared` uses to reach it.
 */
@Composable
fun appIconPainter(): Painter = painterResource(Res.drawable.app_icon)
