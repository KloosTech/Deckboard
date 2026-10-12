package de.kloostech.deckboard.data

import com.russhwolf.settings.Settings

/**
 * Storage for secrets (the Nextcloud app password), kept apart from ordinary settings so it can live in the
 * platform's secure storage: Android Keystore, Apple Keychain, the OS keychain on desktop.
 */
interface SecretStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
}

/** Secure storage for the current platform. Never throws; see each platform's actual for fallbacks. */
expect fun createSecretStore(): SecretStore

/** Keeps secrets in memory only; used for tests and for contexts without a platform store (e.g. IDE previews). */
class InMemorySecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun set(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/** Adapts a [Settings] implementation that is itself secure (the Keychain on Apple platforms) to [SecretStore]. */
class SettingsSecretStore(private val settings: Settings) : SecretStore {
    override fun get(key: String): String? = settings.getStringOrNull(key)
    override fun set(key: String, value: String) { settings.putString(key, value) }
    override fun remove(key: String) { settings.remove(key) }
}
