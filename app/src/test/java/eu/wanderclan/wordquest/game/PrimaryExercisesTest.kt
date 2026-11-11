package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.content.*
import org.junit.Assert.*
import org.junit.Test

class PrimaryExercisesTest {
    private val catalog = Fixtures.catalog()
    private val generator = LessonGenerator(catalog)

    @Test fun `band A with speech always uses pictures as answers and includes listening`() {
        for (seed in 0L..30L) {
            val lesson = generator.generate(LessonRequest.UnitLesson("fx-pets", 1), Environment({ true }, true), seed)
            assertTrue(lesson.exercises.any { it.type == ExerciseType.LISTEN_AND_PICK })
            assertTrue(lesson.exercises.all { it is ChoiceExercise && it.options.all { o -> o is Option.Picture } })
        }
    }

    @Test fun `band B mixed picture and definition fixture passes playability without speech`() {
        assertEquals(emptyList<Playability.Gap>(), Playability.gaps(catalog.pack("p3")!!))
    }
    @Test fun `every new type is generated reproducibly and valid for its word`() {
        val types = mutableSetOf<ExerciseType>()
        for (seed in 0L..50L) for (unit in listOf("fx-pets", "fx-home")) {
            val request = LessonRequest.UnitLesson(unit, if (unit == "fx-home") 4 else 3)
            val env = Environment({ true }, true)
            val lesson = generator.generate(request, env, seed)
            assertEquals(lesson, generator.generate(request, env, seed))
            lesson.exercises.forEach { exercise ->
                types += exercise.type
                val home = catalog.word(exercise.wordId)!!
                assertTrue(Capabilities.supports(exercise.type, home.word, home.gradePack.band, env, home.gradePack.words))
                when (exercise) {
                    is ChoiceExercise -> {
                        assertEquals(home.gradePack.band.choices, exercise.options.size)
                        assertEquals(1, exercise.options.count { it.wordId == exercise.wordId })
                        val words = exercise.options.map { catalog.word(it.wordId)!!.word }
                        assertEquals(words.size, words.map { normaliseText(it.text) }.distinct().size)
                        val pictures = words.mapNotNull { it.emoji?.let(::normaliseEmoji) }
                        assertEquals(pictures.size, pictures.distinct().size)
                        assertEquals(emptySet<String>(), exercise.incorrectWords(Answer.Choice(exercise.answerIndex)))
                        if (exercise.type == ExerciseType.GAP_FILL) {
                            assertTrue(words.all { it.pos == home.word.pos })
                            assertEquals(home.word.parsedExample, (exercise.prompt as Prompt.Gap).example)
                        }
                    }
                    is PairsExercise -> {
                        assertTrue(exercise.words.size in 4..5)
                        assertEquals(exercise.words.map { it.wordId }.toSet(), exercise.pictures.map { it.wordId }.toSet())
                        assertTrue(exercise.wordIds.all { id -> catalog.word(id)!!.unit == home.unit })
                        assertEquals(emptySet<String>(), exercise.incorrectWords(Answer.Pairs(exercise.pictures.map { it.wordId })))
                    }
                    is TilesExercise -> {
                        assertTrue(exercise.text.length <= 10 && exercise.text.all { it.isLetter() })
                        assertEquals(exercise.text.toList().sorted(), exercise.tiles.sorted())
                        if (exercise.tiles.distinct().size > 1) assertNotEquals(exercise.text, exercise.tiles.joinToString(""))
                    }
                }
            }
        }
        assertTrue(types.containsAll(ExerciseType.entries - ExerciseType.PICTURE_TO_WORD))
    }

    @Test fun `tiles exclude phrases punctuation long words and unavailable prompts`() {
        val env = Environment({ true }, true)
        for (text in listOf("ice cream", "mother-in-law", "abcdefghijk", "can't", "123")) {
            assertFalse(Capabilities.supports(ExerciseType.SPELL_WITH_TILES, Word("x", text, "noun", "x"), Band.B, env, emptyList()))
        }
        val word = Word("letter", "letter", "noun")
        assertTrue(Capabilities.supports(ExerciseType.SPELL_WITH_TILES, word, Band.B, env, emptyList()))
        assertFalse(Capabilities.supports(ExerciseType.SPELL_WITH_TILES, word, Band.B, Environment({ false }, true), emptyList()))
        assertFalse(Capabilities.supports(ExerciseType.SPELL_WITH_TILES, word.copy(emoji = "x"), Band.A, env, emptyList()))
    }

