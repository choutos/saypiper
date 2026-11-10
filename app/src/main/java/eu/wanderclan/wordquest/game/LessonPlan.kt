package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.content.VocabUnit
import eu.wanderclan.wordquest.content.Word

/**
 * How a unit is split into lessons (ADR section 6): its words in order, in introduction lessons of
 * three or four words (6 → 3+3, 7 → 4+3, … 12 → 4+4+4), then one review lesson over the whole unit.
 */
object LessonPlan {
    const val MIN_UNIT_WORDS = 6
    const val MAX_UNIT_WORDS = 12

    fun introductionSizes(wordCount: Int): List<Int> {
        require(wordCount in MIN_UNIT_WORDS..MAX_UNIT_WORDS) { "A unit has 6 to 12 words, not $wordCount" }
        val lessons = (wordCount + 3) / 4
        val base = wordCount / lessons
        val extra = wordCount % lessons
        return List(lessons) { if (it < extra) base + 1 else base }
    }

    /** All lessons of a unit, numbered from 1: the introductions, then the review. */
    fun lessons(unit: VocabUnit): List<List<Word>> {
        val introductions = mutableListOf<List<Word>>()
        var start = 0
        for (size in introductionSizes(unit.words.size)) {
            introductions += unit.words.subList(start, start + size)
            start += size
        }
        return introductions + listOf(unit.words)
    }

    fun lessonCount(unit: VocabUnit): Int = introductionSizes(unit.words.size).size + 1

    fun isReview(unit: VocabUnit, number: Int): Boolean = number == lessonCount(unit)
}
