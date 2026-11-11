package eu.wanderclan.wordquest.game

import java.time.LocalDate

/** The points of one profile on one local day. */
data class DayActivity(val day: LocalDate, val points: Int)

/** Total points and the daily streak (ADR section 6), from the stored days and the device's local date. */
object Rewards {
    fun totalPoints(activity: List<DayActivity>): Int = activity.sumOf { it.points }

    /**
     * Consecutive days played up to [today]. A streak stays alive until the end of the day after the
     * last day played, and resets after a missed day. Days after [today], which only exist when the
     * device clock was moved back, are not counted.
     */
    fun streak(activity: List<DayActivity>, today: LocalDate): Int {
        val played = activity.filter { it.points > 0 }.map { it.day }.toSet()
        var day = if (today in played) today else today.minusDays(1)
        var days = 0
        while (day in played) {
            days++
            day = day.minusDays(1)
        }
        return days
    }
}
