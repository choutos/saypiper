package eu.wanderclan.wordquest.ui

import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.game.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestWordQuestApplication::class, qualifiers = "w400dp-h860dp-xhdpi")
class SecondaryExerciseUiTest {
    @get:Rule val rule = createComposeRule()
    private val catalog = Fixtures.catalog()
    private val generator = LessonGenerator(catalog)
    private val exercises = generator.generate(LessonRequest.UnitLesson("fx-feelings", 3), Environment({ false }, false), 0).exercises

    private fun typing(right: Boolean) {
        val exercise = exercises.filterIsInstance<TypingExercise>().first()
        var response by mutableStateOf<Answer?>(null)
        val spoken = mutableListOf<String>()
        var continued = false
        val speechAvailable = !right
        lateinit var view: View
        rule.setContent {
            view = LocalView.current
            WordQuestTheme {
                ExerciseQuestion(exercise, response, Band.C, speechAvailable, { spoken += it }, { response = it },
                    { continued = true }, exercise.text)
            }
        }
        rule.onNodeWithTag("speaker").assertDoesNotExist()
        rule.runOnIdle { assertTrue(spoken.isEmpty()) }
        rule.onNodeWithTag("submit").assertIsNotEnabled()
        rule.onNodeWithTag("typed-answer").performTextInput("  ")
        rule.onNodeWithTag("submit").assertIsNotEnabled()
        rule.onNodeWithTag("reset").performScrollTo().performClick()
        rule.onNodeWithTag("typed-answer").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        rule.onNodeWithTag("typed-answer").performClick()
        rule.runOnIdle {
            val info = EditorInfo()
            assertNotNull(view.onCreateInputConnection(info))
            assertEquals(InputType.TYPE_CLASS_TEXT, info.inputType and InputType.TYPE_MASK_CLASS)
            assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD, info.inputType and InputType.TYPE_MASK_VARIATION)
            assertEquals(0, info.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
            assertEquals(0, info.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
            assertEquals(EditorInfo.IME_ACTION_DONE, info.imeOptions and EditorInfo.IME_MASK_ACTION)
        }
        val answer = if (right) "  ${exercise.text.uppercase()}  " else "${exercise.text}x"
        rule.onNodeWithTag("typed-answer").performTextInput(answer)
        // The password keyboard disables suggestions; it must not mask the child's answer.
        rule.onNodeWithTag("typed-answer").assertTextContains(answer)
        if (right) rule.onRoot().captureRoboImage("build/outputs/roborazzi/secondary-type_the_word.png")
        if (right) rule.onNodeWithTag("typed-answer").performImeAction()
        else rule.onNodeWithTag("submit").performScrollTo().performClick()
        rule.onNodeWithText(if (right) "Well done!" else "Not quite. It is: ${exercise.text}").assertExists()
        rule.onNodeWithTag("submit").assertDoesNotExist()
        if (speechAvailable) rule.onNodeWithTag("speaker").assertExists()
        else rule.onNodeWithTag("speaker").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(right, exercise.incorrectWords(response!!).isEmpty())
            assertEquals(if (speechAvailable) listOf(exercise.wordId) else emptyList<String>(), spoken)
        }
        if (!right) rule.onRoot().captureRoboImage("build/outputs/roborazzi/secondary-type_the_word-feedback.png")
        rule.onNodeWithText("Continue").performClick()
        rule.runOnIdle { assertTrue(continued) }
    }

