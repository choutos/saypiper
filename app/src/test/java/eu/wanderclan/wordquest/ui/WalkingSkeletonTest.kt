package eu.wanderclan.wordquest.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import eu.wanderclan.wordquest.MainActivity
import eu.wanderclan.wordquest.WordQuestApplication
import eu.wanderclan.wordquest.game.ChoiceExercise
import eu.wanderclan.wordquest.game.Environment
import eu.wanderclan.wordquest.game.LessonRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The whole skeleton on fixture content: create a profile, pick P1, play a lesson, see the stars,
 * and find them again after the activity is recreated. Screenshots go to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestWordQuestApplication::class, qualifiers = "w400dp-h860dp-xhdpi")
class WalkingSkeletonTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as WordQuestApplication).container

    private fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.waitFor(matcher: SemanticsMatcher) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(matcher, useUnmergedTree = false).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun createProfilePickClassPlayLessonAndKeepStars() {
        // Profile creation.
        rule.waitFor(hasText("New player"))
        rule.onNode(hasText("New player")).performClick()
        rule.waitFor(hasSetTextAction())
        rule.onNode(hasSetTextAction()).performTextInput("Mia")
        screenshot("1-profile-creation")
        rule.onNode(hasText("Let's go")).performClick()

        // Class picker: primary and secondary fixture packs are playable; S7 has none.
        rule.waitFor(hasText("P1"))
        rule.onNode(hasContentDescription("S7, coming soon")).assertIsNotEnabled()
        rule.onNode(hasText("S1")).assertIsEnabled()
        screenshot("2-class-picker")
        rule.onNode(hasText("P1")).performClick()

        // Path.
        rule.waitFor(hasContentDescription("Pets, lesson 1: 0 of 3 stars"))
        rule.waitFor(hasText("Mia · P1"))
        screenshot("3-path")
        rule.onNode(hasContentDescription("Pets, lesson 1: 0 of 3 stars")).performClick()

        // Lesson: the app uses the fixed seed, so the test can rebuild the same lesson and answer it.
        val environment = Environment(speechAvailable = container.speech::canSpeak, picturesPerceivable = true)
        val lesson = container.generator.generate(LessonRequest.UnitLesson("fx-pets", 1), environment, TestWordQuestApplication.SEED)
        assertEquals(10, lesson.exercises.size)
        lesson.exercises.forEachIndexed { index, exercise ->
            val tag = "option-${(exercise as ChoiceExercise).answerIndex}"
            rule.waitFor(hasTestTag(tag))
            if (index == 0) {
                val matcher = if (exercise.prompt is eu.wanderclan.wordquest.game.Prompt.Picture) {
                    hasContentDescription("Picture to name")
                } else hasContentDescription("Picture", substring = true)
                rule.waitFor(matcher)
                screenshot("4-question")
            }
            rule.onNodeWithTag(tag).performScrollTo().performClick()
            rule.waitFor(hasText("Well done!"))
            if (index == 0) screenshot("5-feedback")
            rule.onNode(hasText("Continue")).performClick()
        }

        // Result.
        rule.waitFor(hasContentDescription("3 of 3 stars"))
        rule.waitFor(hasText("Lesson complete!"))
        screenshot("6-result")
        rule.onNode(hasText("Continue")).performClick()

        // Back on the path, with the stars, and still there after the activity is recreated.
        rule.waitFor(hasContentDescription("Pets, lesson 1: 3 of 3 stars"))
        rule.activityRule.scenario.recreate()
        rule.waitFor(hasContentDescription("Pets, lesson 1: 3 of 3 stars"))
        val profileId = runBlocking { container.profiles.profiles().first().single().id }
        assertEquals(mapOf(("fx-pets" to 1) to 3), runBlocking { container.progress.bestStars(profileId).first() })

        // About.
        rule.onNode(hasContentDescription("About")).performClick()
        rule.waitFor(hasText("Pictures: OpenMoji (openmoji.org), licence CC BY-SA 4.0"))
        rule.waitFor(hasText("Synthetic speech: Piper", substring = true))
        screenshot("7-about")
    }
}
