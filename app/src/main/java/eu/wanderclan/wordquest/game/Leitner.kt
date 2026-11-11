package eu.wanderclan.wordquest.game

import java.time.LocalDate

/** Today's local date. Injected so tests can move time. */
fun interface DayClock {
    fun today(): LocalDate
}

/** What is known about one word for one profile. */
data class WordProgress(val box: Int, val dueDay: LocalDate, val correctCount: Int, val wrongCount: Int)

/**
 * Five-box Leitner scheduling (ADR section 6). A session gives each word one outcome:
 * wrong sends it to box 1, due today; right moves it up one box, but only when it was due.
 * A word with no history counts as box 1 and due.
 */
class Leitner(private val clock: DayClock) {

    fun apply(previous: WordProgress?, correct: Boolean): WordProgress {
        val today = clock.today()
        val box = previous?.box ?: 1
        val due = previous?.dueDay ?: today
        val correctCount = (previous?.correctCount ?: 0) + if (correct) 1 else 0
        val wrongCount = (previous?.wrongCount ?: 0) + if (correct) 0 else 1
        return when {
            !correct -> WordProgress(1, today, correctCount, wrongCount)
            !due.isAfter(today) -> {
                val next = minOf(box + 1, TOP_BOX)
                WordProgress(next, today.plusDays(interval(next)), correctCount, wrongCount)
            }
            else -> WordProgress(box, due, correctCount, wrongCount)
        }
    }

    companion object {
        const val TOP_BOX = 5

        /** Days until a word in box n (index n - 1) is due again. */
        private val intervalDays = listOf(0L, 1L, 3L, 7L, 21L)

        fun interval(box: Int): Long = intervalDays[box - 1]
    }
}
