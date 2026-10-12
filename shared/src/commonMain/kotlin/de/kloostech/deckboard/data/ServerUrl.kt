package de.kloostech.deckboard.data

/** Deck REST API version this client speaks. Needs Deck 1.3.0+ (API v1.0 predates it). */
const val DECK_API_VERSION = "1.1"

/** First Deck app release that serves [DECK_API_VERSION]. */
val MIN_DECK_VERSION = intArrayOf(1, 3, 0)

/**
 * A user-entered server address reduced to the Nextcloud root, e.g. `https://cloud.example.com`
 * or `https://example.com/nextcloud` for subfolder installs. No trailing slash.
 */
data class NormalizedServerUrl(
    val url: String,
    /** The input had no scheme, so https was assumed. */
    val assumedHttps: Boolean,
    /** Plain http — credentials would cross the network unencrypted. */
    val insecure: Boolean,
    /** A trailing Nextcloud path (e.g. `/index.php/apps/deck/...`) was cut off the input. */
    val trimmedPath: Boolean,
)

/** First path segments that belong to Nextcloud itself, never to the folder it's installed in. */
private val NEXTCLOUD_PATH_MARKERS = setOf(
    "index.php", "apps", "ocs", "remote.php", "status.php", "login", "settings", "core", "dashboard",
)

private val SCHEME_REGEX = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://")
private val HOST_REGEX = Regex("^(\\[[0-9a-f:.]+]|[a-z0-9]([a-z0-9._-]*[a-z0-9])?)$")

/**
 * Extracts the Nextcloud server root from whatever the user typed or pasted: a bare host
 * (`cloud.example.com`), a browser address-bar URL (`https://cloud.example.com/apps/deck/#/board/3`)
 * or a full API URL. Keeps a subfolder install path (`/nextcloud`), drops everything from the
 * first Nextcloud-owned segment onward plus query and fragment. Returns null when nothing usable
 * remains.
 */
fun normalizeServerUrl(input: String): NormalizedServerUrl? {
    var rest = input.trim().trim('"', '\'', '<', '>')
    if (rest.isEmpty() || rest.any { it.isWhitespace() }) return null

    val scheme: String
    val assumed: Boolean
    val schemeMatch = SCHEME_REGEX.find(rest)
    if (schemeMatch != null) {
        scheme = schemeMatch.groupValues[1].lowercase()
        if (scheme != "http" && scheme != "https") return null
        rest = rest.substring(schemeMatch.value.length)
        assumed = false
    } else {
        rest = rest.removePrefix("//")
        scheme = "https"
        assumed = true
    }

    rest = rest.substringBefore('#').substringBefore('?')
    val authority = rest.substringBefore('/').substringAfterLast('@').lowercase()
    val path = rest.substringAfter('/', "")

    val portStart = authority.lastIndexOf(':').takeIf { it > authority.lastIndexOf(']') }
    val host = if (portStart != null) authority.substring(0, portStart) else authority
    val port = if (portStart != null) authority.substring(portStart + 1) else null
    if (!HOST_REGEX.matches(host)) return null
    if (port != null && (port.isEmpty() || !port.all(Char::isDigit))) return null

    val segments = path.split('/').filter { it.isNotEmpty() }
    val cut = segments.indexOfFirst { it.lowercase() in NEXTCLOUD_PATH_MARKERS }
    val kept = if (cut >= 0) segments.take(cut) else segments

    val url = buildString {
        append(scheme).append("://").append(authority)
        kept.forEach { append('/').append(it) }
    }
    return NormalizedServerUrl(
        url = url,
        assumedHttps = assumed,
        insecure = scheme == "http",
        trimmedPath = kept.size < segments.size,
    )
}

/** Parses `"1.12.1"` / `"1.12.1-beta"` into numeric components; null if there's no number. */
fun parseVersion(raw: String): List<Int>? {
    val parts = raw.trim().takeWhile { it.isDigit() || it == '.' }.split('.').mapNotNull { it.toIntOrNull() }
    return parts.takeIf { it.isNotEmpty() }
}

/** True when [version] is older than [minimum] (missing components count as 0). */
fun isVersionBelow(version: List<Int>, minimum: IntArray): Boolean {
    for (i in 0 until maxOf(version.size, minimum.size)) {
        val a = version.getOrElse(i) { 0 }
        val b = minimum.getOrElse(i) { 0 }
        if (a != b) return a < b
    }
    return false
}
