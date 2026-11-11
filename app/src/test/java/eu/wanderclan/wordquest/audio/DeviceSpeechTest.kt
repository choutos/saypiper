package eu.wanderclan.wordquest.audio

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.wanderclan.wordquest.ui.TestWordQuestApplication
import eu.wanderclan.wordquest.AppContainer
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.data.WordQuestDatabase
import eu.wanderclan.wordquest.game.*
import eu.wanderclan.wordquest.ui.LessonViewModel
import androidx.room.Room
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(application = TestWordQuestApplication::class, shadows = [PendingSpeechShadow::class])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeviceSpeechTest {
    private fun testEngine(test: suspend TestScope.(DeviceSpeech) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val speech = DeviceSpeech(ApplicationProvider.getApplicationContext())
        try { test(speech) } finally { speech.close(); Dispatchers.resetMain() }
    }

    private fun offlineVoice() = Voice("installed-uk", Locale.UK, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, emptySet())

    @Test fun `engine failure leaves speech unavailable and shuts down`() = testEngine { speech ->
        val job = launch { speech.prepare() }
        runCurrent()
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowOf(tts).onInitListener.onInit(TextToSpeech.ERROR)
        job.join()
        assertFalse(speech.isAvailable)
        assertTrue(shadowOf(tts).isShutdown)
    }

    @Test fun `engine with no installed English voice is unavailable`() = testEngine { speech ->
        val job = launch { speech.prepare() }
        runCurrent()
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowOf(tts).onInitListener.onInit(TextToSpeech.SUCCESS)
        job.join()
        assertFalse(speech.isAvailable)
        assertTrue(shadowOf(tts).isShutdown)
    }

    @Test fun `engine that never starts times out rather than trapping the app`() = testEngine { speech ->
        val job = launch { speech.prepare() }
        runCurrent()
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        advanceTimeBy(5_001)
        job.join()
        assertFalse(speech.isAvailable)
        assertTrue(shadowOf(tts).isShutdown)
    }

    @Test fun `recreation during initialisation can await the same engine`() = testEngine { speech ->
        ShadowTextToSpeech.addVoice(offlineVoice())
        val first = launch { speech.prepare() }
        runCurrent()
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        first.cancelAndJoin()
        val second = launch { speech.prepare() }
        runCurrent()
        shadowOf(tts).onInitListener.onInit(TextToSpeech.SUCCESS)
        second.join()
        assertTrue(speech.isAvailable)
        assertEquals("installed-uk", tts.voice.name)
        assertSame(tts, ShadowTextToSpeech.getLastTextToSpeechInstance())
    }
    private fun lessonContainer(speech: DeviceSpeech): AppContainer {
        val request = LessonRequest.UnitLesson("fx-pets", 1)
        val generator = LessonGenerator(Fixtures.catalog())
        val seed = (0L..100L).first {
            generator.generate(request, Environment(true, true), it).exercises.first().type == ExerciseType.LISTEN_AND_PICK
        }
        return AppContainer(ContentLoader.load(Fixtures.source), Fixtures.source,
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WordQuestDatabase::class.java).build(),
            speech, { LocalDate.of(2026, 11, 11) }, { seed })
    }

    private suspend fun TestScope.start(speech: DeviceSpeech): TextToSpeech {
        ShadowTextToSpeech.addVoice(offlineVoice())
        val job = launch { speech.prepare() }
        runCurrent()
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        shadowOf(tts).onInitListener.onInit(TextToSpeech.SUCCESS)
        job.join()
        assertTrue(speech.isAvailable)
        return tts
    }

    @Test fun `stopping pending device speech preserves availability and the lesson`() = testEngine { speech ->
        val tts = start(speech)
        val container = lessonContainer(speech)
        val vm = LessonViewModel(container, 0, LessonRequest.UnitLesson("fx-pets", 1), Environment(true, true))
        val before = vm.session.value
        vm.say()
        runCurrent()
        val id = Shadow.extract<PendingSpeechShadow>(tts).utteranceId!!
        speech.stop()
        runCurrent()
        // Some engines also send callbacks after stop returns; neither may invalidate the lesson.
        shadowOf(tts).utteranceProgressListener.onStop(id, true)
        shadowOf(tts).utteranceProgressListener.onError(id, TextToSpeech.ERROR_SYNTHESIS)
        runCurrent()
        assertTrue(speech.isAvailable)
        assertEquals(before, vm.session.value)
        vm.say()
        runCurrent()
        val replay = Shadow.extract<PendingSpeechShadow>(tts).utteranceId!!
        assertNotEquals(id, replay)
        shadowOf(tts).utteranceProgressListener.onDone(replay)
        runCurrent()
        assertEquals(before, vm.session.value)
    }

    @Test fun `real engine utterance errors disable only the current lesson`() = testEngine { speech ->
        val tts = start(speech)
        val container = lessonContainer(speech)
        val request = LessonRequest.UnitLesson("fx-pets", 1)
        val vm = LessonViewModel(container, 0, request, Environment(true, true))
        vm.say()
        runCurrent()
        val id = Shadow.extract<PendingSpeechShadow>(tts).utteranceId!!
        shadowOf(tts).utteranceProgressListener.onError(id, TextToSpeech.ERROR_SYNTHESIS)
        runCurrent()
        assertTrue(vm.session.value.speechUnavailable)
        assertTrue(speech.isAvailable)
        assertTrue(vm.session.value.queue.none { it.type == ExerciseType.LISTEN_AND_PICK })
        val next = LessonViewModel(container, 0, request, Environment(speech.isAvailable, true))
        assertFalse(next.session.value.speechUnavailable)
        assertTrue(next.session.value.queue.any { it.type == ExerciseType.LISTEN_AND_PICK })
    }

    @Test fun `engine stop callback is an interruption without changing the lesson`() = testEngine { speech ->
        val tts = start(speech)
        val container = lessonContainer(speech)
        val vm = LessonViewModel(container, 0, LessonRequest.UnitLesson("fx-pets", 1), Environment(true, true))
        val before = vm.session.value
        vm.say()
        runCurrent()
        shadowOf(tts).utteranceProgressListener.onStop(Shadow.extract<PendingSpeechShadow>(tts).utteranceId!!, true)
        runCurrent()
        assertTrue(speech.isAvailable)
        assertEquals(before, vm.session.value)
    }

    @Test fun `utterance timeout disables only the current lesson`() = testEngine { speech ->
        start(speech)
        val vm = LessonViewModel(lessonContainer(speech), 0, LessonRequest.UnitLesson("fx-pets", 1), Environment(true, true))
        vm.say()
        runCurrent()
        advanceTimeBy(15_001)
        runCurrent()
        assertTrue(speech.isAvailable)
        assertTrue(vm.session.value.speechUnavailable)
    }

}


/** An engine that accepts speech but waits for the test to send a completion callback. */
@Implements(TextToSpeech::class)
class PendingSpeechShadow : ShadowTextToSpeech() {
    var utteranceId: String? = null
        private set

    @Implementation
    protected fun setSpeechRate(rate: Float): Int = TextToSpeech.SUCCESS

    @Implementation
    override fun speak(text: CharSequence?, queueMode: Int, params: Bundle?, utteranceId: String?): Int {
        this.utteranceId = utteranceId
        return TextToSpeech.SUCCESS
    }
}
