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
    private val pictures = Pictures(Fixtures.source, ContentLoader.read(Fixtures.source).first.pictureIndex)

    private fun exercise(type: ExerciseType): Exercise = (0L..30L).flatMap { seed ->
        listOf("fx-pets" to 3, "fx-home" to 4).flatMap { (unit, number) ->
            generator.generate(LessonRequest.UnitLesson(unit, number), Environment(true, true), seed).exercises
        }
    }.first { it.type == type }

    private fun check(type: ExerciseType, right: Boolean) {
        val exercise = exercise(type)
        var session by mutableStateOf(LessonSession("ui", Lesson(LessonRequest.Practice(exercise.wordIds), listOf(exercise), emptyList())))
        var continued = false
        rule.setContent {
            CompositionLocalProvider(LocalPictures provides pictures) {
                WordQuestTheme {
                    ExerciseQuestion(exercise, session.response, catalog.word(exercise.wordId)!!.gradePack.band, true,
                        {}, { session = session.answer(it) }, { continued = true })
                }
            }
        }
        rule.waitForIdle()
        // Await the asynchronous decode before capturing; test nodes only appear once Image is ready.
        val hasPictures = exercise is PairsExercise || (exercise is TilesExercise && exercise.prompt is Prompt.Picture) ||
            (exercise is ChoiceExercise && exercise.options.any { it is Option.Picture })
        if (hasPictures) rule.waitUntil(10_000) {
            rule.onAllNodes(hasContentDescription("Picture", substring = true)).fetchSemanticsNodes().isNotEmpty()
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
        rule.runOnIdle { assertEquals(right, session.lastAnswerCorrect); assertEquals(if (right) 3 else 2, session.stars) }
        rule.onNodeWithText("Continue").performClick()
        rule.runOnIdle { assertTrue(continued) }
    }

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
