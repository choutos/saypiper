package eu.wanderclan.wordquest.audio

import eu.wanderclan.wordquest.content.Word

/**
 * Speech behind a small interface (ADR section 6, "Speech"). It is asked to say a word, not a string,
 * so a recorded clip can later replace the synthetic voice for some words without touching callers.
 * The device implementation arrives with the primary exercise set.
 */
interface Speech {
    /** False when no installed, offline English voice is usable; listening exercises are then dropped. */
    val isAvailable: Boolean

    /** Says [word]. Returns false if the utterance failed; the caller then treats speech as unavailable. */
    suspend fun say(word: Word): Boolean
}

/** A speech stand-in for tests and for builds without device speech. Records what it was asked to say. */
class FakeSpeech(var mode: Mode = Mode.UNAVAILABLE) : Speech {
    enum class Mode {
        /** Available, and every utterance "succeeds" without a sound. */
        SILENT,
        /** No voice on the device. */
        UNAVAILABLE,
        /** A voice is reported, but every utterance fails. */
        FAILING,
    }

    val spoken = mutableListOf<String>()

    override val isAvailable: Boolean get() = mode != Mode.UNAVAILABLE

    override suspend fun say(word: Word): Boolean {
        if (mode != Mode.SILENT) return false
        spoken += word.id
        return true
    }
}
