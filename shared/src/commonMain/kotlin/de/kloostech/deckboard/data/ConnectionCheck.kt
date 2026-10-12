package de.kloostech.deckboard.data

import de.kloostech.deckboard.i18n.tr
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Outcome of [checkConnection]: a headline plus a plain-language hint on what to do next. */
sealed interface ConnectionReport {
    val message: String
    val hint: String?

    data class Success(
        val boardCount: Int,
        val serverVersion: String?,
        val deckVersion: String?,
        override val hint: String? = null,
    ) : ConnectionReport {
        override val message: String
            get() {
                val boards = if (boardCount == 1) tr("1 board") else tr("{count} boards", "count" to boardCount)
                val versions = listOfNotNull(
                    serverVersion?.let { "Nextcloud $it" },
                    deckVersion?.let { "Deck $it" },
                ).joinToString(", ")
                return tr("Connected — found {boards}", "boards" to boards) + if (versions.isNotEmpty()) " ($versions)" else ""
            }
    }

    data class Failure(override val message: String, override val hint: String?) : ConnectionReport
}

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Walks the connection in the order a user would debug it — server reachable, is Nextcloud,
 * credentials accepted, Deck installed and new enough — and stops at the first problem with a
 * message that says what to fix. [connection] must already hold a normalized server root.
 */
suspend fun checkConnection(connection: DeckConnection): ConnectionReport {
    val server = connection.baseUrl
    val http = HttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
        }
        install(Auth) {
            basic {
                credentials { BasicAuthCredentials(connection.username, connection.appPassword) }
                sendWithoutRequest { true }
            }
        }
        expectSuccess = false
    }
    try {
        // 1) Reachable + actually Nextcloud? status.php is public and unauthenticated.
        val status = try {
            http.get("$server/status.php")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return describeNetworkError(e, server)
        }
        val statusJson = if (status.status.isSuccess()) {
            runCatching { lenientJson.parseToJsonElement(status.bodyAsText()).jsonObject }.getOrNull()
        } else null
        if (statusJson == null || "versionstring" !in statusJson) {
            return ConnectionReport.Failure(
                tr("No Nextcloud server found at {server}", "server" to server),
                tr(
                    "Check the address. If Nextcloud lives in a subfolder, include it " +
                        "(e.g. example.com/nextcloud). Copying the address from your browser works too.",
                ),
            )
        }
        val serverVersion = statusJson["versionstring"]?.jsonPrimitive?.content
        if (statusJson["maintenance"]?.jsonPrimitive?.boolean == true) {
            return ConnectionReport.Failure(
                tr("The server is in maintenance mode"),
                tr("Try again in a few minutes, or ask your server admin."),
            )
        }

        // 2) Deck app version, for diagnosis only. Tolerate any failure here.
        val deckVersion = runCatching {
            val body = http.get("$server/ocs/v1.php/cloud/capabilities?format=json") {
                header("OCS-APIRequest", "true")
                header("Accept", "application/json")
            }.bodyAsText()
            lenientJson.parseToJsonElement(body).jsonObject["ocs"]?.jsonObject
                ?.get("data")?.jsonObject?.get("capabilities")?.jsonObject
                ?.get("deck")?.jsonObject?.get("version")?.jsonPrimitive?.content
        }.getOrNull()

        if (deckVersion != null) {
            val parsed = parseVersion(deckVersion)
            if (parsed != null && isVersionBelow(parsed, MIN_DECK_VERSION)) {
                return ConnectionReport.Failure(
                    tr("Your Deck app ({version}) is too old", "version" to deckVersion),
                    tr(
                        "Deckboard needs Deck {version} or newer. Ask your server admin to update the Deck app.",
                        "version" to MIN_DECK_VERSION.joinToString("."),
                    ),
                )
            }
        }

        // 3) Credentials + Deck API, in one authenticated call.
        val boards = http.get("${connection.apiBaseUrl}/boards") {
            header("OCS-APIRequest", "true")
            header("Accept", "application/json")
        }
        return when {
            boards.status.isSuccess() -> {
                val count = runCatching { lenientJson.parseToJsonElement(boards.bodyAsText()) }
                    .getOrNull()?.let { (it as? JsonArray)?.size }
                    ?: return ConnectionReport.Failure(
                        tr("The server answered, but not like the Deck app"),
                        tr("A proxy or login page may be in the way. Open the address in your browser to check."),
                    )
                ConnectionReport.Success(
                    boardCount = count,
                    serverVersion = serverVersion,
                    deckVersion = deckVersion,
                    hint = if (count == 0) tr("No boards yet — create one in Deckboard or in Deck on the web.") else null,
                )
            }
            boards.status.value == 401 -> ConnectionReport.Failure(
                tr("Username or app password was rejected"),
                tr(
                    "Use your Nextcloud username (not your email unless it's your login) and an app password " +
                        "from Settings → Security → Devices & sessions. Your normal password won't work if 2FA is on.",
                ),
            )
            boards.status.value == 403 -> ConnectionReport.Failure(
                tr("Access denied (403)"),
                tr("The account or a firewall in front of the server blocks API access. Ask your server admin."),
            )
            boards.status.value == 404 -> ConnectionReport.Failure(
                tr("The Deck app wasn't found on this server"),
                tr(
                    "Deck may not be installed or enabled for your account. " +
                        "Check Apps → Deck in Nextcloud's web interface.",
                ),
            )
            boards.status.value == 429 -> ConnectionReport.Failure(
                tr("Too many attempts (429)"),
                tr("The server is rate-limiting you. Wait a minute and try again."),
            )
            else -> ConnectionReport.Failure(
                tr("Unexpected server response ({code})", "code" to boards.status.value),
                tr("Check the server address and try again. If it keeps happening, the server logs will tell more."),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return describeNetworkError(e, server)
    } finally {
        http.close()
    }
}

private fun describeNetworkError(e: Exception, server: String): ConnectionReport.Failure {
    val detail = (e.message ?: e::class.simpleName ?: tr("unknown error"))
    val lower = detail.lowercase()
    return when {
        "timeout" in lower || "timed out" in lower -> ConnectionReport.Failure(
            tr("The server took too long to answer"),
            tr("Check your internet connection and that {server} is reachable from this device.", "server" to server),
        )
        "ssl" in lower || "certificate" in lower || "trust" in lower || "handshake" in lower -> ConnectionReport.Failure(
            tr("Secure connection failed"),
            tr(
                "The server's HTTPS certificate isn't trusted by this device (expired, self-signed or " +
                    "wrong name). Fix the certificate on the server; Deckboard won't bypass it.",
            ),
        )
        "unknownhost" in lower || "unable to resolve" in lower || "nodename" in lower ||
            "not known" in lower || "could not be found" in lower -> ConnectionReport.Failure(
            tr("Can't find that server"),
            tr("Check the spelling of the address and your internet connection."),
        )
        else -> ConnectionReport.Failure(
            tr("Couldn't reach the server"),
            tr("Check the address and your connection. ({detail})", "detail" to detail),
        )
    }
}
