package eu.wanderclan.wordquest.ui

import androidx.compose.ui.test.*
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.wanderclan.wordquest.game.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestWordQuestApplication::class, qualifiers = "w400dp-h860dp-xhdpi")
class FamilyProfilesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val container get() = ApplicationProvider.getApplicationContext<TestWordQuestApplication>().container

    private fun seed(name: String) = runBlocking {
        container.profiles.create(name, "fox").also { container.profiles.setGrade(it, "p1") }
    }
    private fun waitText(text: String) = rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) {
        waitText(text)
        rule.waitUntil(10_000) { rule.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        val node = rule.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun confirm() {
        click("Confirm")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Confirm").fetchSemanticsNodes().isEmpty() }
    }
    private fun toggle(label: String) {
        rule.onNodeWithContentDescription(label).performScrollTo().assertIsDisplayed()
            .performClick()
    }
    private fun root() { rule.setContent { WordQuestRoot(container) } }
    private fun enterParent() {
        click("Parent corner")
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("What is", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        val question = rule.onNode(hasText("What is", substring = true)).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        val numbers = Regex("\\d+").findAll(question).map { it.value.toInt() }.toList()
        rule.onNode(hasSetTextAction()).performScrollTo().performTextInput(numbers.sum().toString())
        click("Enter parent corner")
        waitText("Sound")
    }

    @Test fun oneProfileStartsOnItsPath() {
        seed("Mia")
        root()
        waitText("Mia · P1")
        rule.onNodeWithText("Who is playing?").assertDoesNotExist()
    }

    @Test fun twoProfilesStartOnTheChooser() {
        seed("Mia"); seed("Leo")
        root()
        waitText("Who is playing?")
        rule.onNodeWithText("Mia").assertIsDisplayed()
        rule.onNodeWithText("Leo").assertIsDisplayed()
    }

    @Test fun addPlayerThenChangeAvatarAndClassKeepsProgress() {
        val mia = seed("Mia")
        runBlocking { container.progress.save(mia, LessonResult("seed", LessonRequest.UnitLesson("fx-pets", 1), listOf(WordOutcome("rabbit", true)), 3)) }
        root()
        click("Players"); click("New player")
        rule.onNode(hasSetTextAction()).performTextInput("Leo")
        click("Let's go"); click("P1")
        click("Players"); click("Mia")
        click("Change avatar")
        rule.onNodeWithContentDescription("Avatar: sun").performScrollTo().performClick()
        click("Save avatar"); click("Change class"); click("P3")
        waitText("Mia · P3")
        runBlocking {
            val profile = container.profiles.profile(mia).first()!!
            assertEquals("sun", profile.avatarId)
            assertEquals("p3", profile.gradeId)
            assertEquals(3, container.progress.bestStars(mia).first()["fx-pets" to 1])
            assertEquals(2, container.profiles.profiles().first().size)
        }
    }

    @Test fun wrongAnswerAndSystemBackDoNotUnlockTheCorner() {
        seed("Mia"); seed("Leo"); root()
        click("Parent corner")
        rule.onNode(hasSetTextAction()).performTextInput("0")
        click("Enter parent corner")
        rule.onNodeWithText("Sound").assertDoesNotExist()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        click("Parent corner")
        rule.onNodeWithText("Sound").assertDoesNotExist()
        rule.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }

    @Test fun resetAndDeleteRequireConfirmationAndLeaveTheOtherPlayerUntouched() {
        val mia = seed("Mia"); val leo = seed("Leo")
        runBlocking {
            for (id in listOf(mia, leo)) container.progress.save(id,
                LessonResult("result-$id", LessonRequest.UnitLesson("fx-pets", 1), listOf(WordOutcome("rabbit", true)), 3))
        }
        root(); enterParent()
        rule.onAllNodesWithText("Reset progress")[0].performScrollTo().performClick()
        click("Cancel")
        runBlocking { assertFalse(container.progress.bestStars(mia).first().isEmpty()) }
        rule.onAllNodesWithText("Reset progress")[0].performScrollTo().performClick()
        confirm()
        rule.waitUntil(10_000) { rule.waitForIdle(); runBlocking { container.progress.bestStars(mia).first().isEmpty() } }
        runBlocking {
            assertTrue(container.progress.schedule(mia).first().isEmpty())
            assertTrue(container.progress.activity(mia).first().isEmpty())
            // Delete a profile that has progress, independently of the earlier reset assertion.
            container.progress.save(mia, LessonResult("played-again", LessonRequest.UnitLesson("fx-pets", 1), listOf(WordOutcome("rabbit", false)), 2))
        }
        rule.onAllNodesWithText("Delete player")[0].performScrollTo().performClick()
        click("Cancel")
        runBlocking {
            assertNotNull(container.profiles.profile(mia).first())
            assertEquals(2, container.progress.bestStars(mia).first()["fx-pets" to 1])
            assertEquals(1, container.progress.schedule(mia).first().size)
            assertEquals(1, container.progress.activity(mia).first().size)
        }
        rule.onAllNodesWithText("Delete player")[0].performScrollTo().performClick()
        confirm()
        rule.waitUntil(10_000) { rule.waitForIdle(); runBlocking { container.profiles.profile(mia).first() == null } }
        runBlocking {
            assertTrue(container.progress.bestStars(mia).first().isEmpty())
            assertTrue(container.progress.schedule(mia).first().isEmpty())
            assertTrue(container.progress.activity(mia).first().isEmpty())
            assertEquals(3, container.progress.bestStars(leo).first()["fx-pets" to 1])
            assertEquals(1, container.progress.schedule(leo).first().size)
            assertEquals(1, container.progress.activity(leo).first().size)
        }
        click("Back to players")
        click("Parent corner")
        rule.onNodeWithText("Sound").assertDoesNotExist()
    }

    @Test fun parentSettingsPersistAndOverrideOnlyTheChosenPlayer() {
        val mia = seed("Mia"); seed("Leo"); root(); enterParent()
        toggle("Sound")
        rule.waitUntil(10_000) { rule.waitForIdle(); runBlocking { !container.settings.settings().first().soundEnabled } }
        toggle("Open all units for Mia")
        rule.waitUntil(10_000) { rule.waitForIdle(); runBlocking { container.profiles.profile(mia).first()!!.openUnitsGradeId == "p1" } }
        click("Back to players"); click("Mia")
        waitText("Mia · P1")
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Fruit, review: 0 of 3 stars"))
        rule.onNodeWithContentDescription("Fruit, review: 0 of 3 stars").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Stop the lesson").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Stop the lesson").assertIsDisplayed().performClick()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Players"))
        click("Players"); click("Leo")
        waitText("Leo · P1")
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Fruit: locked"))
        rule.onNodeWithContentDescription("Fruit, review: locked").assertExists()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Parent corner"))
        enterParent()
        toggle("Open all units for Mia")
        toggle("Sound")
        rule.waitUntil(10_000) { rule.waitForIdle(); runBlocking {
            container.profiles.profile(mia).first()!!.openUnitsGradeId == null && container.settings.settings().first().soundEnabled
        } }
    }

    @Test fun renamingBehindTheGateKeepsProgress() {
        val mia = seed("Mia"); seed("Leo")
        runBlocking { container.progress.save(mia, LessonResult("seed", LessonRequest.UnitLesson("fx-pets", 1), listOf(WordOutcome("rabbit", true)), 3)) }
        root(); enterParent()
        rule.onAllNodesWithText("Rename")[0].performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("Mimi")
        confirm()
        rule.waitUntil(10_000) { rule.waitForIdle(); runBlocking { container.profiles.profile(mia).first()!!.nickname == "Mimi" } }
        runBlocking { assertEquals(3, container.progress.bestStars(mia).first()["fx-pets" to 1]) }
    }

    @Test fun wrongAnswerChangesTheSumAndClearsTheField() {
        var passed = false
        rule.setContent { WordQuestTheme { androidx.compose.foundation.layout.Column {
            ParentGate({ passed = true }, {}, ParentChallenge(37, 48))
        } } }
        rule.onNode(hasSetTextAction()).performTextInput("0")
        click("Enter parent corner")
        rule.onNodeWithText("What is 37 + 48?").assertDoesNotExist()
        rule.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        rule.onNode(hasSetTextAction()).performTextInput("85")
        click("Enter parent corner")
        rule.runOnIdle { assertFalse(passed) }
    }

    @Test fun sameNamePlayersHaveTheirOwnAvatarAndClassInTheConfirmation() {
        seed("Mia")
        val second = seed("Mia")
        runBlocking { container.profiles.setAvatar(second, "sun"); container.profiles.setGrade(second, "p3") }
        root(); enterParent()
        rule.onAllNodesWithText("Delete player")[1].performScrollTo().performClick()
        rule.onNode(hasContentDescription("Avatar: sun") and hasAnyAncestor(isDialog()), useUnmergedTree = true).assertExists()
        rule.onNode(hasText("P3") and hasAnyAncestor(isDialog())).assertExists()
        click("Cancel")
        runBlocking { assertEquals(2, container.profiles.profiles().first().size) }
    }
}
