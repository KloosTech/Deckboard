package de.kloostech.deckboard.data

import de.kloostech.deckboard.i18n.tr
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A started Nextcloud Login Flow v2: send the user to [loginUrl], then poll [pollEndpoint] with [pollToken]. */
data class LoginFlowSession(val loginUrl: String, val pollEndpoint: String, val pollToken: String)

/** What the server hands over once the user approved: a fresh app password for [loginName]. */
data class LoginFlowResult(val server: String, val loginName: String, val appPassword: String)

class LoginFlowException(message: String) : Exception(message)

private val flowJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Parses the response of `POST /index.php/login/v2`. */
fun parseLoginFlowStart(body: String): LoginFlowSession {
    val root = runCatching { flowJson.parseToJsonElement(body).jsonObject }.getOrNull()
        ?: throw LoginFlowException(tr("The server's answer wasn't what Nextcloud sends for a browser sign-in."))
    val poll = root["poll"]?.jsonObject
    val token = poll?.get("token")?.jsonPrimitive?.content
    val endpoint = poll?.get("endpoint")?.jsonPrimitive?.content
    val login = root["login"]?.jsonPrimitive?.content
    if (token.isNullOrBlank() || endpoint.isNullOrBlank() || login.isNullOrBlank()) {
        throw LoginFlowException(tr("The server's answer was incomplete."))
    }
    return LoginFlowSession(loginUrl = login, pollEndpoint = endpoint, pollToken = token)
}

/** Parses the 200 response of the poll endpoint. */
fun parseLoginFlowResult(body: String): LoginFlowResult {
    val o = flowJson.parseToJsonElement(body).jsonObject
    return LoginFlowResult(
        server = o["server"]?.jsonPrimitive?.content ?: throw LoginFlowException(tr("Sign-in result had no server.")),
        loginName = o["loginName"]?.jsonPrimitive?.content ?: throw LoginFlowException(tr("Sign-in result had no user name.")),
        appPassword = o["appPassword"]?.jsonPrimitive?.content ?: throw LoginFlowException(tr("Sign-in result had no app password.")),
    )
}

/**
 * Nextcloud "Login Flow v2": the user approves the app in their browser and the server creates an app password for it,
 * so nobody has to copy one by hand. [serverUrl] is the normalized server root.
 */
class LoginFlowClient(private val serverUrl: String, private val clientName: String = "Deckboard") {
    private val http = HttpClient {
        install(HttpTimeout) { connectTimeoutMillis = 10_000; requestTimeoutMillis = 20_000 }
        expectSuccess = false
    }

    /** Starts the flow. The [clientName] is what the user sees in Nextcloud's list of app passwords. */
    suspend fun start(): LoginFlowSession {
        val response = try {
            http.post("$serverUrl/index.php/login/v2") { header(HttpHeaders.UserAgent, clientName) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw LoginFlowException(tr("Couldn't reach the server ({detail}).", "detail" to (e.message ?: e::class.simpleName)))
        }
        if (!response.status.isSuccess()) {
            throw LoginFlowException(
                if (response.status.value == 404) tr("This server doesn't offer browser sign-in. Enter an app password instead.")
                else tr("The server refused browser sign-in ({code}).", "code" to response.status.value),
            )
        }
        val session = parseLoginFlowStart(response.bodyAsText())
        // Never follow a poll endpoint that downgrades an https server to plain http.
        if (serverUrl.startsWith("https://", ignoreCase = true) && !session.pollEndpoint.startsWith("https://", ignoreCase = true)) {
            throw LoginFlowException(tr("The server answered with an insecure (http) sign-in address."))
        }
        if (!session.loginUrl.startsWith("https://", ignoreCase = true) && !session.loginUrl.startsWith("http://", ignoreCase = true)) {
            throw LoginFlowException(tr("The server answered with an unusable sign-in address."))
        }
        return session
    }

    /**
     * Polls until the user approved (returns the result), or [timeoutMillis] passed (returns null). Network hiccups while
     * waiting (an iOS app sent to the background, a flaky connection) don't end the wait; cancel the coroutine to stop.
     */
    suspend fun awaitResult(session: LoginFlowSession, timeoutMillis: Long = 20 * 60_000L, intervalMillis: Long = 2_000L): LoginFlowResult? =
        withTimeoutOrNull(timeoutMillis) {
            while (true) {
                try {
                    val response = http.submitForm(url = session.pollEndpoint, formParameters = parameters { append("token", session.pollToken) })
                    if (response.status.isSuccess()) return@withTimeoutOrNull parseLoginFlowResult(response.bodyAsText())
                    // 404 = not approved yet; anything else is treated the same, the flow just keeps waiting until it expires.
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // transient network problem: try again
                }
                delay(intervalMillis)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }

    fun close() = http.close()
}
