package eu.wanderclan.wordquest.game

/** What the device can do right now. Pictures cannot be perceived while a screen reader is on. */
data class Environment(val speechAvailable: Boolean, val picturesPerceivable: Boolean)

/** Every exercise type the generator knows. Later issues add types here and in [Capabilities]. */
enum class ExerciseType { PICTURE_TO_WORD }

/** What the child is shown or hears. */
sealed interface Prompt {
    data class Picture(val emoji: String) : Prompt
}

/** What the child picks from. Each option is a word of the pack. */
sealed interface Option {
    val wordId: String

    data class Text(override val wordId: String, val text: String) : Option
}

sealed interface Exercise {
    val type: ExerciseType
    val wordId: String
}

/** A prompt with three or four options, one of them right. Most exercise types are a choice. */
data class ChoiceExercise(
    override val type: ExerciseType,
    override val wordId: String,
    val prompt: Prompt,
    val options: List<Option>,
) : Exercise {
    val answerIndex: Int get() = options.indexOfFirst { it.wordId == wordId }
}

data class Lesson(val request: LessonRequest, val exercises: List<Exercise>, val skippedWordIds: List<String>) {
    val wordIds: List<String> get() = exercises.map { it.wordId }.distinct()
}
