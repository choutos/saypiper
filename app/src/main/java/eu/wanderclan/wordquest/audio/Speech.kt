package eu.wanderclan.wordquest.audio

import eu.wanderclan.wordquest.content.Word
import eu.wanderclan.wordquest.content.Band
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** An interruption does not invalidate the voice or the lesson. */
enum class SpeechResult { SPOKEN, INTERRUPTED, FAILED }

/**
 * Speech behind a small interface (ADR section 6, "Speech"). It is asked to say a word, not a string,
 * so a recorded clip can later replace the synthetic voice for some words without touching callers.
 */
interface Speech {
    /** False when no installed, offline English voice is usable; listening exercises are then dropped. */
    val availability: StateFlow<Boolean>
    val isAvailable: Boolean get() = availability.value

    suspend fun prepare() {}
    fun stop() {}
    fun close() {}

    /** Only FAILED disables speech for a lesson. App-requested interruption preserves its questions. */
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

    override val availability = MutableStateFlow(mode != Mode.UNAVAILABLE)
    var mode: Mode = mode
        set(value) { field = value; availability.value = value != Mode.UNAVAILABLE }

    override suspend fun say(word: Word, band: Band): SpeechResult {
        if (mode != Mode.SILENT) return SpeechResult.FAILED
        spoken += word.id
        return SpeechResult.SPOKEN
    }
}
