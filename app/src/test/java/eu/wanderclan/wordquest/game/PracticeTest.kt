package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.content.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PracticeTest {
    private val catalog = Fixtures.catalog()
    private val today = LocalDate.of(2026, 11, 10)
    private fun due(wordId: String, daysAgo: Long) = ScheduledWord(wordId, today.minusDays(daysAgo))

    @Test
    fun `only scheduled words that are due, the longest overdue first`() {
        val schedule = listOf(due("rabbit", 0), due("mouse", 5), due("hamster", -1), due("turtle", 2))
        // Every other fixture word has no history and is not offered, although a new word counts as due in a lesson.
        assertEquals(listOf("mouse", "turtle", "rabbit"), Practice.dueWords(schedule, today, catalog))
    }

    @Test
    fun `at most ten words, ties in a stable order`() {
        val ids = catalog.packs.flatMap { it.words }.map { it.id }
        assertTrue(ids.size > Practice.MAX_WORDS)
        val schedule = ids.mapIndexed { index, id -> due(id, if (index == ids.lastIndex) 9 else 1) }
        val selected = Practice.dueWords(schedule, today, catalog)
        assertEquals(Practice.MAX_WORDS, selected.size)
        assertEquals(ids.last(), selected.first())
        assertEquals(ids.dropLast(1).sorted().take(Practice.MAX_WORDS - 1), selected.drop(1))
    }

    @Test
    fun `a word whose id is no longer in the content is ignored`() {
        val schedule = listOf(due("retired-word", 30), due("rabbit", 1))
        assertEquals(listOf("rabbit"), Practice.dueWords(schedule, today, catalog))
    }

    @Test
    fun `a clock moved backwards makes nothing due early`() {
        val schedule = listOf(ScheduledWord("rabbit", today.plusDays(1)), ScheduledWord("mouse", today.plusDays(3)))
        assertEquals(emptyList<String>(), Practice.dueWords(schedule, today, catalog))
        assertNull(Practice.request(emptyList()))
    }

    @Test
    fun `a session with a single due word is played and gives one outcome`() {
        val request = requireNotNull(Practice.request(listOf("rabbit")))
        val lesson = LessonGenerator(catalog).generate(request, Environment(speechAvailable = { false }, picturesPerceivable = true), seed = 7)
        val exercise = lesson.exercises.single() as ChoiceExercise
        assertEquals("rabbit", exercise.wordId)
        // Distractors come from the word's own pack, not from the (single-word) due list.
        val p1 = catalog.pack("p1")!!.words.map { it.id }
        assertTrue(exercise.options.all { it.wordId in p1 })
        assertEquals(Band.A.choices, exercise.options.size)

        val session = LessonSession("s", lesson).answer(exercise.answerIndex).next()
        assertEquals(listOf(WordOutcome("rabbit", true)), session.result().outcomes)
        assertEquals(request, session.result().request)
    }

    @Test
    fun `each word is exercised in the band of its own class`() {
        val request = LessonRequest.Practice(listOf("rabbit", "chair"))
        val environment = Environment(speechAvailable = { false }, picturesPerceivable = true)
        (0L..20L).forEach { seed ->
            val lesson = LessonGenerator(catalog).generate(request, environment, seed)
            assertEquals(setOf("rabbit", "chair"), lesson.wordIds.toSet())
            assertEquals(2, lesson.exercises.size)
            val rabbit = lesson.exercises.single { it.wordId == "rabbit" } as ChoiceExercise
            assertEquals(Band.A.choices, rabbit.options.size)
            assertTrue(rabbit.type in setOf(ExerciseType.PICTURE_TO_WORD, ExerciseType.WORD_TO_PICTURE))
            val chair = lesson.exercises.single { it.wordId == "chair" }
            if (chair is ChoiceExercise) assertEquals(Band.B.choices, chair.options.size)
        }
    }
}
