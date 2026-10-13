package de.kloostech.deckboard.ui

import de.kloostech.deckboard.i18n.tr
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme

const val APP_NAME = "Deckboard"
const val APP_VERSION = "1.0.0"
fun appTagline(): String = tr("A free third-party Kanban client for Nextcloud Deck")

fun trademarkDisclaimer(): String =
    tr("Not affiliated with or endorsed by Nextcloud GmbH. Nextcloud is a trademark of Nextcloud GmbH.")

/** Shared About dialog: name, version, tagline and the third-party/trademark disclaimer. */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    var showLicenses by remember { mutableStateOf(false) }
    if (showLicenses) OpenSourceLicensesDialog(onDismiss = { showLicenses = false })
    KDialog(
        onDismissRequest = onDismiss,
        actions = {
            KButton(onClick = { showLicenses = true }, variant = KButtonVariant.Text) { KText(tr("Open-source licenses")) }
            KButton(onClick = onDismiss, variant = KButtonVariant.Text) { KText(KompoundTheme.strings.close) }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(appIconPainter(), contentDescription = null, modifier = Modifier.size(72.dp))
            KText(APP_NAME, textStyle = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            val dim = Style { contentColor(DeckTextTertiary) }
            KText(tr("Version {version}", "version" to APP_VERSION), style = dim)
            KText(appTagline(), style = Style(dim, Style { textAlign(TextAlign.Center) }))
            KText(trademarkDisclaimer(), style = Style(dim, Style { textAlign(TextAlign.Center) }))
        }
    }
}
