package de.kloostech.deckboard.data

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Starts a real Login Flow v2 on the configured server, never approves it, and checks that polling reports "not yet".
 * The pending flow just expires on the server. Skipped when no server is configured.
 */
class LoginFlowIntegrationTest {
    init { de.kloostech.deckboard.i18n.Language.current = "en" } // message text is asserted in English

    @Test
    fun startsAFlowAndPollingStaysPendingUntilApproved() = runBlocking {
        val connection = DeckSettingsStore().connection.value
        assumeTrue("No Deck backend configured", connection.isConfigured)

        val client = LoginFlowClient(connection.baseUrl, clientName = "Deckboard integration test")
        try {
            val session = client.start()
            assertTrue(session.loginUrl.startsWith("http"), "login url: ${session.loginUrl}")
            assertTrue(session.pollToken.isNotBlank())
            assertNull(client.awaitResult(session, timeoutMillis = 2_500, intervalMillis = 500), "an unapproved flow must not return credentials")
        } finally {
            client.close()
        }
    }

    @Test
    fun unknownServerGivesAReadableError() = runBlocking {
        val client = LoginFlowClient("https://no-such-host.invalid")
        try {
            val e = runCatching { client.start() }.exceptionOrNull()
            assertTrue(e is LoginFlowException, "expected LoginFlowException, got $e")
            assertTrue(e.message!!.contains("reach"), e.message)
        } finally {
            client.close()
        }
    }
}
