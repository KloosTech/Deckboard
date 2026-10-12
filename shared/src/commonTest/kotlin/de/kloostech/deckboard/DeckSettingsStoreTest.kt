package de.kloostech.deckboard

import com.russhwolf.settings.MapSettings
import de.kloostech.deckboard.data.DeckConnection
import de.kloostech.deckboard.data.DeckSettingsStore
import de.kloostech.deckboard.data.InMemorySecretStore
import de.kloostech.deckboard.data.SecretStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DeckSettingsStoreTest {
    private val connection = DeckConnection("https://cloud.example.com", "jack", "s3cret-app-password")

    @Test
    fun passwordGoesToTheSecretStoreNotPlainSettings() {
        val settings = MapSettings()
        val secrets = InMemorySecretStore()
        DeckSettingsStore(settings, secrets).save(connection)

        assertEquals("s3cret-app-password", secrets.get("app_password"))
        assertFalse(settings.hasKey("app_password"), "password must not stay in plain settings")
        assertEquals("jack", settings.getString("username", ""))
    }

    @Test
    fun savedConnectionLoadsBackAcrossInstances() {
        val settings = MapSettings()
        val secrets = InMemorySecretStore()
        DeckSettingsStore(settings, secrets).save(connection)

        assertEquals(connection, DeckSettingsStore(settings, secrets).connection.value)
    }

    @Test
    fun oldPlainPasswordIsMigratedAndRemoved() {
        val settings = MapSettings("base_url" to "https://cloud.example.com", "username" to "jack", "app_password" to "legacy-pw")
        val secrets = InMemorySecretStore()

        val loaded = DeckSettingsStore(settings, secrets).connection.value

        assertEquals("legacy-pw", loaded.appPassword)
        assertEquals("legacy-pw", secrets.get("app_password"))
        assertFalse(settings.hasKey("app_password"))
    }

    @Test
    fun blankPasswordIsRemovedEverywhere() {
        val settings = MapSettings()
        val secrets = InMemorySecretStore()
        val store = DeckSettingsStore(settings, secrets)
        store.save(connection)
        store.save(connection.copy(appPassword = ""))

        assertNull(secrets.get("app_password"))
        assertFalse(settings.hasKey("app_password"))
        assertEquals("", store.connection.value.appPassword)
    }

    @Test
    fun failingSecretStoreFallsBackToSettingsInsteadOfLosingTheLogin() {
        val broken = object : SecretStore {
            override fun get(key: String): String? = error("keystore unavailable")
            override fun set(key: String, value: String) = error("keystore unavailable")
            override fun remove(key: String) = error("keystore unavailable")
        }
        val settings = MapSettings()
        DeckSettingsStore(settings, broken).save(connection)

        assertEquals("s3cret-app-password", DeckSettingsStore(settings, broken).connection.value.appPassword)
    }
}
