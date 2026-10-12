package de.kloostech.deckboard

import de.kloostech.deckboard.data.ActivityEntry
import de.kloostech.deckboard.i18n.Language
import de.kloostech.deckboard.ui.groupActivities
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ActivityGroupingTest {
    init { Language.current = "en" } // titles are asserted in English

    private val now = Instant.parse("2026-10-12T12:00:00Z")

    private fun at(id: Long, iso: String) = ActivityEntry(id, iso, "jack", "did something $id", boardId = 1, cardId = null)

    private fun titles(vararg entries: ActivityEntry) = groupActivities(entries.toList(), now, TimeZone.UTC).map { it.title }

    @Test
    fun bucketsByCalendarDay() {
        assertEquals(
            listOf("Today", "Yesterday", "Last 7 days", "Last 30 days", "September 2026", "June 2025"),
            titles(
                at(1, "2026-10-12T00:05:00+00:00"),
                at(2, "2026-10-11T23:59:00+00:00"),
                at(3, "2026-10-05T10:00:00+00:00"),
                at(4, "2026-09-20T10:00:00+00:00"),
                at(5, "2026-09-05T10:00:00+00:00"),
                at(6, "2025-06-01T10:00:00+00:00"),
            ),
        )
    }

    @Test
    fun keepsOrderAndMergesSameBucket() {
        val groups = groupActivities(
            listOf(at(1, "2026-10-12T11:00:00+00:00"), at(2, "2026-10-12T09:00:00+00:00"), at(3, "2026-10-11T09:00:00+00:00")),
            now, TimeZone.UTC,
        )
        assertEquals(listOf("Today", "Yesterday"), groups.map { it.title })
        assertEquals(listOf(1L, 2L), groups[0].entries.map { it.id })
    }

    @Test
    fun usesTheGivenTimeZoneForTheDay() {
        // 23:30 UTC on the 11th is already the 12th in Berlin (UTC+2): "Today" there, "Yesterday" in UTC.
        val e = at(1, "2026-10-11T23:30:00+00:00")
        assertEquals("Yesterday", groupActivities(listOf(e), now, TimeZone.UTC).single().title)
        assertEquals("Today", groupActivities(listOf(e), now, TimeZone.of("Europe/Berlin")).single().title)
    }

    @Test
    fun boundariesAndBadDates() {
        assertEquals(listOf("Last 7 days"), titles(at(1, "2026-10-05T12:00:00Z")))   // 7 days ago
        assertEquals(listOf("Last 30 days"), titles(at(1, "2026-10-04T12:00:00Z")))  // 8 days ago
        assertEquals(listOf("Last 30 days"), titles(at(1, "2026-09-12T12:00:00Z")))  // 30 days ago
        assertEquals(listOf("September 2026"), titles(at(1, "2026-09-11T12:00:00Z"))) // 31 days ago
        assertEquals(listOf("Earlier"), titles(at(1, "not a date")))
    }
}
