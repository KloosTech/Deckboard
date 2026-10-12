package de.kloostech.deckboard

import de.kloostech.deckboard.data.LoginFlowException
import de.kloostech.deckboard.data.parseLoginFlowResult
import de.kloostech.deckboard.data.parseLoginFlowStart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoginFlowTest {
    @Test
    fun parsesTheStartResponse() {
        val s = parseLoginFlowStart(
            """{"poll":{"token":"abc123","endpoint":"https:\/\/cloud.example.com\/login\/v2\/poll"},"login":"https:\/\/cloud.example.com\/login\/v2\/flow\/xyz"}""",
        )
        assertEquals("abc123", s.pollToken)
        assertEquals("https://cloud.example.com/login/v2/poll", s.pollEndpoint)
        assertEquals("https://cloud.example.com/login/v2/flow/xyz", s.loginUrl)
    }

    @Test
    fun rejectsIncompleteOrForeignResponses() {
        assertFailsWith<LoginFlowException> { parseLoginFlowStart("""{"poll":{"token":"t"},"login":"https://x"}""") }
        assertFailsWith<LoginFlowException> { parseLoginFlowStart("<html>not json</html>") }
        assertFailsWith<LoginFlowException> { parseLoginFlowStart("""{"login":"https://x"}""") }
    }

    @Test
    fun parsesTheApprovedResult() {
        val r = parseLoginFlowResult("""{"server":"https:\/\/cloud.example.com","loginName":"jack","appPassword":"A1b2-C3d4-E5f6"}""")
        assertEquals("https://cloud.example.com", r.server)
        assertEquals("jack", r.loginName)
        assertEquals("A1b2-C3d4-E5f6", r.appPassword)
    }

    @Test
    fun rejectsAnIncompleteResult() {
        assertFailsWith<LoginFlowException> { parseLoginFlowResult("""{"server":"https://x","loginName":"jack"}""") }
    }
}
