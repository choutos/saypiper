package eu.wanderclan.wordquest.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import eu.wanderclan.wordquest.AppContainer
import eu.wanderclan.wordquest.FileContentSource
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.MainActivity
import eu.wanderclan.wordquest.WordQuestApplication
import eu.wanderclan.wordquest.audio.*
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.content.VocabUnit
import eu.wanderclan.wordquest.data.WordQuestDatabase
import eu.wanderclan.wordquest.game.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Shipped content and real repositories; only time, randomness and audio playback are replaced. */
class ShowcaseApplication : WordQuestApplication() {
    var today: LocalDate = LocalDate.of(2026, 11, 13)
    var lessonSeed = 2026L
    lateinit var database: WordQuestDatabase

    override fun createContainer(): AppContainer {
        val source = FileContentSource(Fixtures.shippedContentDir)
        val content = ContentLoader.load(source)
        check(content.problems.isEmpty()) { content.problems.toString() }
        database = Room.inMemoryDatabaseBuilder(this, WordQuestDatabase::class.java).build()
        return AppContainer(
            content, source, database,
            ClipSpeech(content.audioWordIds, FakeSpeech(FakeSpeech.Mode.SILENT), object : ClipPlayer {
                override fun play(path: String, completed: (SpeechResult) -> Unit) { completed(SpeechResult.SPOKEN) }
                override fun stop() {}
            }, Dispatchers.Unconfined),
            clock = { today },
            nextSeed = { lessonSeed },
            pictureDecodeDispatcher = Dispatchers.Unconfined,
        )
    }
}

