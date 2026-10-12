package de.kloostech.deckboard.data

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings

/** iOS and macOS: the system Keychain, scoped to this app by [KEYCHAIN_SERVICE]. */
@OptIn(ExperimentalSettingsImplementation::class)
actual fun createSecretStore(): SecretStore = SettingsSecretStore(KeychainSettings(service = KEYCHAIN_SERVICE))

private const val KEYCHAIN_SERVICE = "de.kloostech.deckboard"
