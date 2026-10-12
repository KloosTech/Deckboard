package de.kloostech.deckboard

import de.kloostech.deckboard.ui.shortTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ShortTimeTest {
    @Test
    fun convertsToTheGivenTimeZone() {
        assertEquals("2026-10-12 14:34", shortTime("2026-10-12T12:34:56+00:00", TimeZone.of("Europe/Berlin")))
        assertEquals("2026-10-12 12:34", shortTime("2026-10-12T12:34:56Z", TimeZone.UTC))
        assertEquals("2026-10-12 12:34", shortTime("2026-10-12T14:34:56+02:00", TimeZone.UTC))
    }

    @Test
    fun passesThroughWhatItCannotParse() {
        assertEquals("2026-10-12 12:34", shortTime("2026-10-12 12:34:56", TimeZone.UTC)) // no offset: trimmed, not converted
        assertEquals("yesterday", shortTime("yesterday", TimeZone.UTC))
    }
}