/** Twelve shareable captures, also exercised by the normal unit-test suite. No production UI overrides. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShowcaseApplication::class, qualifiers = "w432dp-h960dp-400dpi")
class ShowcaseTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<ShowcaseApplication>()
    private val container get() = app.container
    private val environment get() = Environment(container.speech::canSpeak, true)
    private var scenario: ActivityScenario<MainActivity>? = null
    private var sessionNumber = 0

    @After fun close() {
        scenario?.close()
        container.speech.close()
        app.database.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(10_000) {
        rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        rule.assertReadableText(name)
        rule.onRoot().captureRoboImage("build/outputs/showcase/$name.png")
    }

    private fun players(typing: Boolean = false): Map<String, Long> = runBlocking {
        listOf(Triple("Mia", "fox", "p1"), Triple("Leo", "lion", "p4"),
            Triple("Sofia", "owl", if (typing) "s5" else "s2")).associate { (name, avatar, grade) ->
            name to container.profiles.create(name, avatar).also { container.profiles.setGrade(it, grade) }
        }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // Wait for the complete list, not a node from the initial, empty composition.
        waitFor(hasText("Sofia"))
        rule.onNodeWithText("Mia").assertIsDisplayed()
        rule.onNodeWithText("Leo").assertIsDisplayed()
    }

    private fun open(name: String, grade: String) {
        rule.onNodeWithText(name).performClick()
        waitFor(hasText("$name · $grade"))
    }

    private fun correct(exercise: Exercise): Answer = when (exercise) {
        is ChoiceExercise -> Answer.Choice(exercise.answerIndex)
        is PairsExercise -> Answer.Pairs(exercise.meanings.map { it.wordId })
        is TypingExercise -> Answer.Typed(exercise.text)
        is TilesExercise -> Answer.Tiles(tileOrder(exercise))
    }

    private fun tileOrder(exercise: TilesExercise): List<Int> {
        val remaining = exercise.tiles.indices.toMutableList()
        return exercise.text.map { letter -> remaining.first { exercise.tiles[it] == letter }.also { remaining.remove(it) } }
    }

    /** Play the generated lesson in the pure game core, then save exactly its result through the repository. */
    private suspend fun finish(profile: Long, unit: VocabUnit, number: Int, mistakes: Int = 0) {
        val lesson = container.generator.generate(LessonRequest.UnitLesson(unit.id, number), environment, 2026L)
        var session = LessonSession("showcase-${sessionNumber++}", lesson)
        var wrong = 0
        while (!session.isFinished) {
            val exercise = requireNotNull(session.current)
            val answer = if (wrong < mistakes && exercise is ChoiceExercise) {
                wrong++
                Answer.Choice((exercise.answerIndex + 1) % exercise.options.size)
            } else correct(exercise)
            session = session.answer(answer).next()
        }
        assertEquals(mistakes, session.mistakes)
        assertTrue(container.progress.save(profile, session.result()))
    }

    private fun pathHistory(profile: Long) = runBlocking {
        val units = container.catalog.pack("p1")!!.pack.units
        val end = app.today
        // Six completed lessons over five consecutive days, ending yesterday.
        app.today = end.minusDays(5)
        finish(profile, units[0], 1)
        finish(profile, units[0], 2)
        app.today = end.minusDays(4)
        finish(profile, units[0], 3)
        for (number in 1..3) {
            app.today = end.minusDays((4 - number).toLong())
            finish(profile, units[1], number, mistakes = listOf(3, 1, 0)[number - 1])
        }
        app.today = end
    }

    private fun assertPath(profile: Long) {
        waitFor(hasText("5-day streak"))
        rule.onNodeWithText("135 points").assertIsDisplayed()
        rule.onNodeWithContentDescription("Colours: crown earned").assertIsDisplayed()
        for ((number, stars) in listOf(1 to 1, 2 to 2, 3 to 3)) {
            rule.onNodeWithContentDescription("Numbers, lesson $number: $stars of 3 stars").assertIsDisplayed()
        }
        val due = runBlocking { Practice.dueWords(container.progress.schedule(profile).first(), app.today, container.catalog).size }
        assertTrue(due > 0)
        rule.onNodeWithTag("practice").assertIsEnabled().assertTextContains("Practise $due words")
        val stars = runBlocking { container.progress.bestStars(profile).first() }
        val units = container.catalog.pack("p1")!!.pack.units
        assertTrue(Progression.path(units, stars).drop(2).all { !it.isOpen })
    }

    /** Find a fixed seed whose first question meets the row, then unlock only the preceding lessons. */
    private fun prepareLesson(profile: Long, grade: String, predicate: (Exercise) -> Boolean): Lesson {
        val units = container.catalog.pack(grade)!!.pack.units
        val selected = units.firstNotNullOf { unit ->
            (0L..100L).firstNotNullOfOrNull { seed ->
                val lesson = container.generator.generate(LessonRequest.UnitLesson(unit.id, 1), environment, seed)
                lesson.takeIf { predicate(it.exercises.first()) }?.let { Triple(unit, seed, it) }
            }
        }
        app.lessonSeed = selected.second
        runBlocking {
            for (unit in units.takeWhile { it.id != selected.first.id }) {
                for (number in 1..LessonPlan.lessonCount(unit)) finish(profile, unit, number)
            }
        }
        return selected.third
    }

    private fun start(lesson: Lesson) {
        val request = lesson.request as LessonRequest.UnitLesson
        val title = container.catalog.unit(request.unitId)!!.first.title
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("$title, lesson ${request.number}: 0 of 3 stars"))
        rule.onNodeWithContentDescription("$title, lesson ${request.number}: 0 of 3 stars").performClick()
        waitFor(hasContentDescription("Stop the lesson"))
        when (val exercise = lesson.exercises.first()) {
            is ChoiceExercise -> waitFor(hasTestTag("option-0"))
            is PairsExercise -> waitFor(hasTestTag("pair-word-${exercise.words.first().wordId}"))
            is TilesExercise -> waitFor(hasTestTag("tile-0"))
            is TypingExercise -> waitFor(hasTestTag("typed-answer"))
        }
    }

    private fun answer(exercise: Exercise) {
        when (exercise) {
            is ChoiceExercise -> rule.onNodeWithTag("option-${exercise.answerIndex}").performScrollTo().performClick()
            is TilesExercise -> {
                tileOrder(exercise).forEach { rule.onNodeWithTag("tile-$it").performScrollTo().performClick() }
                rule.onNodeWithTag("submit").performScrollTo().performClick()
            }
            is TypingExercise -> {
                rule.onNodeWithTag("typed-answer").performScrollTo().performTextInput(exercise.text)
                rule.onNodeWithTag("submit").performScrollTo().performClick()
            }
            is PairsExercise -> {
                match(exercise, exercise.meanings.size)
                rule.onNodeWithTag("submit").performScrollTo().performClick()
            }
        }
        waitFor(hasText("Well done!"))
    }

    private fun match(exercise: PairsExercise, count: Int) {
        exercise.meanings.take(count).forEach { meaning ->
            rule.onNodeWithTag("pair-word-${meaning.wordId}").performScrollTo().performClick()
            rule.onNodeWithTag("pair-${if (meaning is Option.Text) "definition" else "picture"}-${meaning.wordId}")
                .performScrollTo().performClick()
        }
    }

    @Test fun classes() {
        players(); launch(); open("Mia", "P1")
        rule.onNodeWithText("Change class").performClick()
        waitFor(hasText("S7"))
        (listOf("P1", "P2", "P3", "P4", "P5") + (1..7).map { "S$it" }).forEach {
            rule.onNodeWithText(it).assertIsDisplayed().assertIsEnabled()
        }
        capture("01-classes")
    }

    @Test fun profiles() {
        players(); launch(); capture("02-players")
    }

    @Test fun path() {
        val ids = players(); pathHistory(ids.getValue("Mia")); launch(); open("Mia", "P1")
        assertPath(ids.getValue("Mia"))
        rule.onNodeWithContentDescription("Animals: locked").assertIsDisplayed()
        capture("03-path")
    }

    @Test fun picture() {
        val ids = players()
        val lesson = prepareLesson(ids.getValue("Mia"), "p1") {
            it is ChoiceExercise && it.type == ExerciseType.WORD_TO_PICTURE &&
                container.catalog.word(it.wordId)!!.unit.id == "p1-animals"
        }
        launch(); open("Mia", "P1"); start(lesson); answer(lesson.exercises.first())
        rule.onNodeWithTag("speaker").assertIsDisplayed()
        capture("04-p1-picture")
    }

    @Test fun tiles() {
        val ids = players()
        val lesson = prepareLesson(ids.getValue("Leo"), "p4") { it is TilesExercise && it.prompt is Prompt.Picture && it.text.length >= 4 }
        launch(); open("Leo", "P4"); start(lesson)
        tileOrder(lesson.exercises.first() as TilesExercise).take(2).forEach {
            rule.onNodeWithTag("tile-$it").performClick()
        }
        capture("05-tiles")
    }

    @Test fun gapFill() {
        val ids = players()
        val lesson = prepareLesson(ids.getValue("Leo"), "p4") { it is ChoiceExercise && it.type == ExerciseType.GAP_FILL && it.options.size == 4 }
        launch(); open("Leo", "P4"); start(lesson); capture("06-gap-fill")
    }

    @Test fun definitions() {
        val ids = players()
        val lesson = prepareLesson(ids.getValue("Sofia"), "s2") { it is PairsExercise && it.meanings.all { meaning -> meaning is Option.Text } }
        launch(); open("Sofia", "S2"); start(lesson)
        match(lesson.exercises.first() as PairsExercise, 2)
        capture("07-definitions")
    }

    @Test fun typing() {
        val ids = players(typing = true)
        val lesson = prepareLesson(ids.getValue("Sofia"), "s5") { it is TypingExercise && it.prompt is Prompt.Text }
        launch(); open("Sofia", "S5"); start(lesson)
        rule.onNodeWithTag("typed-answer").performTextInput((lesson.exercises.first() as TypingExercise).text)
        // Capture the same phase of the blinking cursor, with the answer still unsubmitted.
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1_000)
        capture("08-typing")
    }

    @Test fun crown() {
        val ids = players()
        val unit = container.catalog.pack("p1")!!.pack.units.first()
        runBlocking { for (number in 1 until LessonPlan.lessonCount(unit)) finish(ids.getValue("Mia"), unit, number) }
        val request = LessonRequest.UnitLesson(unit.id, LessonPlan.lessonCount(unit))
        val lesson = container.generator.generate(request, environment, app.lessonSeed)
        launch(); open("Mia", "P1")
        waitFor(hasContentDescription("Colours, review: 0 of 3 stars"))
        rule.onNodeWithContentDescription("Colours, review: 0 of 3 stars").performClick()
        lesson.exercises.forEach { exercise ->
            waitFor(hasTestTag("option-0")); answer(exercise)
            rule.onNodeWithText("Continue").performClick()
        }
        waitFor(hasText("You earned the crown for Colours!"))
        rule.onNodeWithContentDescription("3 of 3 stars").assertIsDisplayed()
        capture("09-crown")
    }

    @Test fun parentCorner() {
        players(); launch()
        rule.onNodeWithText("Parent corner").performClick()
        waitFor(hasText("What is", substring = true))
        val question = rule.onNode(hasText("What is", substring = true)).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        val answer = Regex("\\d+").findAll(question).sumOf { it.value.toInt() }
        rule.onNode(hasSetTextAction()).performTextInput(answer.toString())
        rule.onNodeWithText("Enter parent corner").performClick()
        waitFor(hasText("Sound")); waitFor(hasText("Mia · P1"))
        rule.onNodeWithText("Sound").assertIsDisplayed()
        rule.onAllNodesWithText("Delete player").onFirst().assertIsDisplayed()
        capture("10-parent-corner")
    }

    @Test fun about() {
        players(); launch()
        rule.onNodeWithContentDescription("About").performClick()
        waitFor(hasText("no accounts", substring = true))
        capture("11-about")
    }

    @Test
    @Config(qualifiers = "w1280dp-h960dp-land-xhdpi")
    fun tablet() {
        val ids = players(); pathHistory(ids.getValue("Mia")); launch(); open("Mia", "P1")
        assertPath(ids.getValue("Mia")); capture("12-tablet")
    }
}
