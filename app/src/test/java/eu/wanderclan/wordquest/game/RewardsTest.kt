package eu.wanderclan.wordquest.game

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RewardsTest {
    private val today = LocalDate.of(2026, 11, 10)
    private fun played(vararg daysAgo: Long) = daysAgo.map { DayActivity(today.minusDays(it), 20) }

    @Test
    fun `points are 10 for finishing and 5 per star`() {
        val result = { stars: Int -> LessonResult("s", LessonRequest.Practice(listOf("dog")), emptyList(), stars) }
        assertEquals(listOf(15, 20, 25), (1..3).map { Scoring.points(result(it)) })
        assertEquals(60, Rewards.totalPoints(played(0, 1, 5)))
    }

    @Test
    fun `no days played, no streak`() {
        assertEquals(0, Rewards.streak(emptyList(), today))
        assertEquals(0, Rewards.totalPoints(emptyList()))
    }

    @Test
    fun `the streak grows on consecutive days, across midnight`() {
        assertEquals(1, Rewards.streak(played(0), today))
        // Played late yesterday and just after midnight: two local dates in a row.
        assertEquals(2, Rewards.streak(played(1, 0), today))
        assertEquals(4, Rewards.streak(played(0, 1, 2, 3, 7), today))
    }

    @Test
    fun `the streak lasts until the end of the next day, and resets after a missed day`() {
        assertEquals(3, Rewards.streak(played(1, 2, 3), today))
        assertEquals(0, Rewards.streak(played(2, 3, 4), today))
        assertEquals(1, Rewards.streak(played(0, 2, 3), today))
    }

    @Test
    fun `a clock moved backwards does not inflate the streak`() {
        // Played on the 11th and 12th, then the device clock is set back to the 10th.
        val activity = listOf(DayActivity(today.plusDays(1), 20), DayActivity(today.plusDays(2), 20))
        assertEquals(0, Rewards.streak(activity, today))
        assertEquals(1, Rewards.streak(activity + DayActivity(today, 15), today))
    }
}
