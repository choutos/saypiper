package eu.wanderclan.wordquest.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.content.Word
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Descriptors keep voice policy testable without a speech engine or Android. */
data class VoiceInfo(val name: String, val locale: Locale, val needsNetwork: Boolean, val installed: Boolean)

fun chooseVoice(voices: List<VoiceInfo>): VoiceInfo? = voices
    .filter { it.locale.language == "en" && !it.needsNetwork && it.installed }
    .sortedWith(compareBy<VoiceInfo> { if (it.locale.country == "GB") 0 else 1 }.thenBy { it.name })
    .firstOrNull()

/** Only asks the engine to use an already installed voice. Never launches a voice-data intent. */
class DeviceSpeech(private val context: Context) : Speech {
    override val availability = MutableStateFlow(false)
    private var engine: TextToSpeech? = null
    private var initialised: CompletableDeferred<Int>? = null
    private val utterances = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    override suspend fun prepare() = withContext(Dispatchers.Main.immediate) {
        if (isAvailable) return@withContext
        // Reuse a pending initialisation after an activity recreation cancels its first await.
        val ready = initialised ?: CompletableDeferred<Int>().also { initialised = it }
        try {
            val tts = engine ?: TextToSpeech(context.applicationContext) { ready.complete(it) }.also { engine = it }
            if (withTimeoutOrNull(5_000) { ready.await() } != TextToSpeech.SUCCESS) {
                close()
                return@withContext
            }
            val voices = tts.voices.orEmpty()
            val selected = chooseVoice(voices.map {
                VoiceInfo(it.name, it.locale, it.isNetworkConnectionRequired,
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty())
            })
            val voice = voices.firstOrNull { it.name == selected?.name }
            if (voice == null || tts.setVoice(voice) != TextToSpeech.SUCCESS) {
                close()
                return@withContext
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { utteranceId?.let { utterances.remove(it)?.complete(true) } }
                @Deprecated("Android compatibility callback")
                override fun onError(utteranceId: String?) { utteranceId?.let { utterances.remove(it)?.complete(false) } }
                override fun onError(utteranceId: String?, errorCode: Int) { utteranceId?.let { utterances.remove(it)?.complete(false) } }
                override fun onStop(utteranceId: String?, interrupted: Boolean) { utteranceId?.let { utterances.remove(it)?.complete(false) } }
            })
            availability.value = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            close()
        }
    }

    override suspend fun say(word: Word, band: Band): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!isAvailable) return@withContext false
        val tts = engine ?: return@withContext false
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>()
        utterances[id] = done
        try {
            val rate = if (band == Band.A || band == Band.B) 0.75f else 1f
            val ok = tts.setSpeechRate(rate) == TextToSpeech.SUCCESS &&
                tts.speak(word.text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS &&
                withTimeoutOrNull(15_000) { done.await() } == true
            if (!ok) { availability.value = false; stop() }
            ok
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            availability.value = false
            stop()
            false
        } finally {
            utterances.remove(id)
        }
    }

    override fun stop() {
        utterances.values.forEach { it.complete(false) }
        utterances.clear()
        try { engine?.stop() } catch (_: RuntimeException) { availability.value = false }
    }

    override fun close() {
        stop()
        try { engine?.shutdown() } catch (_: RuntimeException) { /* Engine already disconnected. */ }
        engine = null
        initialised = null
        availability.value = false
    }
}
