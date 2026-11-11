package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.content.Word
import eu.wanderclan.wordquest.content.normaliseEmoji
import eu.wanderclan.wordquest.content.normaliseText

object Capabilities {
    fun supports(type: ExerciseType, word: Word, band: Band, environment: Environment, pool: List<Word>): Boolean {
        val picture = word.emoji != null && environment.picturesPerceivable
        val candidates = distractorCandidates(word, pool)
        val choice = candidates.size >= band.choices - 1
        val pictureChoice = candidates.count { it.emoji != null } >= band.choices - 1
        // FRL-9 owns the secondary mixes; preserve their existing picture exercise for now.
        if (band == Band.C || band == Band.D) return type == ExerciseType.PICTURE_TO_WORD && picture && choice
        return when (type) {
            ExerciseType.PICTURE_TO_WORD -> picture && choice && (band != Band.A || !environment.speechAvailable)
            ExerciseType.WORD_TO_PICTURE -> picture && pictureChoice
            ExerciseType.LISTEN_AND_PICK -> environment.speechAvailable &&
                if (band == Band.A) picture && pictureChoice else choice
            ExerciseType.MATCH_PAIRS -> band == Band.B && picture && candidates.count { it.emoji != null } >= 3
            ExerciseType.SPELL_WITH_TILES -> band == Band.B && word.text.matches(Regex("[a-zA-Z]{1,10}")) &&
                (picture || environment.speechAvailable)
            ExerciseType.GAP_FILL -> band == Band.B && word.parsedExample != null &&
                candidates.count { it.pos == word.pos } >= band.choices - 1
            ExerciseType.DEFINITION_TO_WORD -> band == Band.B && word.emoji == null && word.definition != null && choice
        }
    }

    fun distractorCandidates(answer: Word, pool: List<Word>): List<Word> {
        val answerText = normaliseText(answer.text)
        val answerEmoji = answer.emoji?.let(::normaliseEmoji)
        val seenTexts = mutableSetOf(answerText)
        val seenPictures = mutableSetOf<String>()
        return pool.filter { candidate ->
            val emoji = candidate.emoji?.let(::normaliseEmoji)
            candidate.id != answer.id &&
                (answerEmoji == null || emoji == null || emoji != answerEmoji) &&
                normaliseText(candidate.text) !in seenTexts &&
                (emoji == null || emoji !in seenPictures) &&
                seenTexts.add(normaliseText(candidate.text)).also { if (emoji != null) seenPictures += emoji }
        }
    }
}
