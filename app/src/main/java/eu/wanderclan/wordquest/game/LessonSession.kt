package eu.wanderclan.wordquest.game

/** One immutable session for choices, pairs and tiles; mistakes return at most twice per word. */
data class LessonSession(
    val sessionId: String,
    val lesson: Lesson,
    val queue: List<Exercise> = lesson.exercises,
    val position: Int = 0,
    val response: Answer? = null,
    val mistakes: Int = 0,
    val requeues: Map<String, Int> = emptyMap(),
    val wrongWords: Set<String> = emptySet(),
    val speechUnavailable: Boolean = false,
) {
    val isFinished: Boolean get() = position >= queue.size
    val current: Exercise? get() = queue.getOrNull(position)
    val chosen: Int? get() = (response as? Answer.Choice)?.index
    val isAnswered: Boolean get() = response != null
    val lastAnswerCorrect: Boolean? get() = response?.let { requireNotNull(current).incorrectWords(it).isEmpty() }
    val progress: Float get() = if (queue.isEmpty()) 1f else (position + if (isAnswered) 1 else 0).toFloat() / queue.size

    fun answer(option: Int): LessonSession = answer(Answer.Choice(option))

    fun answer(answer: Answer): LessonSession {
        check(!isFinished && !isAnswered) { "No question is waiting for an answer" }
        val exercise = requireNotNull(current)
        val wrong = exercise.incorrectWords(answer)
        if (wrong.isEmpty()) return copy(response = answer)
        val retry = wrong.filter { (requeues[it] ?: 0) < MAX_REQUEUES }
        return copy(
            response = answer,
            mistakes = mistakes + 1,
            wrongWords = wrongWords + wrong,
            queue = if (retry.isNotEmpty()) queue + exercise else queue,
            requeues = requeues + retry.associateWith { (requeues[it] ?: 0) + 1 },
        )
    }

    fun withoutSpeech(generator: LessonGenerator, environment: Environment = Environment(false, true)): LessonSession = copy(
        speechUnavailable = true,
        queue = queue.mapIndexed { index, exercise ->
            if (index < position) exercise else generator.withoutSpeech(exercise, showWord = index == position, environment = environment)
        },
    )

    fun next(): LessonSession {
        check(isAnswered) { "Answer the question first" }
        return copy(position = position + 1, response = null)
    }

    val stars: Int get() = Scoring.stars(mistakes)
    fun result(): LessonResult {
        check(isFinished) { "The lesson is not finished" }
        return LessonResult(sessionId, lesson.request, lesson.wordIds.map { WordOutcome(it, it !in wrongWords) }, stars)
    }
    companion object { const val MAX_REQUEUES = 2 }
}

object Scoring {
    fun stars(mistakes: Int): Int = when {
        mistakes == 0 -> 3
        mistakes <= 2 -> 2
        else -> 1
    }
    /** Placeholder until the rewards issue owns the formula. */
    fun points(result: LessonResult): Int = 10 * result.stars
}
