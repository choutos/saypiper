package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.content.Band
import org.junit.Assert.*
import org.junit.Test

class AccessibilityTest {
    private val catalog = Fixtures.catalog()

    @Test fun `a mixed lesson without perceivable pictures uses labels and skips picture-only words`() {
        val request = LessonRequest.Practice(listOf("chair", "bed", "kitchen"))
        for (seed in 0L..30L) {
            val lesson = LessonGenerator(catalog).generate(request, Environment({ false }, false), seed)
            assertEquals(listOf("bed"), lesson.skippedWordIds)
            assertEquals(setOf("chair", "kitchen"), lesson.wordIds.toSet())
            lesson.exercises.forEach { ex ->
                assertTrue(ex is ChoiceExercise)
                ex as ChoiceExercise
                assertTrue(ex.prompt is Prompt.Text || ex.prompt is Prompt.Gap)
                assertTrue(ex.options.all { it is Option.Text && it.text.isNotBlank() })
                assertTrue(ex.incorrectWords(Answer.Choice(ex.answerIndex)).isEmpty())
            }
        }
    }

    @Test fun `a definition remains usable when a word also has an imperceivable picture`() {
        val word = catalog.word("chair")!!.word.copy(definition = "a seat for one person")
        assertTrue(Capabilities.supports(ExerciseType.DEFINITION_TO_WORD, word, Band.B,
            Environment({ false }, false), catalog.pack("p3")!!.words))
    }

    @Test fun `band A has no exercises without sight`() {
        val lesson = LessonGenerator(catalog).generate(LessonRequest.UnitLesson("fx-pets", 1), Environment({ true }, false), 0)
        assertTrue(lesson.exercises.isEmpty())
        assertEquals(setOf("rabbit", "mouse", "hamster"), lesson.skippedWordIds.toSet())
    }
}
