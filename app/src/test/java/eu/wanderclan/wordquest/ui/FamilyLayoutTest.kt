package eu.wanderclan.wordquest.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import eu.wanderclan.wordquest.audio.FakeSpeech
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.game.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.robolectric.RuntimeEnvironment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Same screens and assertions at every size; PNGs are uploaded by the existing CI screenshot job. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class FamilyLayoutTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<TestWordQuestApplication>()
    private val container get() = app.container
    private val sizeName get() = javaClass.simpleName
    @Before fun configureFontScale() { if (this is LargeFontLayoutTest || this is LargeFontPortraitLayoutTest) RuntimeEnvironment.setFontScale(2f) }
    private fun seed() = runBlocking { container.profiles.create("Alexandra", "fox").also { container.profiles.setGrade(it, "p3") } }
    private fun capture(name: String) {
        rule.waitForIdle()
        rule.onAllNodes(isRoot()).onLast().captureRoboImage("build/outputs/roborazzi/FRL-12-$sizeName-$name.png")
        // Detect ellipsised or vertically clipped text, including inside buttons.
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)).fetchSemanticsNodes().forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            layouts.forEach { layout ->
                // MultiParagraph.width is the offered width, which can exceed the final Text size
                // even when every glyph fits. Check actual line extents, allowing pixel rounding.
                assertFalse("Ellipsised text in $name: ${layout.layoutInput.text}", layout.multiParagraph.didExceedMaxLines)
                for (line in 0 until layout.lineCount) {
                    assertTrue("Clipped line in $name: ${layout.layoutInput.text}",
                        layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1 &&
                            layout.getLineBottom(line) <= layout.size.height + 1)
                }
            }
        }
    }
    private fun content(body: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            if (this is LargeFontLayoutTest || this is LargeFontPortraitLayoutTest) assertEquals(2f, androidx.compose.ui.platform.LocalDensity.current.fontScale)
            CompositionLocalProvider(LocalPictures provides container.pictures) { WordQuestTheme { body() } }
        }
    }
    private fun waitText(text: String) = rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun profiles() {
        seed()
        runBlocking { container.profiles.create("Frederick", "sun") }
        content { ProfilesScreen(container, {}, {}, {}) }
        waitText("Alexandra"); capture("profiles")
        rule.onNodeWithText("New player").assertIsDisplayed()
    }

    @Test fun classPicker() {
        val id = seed()
        content { ClassPickerScreen(container, id) {} }
        capture("classes")
        rule.onNodeWithContentDescription("S7, coming soon").performScrollTo().assertIsDisplayed()
        capture("classes-bottom")
    }

    @Test fun path() {
        val id = seed()
        content { PathScreen(container, id, {}, { _, _ -> }, {}, {}) }
        waitText("Alexandra · P3"); capture("path")
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("At home, review: locked"))
        rule.onNodeWithContentDescription("At home, review: locked").assertIsDisplayed()
        capture("path-bottom")
    }

    @Test fun profileForm() {
        content { CreateProfileScreen(container) {} }
        rule.onNode(hasSetTextAction()).performTextInput("Alexandra")
        capture("new-player")
        rule.onNodeWithText("Let's go").performScrollTo().assertIsDisplayed()
        capture("new-player-bottom")
    }

    @Test fun avatarForm() {
        val id = seed()
        content { EditAvatarScreen(container, id) {} }
        waitText("Save avatar")
        capture("avatar")
        rule.onNodeWithText("Save avatar").performScrollTo().assertIsDisplayed()
    }

    @Test fun parentGateCornerAndConfirmation() {
        seed()
        content { ParentCornerScreen(container) {} }
        capture("parent-gate")
        val question = rule.onNode(hasText("What is", substring = true)).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        val answer = Regex("\\d+").findAll(question).sumOf { it.value.toInt() }
        rule.onNode(hasSetTextAction()).performScrollTo().performTextInput(answer.toString())
        rule.onNodeWithText("Enter parent corner").performScrollTo().performClick()
        waitText("Sound")
        capture("parent-corner")
        rule.onNodeWithText("Reset progress").performScrollTo().performClick()
        capture("reset-confirmation")
        rule.onNodeWithText("Confirm").assertIsDisplayed()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Rename").performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("Alex")
        capture("rename")
        rule.onNodeWithText("Confirm").performScrollTo().assertIsDisplayed()
    }

    @Test fun eachExerciseType() {
        val id = seed()
        app.deviceVoice.mode = FakeSpeech.Mode.SILENT
        runBlocking { container.speech.prepare() }
        val plans = (0L..100L).flatMap { seed -> listOf("fx-pets" to 3, "fx-home" to 4).map { (unit, number) ->
            val request = LessonRequest.UnitLesson(unit, number)
            val first = container.generator.generate(request, Environment({ true }, true), seed).exercises.first()
            Triple(request, seed, first)
        } }.groupBy { it.third.type }.mapValues { it.value.first() }
        assertEquals(ExerciseType.entries.toSet(), plans.keys)
        lateinit var navigation: NavHostController
        content { navigation = rememberNavController(); WordQuestRoot(container, navigation) }
        waitText("Alexandra · P3")
        for (type in ExerciseType.entries) {
            val (request, seed, exercise) = plans.getValue(type)
            for (correct in listOf(true, false)) {
                rule.runOnIdle {
                    app.lessonSeed = seed
                    navigation.navigate("lesson/$id/${request.unitId}/${request.number}")
                }
                rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Stop the lesson").fetchSemanticsNodes().isNotEmpty() }
                capture("${type.name}-${if (correct) "question" else "retry-question"}")
                answer(exercise, correct)
                // This must be visible immediately after answering, without test-driven scrolling.
                rule.onNodeWithText("Continue").assertIsDisplayed()
                capture("${type.name}-${if (correct) "correct" else "incorrect"}")
                rule.runOnIdle { navigation.popBackStack() }
                waitText("Alexandra · P3")
            }
        }
    }

    private fun answer(exercise: Exercise, correct: Boolean) {
        when (exercise) {
            is ChoiceExercise -> rule.onNodeWithTag("option-${if (correct) exercise.answerIndex else (exercise.answerIndex + 1) % exercise.options.size}")
                .performScrollTo().performClick()
            is PairsExercise -> {
                val words = exercise.pictures.map { it.wordId }
                exercise.pictures.forEachIndexed { index, picture ->
                    val word = words[if (correct) index else (index + 1) % words.size]
                    rule.onNodeWithTag("pair-word-$word").performScrollTo().performClick()
                    rule.onNodeWithTag("pair-picture-${picture.wordId}").performScrollTo().performClick()
                }
                rule.onNodeWithTag("submit").performScrollTo().performClick()
            }
            is TilesExercise -> {
                val remaining = exercise.tiles.indices.toMutableList()
                val indices = exercise.text.map { letter -> remaining.first { exercise.tiles[it] == letter }.also { remaining.remove(it) } }
                val order = if (correct) indices else indices.reversed()
                order.forEach { rule.onNodeWithTag("tile-$it").performScrollTo().performClick() }
                rule.onNodeWithTag("submit").performScrollTo().performClick()
            }
        }
    }

    @Test fun pictureLabelsNeverContainTheirAnswer() {
        val catalog = Fixtures.catalog()
        val exercises = (0L..20L).flatMap { seed -> listOf("fx-pets" to 3, "fx-home" to 4).flatMap { (unit, number) ->
            forPictures(catalog, unit, number, seed)
        } }.filter { exercise -> when (exercise) {
            is ChoiceExercise -> exercise.prompt is Prompt.Picture || exercise.options.any { it is Option.Picture }
            is PairsExercise -> true
            is TilesExercise -> exercise.prompt is Prompt.Picture
        } }.distinctBy { it.type }
        assertEquals(setOf(ExerciseType.PICTURE_TO_WORD, ExerciseType.WORD_TO_PICTURE, ExerciseType.LISTEN_AND_PICK,
            ExerciseType.MATCH_PAIRS, ExerciseType.SPELL_WITH_TILES), exercises.map { it.type }.toSet())
        val current = mutableStateOf(exercises.first())
        content {
            val exercise = current.value
            androidx.compose.runtime.key(exercise) {
                ExerciseQuestion(exercise, null, catalog.word(exercise.wordId)!!.gradePack.band, false, {}, {}, {},
                    catalog.word(exercise.wordId)!!.word.text)
            }
        }
        fun neutral(label: String, wordId: String) {
            val descriptions = rule.onNodeWithContentDescription(label, useUnmergedTree = true).fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
            assertTrue(descriptions.none { it.contains(catalog.word(wordId)!!.word.text, ignoreCase = true) })
        }
        for (exercise in exercises) {
            rule.runOnIdle { current.value = exercise }
            when (exercise) {
                is ChoiceExercise -> {
                    if (exercise.prompt is Prompt.Picture) neutral("Picture to name", exercise.wordId)
                    exercise.options.forEachIndexed { index, option -> if (option is Option.Picture) neutral("Picture ${index + 1}", option.wordId) }
                }
                is PairsExercise -> exercise.pictures.forEachIndexed { index, picture -> neutral("Picture ${index + 1}", picture.wordId) }
                is TilesExercise -> neutral("Picture to name", exercise.wordId)
            }
        }
    }

    private fun forPictures(catalog: eu.wanderclan.wordquest.content.Catalog, unit: String, number: Int, seed: Long): List<Exercise> =
        listOf(false, true).flatMap { speech -> LessonGenerator(catalog).generate(LessonRequest.UnitLesson(unit, number),
            Environment({ speech }, true), seed).exercises }

    @Test fun mutedP1IsPlayableWithoutSpeakerButtons() = mutedLesson("fx-pets")
    @Test fun mutedP3IsPlayableWithoutSpeakerButtons() = mutedLesson("fx-home")

    private fun mutedLesson(unitId: String) {
        val id = seed()
        runBlocking { container.settings.setSound(false) }
        val request = LessonRequest.UnitLesson(unitId, 1)
        val exercise = container.generator.generate(request, Environment({ false }, true), TestWordQuestApplication.SEED).exercises.first()
        content { LessonScreen(container, id, request, {}, {}) }
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("option-0") or hasTestTag("submit")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("speaker").assertDoesNotExist()
        rule.onNodeWithTag("listen-replay").assertDoesNotExist()
        when (exercise) {
            is ChoiceExercise -> rule.onNodeWithTag("option-${exercise.answerIndex}").performScrollTo().performClick()
            is TilesExercise -> {
                val remaining = exercise.tiles.indices.toMutableList()
                for (letter in exercise.text) {
                    val index = remaining.first { exercise.tiles[it] == letter }
                    remaining.remove(index)
                    rule.onNodeWithTag("tile-$index").performScrollTo().performClick()
                }
                rule.onNodeWithTag("submit").performScrollTo().performClick()
            }
            is PairsExercise -> {
                exercise.pictures.forEach {
                    rule.onNodeWithTag("pair-word-${it.wordId}").performScrollTo().performClick()
                    rule.onNodeWithTag("pair-picture-${it.wordId}").performScrollTo().performClick()
                }
                rule.onNodeWithTag("submit").performScrollTo().performClick()
            }
        }
        rule.onNodeWithTag("speaker").assertDoesNotExist()
        rule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test fun result() {
        content { ResultScreen(3, 25, Fixtures.catalog().unit("fx-pets")!!.first, false) {} }
        capture("result")
        rule.onNodeWithText("Continue").performScrollTo().assertIsDisplayed()
        capture("result-bottom")
    }

    @Test fun aboutPrivacyAndCreditsHaveNoExternalActions() {
        content { AboutScreen {} }
        rule.onNodeWithText("Privacy:", substring = true).performScrollTo().assertExists()
        capture("privacy")
        rule.onNodeWithText("P1 and P2 cannot be played without sight in this version.").performScrollTo().assertExists()
        rule.onNodeWithText("Pictures: OpenMoji", substring = true).performScrollTo().assertExists()
        rule.onNodeWithText("Synthetic speech: Piper", substring = true).performScrollTo().assertExists()
        rule.onAllNodes(hasClickAction()).assertCountEquals(1) // The in-app Back button only.
    }

    @Test fun bandAWithScreenReaderExplainsThatSightIsNeeded() {
        shadowOf(app.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(true)
        val id = seed()
        content { LessonScreen(container, id, LessonRequest.UnitLesson("fx-pets", 1), {}, {}) }
        waitText("P1 and P2 need sight in this version. Choose P3 or above for text exercises with a screen reader.")
        capture("needs-sight")
    }
}

@Config(application = TestWordQuestApplication::class, qualifiers = "w400dp-h860dp-xhdpi")
class PhoneLayoutTest : FamilyLayoutTest()

@Config(application = TestWordQuestApplication::class, qualifiers = "w1280dp-h800dp-land-mdpi")
class TabletLayoutTest : FamilyLayoutTest()

@Config(application = TestWordQuestApplication::class, qualifiers = "w640dp-h360dp-land-mdpi")
class LargeFontLayoutTest : FamilyLayoutTest()

@Config(application = TestWordQuestApplication::class, qualifiers = "w640dp-h360dp-land-mdpi")
class PhoneLandscapeLayoutTest : FamilyLayoutTest()

@Config(application = TestWordQuestApplication::class, qualifiers = "w400dp-h860dp-xhdpi")
class LargeFontPortraitLayoutTest : FamilyLayoutTest()
