package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.content.Catalog
import eu.wanderclan.wordquest.content.Cycle
import eu.wanderclan.wordquest.content.Grade
import eu.wanderclan.wordquest.content.GradePack
import eu.wanderclan.wordquest.content.Pack
import eu.wanderclan.wordquest.content.VocabUnit
import eu.wanderclan.wordquest.content.Word
import eu.wanderclan.wordquest.content.normaliseEmoji
import eu.wanderclan.wordquest.content.normaliseText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonGeneratorTest {
    private val catalog = Fixtures.catalog()
    private val generator = LessonGenerator(catalog)
    private val noSpeech = Environment(speechAvailable = { false }, picturesPerceivable = true)

    private fun choices(lesson: Lesson) = lesson.exercises.filterIsInstance<ChoiceExercise>()

    @Test
    fun `the same seed gives the same lesson`() {
        val request = LessonRequest.UnitLesson("fx-fruit", 1)
        assertEquals(generator.generate(request, noSpeech, 42), generator.generate(request, noSpeech, 42))
        assertNotEquals(generator.generate(request, noSpeech, 42), generator.generate(request, noSpeech, 43))
    }

    @Test
    fun `a unit lesson has about ten exercises covering each of its words, never the same word twice in a row`() {
        for (seed in 0L until 50L) {
            val lesson = generator.generate(LessonRequest.UnitLesson("fx-fruit", 1), noSpeech, seed)
            assertEquals(10, lesson.exercises.size)
            assertEquals(setOf("pear", "grapes", "lemon", "cherries"), lesson.wordIds.toSet())
            lesson.exercises.zipWithNext().forEach { (a, b) -> assertNotEquals(a.wordId, b.wordId) }
        }
        val review = generator.generate(LessonRequest.UnitLesson("fx-fruit", 3), noSpeech, 1)
        assertEquals(7, review.wordIds.size)
    }

    @Test
    fun `band A has three options and band B four`() {
        choices(generator.generate(LessonRequest.UnitLesson("fx-pets", 1), noSpeech, 7)).forEach { assertEquals(3, it.options.size) }
        choices(generator.generate(LessonRequest.UnitLesson("fx-home", 1), noSpeech, 7)).forEach { assertEquals(4, it.options.size) }
    }

    @Test
    fun `distractors never share a picture or a text with the answer`() {
        // Rule 5 keeps this out of valid packs; the generator must still never offer two right answers.
        val words = listOf(
            Word("bat-animal", "bat", "noun", "🦇"),
            Word("bat-sport", "Bat ", "noun", "🏏"),
            Word("sun", "sun", "noun", "☀️"),
            Word("sunshine", "sunshine", "noun", "☀"),
            Word("moon", "moon", "noun", "🌙"),
            Word("star", "star", "noun", "⭐"),
            Word("cloud", "cloud", "noun", "☁️"),
            Word("rain", "rain", "noun", "🌧️"),
        )
        val grade = Grade("p3", "P3", Cycle.PRIMARY, "A1", Band.B, "packs/p3.json")
        val pack = Pack(1, "p3", listOf(VocabUnit("sky", "Sky", null, words)))
        val generator = LessonGenerator(Catalog(listOf(grade), listOf(GradePack(grade, pack))))
        for (seed in 0L until 200L) {
            for (number in 1..3) {
                val lesson = generator.generate(LessonRequest.UnitLesson("sky", number), noSpeech, seed)
                lesson.exercises.filterIsInstance<PairsExercise>().forEach { assertTrue(it.words.size in 4..5) }
                for (exercise in choices(lesson)) {
                    val answer = words.first { it.id == exercise.wordId }
                    val texts = exercise.options.map { normaliseText(words.first { w -> w.id == it.wordId }.text) }
                    assertEquals("option texts must be distinct: $texts", texts.size, texts.toSet().size)
                    exercise.options.filter { it.wordId != answer.id }.forEach { option ->
                        val distractor = words.first { it.id == option.wordId }
                        assertNotEquals(normaliseEmoji(answer.emoji!!), normaliseEmoji(distractor.emoji!!))
                        assertNotEquals(normaliseText(answer.text), normaliseText(distractor.text))
                    }
                    assertEquals(1, exercise.options.count { it.wordId == answer.id })
                }
            }
        }
    }

    @Test
    fun `a word with no supported exercise type is skipped`() {
        // Secondary definition mixes arrive in FRL-9.
        val lesson = generator.generate(LessonRequest.Practice(listOf("proud")), noSpeech, 3)
        assertEquals(listOf("proud"), lesson.skippedWordIds)
        assertTrue(lesson.exercises.isEmpty())
    }

    @Test
    fun `without perceivable pictures nothing can be played yet`() {
        val lesson = generator.generate(LessonRequest.UnitLesson("fx-pets", 1), Environment({ true }, picturesPerceivable = false), 3)
        assertTrue(lesson.exercises.isEmpty())
        assertEquals(listOf("rabbit", "mouse", "hamster"), lesson.skippedWordIds)
    }

    @Test
    fun `a practice request with a single word gets its distractors from the word's pack`() {
        val lesson = generator.generate(LessonRequest.Practice(listOf("lemon")), noSpeech, 5)
        val exercise = choices(lesson).single()
        assertEquals("lemon", exercise.wordId)
        assertEquals(3, exercise.options.size)
        val p1Words = catalog.pack("p1")!!.words.map { it.id }.toSet()
        assertTrue(exercise.options.all { it.wordId in p1Words })
    }

    @Test
    fun `practice exercises each word in the band of its own class and ignores unknown ids`() {
        val lesson = generator.generate(LessonRequest.Practice(listOf("rabbit", "gone-word", "chair")), noSpeech, 5)
        val byWord = lesson.exercises.associateBy { it.wordId }
        assertEquals(setOf("rabbit", "chair"), byWord.keys)
        assertEquals(3, (byWord.getValue("rabbit") as ChoiceExercise).options.size)
        assertEquals(4, (byWord.getValue("chair") as ChoiceExercise).options.size)
        val p3Words = catalog.pack("p3")!!.words.map { it.id }.toSet()
        assertTrue((byWord.getValue("chair") as ChoiceExercise).options.all { it.wordId in p3Words })
    }

    @Test
    fun `a request for a lesson that does not exist gives an empty lesson`() {
        assertTrue(generator.generate(LessonRequest.UnitLesson("fx-pets", 9), noSpeech, 1).exercises.isEmpty())
        assertTrue(generator.generate(LessonRequest.UnitLesson("nope", 1), noSpeech, 1).exercises.isEmpty())
    }
}
