package eu.wanderclan.wordquest.game

/**
 * The lesson contract (ADR section 7). Everything that starts a lesson builds a [LessonRequest];
 * everything that finishes one consumes a [LessonResult]. New exercise types do not change these types.
 */
sealed interface LessonRequest {
    /** Lesson [number] (1-based) of a unit; the last number is the unit's review lesson. */
    data class UnitLesson(val unitId: String, val number: Int) : LessonRequest

    /** A practice session over the given words, in the order given. */
    data class Practice(val wordIds: List<String>) : LessonRequest
}

/** One outcome per word per session, however often the word appeared. */
data class WordOutcome(val wordId: String, val correct: Boolean)

data class LessonResult(
    val sessionId: String,
    val request: LessonRequest,
    val outcomes: List<WordOutcome>,
    val stars: Int,
)
