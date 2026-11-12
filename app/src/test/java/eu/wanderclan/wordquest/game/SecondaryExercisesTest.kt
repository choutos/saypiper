package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.content.*
import org.junit.Assert.*
import org.junit.Test

class SecondaryExercisesTest {
    private val catalog = Fixtures.catalog()
    private val generator = LessonGenerator(catalog)
    private val silent = Environment({ false }, true)

    @Test fun `secondary fixture packs pass content rule 10 with and without speech`() {
        for (grade in listOf("s1", "s4")) assertEquals(emptyList<Playability.Gap>(), Playability.gaps(catalog.pack(grade)!!))
    }

    @Test fun `every word without pictures is playable in every secondary lesson without speech`() {
        for (grade in listOf("s1", "s4")) {
            val pack = catalog.pack(grade)!!
            for (unit in pack.pack.units) for ((index, words) in LessonPlan.lessons(unit).withIndex()) {
                for (seed in 0L..20L) {
                    val request = LessonRequest.UnitLesson(unit.id, index + 1)
                    val lesson = generator.generate(request, silent, seed)
                    assertEquals(lesson, generator.generate(request, silent, seed))
                    assertTrue(lesson.skippedWordIds.isEmpty())
                    assertEquals(words.map { it.id }.toSet(), lesson.wordIds.toSet())
                    assertEquals(10, lesson.exercises.size)
                    assertTrue(lesson.exercises.all { it.type in writtenTypes })
                    assertTrue(lesson.exercises.any { it is TypingExercise })
                    lesson.exercises.forEach { exercise ->
                        val home = catalog.word(exercise.wordId)!!
                        assertTrue(Capabilities.supports(exercise.type, home.word, pack.band, silent, pack.words))
                        if (exercise is TypingExercise) {
                            assertEquals(Prompt.Text(home.word.definition!!), exercise.prompt)
                            assertEquals(emptySet<String>(), exercise.incorrectWords(Answer.Typed(home.word.text)))
                        }
                        if (exercise is ChoiceExercise) assertEquals(4, exercise.options.size)
                    }
                }
            }
        }
    }

    @Test fun `definition pairs use four or five distinct definitions from the requested targets`() {
        for (grade in listOf("s1", "s4")) {
            val unit = catalog.pack(grade)!!.pack.units.single()
            val pairs = (0L..20L).flatMap {
                generator.generate(LessonRequest.UnitLesson(unit.id, 3), silent, it).exercises.filterIsInstance<PairsExercise>()
            }
            assertTrue(pairs.isNotEmpty())
            pairs.forEach { exercise ->
                assertTrue(exercise.words.size in 4..5)
                assertEquals(exercise.wordIds.toSet(), exercise.meanings.map { it.wordId }.toSet())
                val definitions = exercise.meanings.map { it as Option.Text }
                assertEquals(definitions.size, definitions.map { it.text }.distinct().size)
                definitions.forEach { assertEquals(catalog.word(it.wordId)!!.word.definition, it.text) }
                assertTrue(exercise.wordIds.all { id -> id in unit.words.map { it.id } })
                val right = Answer.Pairs(definitions.map { it.wordId })
                assertEquals(emptySet<String>(), exercise.incorrectWords(right))
                val wrong = right.wordIds.toMutableList()
                wrong[0] = wrong[1].also { wrong[1] = wrong[0] }
                assertEquals(right.wordIds.take(2).toSet(), exercise.incorrectWords(Answer.Pairs(wrong)))
            }
        }
    }

    @Test fun `pairs do not borrow words outside a small practice or introduction request`() {
        for (ids in listOf(listOf("proud"), listOf("proud", "calm", "lonely"))) {
            for (seed in 0L..20L) {
                val lesson = generator.generate(LessonRequest.Practice(ids), silent, seed)
                assertEquals(ids.toSet(), lesson.wordIds.toSet())
                assertTrue(lesson.exercises.none { it is PairsExercise })
            }
        }
    }

