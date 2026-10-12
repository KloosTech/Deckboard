package de.kloostech.deckboard

import de.kloostech.deckboard.data.DeckConnection
import de.kloostech.deckboard.data.isVersionBelow
import de.kloostech.deckboard.data.normalizeServerUrl
import de.kloostech.deckboard.data.parseVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerUrlTest {

    private fun url(input: String) = normalizeServerUrl(input)?.url

    @Test
    fun bareHostGetsHttps() {
        val n = assertNotNull(normalizeServerUrl("cloud.example.com"))
        assertEquals("https://cloud.example.com", n.url)
        assertTrue(n.assumedHttps)
        assertFalse(n.insecure)
    }

    @Test
    fun fullApiUrlIsReducedToServerRoot() {
        assertEquals("https://cloud.example.com", url("https://cloud.example.com/index.php/apps/deck/api/v1.0"))
        assertEquals("https://cloud.example.com", url("https://cloud.example.com/index.php/apps/deck/api/v1.1/"))
        assertEquals("https://x.de", url("https://x.de/ocs/v2.php/apps/deck/api/v1.1"))
    }

    @Test
    fun browserUrlsAreReduced() {
        assertEquals("https://x.de", url("https://x.de/apps/deck/#/board/3"))
        assertEquals("https://x.de", url("https://x.de/index.php/login?redirect_url=/apps/deck"))
        assertEquals("https://x.de", url("https://x.de/apps/dashboard/"))
    }

    @Test
    fun subfolderInstallIsKept() {
        assertEquals("https://x.de/nextcloud", url("x.de/nextcloud"))
        assertEquals("https://x.de/nextcloud", url("https://x.de/nextcloud/index.php/apps/deck/"))
        assertEquals("https://x.de/my/cloud", url("https://x.de/my/cloud/"))
    }

    @Test
    fun portsSchemesAndCaseAreHandled() {
        assertEquals("https://x.de:8443", url("https://X.DE:8443/apps/deck"))
        assertEquals("http://192.168.1.5:8080", url("http://192.168.1.5:8080"))
        assertTrue(assertNotNull(normalizeServerUrl("http://nas.local")).insecure)
        assertEquals("https://x.de", url("  \"https://x.de/\"  "))
        assertEquals("https://x.de", url("//x.de"))
        assertEquals("https://x.de", url("https://user@x.de"))
    }

    @Test
    fun trimmedPathFlag() {
        assertTrue(assertNotNull(normalizeServerUrl("https://x.de/apps/deck")).trimmedPath)
        assertFalse(assertNotNull(normalizeServerUrl("https://x.de/nextcloud")).trimmedPath)
    }

    @Test
    fun garbageIsRejected() {
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("   "))
        assertNull(normalizeServerUrl("not a url"))
        assertNull(normalizeServerUrl("ftp://x.de"))
        assertNull(normalizeServerUrl("https://"))
        assertNull(normalizeServerUrl("https://x.de:abc"))
        assertNull(normalizeServerUrl("https://exa mple.de"))
    }

    @Test
    fun apiUrlsAreDerivedFromServerRoot() {
        val c = DeckConnection("https://x.de/nextcloud", "u", "p")
        assertEquals("https://x.de/nextcloud/index.php/apps/deck/api/v1.1", c.apiBaseUrl)
        assertEquals("https://x.de/nextcloud/ocs/v2.php/apps/deck/api/v1.1", c.ocsApiBaseUrl)
    }

    @Test
    fun versionComparison() {
        val min = intArrayOf(1, 3, 0)
        assertTrue(isVersionBelow(parseVersion("1.2.9")!!, min))
        assertTrue(isVersionBelow(parseVersion("0.8.0")!!, min))
        assertFalse(isVersionBelow(parseVersion("1.3.0")!!, min))
        assertFalse(isVersionBelow(parseVersion("1.12.1")!!, min))
        assertFalse(isVersionBelow(parseVersion("1.15.0-beta.1")!!, min))
        assertNull(parseVersion("dev"))
    }
}
