package eu.wanderclan.wordquest.audio

import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.content.Word
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** No platform player, clock delay or background dispatcher. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClipSpeechTest {
    private val clip = Word("rabbit", "rabbit", "noun")
    private val other = Word("chair", "chair", "noun")

    private class Player : ClipPlayer {
        var path: String? = null
        var done: ((SpeechResult) -> Unit)? = null
        var stops = 0
        override fun play(path: String, completed: (SpeechResult) -> Unit) { this.path = path; done = completed }
        override fun stop() { stops++; done?.invoke(SpeechResult.INTERRUPTED); done = null }
    }

    @Test fun `clips work with no device voice and other words do not`() = runTest {
        val player = Player()
        val speech = ClipSpeech(setOf(clip.id), FakeSpeech(), player, StandardTestDispatcher(testScheduler))
        speech.prepare()
        assertTrue(speech.canSpeak(clip))
        assertFalse(speech.canSpeak(other))
        val result = async { speech.say(clip, Band.A) }
        runCurrent()
        assertEquals("content/audio/rabbit.ogg", player.path)
        assertFalse(result.isCompleted)
        player.done!!(SpeechResult.SPOKEN)
        runCurrent()
        assertEquals(SpeechResult.SPOKEN, result.await())
        assertEquals(1, player.stops)
    }

    @Test fun `device words delegate and clips never use the device even on failure`() = runTest {
        val player = Player()
        val device = FakeSpeech(FakeSpeech.Mode.SILENT)
        val speech = ClipSpeech(setOf(clip.id), device, player, StandardTestDispatcher(testScheduler))
        assertEquals(SpeechResult.SPOKEN, speech.say(other))
        assertEquals(listOf(other.id), device.spoken)
        val result = async { speech.say(clip) }
        runCurrent()
        player.done!!(SpeechResult.FAILED)
        assertEquals(SpeechResult.FAILED, result.await())
        assertEquals(listOf(other.id), device.spoken)
        assertTrue(speech.canSpeak(clip)) // A failure belongs to the session, not the source globally.
    }

    @Test fun `stop and cancellation interrupt without losing clip availability`() = runTest {
        val player = Player()
        val speech = ClipSpeech(setOf(clip.id), FakeSpeech(), player, StandardTestDispatcher(testScheduler))
        val first = async { speech.say(clip) }
        runCurrent()
        speech.stop()
        assertEquals(SpeechResult.INTERRUPTED, first.await())
        val second = async { speech.say(clip) }
        runCurrent()
        second.cancelAndJoin()
        assertTrue(speech.canSpeak(clip))
        assertNull(player.done)
    }

    @Test fun `timeout uses virtual time and releases the player`() = runTest {
        val player = Player()
        val speech = ClipSpeech(setOf(clip.id), FakeSpeech(), player, StandardTestDispatcher(testScheduler))
        val result = async { speech.say(clip) }
        runCurrent()
        advanceTimeBy(15_001)
        assertEquals(SpeechResult.FAILED, result.await())
        assertNull(player.done)
    }

    @Test fun `late player callback cannot complete a replay`() = runTest {
        val player = Player()
        val speech = ClipSpeech(setOf(clip.id), FakeSpeech(), player, StandardTestDispatcher(testScheduler))
        val first = async { speech.say(clip) }
        runCurrent()
        val late = player.done!!
        speech.stop()
        first.await()
        val replay = async { speech.say(clip) }
        runCurrent()
        late(SpeechResult.FAILED)
        assertFalse(replay.isCompleted)
        player.done!!(SpeechResult.SPOKEN)
        assertEquals(SpeechResult.SPOKEN, replay.await())
    }
}
