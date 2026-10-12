package de.kloostech.deckboard.ui

import de.kloostech.deckboard.i18n.tr
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField
import tech.kloos.kompound.theme.KompoundTheme

/** A small single-field dialog for quick "create X" flows (new board, new card, ...). */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String = tr("Create"),
    submitting: Boolean = false,
    error: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    fun submit() {
        if (text.isNotBlank() && !submitting) onConfirm(text)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    KDialog(
        onDismissRequest = onDismiss,
        title = title,
        actions = {
            KButton(onClick = onDismiss, variant = KButtonVariant.Text) { KText(KompoundTheme.strings.cancel) }
            KButton(onClick = { submit() }, enabled = text.isNotBlank(), loading = submitting) { KText(confirmText) }
        },
    ) {
        KTextField(
            value = text,
            onValueChange = { text = it },
            label = label,
            // Not single-line: a title longer than the field's width wraps and the field
            // grows to fit, so the full title stays visible instead of being clipped.
            // Enter is intercepted below to submit rather than insert a newline.
            singleLine = false,
            isError = error != null,
            supportingText = error,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        submit()
                        true
                    } else {
                        false
                    }
                },
        )
    }
}
