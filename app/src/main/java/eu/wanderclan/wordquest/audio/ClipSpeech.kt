package eu.wanderclan.wordquest.audio

import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.content.Word
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Callback boundary for the platform player. Tests complete it themselves, without real time. */
interface ClipPlayer {
    fun play(path: String, completed: (SpeechResult) -> Unit)
    fun stop()
}

/** Clips always take precedence; a broken clip is a failed utterance, never a change of voice. */
class ClipSpeech(
    private val clipWordIds: Set<String>,
    private val device: Speech,
    private val player: ClipPlayer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : Speech {
    override val availability = MutableStateFlow(device.availability.value.copy(clipWordIds = clipWordIds))
    private var active: CompletableDeferred<SpeechResult>? = null

    override suspend fun prepare() {
        device.prepare()
        availability.value = device.availability.value.copy(clipWordIds = clipWordIds)
    }

    override suspend fun say(word: Word, band: Band): SpeechResult {
        if (word.id !in clipWordIds) return device.say(word, band)
        return withContext(dispatcher) {
            val done = CompletableDeferred<SpeechResult>()
            active = done
            try {
                player.play("content/audio/${word.id}.ogg") { done.complete(it) }
                withTimeoutOrNull(15_000) { done.await() } ?: SpeechResult.FAILED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                SpeechResult.FAILED
            } finally {
                if (active === done) {
                    active = null
                    player.stop()
                }
            }
        }
    }

    override fun stop() {
        player.stop()
        device.stop()
    }

    override fun close() {
        stop()
        device.close()
        availability.value = SpeechAvailability(clipWordIds = clipWordIds)
    }
}
