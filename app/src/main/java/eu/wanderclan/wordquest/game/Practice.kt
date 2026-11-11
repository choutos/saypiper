package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.content.Catalog
import java.time.LocalDate

/** When a word that has history is due again for one profile. */
data class ScheduledWord(val wordId: String, val dueDay: LocalDate)

/** Practice (ADR section 6): which words are due, as a request through the lesson contract. */
object Practice {
    const val MAX_WORDS = 10

    /**
     * Up to [MAX_WORDS] words due on [today], the longest overdue first. Only words with history are
     * scheduled, and ids that are no longer in the content are ignored.
     */
    fun dueWords(schedule: List<ScheduledWord>, today: LocalDate, catalog: Catalog): List<String> =
        schedule.filter { !it.dueDay.isAfter(today) && catalog.word(it.wordId) != null }
            .sortedWith(compareBy<ScheduledWord> { it.dueDay }.thenBy { it.wordId })
            .take(MAX_WORDS)
            .map { it.wordId }

    /** Null when nothing is due: an empty practice is never started. */
    fun request(dueWordIds: List<String>): LessonRequest.Practice? =
        dueWordIds.takeIf { it.isNotEmpty() }?.let { LessonRequest.Practice(it) }
}
