package de.kloostech.deckboard.data

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import java.util.prefs.Preferences

private const val SERVICE = "de.kloostech.deckboard"

/**
 * Desktop: the operating system's keychain (macOS Keychain, Windows Credential Manager, Linux Secret Service).
 * Where none is available (e.g. a headless Linux box) it falls back to plain user preferences, so the app still works;
 * [isSecure] tells which one is in use.
 */
actual fun createSecretStore(): SecretStore =
    runCatching { KeyringSecretStore(Keyring.create()) }.getOrElse { PlainPreferencesSecretStore() }

class KeyringSecretStore(private val keyring: Keyring) : SecretStore {
    override fun get(key: String): String? =
        try { keyring.getPassword(SERVICE, key) } catch (_: PasswordAccessException) { null }

    override fun set(key: String, value: String) {
        keyring.setPassword(SERVICE, key, value)
    }

    override fun remove(key: String) {
        try { keyring.deletePassword(SERVICE, key) } catch (_: PasswordAccessException) { /* nothing stored */ }
    }
}

/** Unencrypted fallback for systems without a keychain. */
class PlainPreferencesSecretStore : SecretStore {
    private val prefs: Preferences = Preferences.userRoot().node("de/kloostech/deckboard/secrets")
    override fun get(key: String): String? = prefs.get(key, null)
    override fun set(key: String, value: String) { prefs.put(key, value) }
    override fun remove(key: String) { prefs.remove(key) }
}
