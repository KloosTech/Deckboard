package de.kloostech.deckboard.ui

import de.kloostech.deckboard.i18n.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.kloostech.deckboard.data.ConnectionReport
import de.kloostech.deckboard.data.DeckConnection
import de.kloostech.deckboard.data.LoginFlowClient
import de.kloostech.deckboard.data.LoginFlowException
import de.kloostech.deckboard.getPlatform
import kotlinx.coroutines.Job
import de.kloostech.deckboard.data.MIN_POLL_INTERVAL_SECONDS
import de.kloostech.deckboard.data.NormalizedServerUrl
import de.kloostech.deckboard.data.checkConnection
import de.kloostech.deckboard.data.normalizeServerUrl
import kotlinx.coroutines.launch
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.dialog.KDialog
import tech.kloos.kompound.form.KFieldGroup
import tech.kloos.kompound.form.KForm
import tech.kloos.kompound.form.KFormField
import tech.kloos.kompound.form.KFormTextField
import tech.kloos.kompound.form.KSubmitButton
import tech.kloos.kompound.form.KValidators
import tech.kloos.kompound.form.rememberKField
import tech.kloos.kompound.form.rememberKFormState
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.icon.KIcons
import tech.kloos.kompound.selection.KSwitch
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KNumberField
import tech.kloos.kompound.textfield.KPasswordField
import tech.kloos.kompound.textfield.KTextArea
import tech.kloos.kompound.theme.KompoundTheme

private const val MAX_POLL_INTERVAL_SECONDS = 86_400

/** Progress of "Sign in with browser" (Nextcloud Login Flow v2). */
private sealed interface BrowserSignIn {
    data object Idle : BrowserSignIn
    data object Starting : BrowserSignIn
    data class Waiting(val loginUrl: String) : BrowserSignIn
    data class Done(val user: String) : BrowserSignIn
    data class Failed(val message: String) : BrowserSignIn
}

