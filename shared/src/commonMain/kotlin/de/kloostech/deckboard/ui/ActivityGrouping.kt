package de.kloostech.deckboard.ui

import de.kloostech.deckboard.i18n.tr
import de.kloostech.deckboard.data.ActivityEntry
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** A run of activity under one heading such as "Today" or "September 2026". */
data class ActivityGroup(val title: String, val entries: List<ActivityEntry>)

private fun monthName(month: Month): String = when (month) {
    Month.JANUARY -> tr("January")
    Month.FEBRUARY -> tr("February")
    Month.MARCH -> tr("March")
    Month.APRIL -> tr("April")
    Month.MAY -> tr("May")
    Month.JUNE -> tr("June")
    Month.JULY -> tr("July")
    Month.AUGUST -> tr("August")
    Month.SEPTEMBER -> tr("September")
    Month.OCTOBER -> tr("October")
    Month.NOVEMBER -> tr("November")
    Month.DECEMBER -> tr("December")
}

/**
 * Groups [entries] (newest first, as the server returns them) by how long ago they happened, judged on calendar
 * days in [timeZone]: Today, Yesterday, Last 7 days, Last 30 days, then one group per month ("September 2026").
 * Order is kept; entries with an unreadable date go into "Earlier".
 */
@OptIn(ExperimentalTime::class)
fun groupActivities(
    entries: List<ActivityEntry>,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<ActivityGroup> {
    val today = now.toLocalDateTime(timeZone).date
    val groups = LinkedHashMap<String, MutableList<ActivityEntry>>()
    for (entry in entries) {
        val date = runCatching { Instant.parse(entry.datetime).toLocalDateTime(timeZone).date }.getOrNull()
        val title = if (date == null) {
            tr("Earlier")
        } else {
            val daysAgo = today.toEpochDays() - date.toEpochDays()
            when {
                daysAgo <= 0 -> tr("Today")
                daysAgo == 1L -> tr("Yesterday")
                daysAgo <= 7 -> tr("Last 7 days")
                daysAgo <= 30 -> tr("Last 30 days")
                else -> "${monthName(date.month)} ${date.year}"
            }
        }
        groups.getOrPut(title) { mutableListOf() }.add(entry)
    }
    return groups.map { (title, list) -> ActivityGroup(title, list) }
}