    private fun pairs(right: Boolean) {
        val exercise = exercises.filterIsInstance<PairsExercise>().first()
        var response by mutableStateOf<Answer?>(null)
        var continued = false
        val spoken = mutableListOf<String>()
        rule.setContent {
            WordQuestTheme {
                ExerciseQuestion(exercise, response, Band.D, true, { spoken += it }, { response = it },
                    { continued = true }, catalog.word(exercise.wordId)!!.word.text)
            }
        }
        rule.onNodeWithText("Match the words and definitions").assertExists()
        if (right) rule.onRoot().captureRoboImage("build/outputs/roborazzi/secondary-definition_pairs.png")
        rule.onNodeWithTag("speaker").assertDoesNotExist()
        rule.onNodeWithTag("submit").performScrollTo().assertIsNotEnabled()
        val ids = exercise.meanings.map { it.wordId }.toMutableList()
        if (!right) ids[0] = ids[1].also { ids[1] = ids[0] }
        exercise.meanings.forEachIndexed { index, meaning ->
            rule.onNodeWithText((meaning as Option.Text).text, useUnmergedTree = true).assertExists()
            rule.onNodeWithTag("pair-word-${ids[index]}").performScrollTo().performClick()
            rule.onNodeWithTag("pair-definition-${meaning.wordId}").performScrollTo().performClick()
        }
        rule.onNodeWithTag("reset").performScrollTo().performClick()
        rule.onNodeWithTag("submit").assertIsNotEnabled()
        exercise.meanings.forEachIndexed { index, meaning ->
            rule.onNodeWithTag("pair-word-${ids[index]}").performScrollTo().performClick()
            rule.onNodeWithTag("pair-definition-${meaning.wordId}").performScrollTo().performClick()
        }
        rule.onNodeWithText("Match the words and definitions").performScrollTo()
        rule.onNodeWithTag("submit").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(right, exercise.incorrectWords(response!!).isEmpty())
            assertTrue(spoken.isEmpty())
        }
        exercise.meanings.forEachIndexed { index, meaning ->
            rule.onNodeWithTag("pair-feedback-${meaning.wordId}", useUnmergedTree = true)
                .assertContentDescriptionEquals(if (right || index >= 2) "Correct pair" else "Try this pair again")
            if (!right && index < 2) rule.onNodeWithTag("pair-answer-${meaning.wordId}", useUnmergedTree = true)
                .assertTextEquals("→ ${catalog.word(meaning.wordId)!!.word.text}")
        }
        if (!right) rule.onRoot().captureRoboImage("build/outputs/roborazzi/secondary-definition_pairs-feedback.png")
        rule.onNodeWithText("Continue").performClick()
        rule.runOnIdle { assertTrue(continued) }
    }

    @Test fun typingRight() = typing(true)
    @Test fun typingWrong() = typing(false)
    @Test fun definitionPairsRight() = pairs(true)
    @Test fun definitionPairsWrong() = pairs(false)

    @Test fun typedAnswerSurvivesRecreationAndNextQuestionStartsEmpty() {
        val exercise = exercises.filterIsInstance<TypingExercise>().first()
        val restoration = StateRestorationTester(rule)
        var position by mutableStateOf(0)
        restoration.setContent {
            WordQuestTheme { key(position) {
                ExerciseQuestion(exercise, null, Band.C, false, {}, {}, {}, exercise.text)
            } }
        }
        rule.onNodeWithTag("typed-answer").performTextInput("partly typed")
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("typed-answer").assertTextContains("partly typed")
        rule.runOnIdle { position++ }
        rule.onNodeWithTag("typed-answer").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }

    @Test fun dictationRight() = dictation(true)
    @Test fun dictationWrong() = dictation(false)

    private fun dictation(right: Boolean) {
        val exercise = TypingExercise("proud", Prompt.Listening, "proud", Prompt.Text("happy about something you have done well"))
        val spoken = mutableListOf<String>()
        var response by mutableStateOf<Answer?>(null)
        rule.setContent {
            WordQuestTheme { ExerciseQuestion(exercise, response, Band.D, true, { spoken += it }, { response = it }, {}, exercise.text) }
        }
        rule.onNodeWithTag("prompt").assertTextEquals((exercise.clue as Prompt.Text).text)
        rule.runOnIdle { assertEquals(listOf("proud"), spoken) }
        rule.onNodeWithTag("listen-replay").performClick()
        rule.runOnIdle { assertEquals(listOf("proud", "proud"), spoken) }
        rule.onNodeWithTag("typed-answer").performTextInput(if (right) "proud" else "prout")
        rule.onNodeWithTag("submit").performScrollTo().performClick()
        rule.onNodeWithText(if (right) "Well done!" else "Not quite. It is: proud").assertExists()
        rule.runOnIdle { assertEquals(right, exercise.incorrectWords(response!!).isEmpty()) }
    }

    @Test fun pictureDictationShowsItsClue() {
        val exercise = TypingExercise("mouse", Prompt.Listening, "mouse", Prompt.Picture("🐭"))
        val pictures = androidx.test.core.app.ApplicationProvider.getApplicationContext<TestWordQuestApplication>().container.pictures
        rule.setContent { CompositionLocalProvider(LocalPictures provides pictures) {
            WordQuestTheme { ExerciseQuestion(exercise, null, Band.C, true, {}, {}, {}, exercise.text) }
        } }
        rule.onNodeWithContentDescription("Picture to name").assertExists()
        rule.onNodeWithTag("listen-replay").assertExists()
    }
}
