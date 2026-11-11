package eu.wanderclan.wordquest.audio

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.wanderclan.wordquest.ui.TestWordQuestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(application = TestWordQuestApplication::class)
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
}
