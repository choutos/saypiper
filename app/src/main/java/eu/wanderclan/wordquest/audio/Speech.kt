package eu.wanderclan.wordquest.audio

import eu.wanderclan.wordquest.content.Word
import eu.wanderclan.wordquest.content.Band
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** An interruption does not invalidate the voice or the lesson. */
enum class SpeechResult { SPOKEN, INTERRUPTED, FAILED }

/** A snapshot of the two sources; callers ask about a word, never a global speech flag. */
data class SpeechAvailability(val deviceVoice: Boolean = false, val clipWordIds: Set<String> = emptySet()) {
    fun canSpeak(word: Word): Boolean = word.id in clipWordIds || deviceVoice
    fun hasClip(word: Word): Boolean = word.id in clipWordIds
}

/**
 * Speech behind a small interface (ADR section 6, "Speech"). It is asked to say a word, not a string,
 * so clips and the device voice share the same callers.
 */
interface Speech {
    val availability: StateFlow<SpeechAvailability>
    fun canSpeak(word: Word): Boolean = availability.value.canSpeak(word)

    suspend fun prepare() {}
    fun stop() {}
    fun close() {}

    /** Only FAILED disables that source for a lesson. App-requested interruption preserves its questions. */
    suspend fun say(word: Word, band: Band = Band.B): SpeechResult
}

/** A speech stand-in for tests and for builds without device speech. Records what it was asked to say. */
class FakeSpeech(mode: Mode = Mode.UNAVAILABLE) : Speech {
    enum class Mode {
        /** Available, and every utterance "succeeds" without a sound. */
        SILENT,
        /** No voice on the device. */
        UNAVAILABLE,
        /** A voice is reported, but every utterance fails. */
        FAILING,
    }

    val spoken = mutableListOf<String>()

    override val availability = MutableStateFlow(SpeechAvailability(deviceVoice = mode != Mode.UNAVAILABLE))
    var mode: Mode = mode
        set(value) { field = value; availability.value = SpeechAvailability(deviceVoice = value != Mode.UNAVAILABLE) }

    override suspend fun say(word: Word, band: Band): SpeechResult {
        if (mode != Mode.SILENT) return SpeechResult.FAILED
        spoken += word.id
        return SpeechResult.SPOKEN
    }
}
