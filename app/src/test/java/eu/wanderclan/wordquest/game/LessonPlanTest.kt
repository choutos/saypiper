package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.content.VocabUnit
import eu.wanderclan.wordquest.content.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LessonPlanTest {
    private fun unit(size: Int) = VocabUnit("u", "U", null, List(size) { Word("w$it", "word$it", "noun", "e$it") })

    @Test
    fun `units of 6 to 12 words split into lessons of three or four`() {
        assertEquals(listOf(3, 3), LessonPlan.introductionSizes(6))
        assertEquals(listOf(4, 3), LessonPlan.introductionSizes(7))
        assertEquals(listOf(4, 4), LessonPlan.introductionSizes(8))
        assertEquals(listOf(3, 3, 3), LessonPlan.introductionSizes(9))
        assertEquals(listOf(4, 3, 3), LessonPlan.introductionSizes(10))
        assertEquals(listOf(4, 4, 3), LessonPlan.introductionSizes(11))
        assertEquals(listOf(4, 4, 4), LessonPlan.introductionSizes(12))
    }

    @Test
    fun `lessons take words in order and end with a review of the whole unit`() {
        val unit = unit(7)
        val lessons = LessonPlan.lessons(unit)
        assertEquals(3, lessons.size)
        assertEquals(listOf("w0", "w1", "w2", "w3"), lessons[0].map { it.id })
        assertEquals(listOf("w4", "w5", "w6"), lessons[1].map { it.id })
        assertEquals(unit.words, lessons[2])
        assertEquals(3, LessonPlan.lessonCount(unit))
        assertEquals(true, LessonPlan.isReview(unit, 3))
    }

    @Test
    fun `units outside 6 to 12 words are refused`() {
        assertThrows(IllegalArgumentException::class.java) { LessonPlan.introductionSizes(5) }
        assertThrows(IllegalArgumentException::class.java) { LessonPlan.introductionSizes(13) }
    }
}
