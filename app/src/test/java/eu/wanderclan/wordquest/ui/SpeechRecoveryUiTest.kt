package eu.wanderclan.wordquest.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.wanderclan.wordquest.AppContainer
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.audio.FakeSpeech
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.data.WordQuestDatabase
import eu.wanderclan.wordquest.game.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestWordQuestApplication::class, qualifiers = "w400dp-h860dp-xhdpi")
class SpeechRecoveryUiTest {
    @get:Rule val rule = createComposeRule()
    private val request = LessonRequest.UnitLesson("fx-pets", 1)

    private fun container(speech: FakeSpeech, seed: Long): AppContainer = AppContainer(
        ContentLoader.load(Fixtures.source), Fixtures.source,
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WordQuestDatabase::class.java).build(),
        speech, { LocalDate.of(2026, 11, 11) }, { seed },
    )

    @Test fun `failed utterance reveals the word and rest of real lesson has no listening`() {
        val generator = LessonGenerator(Fixtures.catalog())
        val seed = (0L..100L).first {
            generator.generate(request, Environment(true, true), it).exercises.first().type == ExerciseType.LISTEN_AND_PICK
        }
        val container = container(FakeSpeech(FakeSpeech.Mode.FAILING), seed)
        val lesson = container.generator.generate(request, Environment(true, true), seed)
        var expected = LessonSession("ui", lesson).withoutSpeech(container.generator)
        rule.setContent {
            CompositionLocalProvider(LocalPictures provides container.pictures) {
                WordQuestTheme { LessonScreen(container, 0, request, {}, {}) }
            }
        }
        val word = container.catalog.word(expected.current!!.wordId)!!.word.text
        rule.waitUntil(10_000) { rule.onAllNodesWithText(word).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("prompt").assertTextEquals(word)
        // Follow every remaining question through the actual view model, without finishing/saving a profile.
        repeat(lesson.exercises.size - 1) {
            rule.onNodeWithTag("speaker").assertDoesNotExist()
            rule.onNodeWithContentDescription("Listen").assertDoesNotExist()
            val exercise = expected.current as ChoiceExercise
            rule.onNodeWithTag("option-${exercise.answerIndex}").performScrollTo().performClick()
            rule.onNodeWithText("Continue").performClick()
            expected = expected.answer(exercise.answerIndex).next()
        }
        rule.onNodeWithContentDescription("Listen").assertDoesNotExist()
        container.speech.close()
    }

    @Test fun `a tile retry starts with unused tiles even when it repeats the same exercise`() {
        val request = LessonRequest.Practice(listOf("chair"))
        val generator = LessonGenerator(Fixtures.catalog())
        val seed = (0L..100L).first {
            generator.generate(request, Environment(false, true), it).exercises.first() is TilesExercise
        }
        val container = container(FakeSpeech(), seed)
        val exercise = container.generator.generate(request, Environment(false, true), seed).exercises.first() as TilesExercise
        rule.setContent {
            CompositionLocalProvider(LocalPictures provides container.pictures) {
                WordQuestTheme { LessonScreen(container, 0, request, {}, {}) }
            }
        }
        exercise.tiles.indices.forEach { rule.onNodeWithTag("tile-$it").performScrollTo().performClick() }
        rule.onNodeWithTag("submit").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithTag("spelling").assertTextEquals("____")
        exercise.tiles.indices.forEach { rule.onNodeWithTag("tile-$it").assertIsEnabled() }
        rule.onNodeWithTag("speaker").assertDoesNotExist()
    }

    @Test fun `band A automatically speaks and has only picture choices`() {
        val speech = FakeSpeech(FakeSpeech.Mode.SILENT)
        val container = container(speech, 8)
        val first = container.generator.generate(request, Environment(true, true), 8).exercises.first() as ChoiceExercise
        rule.setContent {
            CompositionLocalProvider(LocalPictures provides container.pictures) {
                WordQuestTheme { LessonScreen(container, 0, request, {}, {}) }
            }
        }
        rule.waitUntil(10_000) { speech.spoken.isNotEmpty() }
        assertEquals(first.wordId, speech.spoken.first())
        assertTrue(first.options.all { it is Option.Picture })
        rule.onNodeWithTag("speaker").assertExists()
        rule.onNodeWithTag("option-0").assertExists()
    }
    @Test fun `optional speaker failure keeps the gap question sentence`() {
        val request = LessonRequest.Practice(listOf("kitchen"))
        val generator = LessonGenerator(Fixtures.catalog())
        val seed = (0L..100L).first {
            generator.generate(request, Environment(true, true), it).exercises.first().type == ExerciseType.GAP_FILL
        }
        val container = container(FakeSpeech(FakeSpeech.Mode.FAILING), seed)
        val exercise = container.generator.generate(request, Environment(true, true), seed).exercises.single() as ChoiceExercise
        val example = (exercise.prompt as Prompt.Gap).example
        val sentence = example.before + "____" + example.after
        rule.setContent {
            CompositionLocalProvider(LocalPictures provides container.pictures) {
                WordQuestTheme { LessonScreen(container, 0, request, {}, {}) }
            }
        }
        rule.onNodeWithTag("prompt").assertTextEquals(sentence)
        rule.onNodeWithTag("speaker").performClick()
        rule.onNodeWithTag("speaker").assertDoesNotExist()
        rule.onNodeWithTag("prompt").assertTextEquals(sentence)
    }

}
