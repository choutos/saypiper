package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.content.Word
import eu.wanderclan.wordquest.content.normaliseEmoji
import eu.wanderclan.wordquest.content.normaliseText

/** The capability filter (ADR section 6): the generator only emits an exercise that can work. */
object Capabilities {

    /**
     * Whether [type] can practise [word] for a child in [band] on a device in [environment].
     * [pool] is the rest of the word's pack, from which the options are filled.
     */
    fun supports(type: ExerciseType, word: Word, band: Band, environment: Environment, pool: List<Word>): Boolean =
        when (type) {
            ExerciseType.PICTURE_TO_WORD ->
                word.emoji != null &&
                    environment.picturesPerceivable &&
                    distractorCandidates(word, pool).size >= band.choices - 1
        }

    /**
     * Words that may stand next to [answer] as a wrong option: never the same picture or the same text,
     * and distinct in text from each other.
     */
    fun distractorCandidates(answer: Word, pool: List<Word>): List<Word> {
        val answerText = normaliseText(answer.text)
        val answerEmoji = answer.emoji?.let(::normaliseEmoji)
        val seenTexts = mutableSetOf(answerText)
        return pool.filter { candidate ->
            candidate.id != answer.id &&
                (answerEmoji == null || candidate.emoji == null || normaliseEmoji(candidate.emoji) != answerEmoji) &&
                seenTexts.add(normaliseText(candidate.text))
        }
    }
}
