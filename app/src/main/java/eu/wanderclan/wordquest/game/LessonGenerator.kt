package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.content.Catalog
import eu.wanderclan.wordquest.content.WordHome
import kotlin.random.Random

/**
 * Builds a lesson from the content. The same request, environment and seed always give the same lesson.
 *
 * A unit lesson has about [UNIT_LESSON_EXERCISES] exercises, more when it has more words, so every
 * word appears at least once. A practice session has one exercise per word. Each word is exercised in
 * the band of its own class, with options from its own pack. A word with no supported type is skipped.
 */
class LessonGenerator(private val catalog: Catalog) {

    fun generate(request: LessonRequest, environment: Environment, seed: Long): Lesson {
        val random = Random(seed)
        val targets = targets(request)
        val playable = mutableListOf<Pair<WordHome, List<ExerciseType>>>()
        val skipped = mutableListOf<String>()
        for (home in targets) {
            val types = ExerciseType.entries.filter {
                Capabilities.supports(it, home.word, home.gradePack.band, environment, home.gradePack.words)
            }
            if (types.isEmpty()) skipped += home.word.id else playable += home to types
        }
        if (playable.isEmpty()) return Lesson(request, emptyList(), skipped)

        val count = when (request) {
            is LessonRequest.UnitLesson -> maxOf(UNIT_LESSON_EXERCISES, playable.size)
            is LessonRequest.Practice -> playable.size
        }
        val exercises = order(playable, count, random).map { (home, types) -> exercise(home, types.random(random), random) }
        return Lesson(request, exercises, skipped)
    }

    private fun targets(request: LessonRequest): List<WordHome> = when (request) {
        is LessonRequest.UnitLesson -> {
            val (unit, gradePack) = catalog.unit(request.unitId) ?: return emptyList()
            val words = LessonPlan.lessons(unit).getOrNull(request.number - 1) ?: return emptyList()
            words.map { WordHome(it, unit, gradePack) }
        }
        // Ids no longer in the content are ignored (ADR section 6, "Practice").
        is LessonRequest.Practice -> request.wordIds.distinct().mapNotNull(catalog::word)
    }

    /** Shuffled rounds over the words until [count] is reached, never the same word twice in a row. */
    private fun <T> order(items: List<T>, count: Int, random: Random): List<T> {
        val result = mutableListOf<T>()
        while (result.size < count) {
            val round = items.shuffled(random).toMutableList()
            if (round.size > 1 && round.first() == result.lastOrNull()) {
                round[0] = round[1].also { round[1] = round[0] }
            }
            result += round.take(count - result.size)
        }
        return result
    }

    private fun exercise(home: WordHome, type: ExerciseType, random: Random): Exercise {
        val word = home.word
        val band = home.gradePack.band
        return when (type) {
            ExerciseType.PICTURE_TO_WORD -> {
                // Same part of speech first, random within each group.
                val distractors = Capabilities.distractorCandidates(word, home.gradePack.words)
                    .shuffled(random)
                    .sortedBy { if (it.pos == word.pos) 0 else 1 }
                    .take(band.choices - 1)
                val options = (distractors + word).shuffled(random).map { Option.Text(it.id, it.text) }
                ChoiceExercise(type, word.id, Prompt.Picture(requireNotNull(word.emoji)), options)
            }
        }
    }

    companion object {
        const val UNIT_LESSON_EXERCISES = 10
    }
}
