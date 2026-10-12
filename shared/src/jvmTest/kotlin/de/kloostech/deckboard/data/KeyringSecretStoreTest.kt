package de.kloostech.deckboard.data

import com.github.javakeyring.Keyring
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Round-trips a throwaway entry through the real OS keychain; skipped where none is available. */
class KeyringSecretStoreTest {
    @Test
    fun roundTripsThroughTheOsKeychain() {
        val keyring = runCatching { Keyring.create() }.getOrNull()
        assumeTrue("No OS keychain available on this machine", keyring != null)
        val store = KeyringSecretStore(keyring!!)
        val key = "test-entry-${System.nanoTime()}"
        try {
            assertNull(store.get(key))
            store.set(key, "päss wörd with spaces & symbols!")
            assertEquals("päss wörd with spaces & symbols!", store.get(key))
            store.set(key, "second")
            assertEquals("second", store.get(key))
        } finally {
            store.remove(key)
        }
        assertNull(store.get(key))
    }
}
