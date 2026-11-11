package eu.wanderclan.wordquest.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
    @Before fun configureFontScale() { if (this is LargeFontLayoutTest) RuntimeEnvironment.setFontScale(2f) }
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
            if (this is LargeFontLayoutTest) assertEquals(2f, androidx.compose.ui.platform.LocalDensity.current.fontScale)
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
        val catalog = Fixtures.catalog()
        val generator = LessonGenerator(catalog)
        val exercises = (0L..20L).flatMap { seed -> listOf("fx-pets" to 3, "fx-home" to 4).flatMap { (unit, number) ->
            generator.generate(LessonRequest.UnitLesson(unit, number), Environment({ true }, true), seed).exercises
        } }.distinctBy { it.type }
        assertEquals(ExerciseType.entries.toSet(), exercises.map { it.type }.toSet())
        val current = mutableStateOf(exercises.first())
        content {
            val exercise = current.value
            androidx.compose.runtime.key(exercise) {
                ExerciseQuestion(exercise, null, catalog.word(exercise.wordId)!!.gradePack.band, true, {}, {}, {},
                    catalog.word(exercise.wordId)!!.word.text)
            }
        }
        for (exercise in exercises) {
            rule.runOnIdle { current.value = exercise }
            capture(exercise.type.name)
            when (exercise) {
                is ChoiceExercise -> exercise.options.indices.forEach { i -> rule.onNodeWithTag("option-$i").performScrollTo().assertIsDisplayed() }
                is PairsExercise -> rule.onNodeWithTag("submit").performScrollTo().assertIsDisplayed()
                is TilesExercise -> rule.onNodeWithTag("submit").performScrollTo().assertIsDisplayed()
            }
            capture("${exercise.type.name}-bottom")
        }
    }

    @Test fun pictureLabelsNeverContainTheirAnswer() {
        val catalog = Fixtures.catalog()
        val exercise = LessonGenerator(catalog).generate(LessonRequest.UnitLesson("fx-pets", 1), Environment({ false }, true), 0)
            .exercises.filterIsInstance<ChoiceExercise>().first { it.prompt is Prompt.Picture }
        content { ExerciseQuestion(exercise, null, eu.wanderclan.wordquest.content.Band.A, false, {}, {}, {},
            catalog.word(exercise.wordId)!!.word.text) }
        val labels = rule.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription),
            useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription] }
        assertTrue(labels.contains("Picture to name"))
        assertTrue(labels.none { it.contains(catalog.word(exercise.wordId)!!.word.text, ignoreCase = true) })
    }

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
        rule.onNodeWithText("Continue").performScrollTo().assertIsDisplayed()
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