@Composable
fun SettingsScreen(
    connection: DeckConnection,
    canDismiss: Boolean,
    onSave: (DeckConnection) -> Unit,
    onDismiss: () -> Unit,
    pollingEnabled: Boolean = false,
    pollingIntervalSeconds: Int = 30,
    onSavePolling: (enabled: Boolean, intervalSeconds: Int) -> Unit = { _, _ -> },
    defaultColumnTitles: List<String> = emptyList(),
    onSaveDefaultColumns: (List<String>) -> Unit = {},
    verticalColumns: Boolean = false,
    onSaveVerticalColumns: (Boolean) -> Unit = {},
) {
    val form = rememberKFormState()
    var pollingOn by remember { mutableStateOf(pollingEnabled) }
    var stackVertically by remember { mutableStateOf(verticalColumns) }

    val server = rememberKField(
        form, "server", connection.baseUrl,
        KValidators.required(tr("Enter your Nextcloud address.")),
        KValidators.check(tr("That doesn't look like a web address. Try something like cloud.example.com.")) {
            normalizeServerUrl(it) != null
        },
    )
    val username = rememberKField(form, "username", connection.username, KValidators.required(tr("Enter your Nextcloud username.")))
    val appPassword = rememberKField(form, "appPassword", connection.appPassword, KValidators.required(tr("Enter an app password.")))
    // Only checked while auto-refresh is on, so a leftover bad value can't block saving.
    val interval = rememberKField(
        form, "interval", pollingIntervalSeconds.toString(),
        KValidators.check(tr("Use {min} to {max} seconds.", "min" to MIN_POLL_INTERVAL_SECONDS, "max" to MAX_POLL_INTERVAL_SECONDS)) {
            !pollingOn || it.trim().toIntOrNull() in MIN_POLL_INTERVAL_SECONDS..MAX_POLL_INTERVAL_SECONDS
        },
    )
    var columnsText by remember { mutableStateOf(defaultColumnTitles.joinToString("\n")) }

    var showAbout by remember { mutableStateOf(false) }
    var browserSignIn by remember { mutableStateOf<BrowserSignIn>(BrowserSignIn.Idle) }
    var signInJob by remember { mutableStateOf<Job?>(null) }
    val uriHandler = LocalUriHandler.current
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ConnectionReport?>(null) }
    val scope = rememberCoroutineScope()

    // Editing a credential invalidates an earlier test result.
    LaunchedEffect(server.value, username.value, appPassword.value) { testResult = null }

    val normalizedUrl = normalizeServerUrl(server.value)

    fun startBrowserSignIn() {
        val url = normalizedUrl?.url ?: return
        signInJob?.cancel()
        signInJob = scope.launch {
            browserSignIn = BrowserSignIn.Starting
            val flow = LoginFlowClient(url, clientName = "Deckboard (${getPlatform().name})")
            try {
                val session = flow.start()
                browserSignIn = BrowserSignIn.Waiting(session.loginUrl)
                runCatching { uriHandler.openUri(session.loginUrl) }
                val result = flow.awaitResult(session)
                if (result == null) {
                    browserSignIn = BrowserSignIn.Failed(tr("Sign-in timed out. Please try again."))
                } else {
                    server.onValueChange(normalizeServerUrl(result.server)?.url ?: result.server)
                    username.onValueChange(result.loginName)
                    appPassword.onValueChange(result.appPassword)
                    browserSignIn = BrowserSignIn.Done(result.loginName)
                    testing = true
                    testResult = checkConnection(DeckConnection(normalizeServerUrl(result.server)?.url ?: result.server, result.loginName, result.appPassword))
                    testing = false
                }
            } catch (e: LoginFlowException) {
                browserSignIn = BrowserSignIn.Failed(e.message ?: tr("Sign-in failed."))
            } finally {
                flow.close()
            }
        }
    }
    val credentialsValid = server.isValid && username.isValid && appPassword.isValid

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    KDialog(
        onDismissRequest = onDismiss,
        title = tr("Connect to your Nextcloud server"),
        dismissOnBackPress = canDismiss,
        dismissOnClickOutside = false,
        actions = {
            KButton(
                onClick = {
                    testing = true
                    testResult = null
                    scope.launch {
                        testResult = checkConnection(DeckConnection(normalizedUrl!!.url, username.value.trim(), appPassword.value))
                        testing = false
                    }
                },
                variant = KButtonVariant.Outlined,
                enabled = credentialsValid && !testing,
                loading = testing,
            ) {
                when (testResult) {
                    is ConnectionReport.Success -> KIcon(KIcons.Check, tr("Connection works"), tint = DeckSuccess)
                    is ConnectionReport.Failure -> KIcon(KIcons.Error, tr("Connection failed"), tint = DeckError)
                    null -> KText(tr("Test connection"))
                }
            }
            if (canDismiss) {
                KButton(onClick = onDismiss, variant = KButtonVariant.Text) { KText(KompoundTheme.strings.cancel) }
            }
            KSubmitButton(form, text = KompoundTheme.strings.save, onSubmit = {
                onSave(DeckConnection(normalizedUrl!!.url, username.value.trim(), appPassword.value))
                onSavePolling(pollingOn, interval.value.trim().toIntOrNull() ?: MIN_POLL_INTERVAL_SECONDS)
                onSaveDefaultColumns(columnsText.split("\n"))
                onSaveVerticalColumns(stackVertically)
            })
        },
    ) {
        KForm(form, spacing = 20.dp, showStatus = false) {
            KFieldGroup(
                title = tr("Account"),
                description = tr(
                    "Enter your Nextcloud address — the one you open in your browser, like cloud.example.com. " +
                        "Deckboard works out the rest.",
                ),
            ) {
                KFormTextField(
                    server,
                    Modifier.fillMaxWidth(),
                    label = tr("Server address"),
                    placeholder = "cloud.example.com",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                ServerUrlHint(raw = server.value, normalized = normalizedUrl)

                when (val state = browserSignIn) {
                    is BrowserSignIn.Waiting -> {
                        KText(tr("Approve Deckboard in your browser, then come back here. Waiting…"), style = Style { contentColor(DeckTextSecondary) })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KButton(onClick = { runCatching { uriHandler.openUri(state.loginUrl) } }, variant = KButtonVariant.Text) { KText(tr("Open browser again")) }
                            KButton(onClick = { signInJob?.cancel(); browserSignIn = BrowserSignIn.Idle }, variant = KButtonVariant.Text) {
                                KText(KompoundTheme.strings.cancel)
                            }
                        }
                    }
                    else -> KButton(
                        onClick = { startBrowserSignIn() },
                        variant = KButtonVariant.Tonal,
                        enabled = normalizedUrl != null && state != BrowserSignIn.Starting,
                        loading = state == BrowserSignIn.Starting,
                    ) { KText(tr("Sign in with browser")) }
                }
                when (val state = browserSignIn) {
                    is BrowserSignIn.Done -> KText(tr("Signed in as {user}. Press Save to finish.", "user" to state.user), style = Style { contentColor(DeckSuccess) })
                    is BrowserSignIn.Failed -> KText(state.message, style = Style { contentColor(DeckError) })
                    else -> Unit
                }
                KText(
                    tr("Or enter your username and an app password yourself:"),
                    style = Style { contentColor(DeckTextTertiary) },
                )
                KFormTextField(username, Modifier.fillMaxWidth(), label = tr("Username"))
                KFormField(
                    appPassword,
                    Modifier.fillMaxWidth(),
                    helperText = tr(
                        "Create one in Nextcloud under Settings → Security → Devices & sessions → " +
                            "\"Create new app password\". Use it here instead of your normal password.",
                    ),
                ) { value, onChange, error ->
                    KPasswordField(
                        value, onChange,
                        Modifier.fillMaxWidth(),
                        label = tr("App password"),
                        isError = error != null,
                        supportingText = error,
                    )
                }
                testResult?.let { ConnectionResult(it) }
            }

            KFieldGroup(title = tr("Refresh")) {
                KSwitch(
                    checked = pollingOn,
                    onCheckedChange = { pollingOn = it },
                    label = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            KText(tr("Auto-refresh"))
                            KText(tr("Polls for board changes in the background"), style = Style { contentColor(DeckTextTertiary) })
                        }
                    },
                )
                if (pollingOn) {
                    KFormField(interval, Modifier.fillMaxWidth(), showError = false) { value, onChange, error ->
                        KNumberField(
                            value, onChange,
                            Modifier.fillMaxWidth(),
                            label = tr("Interval (seconds, min {min})", "min" to MIN_POLL_INTERVAL_SECONDS),
                            isError = error != null,
                            supportingText = error,
                        )
                    }
                }
            }

            KFieldGroup(title = tr("Layout")) {
                KSwitch(
                    checked = stackVertically,
                    onCheckedChange = { stackVertically = it },
                    label = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            KText(tr("Stack columns vertically"))
                            KText(
                                tr("Show a board's columns below each other instead of side by side"),
                                style = Style { contentColor(DeckTextTertiary) },
                            )
                        }
                    },
                )
            }

            KFieldGroup(
                title = tr("Default columns for new boards"),
                description = tr("One per line, created in this order. Leave empty for no automatic columns."),
            ) {
                KTextArea(
                    value = columnsText,
                    onValueChange = { columnsText = it },
                    placeholder = tr("To Do\nIn Progress\nDone"),
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            KButton(onClick = { showAbout = true }, variant = KButtonVariant.Text) { KText(tr("About Deckboard")) }
        }
    }
}

/** Outcome of the connection test: a headline in green/red plus the fix hint. */
@Composable
private fun ConnectionResult(report: ConnectionReport) {
    val ok = report is ConnectionReport.Success
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KText(report.message, style = Style { contentColor(if (ok) DeckSuccess else DeckError) })
        report.hint?.let { KText(it, style = Style { contentColor(DeckTextTertiary) }) }
    }
}

/** Live feedback under the address field: what we'll connect to, and what we fixed or assumed. */
@Composable
private fun ServerUrlHint(raw: String, normalized: NormalizedServerUrl?) {
    if (raw.isBlank() || normalized == null) return
    val dim = Style { contentColor(DeckTextTertiary) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        KText(tr("Will connect to {url}", "url" to normalized.url), style = dim)
        if (normalized.trimmedPath) {
            KText(tr("Removed the page path — Deckboard adds the Deck API path itself."), style = dim)
        }
        if (normalized.insecure) {
            KText(
                tr("This is plain http: your app password would be sent unencrypted. Use https if the server supports it."),
                style = Style { contentColor(DeckError) },
            )
        }
    }
}
