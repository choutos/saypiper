package eu.wanderclan.wordquest.game

/**
 * The state of one lesson being played. Immutable: [answer] and [next] return the new state.
 *
 * A wrong answer re-queues the exercise at the end of the lesson, at most [MAX_REQUEUES] times per word.
 * There are no lives and no timers.
 */
data class LessonSession(
    val sessionId: String,
    val lesson: Lesson,
    val queue: List<Exercise> = lesson.exercises,
    val position: Int = 0,
    /** The option chosen for the current exercise, or null while it is still unanswered. */
    val chosen: Int? = null,
    val mistakes: Int = 0,
    val requeues: Map<String, Int> = emptyMap(),
    val wrongWords: Set<String> = emptySet(),
) {
    val isFinished: Boolean get() = position >= queue.size
    val current: Exercise? get() = queue.getOrNull(position)
    val isAnswered: Boolean get() = chosen != null

    /** Whether the answer given to the current exercise was right; null while unanswered. */
    val lastAnswerCorrect: Boolean?
        get() = chosen?.let { (current as ChoiceExercise).answerIndex == it }

    /** Share of the lesson done, counting the current exercise once it is answered. */
    val progress: Float
        get() = if (queue.isEmpty()) 1f else (position + if (isAnswered) 1 else 0).toFloat() / queue.size

    fun answer(option: Int): LessonSession {
        check(!isFinished && !isAnswered) { "No question is waiting for an answer" }
        val exercise = current as ChoiceExercise
        require(option in exercise.options.indices) { "No option $option" }
        if (option == exercise.answerIndex) return copy(chosen = option)
        val used = requeues[exercise.wordId] ?: 0
        val requeue = used < MAX_REQUEUES
        return copy(
            chosen = option,
            mistakes = mistakes + 1,
            wrongWords = wrongWords + exercise.wordId,
            queue = if (requeue) queue + exercise else queue,
            requeues = if (requeue) requeues + (exercise.wordId to used + 1) else requeues,
        )
    }

    fun next(): LessonSession {
        check(isAnswered) { "Answer the question first" }
        return copy(position = position + 1, chosen = null)
    }

    val stars: Int get() = Scoring.stars(mistakes)

    fun result(): LessonResult {
        check(isFinished) { "The lesson is not finished" }
        val outcomes = lesson.wordIds.map { WordOutcome(it, correct = it !in wrongWords) }
        return LessonResult(sessionId, lesson.request, outcomes, stars)
    }

    companion object {
        const val MAX_REQUEUES = 2
    }
}

object Scoring {
    /** Three stars for no mistakes, two for one or two, one for finishing. */
    fun stars(mistakes: Int): Int = when {
        mistakes == 0 -> 3
        mistakes <= 2 -> 2
        else -> 1
    }

    /**
     * Placeholder points for a finished session, so the day's points can be stored.
     * The practice and rewards issue owns the real formula and its display.
     */
    fun points(result: LessonResult): Int = 10 * result.stars
}