    @Test fun `duplicate definitions cannot form ambiguous pairs`() {
        val words = catalog.pack("s1")!!.words.map { it.copy(definition = "the same explanation") }
        assertFalse(Capabilities.supports(ExerciseType.MATCH_PAIRS, words.first(), Band.C, silent, words))
        assertFalse(Capabilities.supports(ExerciseType.MATCH_PAIRS, words.first(), Band.D, silent, words))
    }

    private val writtenTypes = setOf(ExerciseType.DEFINITION_TO_WORD, ExerciseType.GAP_FILL, ExerciseType.TYPE_THE_WORD, ExerciseType.MATCH_PAIRS)

    @Test fun `C favours written questions and D uses only the written mix even when pictures and speech exist`() {
        for (band in listOf(Band.C, Band.D)) {
            val base = catalog.pack("s1")!!
            val grade = base.grade.copy(band = band)
            val unit = base.pack.units.single().copy(words = base.words.mapIndexed { index, word ->
                word.copy(emoji = "picture-$index")
            })
            val pack = GradePack(grade, base.pack.copy(units = listOf(unit)))
            val gen = LessonGenerator(Catalog(listOf(grade), listOf(pack)))
            val observed = mutableSetOf<ExerciseType>()
            for (seed in 0L..30L) {
                val lesson = gen.generate(LessonRequest.UnitLesson(unit.id, 3), Environment({ true }, true), seed)
                observed += lesson.exercises.map { it.type }
                assertTrue(lesson.exercises.count { it.type in writtenTypes } >= 8)
                if (band == Band.D) assertTrue(lesson.exercises.all { it.type in writtenTypes })
            }
            assertTrue(observed.containsAll(writtenTypes))
        }
    }

    @Test fun `typing ignores case and surrounding spaces but accepts no spelling changes or blank answers`() {
        val exercise = TypingExercise("reliable", Prompt.Text("that you can trust"), "reliable")
        for (text in listOf("reliable", "RELIABLE", "ReLiAbLe", "  reliable \n")) {
            assertTrue(exercise.incorrectWords(Answer.Typed(text)).isEmpty())
        }
        for (text in listOf("reliabel", "reliabl", "re liable", "", "  ")) {
            assertEquals(setOf("reliable"), exercise.incorrectWords(Answer.Typed(text)))
        }
        val phrase = TypingExercise("look-after", Prompt.Listening, "look after")
        assertTrue(phrase.incorrectWords(Answer.Typed(" LOOK AFTER ")).isEmpty())
        assertFalse(phrase.incorrectWords(Answer.Typed("look  after")).isEmpty())
    }

    @Test fun `seeded typing uses dictation about one third of the time with a visible definition`() {
        for (grade in listOf("s1", "s4")) {
            val unit = catalog.pack(grade)!!.pack.units.single()
            var typed = 0
            var dictated = 0
            for (seed in 0L until 300L) {
                val request = LessonRequest.UnitLesson(unit.id, 3)
                val lesson = generator.generate(request, Environment({ true }, true), seed)
                assertEquals(lesson, generator.generate(request, Environment({ true }, true), seed))
                lesson.exercises.filterIsInstance<TypingExercise>().forEach { exercise ->
                    typed++
                    val definition = Prompt.Text(catalog.word(exercise.wordId)!!.word.definition!!)
                    if (exercise.prompt == Prompt.Listening) {
                        dictated++
                        assertEquals(definition, exercise.clue)
                        val recovered = generator.withoutSpeech(exercise, true) as TypingExercise
                        assertEquals(definition, recovered.prompt)
                        assertNull(recovered.clue)
                    } else assertEquals(definition, exercise.prompt)
                }
                generator.generate(request, silent, seed).exercises.filterIsInstance<TypingExercise>().forEach {
                    assertTrue(it.prompt is Prompt.Text)
                    assertNull(it.clue)
                }
            }
            val ratio = dictated.toDouble() / typed
            println("$grade dictation: $dictated / $typed = $ratio")
            assertTrue("dictation ratio $ratio", ratio in 0.28..0.39)
        }
    }

