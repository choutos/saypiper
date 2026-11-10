package eu.wanderclan.wordquest.game

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LeitnerTest {
    private class FakeClock(var day: LocalDate) : DayClock {
        override fun today() = day
    }

    private val start = LocalDate.of(2026, 11, 10)
    private val clock = FakeClock(start)
    private val leitner = Leitner(clock)

    @Test
    fun `a new word answered right goes to box 2, due tomorrow`() {
        val p = leitner.apply(null, correct = true)
        assertEquals(WordProgress(2, start.plusDays(1), 1, 0), p)
    }

    @Test
    fun `a mistake sends the word to box 1, due today`() {
        clock.day = start.plusDays(30)
        val p = leitner.apply(WordProgress(4, start.plusDays(30), 5, 0), correct = false)
        assertEquals(WordProgress(1, start.plusDays(30), 5, 1), p)
    }

    @Test
    fun `all right moves a due word up one box`() {
        var p = leitner.apply(null, true) // box 2, due +1
        clock.day = start.plusDays(1)
        p = leitner.apply(p, true)
        assertEquals(3, p.box)
        assertEquals(clock.day.plusDays(3), p.dueDay)
        clock.day = clock.day.plusDays(5) // overdue still counts as due
        p = leitner.apply(p, true)
        assertEquals(4, p.box)
        assertEquals(clock.day.plusDays(7), p.dueDay)
    }

    @Test
    fun `replaying a lesson on the same day promotes nothing`() {
        val first = leitner.apply(null, true)
        val second = leitner.apply(first, true)
        val third = leitner.apply(second, true)
        assertEquals(2, third.box)
        assertEquals(first.dueDay, third.dueDay)
        assertEquals(3, third.correctCount)
    }

    @Test
    fun `box 5 is the top`() {
        var p = WordProgress(5, start, 9, 0)
        p = leitner.apply(p, true)
        assertEquals(5, p.box)
        assertEquals(start.plusDays(21), p.dueDay)
    }

    @Test
    fun `intervals are 0, 1, 3, 7 and 21 days`() {
        assertEquals(listOf(0L, 1L, 3L, 7L, 21L), (1..Leitner.TOP_BOX).map(Leitner::interval))
    }
}
