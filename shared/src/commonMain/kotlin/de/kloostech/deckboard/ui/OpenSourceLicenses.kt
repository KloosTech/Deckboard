package de.kloostech.deckboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.kloostech.deckboard.i18n.tr
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme

private data class OpenSourceComponent(val name: String, val license: String, val url: String)

/** Keep in sync with THIRD_PARTY_NOTICES.md. */
private val components = listOf(
    OpenSourceComponent("Kompound (incl. compose-dnd, Material icons)", "Apache-2.0", "github.com/kloostech/Kompound"),
    OpenSourceComponent("Kotlin, kotlinx (coroutines, serialization, datetime)", "Apache-2.0", "github.com/Kotlin"),
    OpenSourceComponent("Compose Multiplatform, Jetpack Compose, AndroidX", "Apache-2.0", "github.com/JetBrains/compose-multiplatform"),
    OpenSourceComponent("Ktor client", "Apache-2.0", "github.com/ktorio/ktor"),
    OpenSourceComponent("multiplatform-settings", "Apache-2.0", "github.com/russhwolf/multiplatform-settings"),
    OpenSourceComponent("java-keyring (desktop)", "Apache-2.0", "github.com/javakeyring/java-keyring"),
    OpenSourceComponent("OkHttp, Okio (Android, desktop)", "Apache-2.0", "github.com/square/okhttp"),
)

/** Lists Deckboard's own license and the bundled third-party components. */
@Composable
fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    KDialog(
        onDismissRequest = onDismiss,
        actions = {
            KButton(onClick = onDismiss, variant = KButtonVariant.Text) { KText(KompoundTheme.strings.close) }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KText(tr("Open-source licenses"), textStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            val dim = Style { contentColor(DeckTextTertiary) }
            KText(tr("Deckboard is free software under the Apache License 2.0. Copyright {year} KloosTech.", "year" to "2026"), style = dim)
            components.forEach { c ->
                Column {
                    KText(c.name)
                    KText("${c.license} · ${c.url}", style = dim)
                }
            }
        }
    }
}
