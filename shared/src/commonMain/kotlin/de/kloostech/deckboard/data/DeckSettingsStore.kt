package de.kloostech.deckboard.data

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [baseUrl] is the Nextcloud *server root* (`https://cloud.example.com[/subfolder]`), not an API
 * URL — the Deck API paths are derived from it so users never have to type them.
 */
data class DeckConnection(
    val baseUrl: String = "",
    val username: String = "",
    val appPassword: String = "",
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && username.isNotBlank() && appPassword.isNotBlank()

    /** Deck REST API root. `/index.php/` form works with and without pretty URLs. */
    val apiBaseUrl: String
        get() = "$baseUrl/index.php/apps/deck/api/v$DECK_API_VERSION"

    /** Deck's OCS-flavoured API (comments live here). */
    val ocsDeckBaseUrl: String
        get() = "$baseUrl/ocs/v2.php/apps/deck/api/v1.0"

    /** Same API mounted through OCS (needed for a few newer endpoints, e.g. done column). */
    val ocsApiBaseUrl: String
        get() = "$baseUrl/ocs/v2.php/apps/deck/api/v$DECK_API_VERSION"
}

data class PollingSettings(
    val enabled: Boolean = false,
    val intervalSeconds: Int = 30,
)

/** Floor for [PollingSettings.intervalSeconds] — keeps a mistyped "1" from hammering the server. */
const val MIN_POLL_INTERVAL_SECONDS: Int = 5

/** Column titles auto-created (in order) on every new board. Empty means "don't add any". */
data class DefaultColumns(val titles: List<String> = emptyList())

/**
 * Persists the connection and preferences. The app password goes to [secrets] (platform secure storage), everything
 * else to [settings]. An app password left in plain [settings] by an older build is moved over on first load.
 */
class DeckSettingsStore(
    private val settings: Settings = Settings(),
    private val secrets: SecretStore = createSecretStore(),
) {

    private val _connection = MutableStateFlow(loadConnection())
    val connection: StateFlow<DeckConnection> = _connection.asStateFlow()

    private val _polling = MutableStateFlow(loadPolling())
    val polling: StateFlow<PollingSettings> = _polling.asStateFlow()

    private val _verticalColumns = MutableStateFlow(settings[KEY_VERTICAL_COLUMNS, false])

    /** Small-screen preference: stack a board's columns vertically instead of paging sideways. */
    val verticalColumns: StateFlow<Boolean> = _verticalColumns.asStateFlow()

    private val _defaultColumns = MutableStateFlow(loadDefaultColumns())
    val defaultColumns: StateFlow<DefaultColumns> = _defaultColumns.asStateFlow()

    // Older builds stored the full API URL; normalizing on load migrates that to the server root.
    private fun loadConnection(): DeckConnection = DeckConnection(
        baseUrl = settings[KEY_BASE_URL, ""].let { normalizeServerUrl(it)?.url ?: it.trimEnd('/') },
        username = settings[KEY_USERNAME, ""],
        appPassword = loadAppPassword(),
    )

    private fun loadAppPassword(): String {
        runCatching { secrets.get(KEY_APP_PASSWORD) }.getOrNull()?.let { return it }
        // Nothing in secure storage: pick up a password an older build kept in plain settings (and move it).
        val legacy = settings[KEY_APP_PASSWORD, ""]
        if (legacy.isNotEmpty() && runCatching { secrets.set(KEY_APP_PASSWORD, legacy) }.isSuccess) {
            settings.remove(KEY_APP_PASSWORD)
        }
        return legacy
    }

    /** Stores the password securely; only if the secure store fails does it fall back to plain settings (and says so by keeping it there). */
    private fun saveAppPassword(password: String) {
        if (password.isEmpty()) {
            runCatching { secrets.remove(KEY_APP_PASSWORD) }
            settings.remove(KEY_APP_PASSWORD)
        } else if (runCatching { secrets.set(KEY_APP_PASSWORD, password) }.isSuccess) {
            settings.remove(KEY_APP_PASSWORD)
        } else {
            settings[KEY_APP_PASSWORD] = password
        }
    }

    fun save(connection: DeckConnection) {
        val normalized = connection.copy(
            baseUrl = normalizeServerUrl(connection.baseUrl)?.url ?: connection.baseUrl.trimEnd('/'),
        )
        settings[KEY_BASE_URL] = normalized.baseUrl
        settings[KEY_USERNAME] = normalized.username
        saveAppPassword(normalized.appPassword)
        _connection.value = normalized
    }

    private fun loadPolling(): PollingSettings = PollingSettings(
        enabled = settings[KEY_POLLING_ENABLED, false],
        intervalSeconds = settings[KEY_POLLING_INTERVAL, 30],
    )

    fun savePolling(polling: PollingSettings) {
        val normalized = polling.copy(intervalSeconds = polling.intervalSeconds.coerceAtLeast(MIN_POLL_INTERVAL_SECONDS))
        settings[KEY_POLLING_ENABLED] = normalized.enabled
        settings[KEY_POLLING_INTERVAL] = normalized.intervalSeconds
        _polling.value = normalized
    }

    fun saveVerticalColumns(enabled: Boolean) {
        settings[KEY_VERTICAL_COLUMNS] = enabled
        _verticalColumns.value = enabled
    }

    private fun loadDefaultColumns(): DefaultColumns {
        val raw = settings[KEY_DEFAULT_COLUMNS, ""]
        val titles = raw.split(COLUMN_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
        return DefaultColumns(titles)
    }

    fun saveDefaultColumns(columns: DefaultColumns) {
        val normalized = DefaultColumns(columns.titles.map { it.trim() }.filter { it.isNotEmpty() })
        settings[KEY_DEFAULT_COLUMNS] = normalized.titles.joinToString(COLUMN_DELIMITER)
        _defaultColumns.value = normalized
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_USERNAME = "username"
        const val KEY_APP_PASSWORD = "app_password"
        const val KEY_POLLING_ENABLED = "polling_enabled"
        const val KEY_POLLING_INTERVAL = "polling_interval_seconds"
        const val KEY_DEFAULT_COLUMNS = "default_columns"
        const val KEY_VERTICAL_COLUMNS = "vertical_columns"
        const val COLUMN_DELIMITER = "\n"
    }
}
