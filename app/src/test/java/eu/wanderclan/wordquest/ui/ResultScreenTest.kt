package eu.wanderclan.wordquest.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import eu.wanderclan.wordquest.Fixtures
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The result screen keeps Continue and the points reachable on a short landscape screen, crown included. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestWordQuestApplication::class, qualifiers = "w640dp-h360dp-land-xhdpi")
class ResultScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun crownedResultInLandscapeKeepsContinueReachable() {
        val pets = Fixtures.catalog().unit("fx-pets")!!.first
        var continued = false
        rule.setContent {
            WordQuestTheme { ResultScreen(stars = 3, points = 25, crownedUnit = pets, practice = false) { continued = true } }
        }
        rule.onNodeWithText("You earned the crown for Pets!").assertIsDisplayed()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/crown-celebration-landscape.png")
        rule.onNodeWithText("+25 points").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Continue").performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(continued) }
    }

    @Test
    fun practiceResultSaysPractice() {
        rule.setContent { WordQuestTheme { ResultScreen(stars = 2, points = 20, crownedUnit = null, practice = true) {} } }
        rule.onNodeWithText("Practice complete!").assertIsDisplayed()
    }
}
