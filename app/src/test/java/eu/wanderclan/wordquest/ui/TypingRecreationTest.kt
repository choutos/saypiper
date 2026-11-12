package eu.wanderclan.wordquest.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.wanderclan.wordquest.MainActivity
import eu.wanderclan.wordquest.game.Environment
import eu.wanderclan.wordquest.game.LessonRequest
import eu.wanderclan.wordquest.game.TypingExercise
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestWordQuestApplication::class, qualifiers = "w360dp-h800dp-xhdpi")
class TypingRecreationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(matcher: SemanticsMatcher) {
        rule.waitUntil(10_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun typedAnswerSurvivesActivityRecreation() {
        val app = rule.activity.application as TestWordQuestApplication
        val request = LessonRequest.UnitLesson("fx-feelings", 1)
        rule.runOnIdle {
            app.lessonSeed = (0L..200L).first {
                app.container.generator.generate(request, Environment({ false }, true), it).exercises.first() is TypingExercise
            }
        }
        waitFor(hasText("New player"))
        rule.onNodeWithText("New player").performClick()
        waitFor(hasSetTextAction())
        rule.onNode(hasSetTextAction()).performTextInput("Mia")
        rule.onNodeWithText("Let's go").performScrollTo().performClick()
        waitFor(hasText("S1"))
        rule.onNodeWithText("S1").performScrollTo().performClick()
        waitFor(hasContentDescription("Feelings, lesson 1: 0 of 3 stars"))
        rule.onNodeWithContentDescription("Feelings, lesson 1: 0 of 3 stars").performScrollTo().performClick()
        waitFor(hasTestTag("typed-answer"))
        rule.onNodeWithTag("typed-answer").performTextInput("partly typed")
        rule.activityRule.scenario.recreate()
        waitFor(hasTestTag("typed-answer"))
        rule.onNodeWithTag("typed-answer").assertTextContains("partly typed")
        rule.onNodeWithTag("typed-answer").performTextReplacement("changed answer")
        rule.onNodeWithTag("typed-answer").assertTextContains("changed answer")
    }
}