    @Test fun `dictation without a definition shows a picture and requires it to be perceivable`() {
        for (band in listOf(Band.C, Band.D)) {
            val home = catalog.word("proud")!!
            val grade = home.gradePack.grade.copy(band = band)
            val word = home.word.copy(definition = null, example = null, emoji = "🐶")
            val pack = GradePack(grade, Pack(1, grade.id, listOf(home.unit.copy(words = listOf(word)))))
            val gen = LessonGenerator(Catalog(listOf(grade), listOf(pack)))
            val request = LessonRequest.Practice(listOf(word.id))
            val typing = (0L..100L).flatMap { gen.generate(request, Environment({ true }, true), it).exercises }
                .filterIsInstance<TypingExercise>()
            assertTrue(typing.isNotEmpty())
            typing.forEach { exercise ->
                assertEquals(Prompt.Listening, exercise.prompt)
                assertEquals(Prompt.Picture("🐶"), exercise.clue)
                assertEquals(Prompt.Picture("🐶"), (gen.withoutSpeech(exercise, true) as TypingExercise).prompt)
            }
            for (environment in listOf(silent, Environment({ true }, false))) {
                assertFalse(Capabilities.supports(ExerciseType.TYPE_THE_WORD, word, band, environment, listOf(word)))
            }
        }
    }

    @Test fun `new response kinds share retries scoring and per-word outcomes`() {
        val exercises = generator.generate(LessonRequest.UnitLesson("fx-feelings", 3), silent, 0).exercises
        val pairs = exercises.filterIsInstance<PairsExercise>().first()
        val typing = exercises.filterIsInstance<TypingExercise>().first()
        val rightPairs = pairs.meanings.map { it.wordId }
        val wrongPairs = rightPairs.toMutableList()
        wrongPairs[0] = wrongPairs[1].also { wrongPairs[1] = wrongPairs[0] }
        for ((exercise, wrong, right) in listOf(
            Triple<Exercise, Answer, Answer>(pairs, Answer.Pairs(wrongPairs), Answer.Pairs(rightPairs)),
            Triple<Exercise, Answer, Answer>(typing, Answer.Typed("misspelt"), Answer.Typed(typing.text)),
        )) {
            val lesson = Lesson(LessonRequest.Practice(exercise.wordIds), listOf(exercise), emptyList())
            val session = LessonSession("recovered", lesson).answer(wrong).next().answer(right).next()
            assertTrue(session.isFinished)
            assertEquals(2, session.result().stars)
            assertEquals(exercise.incorrectWords(wrong), session.result().outcomes.filter { !it.correct }.map { it.wordId }.toSet())
            var repeated = LessonSession("repeated", lesson)
            repeat(3) { repeated = repeated.answer(wrong).next() }
            assertTrue(repeated.isFinished)
            assertEquals(3, repeated.queue.size)
            assertEquals(1, repeated.result().stars)
        }
    }

    @Test fun `practice containing a definition-only word can be generated and finished without speech`() {
        // Practice may contain one due word; it must not depend on a picture, example or distractors.
        val base = catalog.pack("s4")!!
        val word = base.words.first().copy(example = null)
        val unit = base.pack.units.single().copy(words = listOf(word))
        val gen = LessonGenerator(Catalog(listOf(base.grade), listOf(base.copy(pack = base.pack.copy(units = listOf(unit))))))
        val request = requireNotNull(Practice.request(listOf(word.id)))
        val lesson = gen.generate(request, silent, 9)
        val exercise = lesson.exercises.single() as TypingExercise
        val result = LessonSession("practice", lesson).answer(Answer.Typed(exercise.text)).next().result()
        assertEquals(request, result.request)
        assertEquals(listOf(WordOutcome(word.id, true)), result.outcomes)
        assertEquals(3, result.stars)
    }
}
