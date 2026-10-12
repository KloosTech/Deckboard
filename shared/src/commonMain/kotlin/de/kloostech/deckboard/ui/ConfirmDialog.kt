package de.kloostech.deckboard.ui

import de.kloostech.deckboard.i18n.tr
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme

/** A small "are you sure?" dialog for destructive actions (delete board, ...). */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = tr("Delete"),
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    KDialog(
        onDismissRequest = onDismiss,
        title = title,
        actions = {
            KButton(onClick = onDismiss, variant = KButtonVariant.Text) { KText(KompoundTheme.strings.cancel) }
            KButton(
                onClick = onConfirm,
                style = Style { background(DeckError); contentColor(Color.White) },
            ) { KText(confirmText) }
        },
    ) {
        KText(message)
    }
}