    @Test fun `no voice means no listening and every P1 target remains playable`() {
        for (seed in 0L..30L) {
            val lesson = generator.generate(LessonRequest.UnitLesson("fx-pets", 1), Environment({ false }, true), seed)
            assertTrue(lesson.skippedWordIds.isEmpty())
            assertEquals(setOf("rabbit", "mouse", "hamster"), lesson.wordIds.toSet())
            assertTrue(lesson.exercises.none { it.type == ExerciseType.LISTEN_AND_PICK })
        }
    }

    @Test fun `gap distractors have the same part of speech and prefer other units`() {
        val grade = catalog.grade("p3")!!
        val home = catalog.pack("p3")!!.pack.units.single()
        val awayWords = listOf("apple", "boat", "coat", "drum").map { Word(it, it, "noun", definition = "an object", example = "Find the {$it}.") }
        val away = VocabUnit("other", "Other", words = awayWords)
        val pack = GradePack(grade, Pack(1, "p3", listOf(home, away)))
        val gen = LessonGenerator(Catalog(listOf(grade), listOf(pack)))
        var count = 0
        for (seed in 0L..30L) {
            val lesson = gen.generate(LessonRequest.UnitLesson(home.id, 4), Environment({ false }, true), seed)
            lesson.exercises.filterIsInstance<ChoiceExercise>().filter { it.type == ExerciseType.GAP_FILL }.forEach { ex ->
                count++
                assertTrue(ex.options.filter { it.wordId != ex.wordId }.all { it.wordId in awayWords.map { w -> w.id } })
            }
        }
        assertTrue(count > 0)
    }

    @Test fun `tiles use audio instead of a picture when a screen reader is on`() {
        var tiles = 0
        val env = Environment({ true }, false)
        for (seed in 0L..30L) {
            val lesson = generator.generate(LessonRequest.UnitLesson("fx-home", 4), env, seed)
            lesson.exercises.filterIsInstance<TilesExercise>().forEach {
                tiles++
                assertEquals(Prompt.Listening, it.prompt)
            }
            val recovered = LessonSession("reader", lesson).withoutSpeech(generator, env)
            recovered.queue.forEach { ex ->
                when (ex) {
                    is ChoiceExercise -> {
                        assertTrue(ex.prompt !is Prompt.Picture && ex.prompt != Prompt.Listening)
                        assertTrue(ex.options.all { it is Option.Text })
                    }
                    is TilesExercise -> {
                        // With neither sound nor a perceivable picture, tiles reveal the word too.
                        assertEquals(Prompt.Text(catalog.word(ex.wordId)!!.word.text), ex.prompt)
                    }
                    is PairsExercise -> fail("Pictures cannot be perceived")
                }
            }
        }
        assertTrue(tiles > 0)
    }

    @Test fun `failed speech reveals current word and replaces all queued listening without losing targets`() {
        val lesson = generator.generate(LessonRequest.UnitLesson("fx-pets", 1), Environment({ true }, true), 3)
        val listening = lesson.exercises.indexOfFirst { it.type == ExerciseType.LISTEN_AND_PICK }
        val session = LessonSession("fail", lesson, position = listening).withoutSpeech(generator)
        assertTrue(session.unavailableSpeechWordIds.isNotEmpty())
        assertEquals(Prompt.Text(catalog.word(session.current!!.wordId)!!.word.text), (session.current as ChoiceExercise).prompt)
        assertTrue(session.queue.drop(listening).none { it.type == ExerciseType.LISTEN_AND_PICK })
        assertEquals(lesson.wordIds.toSet(), session.queue.flatMap { it.wordIds }.toSet())
    }
    @Test fun `optional speaker failure never replaces a working visual or written prompt`() {
        val found = mutableSetOf<ExerciseType>()
        for (seed in 0L..30L) {
            val lesson = generator.generate(LessonRequest.UnitLesson("fx-home", 4), Environment({ true }, true), seed)
            for (exercise in lesson.exercises) {
                val listening = when (exercise) {
                    is ChoiceExercise -> exercise.prompt == Prompt.Listening
                    is TilesExercise -> exercise.prompt == Prompt.Listening
                    is PairsExercise -> false
                }
                if (!listening) {
                    found += exercise.type
                    assertEquals(exercise, generator.withoutSpeech(exercise, showWord = true))
                    assertEquals(exercise, generator.withoutSpeech(exercise, showWord = false))
                }
            }
        }
        assertTrue(found.containsAll(setOf(ExerciseType.PICTURE_TO_WORD, ExerciseType.GAP_FILL,
            ExerciseType.DEFINITION_TO_WORD, ExerciseType.SPELL_WITH_TILES)))
    }

}
