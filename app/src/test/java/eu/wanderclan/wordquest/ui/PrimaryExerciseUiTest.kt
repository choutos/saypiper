package eu.wanderclan.wordquest.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.game.*
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestWordQuestApplication::class, qualifiers = "w400dp-h860dp-xhdpi")
class PrimaryExerciseUiTest {
    @get:Rule val rule = createComposeRule()
    private val catalog = Fixtures.catalog()
    private val generator = LessonGenerator(catalog)
    private val pictures = Pictures(Fixtures.source, ContentLoader.read(Fixtures.source).first.pictureIndex, Dispatchers.Unconfined)

    private fun exercise(type: ExerciseType): Exercise = (0L..30L).flatMap { seed ->
        listOf("fx-pets" to 3, "fx-home" to 4).flatMap { (unit, number) ->
            generator.generate(LessonRequest.UnitLesson(unit, number), Environment({ true }, true), seed).exercises
        }
    }.first { it.type == type && catalog.word(it.wordId)!!.gradePack.band == Band.B &&
        (it !is TilesExercise || it.prompt is Prompt.Picture) }

    private fun check(type: ExerciseType, right: Boolean) {
        val exercise = exercise(type)
        assertEquals(Band.B, catalog.word(exercise.wordId)!!.gradePack.band)
        var session by mutableStateOf(LessonSession("ui", Lesson(LessonRequest.Practice(exercise.wordIds), listOf(exercise), emptyList())))
        var continued = false
        var replayed: String? = null
        val spoken = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalPictures provides pictures) {
                WordQuestTheme {
                    ExerciseQuestion(exercise, session.response, catalog.word(exercise.wordId)!!.gradePack.band, true,
                        { replayed = it; spoken += it }, { session = session.answer(it) }, { continued = true }, catalog.word(exercise.wordId)!!.word.text)
                }
            }
        }
        rule.waitForIdle()
        if (exercise is PairsExercise) {
            rule.onNodeWithTag("speaker").assertDoesNotExist()
            assertTrue(spoken.isEmpty())
        }
        if (exercise.type in setOf(ExerciseType.GAP_FILL, ExerciseType.DEFINITION_TO_WORD, ExerciseType.PICTURE_TO_WORD) ||
            exercise is TilesExercise && exercise.prompt is Prompt.Picture) {
            rule.onNodeWithTag("speaker").assertDoesNotExist()
            assertNull(replayed)
        }
        if (exercise.type in setOf(ExerciseType.WORD_TO_PICTURE, ExerciseType.LISTEN_AND_PICK)) {
            rule.onNodeWithTag("speaker").assertExists()
            rule.runOnIdle { assertEquals(listOf(exercise.wordId), spoken) }
        }
        // The test dispatcher completes decoding before idle; pictures must already exist.
        val pictureDescriptions = when (exercise) {
            is PairsExercise -> exercise.pictures.indices.map { "Picture ${it + 1}" }
            is ChoiceExercise -> exercise.options.mapIndexedNotNull { index, option ->
                if (option is Option.Picture) "Picture ${index + 1}" else null
            } + if (exercise.prompt is Prompt.Picture) listOf("Picture to name") else emptyList()
            is TilesExercise -> if (exercise.prompt is Prompt.Picture) listOf("Picture to name") else emptyList()
        }
        pictureDescriptions.forEach { description ->
            rule.onNodeWithContentDescription(description, useUnmergedTree = true).assertExists()
        }
        if (exercise is ChoiceExercise && exercise.prompt == Prompt.Listening) {
            rule.onNodeWithTag("listen-replay").performClick()
            rule.runOnIdle { assertEquals(exercise.wordId, replayed) }
        }
        if (right) rule.onRoot().captureRoboImage("build/outputs/roborazzi/primary-${type.name.lowercase()}.png")
        when (exercise) {
            is ChoiceExercise -> {
                val index = if (right) exercise.answerIndex else (exercise.answerIndex + 1) % exercise.options.size
                rule.onNodeWithTag("option-$index").performScrollTo().performClick()
            }
            is PairsExercise -> {
                val ids = exercise.pictures.map { it.wordId }.toMutableList()
                if (!right) ids[0] = ids[1].also { ids[1] = ids[0] }
                exercise.pictures.forEachIndexed { index, picture ->
                    rule.onNodeWithTag("pair-word-${ids[index]}").performScrollTo().performClick()
                    rule.onNodeWithTag("pair-picture-${picture.wordId}").performScrollTo().performClick()
                }
                rule.onNodeWithTag("submit").performScrollTo().performClick()
            }
            is TilesExercise -> {
                val remaining = exercise.tiles.indices.toMutableList()
                val indices = exercise.text.map { char -> remaining.first { exercise.tiles[it] == char }.also { remaining.remove(it) } }.toMutableList()
                if (!right) {
                    val other = indices.indexOfFirst { exercise.tiles[it] != exercise.tiles[indices[0]] }
                    indices[0] = indices[other].also { indices[other] = indices[0] }
                }
                indices.forEach { rule.onNodeWithTag("tile-$it").performScrollTo().performClick() }
                rule.onNodeWithTag("submit").performScrollTo().performClick()
            }
        }
        if (exercise.type in setOf(ExerciseType.GAP_FILL, ExerciseType.DEFINITION_TO_WORD, ExerciseType.PICTURE_TO_WORD) ||
            exercise is TilesExercise && exercise.prompt is Prompt.Picture) {
            rule.onNodeWithTag("speaker").assertExists()
            rule.runOnIdle { assertEquals(listOf(exercise.wordId), spoken) }
            // Recomposition after feedback never automatically repeats the word.
            rule.waitForIdle()
            rule.runOnIdle { assertEquals(1, spoken.size) }
            rule.onNodeWithTag("speaker").performScrollTo().performClick()
            rule.runOnIdle { assertEquals(listOf(exercise.wordId, exercise.wordId), spoken) }
        }
        rule.runOnIdle { assertEquals(right, session.lastAnswerCorrect); assertEquals(if (right) 3 else 2, session.stars) }
        if (!right && exercise is ChoiceExercise) {
            rule.onNodeWithText("Not quite. It is: ${catalog.word(exercise.wordId)!!.word.text}").assertExists()
        }
        if (exercise is PairsExercise) {
            rule.onNodeWithTag("speaker").assertDoesNotExist()
            rule.runOnIdle { assertTrue(spoken.isEmpty()) }
            exercise.pictures.forEachIndexed { index, picture ->
                val correct = right || index >= 2
                rule.onNodeWithTag("pair-feedback-${picture.wordId}", useUnmergedTree = true)
                    .assertContentDescriptionEquals(if (correct) "Correct pair" else "Try this pair again")
                if (!correct) rule.onNodeWithTag("pair-answer-${picture.wordId}", useUnmergedTree = true)
                    .assertTextEquals("→ ${catalog.word(picture.wordId)!!.word.text}")
            }
            if (!right) rule.onRoot().captureRoboImage("build/outputs/roborazzi/primary-match_pairs-feedback.png")
        }
        rule.onNodeWithText("Continue").performClick()
        rule.runOnIdle { assertTrue(continued) }
    }

    @Test fun pictureToWordRight() = check(ExerciseType.PICTURE_TO_WORD, true)
    @Test fun pictureToWordWrong() = check(ExerciseType.PICTURE_TO_WORD, false)
    @Test fun wordToPictureRight() = check(ExerciseType.WORD_TO_PICTURE, true)
    @Test fun wordToPictureWrong() = check(ExerciseType.WORD_TO_PICTURE, false)
    @Test fun listenRight() = check(ExerciseType.LISTEN_AND_PICK, true)
    @Test fun listenWrong() = check(ExerciseType.LISTEN_AND_PICK, false)
    @Test fun pairsRight() = check(ExerciseType.MATCH_PAIRS, true)
    @Test fun pairsWrong() = check(ExerciseType.MATCH_PAIRS, false)
    @Test fun tilesRight() = check(ExerciseType.SPELL_WITH_TILES, true)
    @Test fun tilesWrong() = check(ExerciseType.SPELL_WITH_TILES, false)
    @Test fun gapRight() = check(ExerciseType.GAP_FILL, true)
    @Test fun gapWrong() = check(ExerciseType.GAP_FILL, false)
    @Test fun definitionRight() = check(ExerciseType.DEFINITION_TO_WORD, true)
    @Test fun definitionWrong() = check(ExerciseType.DEFINITION_TO_WORD, false)
}
