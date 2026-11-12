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
import eu.wanderclan.wordquest.audio.ClipPlayer
import eu.wanderclan.wordquest.audio.ClipSpeech
import eu.wanderclan.wordquest.audio.Speech
import eu.wanderclan.wordquest.audio.SpeechResult
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.data.WordQuestDatabase
import eu.wanderclan.wordquest.game.*
import kotlinx.coroutines.Dispatchers
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

    private fun waitForLesson() {
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Stop the lesson").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun container(speech: Speech, seed: Long): AppContainer = AppContainer(
        ContentLoader.load(Fixtures.source), Fixtures.source,
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WordQuestDatabase::class.java).build(),
        speech, { LocalDate.of(2026, 11, 11) }, { seed },
        pictureDecodeDispatcher = Dispatchers.Unconfined,
    )

    @Test fun `failed utterance reveals the word and rest of real lesson has no listening`() {
        val generator = LessonGenerator(Fixtures.catalog())
        val seed = (0L..100L).first {
            generator.generate(request, Environment({ true }, true), it).exercises.first().type == ExerciseType.LISTEN_AND_PICK
        }
        val container = container(FakeSpeech(FakeSpeech.Mode.FAILING), seed)
        val lesson = container.generator.generate(request, Environment({ true }, true), seed)
        var expected = LessonSession("ui", lesson).withoutSpeech(container.generator)
        rule.setContent {
            CompositionLocalProvider(LocalPictures provides container.pictures) {
                WordQuestTheme { LessonScreen(container, 0, request, {}, {}) }
            }
        }
        waitForLesson()
        val word = container.catalog.word(expected.current!!.wordId)!!.word.text
        rule.waitForIdle()
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
            generator.generate(request, Environment({ false }, true), it).exercises.first() is TilesExercise
        }
        val container = container(FakeSpeech(), seed)
        val exercise = container.generator.generate(request, Environment({ false }, true), seed).exercises.first() as TilesExercise
        rule.setContent {
            CompositionLocalProvider(LocalPictures provides container.pictures) {
                WordQuestTheme { LessonScreen(container, 0, request, {}, {}) }
            }
        }
        waitForLesson()
        exercise.tiles.indices.forEach { rule.onNodeWithTag("tile-$it").performScrollTo().performClick() }
        rule.onNodeWithTag("submit").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithTag("spelling").assertTextEquals("____")
        exercise.tiles.indices.forEach { rule.onNodeWithTag("tile-$it").assertIsEnabled() }
        rule.onNodeWithTag("speaker").assertDoesNotExist()
    }

    @Test fun `band A without a device voice speaks every question from clips and requires no reading`() {
        val spoken = mutableListOf<String>()
        val speech = ClipSpeech(ContentLoader.load(Fixtures.source).audioWordIds, FakeSpeech(), object : ClipPlayer {
            override fun play(path: String, completed: (SpeechResult) -> Unit) { spoken += path; completed(SpeechResult.SPOKEN) }
            override fun stop() {}
        }, Dispatchers.Unconfined)
        val container = container(speech, 8)
        val lesson = container.generator.generate(request, Environment(speech::canSpeak, true), 8)
        assertTrue(lesson.exercises.any { it.type == ExerciseType.LISTEN_AND_PICK })
        rule.setContent {
            CompositionLocalProvider(LocalPictures provides container.pictures) {
                WordQuestTheme { LessonScreen(container, 0, request, {}, {}) }
            }
        }
        waitForLesson()
        lesson.exercises.forEachIndexed { index, exercise ->
            rule.waitForIdle()
            assertEquals("content/audio/${exercise.wordId}.ogg", spoken[index])
            assertEquals(index + 1, spoken.size)
            val choice = exercise as ChoiceExercise
            assertTrue(choice.options.all { it is Option.Picture })
            rule.onNodeWithTag("speaker").assertExists()
            rule.onNodeWithTag("option-${choice.answerIndex}").performScrollTo().performClick()
            if (index < lesson.exercises.lastIndex) rule.onNodeWithText("Continue").performClick()
        }
    }

    @Test fun `band B without a device voice has no listening and no speaker`() {
        val speech = ClipSpeech(ContentLoader.load(Fixtures.source).audioWordIds, FakeSpeech(), object : ClipPlayer {
            override fun play(path: String, completed: (SpeechResult) -> Unit) { error("P3 must not use a clip") }
            override fun stop() {}
        }, Dispatchers.Unconfined)
        val request = LessonRequest.Practice(listOf("kitchen"))
        val generator = LessonGenerator(Fixtures.catalog())
        val seed = (0L..100L).first {
            generator.generate(request, Environment(speech::canSpeak, true), it).exercises.first().type == ExerciseType.GAP_FILL
        }
        val container = container(speech, seed)
        val lesson = container.generator.generate(request, Environment(speech::canSpeak, true), seed)
        assertTrue(lesson.exercises.none { it.type == ExerciseType.LISTEN_AND_PICK })
        val exercise = lesson.exercises.single() as ChoiceExercise
        rule.setContent {
            CompositionLocalProvider(LocalPictures provides container.pictures) {
                WordQuestTheme { LessonScreen(container, 0, request, {}, {}) }
            }
        }
        waitForLesson()
        rule.onNodeWithTag("speaker").assertDoesNotExist()
        rule.onNodeWithTag("listen-replay").assertDoesNotExist()
        rule.onNodeWithTag("option-${exercise.answerIndex}").performScrollTo().performClick()
        rule.onNodeWithText("Well done!").assertExists()
        rule.onNodeWithTag("speaker").assertDoesNotExist()
        rule.onNodeWithTag("listen-replay").assertDoesNotExist()
    }

    @Test fun `broken clip reveals the current word and disables clip listening for this session`() {
        val speech = ClipSpeech(ContentLoader.load(Fixtures.source).audioWordIds, FakeSpeech(), object : ClipPlayer {
            override fun play(path: String, completed: (SpeechResult) -> Unit) { completed(SpeechResult.FAILED) }
            override fun stop() {}
        }, Dispatchers.Unconfined)
        val generator = LessonGenerator(Fixtures.catalog())
        val seed = (0L..100L).first {
            generator.generate(request, Environment(speech::canSpeak, true), it).exercises.first().type == ExerciseType.LISTEN_AND_PICK
        }
        val container = container(speech, seed)
        val lesson = container.generator.generate(request, Environment(speech::canSpeak, true), seed)
        var expected = LessonSession("ui", lesson).withoutSpeech(container.generator)
        rule.setContent {
            CompositionLocalProvider(LocalPictures provides container.pictures) {
                WordQuestTheme { LessonScreen(container, 0, request, {}, {}) }
            }
        }
        waitForLesson()
        rule.onNodeWithTag("prompt").assertTextEquals(container.catalog.word(expected.current!!.wordId)!!.word.text)
        repeat(lesson.exercises.size - 1) {
            rule.onNodeWithTag("speaker").assertDoesNotExist()
            rule.onNodeWithTag("listen-replay").assertDoesNotExist()
            val exercise = expected.current as ChoiceExercise
            rule.onNodeWithTag("option-${exercise.answerIndex}").performScrollTo().performClick()
            rule.onNodeWithText("Continue").performClick()
            expected = expected.answer(exercise.answerIndex).next()
        }
        rule.onNodeWithTag("listen-replay").assertDoesNotExist()
    }
    @Test fun `feedback speech failure keeps the gap question sentence`() {
        val request = LessonRequest.Practice(listOf("kitchen"))
        val generator = LessonGenerator(Fixtures.catalog())
        val seed = (0L..100L).first {
            generator.generate(request, Environment({ true }, true), it).exercises.first().type == ExerciseType.GAP_FILL
        }
        val container = container(FakeSpeech(FakeSpeech.Mode.FAILING), seed)
        val exercise = container.generator.generate(request, Environment({ true }, true), seed).exercises.single() as ChoiceExercise
        val example = (exercise.prompt as Prompt.Gap).example
        val sentence = example.before + "____" + example.after
        rule.setContent {
            CompositionLocalProvider(LocalPictures provides container.pictures) {
                WordQuestTheme { LessonScreen(container, 0, request, {}, {}) }
            }
        }
        waitForLesson()
        rule.onNodeWithTag("prompt").assertTextEquals(sentence)
        rule.onNodeWithTag("speaker").assertDoesNotExist()
        rule.onNodeWithTag("option-${exercise.answerIndex}").performScrollTo().performClick()
        rule.onNodeWithTag("speaker").assertDoesNotExist()
        rule.onNodeWithTag("prompt").assertTextEquals(sentence)
    }

}
