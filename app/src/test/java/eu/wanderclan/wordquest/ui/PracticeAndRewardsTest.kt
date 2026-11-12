package eu.wanderclan.wordquest.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import eu.wanderclan.wordquest.MainActivity
import eu.wanderclan.wordquest.game.ChoiceExercise
import eu.wanderclan.wordquest.game.Environment
import eu.wanderclan.wordquest.game.LessonRequest
import eu.wanderclan.wordquest.game.LessonResult
import eu.wanderclan.wordquest.game.WordOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Practice, rewards and unlocking on fixture content, with the test app's fake clock.
 * Screenshots go to build/outputs/roborazzi with the others.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestWordQuestApplication::class, qualifiers = "w400dp-h860dp-xhdpi")
class PracticeAndRewardsTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as TestWordQuestApplication
    private val container get() = app.container
    private val environment get() = Environment(speechAvailable = container.speech::canSpeak, picturesPerceivable = true)

    private fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.waitFor(matcher: SemanticsMatcher) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** A P1 profile made through the repository, opened from the profile list. */
    private fun openProfile(prepare: suspend (Long) -> Unit = {}): Long {
        val id = runBlocking {
            container.profiles.create("Mia", "fox").also {
                container.profiles.setGrade(it, "p1")
                prepare(it)
            }
        }
        rule.waitFor(hasText("Mia") or hasText("Mia · P1"))
        // The startup read may see the profile before the initially empty chooser is composed.
        if (rule.onAllNodes(hasText("Mia")).fetchSemanticsNodes().isNotEmpty()) rule.onNode(hasText("Mia")).performClick()
        rule.waitFor(hasText("Mia · P1"))
        return id
    }

    /** Answers every question right: the app uses the fixed seed, so the test rebuilds the same lesson. */
    private fun playRight(request: LessonRequest) {
        val lesson = container.generator.generate(request, environment, TestWordQuestApplication.SEED)
        lesson.exercises.forEach { exercise ->
            val tag = "option-${(exercise as ChoiceExercise).answerIndex}"
            rule.waitFor(hasTestTag(tag))
            rule.onNodeWithTag(tag).performScrollTo().performClick()
            rule.waitFor(hasText("Well done!"))
            rule.onNode(hasText("Continue")).performClick()
        }
        rule.waitFor(hasContentDescription("3 of 3 stars"))
    }

    @Test
    fun finishLessonThenPracticeItsWordsTheNextDay() {
        val profileId = openProfile()

        // Nothing has history yet: the button says so instead of starting an empty lesson.
        rule.waitFor(hasText("Nothing to practise today"))
        rule.onNodeWithTag("practice").assertIsNotEnabled()
        rule.onNode(hasText("Play today to start a streak")).assertExists()
        rule.onNode(hasText("0 points")).assertExists()
        screenshot("practice-empty")

        rule.onNode(hasContentDescription("Pets, lesson 1: 0 of 3 stars")).performClick()
        playRight(LessonRequest.UnitLesson("fx-pets", 1))
        rule.onNode(hasText("+25 points")).assertExists()
        rule.onNode(hasText("Continue")).performClick()

        // Same day: the lesson's words are due tomorrow, so practice is still empty. Lesson 2 opened.
        rule.waitFor(hasContentDescription("Pets, lesson 2: 0 of 3 stars"))
        rule.onNode(hasText("1-day streak")).assertExists()
        rule.onNode(hasText("25 points")).assertExists()
        rule.onNodeWithTag("practice").assertIsNotEnabled()

        // The next day, with the home screen still open: midnight passes while the app is in the foreground.
        app.today = app.today.plusDays(1)
        app.sendBroadcast(Intent(Intent.ACTION_DATE_CHANGED))
        rule.waitFor(hasText("Practise 3 words"))
        screenshot("practice-due")
        rule.onNodeWithTag("practice").assertIsEnabled().performClick()

        // The practice session is lesson 1's words, the same due day, in id order.
        playRight(LessonRequest.Practice(listOf("hamster", "mouse", "rabbit")))
        rule.onNode(hasText("Practice complete!")).assertExists()
        rule.onNode(hasText("+25 points")).assertExists()
        rule.onNode(hasText("Continue")).performClick()

        rule.waitFor(hasText("2-day streak"))
        rule.waitFor(hasText("50 points"))
        rule.waitFor(hasText("Nothing to practise today"))
        screenshot("home-streak-points")

        // Each practised word moved up exactly one box, so the session covered exactly those words.
        val dueDays = runBlocking {
            container.progress.schedule(profileId).first()
        }.associate { it.wordId to it.dueDay }
        assertEquals(mapOf("rabbit" to app.today.plusDays(3), "mouse" to app.today.plusDays(3), "hamster" to app.today.plusDays(3)), dueDays)
    }

    @Test
    fun theDayIsReadAgainWhenTheAppReturnsToTheForeground() {
        openProfile { id ->
            container.progress.save(id, LessonResult("seed", LessonRequest.UnitLesson("fx-pets", 1), listOf(WordOutcome("rabbit", true)), 3))
        }
        rule.waitFor(hasText("Nothing to practise today"))
        app.today = app.today.plusDays(1)
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitFor(hasText("Practise 1 word"))
    }

    @Test
    fun aLockedLessonCannotBeStarted() {
        openProfile()
        rule.waitFor(hasContentDescription("Pets, lesson 2: locked"))
        rule.onNode(hasContentDescription("Pets, lesson 2: locked")).performClick()
        rule.onNode(hasContentDescription("Pets, review: locked")).performClick()
        rule.waitForIdle()
        rule.onNode(hasContentDescription("Stop the lesson")).assertDoesNotExist()
        rule.onNode(hasText("Mia · P1")).assertExists()
    }

    @Test
    fun finishingAUnitEarnsItsCrown() {
        openProfile { id ->
            (1..2).forEach { n ->
                container.progress.save(id, LessonResult("seed-$n", LessonRequest.UnitLesson("fx-pets", n), listOf(WordOutcome("rabbit", true)), 3))
            }
        }
        rule.waitFor(hasContentDescription("Pets, review: 0 of 3 stars"))
        rule.onNode(hasContentDescription("Pets, review: 0 of 3 stars")).performClick()
        playRight(LessonRequest.UnitLesson("fx-pets", 3))
        rule.waitFor(hasText("You earned the crown for Pets!"))
        screenshot("crown-celebration")
        rule.onNode(hasText("Continue")).performClick()

        rule.waitFor(hasContentDescription("Pets: crown earned"))
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Fruit, lesson 1: 0 of 3 stars"))
        rule.onNode(hasContentDescription("Fruit, lesson 2: locked")).assertExists()
    }
}
