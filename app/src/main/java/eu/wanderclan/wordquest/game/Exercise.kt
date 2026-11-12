package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.content.Example
import eu.wanderclan.wordquest.content.Word
import eu.wanderclan.wordquest.content.normaliseText

data class Environment(val speechAvailable: (Word) -> Boolean, val picturesPerceivable: Boolean)

enum class ExerciseType {
    PICTURE_TO_WORD, WORD_TO_PICTURE, LISTEN_AND_PICK, MATCH_PAIRS, SPELL_WITH_TILES, GAP_FILL, DEFINITION_TO_WORD, TYPE_THE_WORD,
}

/** What the child is shown or hears. */
sealed interface Prompt {
    data class Picture(val emoji: String) : Prompt
    data class Text(val text: String) : Prompt
    data class Gap(val example: Example) : Prompt
    data object Listening : Prompt
}

/** Options always identify a word from the same pack. */
sealed interface Option {
    val wordId: String
    data class Text(override val wordId: String, val text: String) : Option
    data class Picture(override val wordId: String, val emoji: String) : Option
}

sealed interface Answer {
    data class Choice(val index: Int) : Answer
    /** Word ids matched to meanings (pictures or definitions), in meaning order. */
    data class Pairs(val wordIds: List<String>) : Answer
    /** Indices into the original shuffled tiles; repeated letters remain separate tiles. */
    data class Tiles(val indices: List<Int>) : Answer
    data class Typed(val text: String) : Answer
}

sealed interface Exercise {
    val type: ExerciseType
    val wordId: String
    val wordIds: List<String> get() = listOf(wordId)
    /** Validates the response and returns exactly the words answered incorrectly. */
    fun incorrectWords(answer: Answer): Set<String>
}

data class ChoiceExercise(
    override val type: ExerciseType,
    override val wordId: String,
    val prompt: Prompt,
    val options: List<Option>,
) : Exercise {
    val answerIndex: Int get() = options.indexOfFirst { it.wordId == wordId }
    override fun incorrectWords(answer: Answer): Set<String> {
        require(answer is Answer.Choice && answer.index in options.indices)
        return if (answer.index == answerIndex) emptySet() else setOf(wordId)
    }
}

data class PairsExercise(
    override val wordId: String,
    val words: List<Option.Text>,
    val meanings: List<Option>,
) : Exercise {
    override val type = ExerciseType.MATCH_PAIRS
    override val wordIds get() = words.map { it.wordId }
    override fun incorrectWords(answer: Answer): Set<String> {
        require(answer is Answer.Pairs && answer.wordIds.size == meanings.size && answer.wordIds.toSet() == wordIds.toSet())
        return meanings.zip(answer.wordIds).filter { (meaning, id) -> meaning.wordId != id }
            .flatMap { (meaning, id) -> listOf(meaning.wordId, id) }.toSet()
    }
}

data class TilesExercise(
    override val wordId: String,
    val prompt: Prompt,
    val text: String,
    val tiles: List<Char>,
) : Exercise {
    override val type = ExerciseType.SPELL_WITH_TILES
    override fun incorrectWords(answer: Answer): Set<String> {
        require(answer is Answer.Tiles && answer.indices.sorted() == tiles.indices.toList())
        return if (answer.indices.map { tiles[it] }.joinToString("") == text) emptySet() else setOf(wordId)
    }
}

data class TypingExercise(
    override val wordId: String,
    val prompt: Prompt,
    val text: String,
    /** A visible definition or picture disambiguates words that sound alike in dictation. */
    val clue: Prompt? = null,
) : Exercise {
    override val type = ExerciseType.TYPE_THE_WORD
    override fun incorrectWords(answer: Answer): Set<String> {
        require(answer is Answer.Typed)
        return if (normaliseText(answer.text) == normaliseText(text)) emptySet() else setOf(wordId)
    }
}

data class Lesson(val request: LessonRequest, val exercises: List<Exercise>, val skippedWordIds: List<String>) {
    val wordIds: List<String> get() = exercises.flatMap { it.wordIds }.distinct()
}
