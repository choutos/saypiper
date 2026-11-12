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
        val secondary = band == Band.C || band == Band.D
        return when (type) {
            ExerciseType.PICTURE_TO_WORD -> band != Band.D && picture && choice && (band != Band.A || !environment.speechAvailable(word))
            ExerciseType.WORD_TO_PICTURE -> band != Band.D && picture && pictureChoice
            ExerciseType.LISTEN_AND_PICK -> band != Band.D && environment.speechAvailable(word) &&
                if (band == Band.A) picture && pictureChoice else choice
            ExerciseType.MATCH_PAIRS -> (if (secondary) word.definition != null else band == Band.B && picture) &&
                pairCandidates(word, band, pool).size >= 3
            ExerciseType.SPELL_WITH_TILES -> (band == Band.B || band == Band.C) && word.text.matches(Regex("[a-zA-Z]{1,10}")) &&
                (picture || environment.speechAvailable(word))
            ExerciseType.GAP_FILL -> band != Band.A && word.parsedExample != null &&
                candidates.count { it.pos == word.pos } >= band.choices - 1
            ExerciseType.DEFINITION_TO_WORD -> (secondary || band == Band.B && !picture) && word.definition != null && choice
            ExerciseType.TYPE_THE_WORD -> secondary && (word.definition != null || environment.speechAvailable(word))
        }
    }

    /** Secondary meanings must be distinct so that each definition has only one matching word. */
    fun pairCandidates(word: Word, band: Band, pool: List<Word>): List<Word> {
        val candidates = distractorCandidates(word, pool)
        return if (band == Band.C || band == Band.D) candidates.filter {
            it.definition != null && normaliseText(it.definition) != word.definition?.let(::normaliseText)
        }.distinctBy { normaliseText(requireNotNull(it.definition)) }
        else candidates.filter { it.emoji != null }
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
