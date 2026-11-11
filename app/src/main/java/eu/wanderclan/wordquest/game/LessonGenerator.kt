package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.content.Band
import eu.wanderclan.wordquest.content.Catalog
import eu.wanderclan.wordquest.content.Word
import eu.wanderclan.wordquest.content.WordHome
import kotlin.random.Random

/** Builds reproducible lessons from a seed, covering every playable target at least once. */
class LessonGenerator(private val catalog: Catalog) {
    fun generate(request: LessonRequest, environment: Environment, seed: Long): Lesson {
        val random = Random(seed)
        val targets = targets(request)
        val playable = mutableListOf<Pair<WordHome, List<ExerciseType>>>()
        val skipped = mutableListOf<String>()
        for (home in targets) {
            val pairPool = targets.filter { it.gradePack == home.gradePack }.map { it.word }
            val types = ExerciseType.entries.filter {
                val pool = if (it == ExerciseType.MATCH_PAIRS) pairPool else home.gradePack.words
                Capabilities.supports(it, home.word, home.gradePack.band, environment, pool)
            }
            if (types.isEmpty()) skipped += home.word.id else playable += home to types
        }
        if (playable.isEmpty()) return Lesson(request, emptyList(), skipped)
        val count = when (request) {
            is LessonRequest.UnitLesson -> maxOf(UNIT_LESSON_EXERCISES, playable.size)
            is LessonRequest.Practice -> playable.size
        }
        // Balance the supported types, with seeded tie-breaking. Even a short band A lesson listens.
        val used = mutableMapOf<ExerciseType, Int>()
        val exercises = order(playable, count, random).map { (home, types) ->
            val type = types.shuffled(random).minBy {
                // In C, written exercises occur three times as often as each picture/listening type.
                if (home.gradePack.band == Band.C) ((used[it] ?: 0) + 1).toDouble() / when (it) {
                    ExerciseType.DEFINITION_TO_WORD, ExerciseType.GAP_FILL, ExerciseType.TYPE_THE_WORD, ExerciseType.MATCH_PAIRS -> 3
                    else -> 1
                } else (used[it] ?: 0).toDouble()
            }
            used[type] = (used[type] ?: 0) + 1
            exercise(home, type, targets.filter { it.gradePack == home.gradePack }.map { it.word }, random, environment)
        }
        return Lesson(request, exercises, skipped)
    }

    private fun targets(request: LessonRequest): List<WordHome> = when (request) {
        is LessonRequest.UnitLesson -> {
            val (unit, gradePack) = catalog.unit(request.unitId) ?: return emptyList()
            val words = LessonPlan.lessons(unit).getOrNull(request.number - 1) ?: return emptyList()
            words.map { WordHome(it, unit, gradePack) }
        }
        is LessonRequest.Practice -> request.wordIds.distinct().mapNotNull(catalog::word)
    }

    /** Shuffled rounds, never the same target word twice in a row. */
    private fun <T> order(items: List<T>, count: Int, random: Random): List<T> {
        val result = mutableListOf<T>()
        while (result.size < count) {
            val round = items.shuffled(random).toMutableList()
            if (round.size > 1 && round.first() == result.lastOrNull()) round[0] = round[1].also { round[1] = round[0] }
            result += round.take(count - result.size)
        }
        return result
    }

    private fun exercise(home: WordHome, type: ExerciseType, pairPool: List<Word>, random: Random, environment: Environment): Exercise {
        val word = home.word
        val band = home.gradePack.band
        if (type == ExerciseType.TYPE_THE_WORD) {
            val prompt = word.definition?.let { Prompt.Text(it) } ?: Prompt.Listening
            return TypingExercise(word.id, prompt, word.text)
        }
        if (type == ExerciseType.SPELL_WITH_TILES) {
            val tiles = word.text.toList().shuffled(random).toMutableList()
            // Never hand the child an already completed spelling, including repeated letters.
            if (tiles.joinToString("") == word.text && tiles.distinct().size > 1) {
                val other = tiles.indexOfFirst { it != tiles[0] }
                tiles[0] = tiles[other].also { tiles[other] = tiles[0] }
            }
            return TilesExercise(word.id, word.emoji?.takeIf { environment.picturesPerceivable }?.let { Prompt.Picture(it) } ?: Prompt.Listening, word.text, tiles)
        }
        if (type == ExerciseType.MATCH_PAIRS) {
            val candidates = Capabilities.pairCandidates(word, band, pairPool)
            val pairs = (listOf(word) + candidates.shuffled(random).take(if (candidates.size >= 4) 4 else 3))
            return PairsExercise(word.id, pairs.shuffled(random).map { Option.Text(it.id, it.text) },
                pairs.shuffled(random).map {
                    if (band == Band.C || band == Band.D) Option.Text(it.id, requireNotNull(it.definition))
                    else Option.Picture(it.id, requireNotNull(it.emoji))
                })
        }
        val pictureOptions = type == ExerciseType.WORD_TO_PICTURE || (type == ExerciseType.LISTEN_AND_PICK && band == Band.A)
        var candidates = Capabilities.distractorCandidates(word, home.gradePack.words)
        if (pictureOptions) candidates = candidates.filter { it.emoji != null }
        if (type == ExerciseType.GAP_FILL) candidates = candidates.filter { it.pos == word.pos }
        val distractors = candidates.shuffled(random).sortedWith(
            compareBy<Word> { if (it.pos == word.pos) 0 else 1 }
                .thenBy { if (type == ExerciseType.GAP_FILL && it in home.unit.words) 1 else 0 },
        ).take(band.choices - 1)
        val options = (distractors + word).shuffled(random).map {
            if (pictureOptions) Option.Picture(it.id, requireNotNull(it.emoji)) else Option.Text(it.id, it.text)
        }
        val prompt = when (type) {
            ExerciseType.PICTURE_TO_WORD -> Prompt.Picture(requireNotNull(word.emoji))
            ExerciseType.WORD_TO_PICTURE -> Prompt.Text(word.text)
            ExerciseType.LISTEN_AND_PICK -> Prompt.Listening
            ExerciseType.GAP_FILL -> Prompt.Gap(requireNotNull(word.parsedExample))
            ExerciseType.DEFINITION_TO_WORD -> Prompt.Text(requireNotNull(word.definition))
            else -> error("Handled above")
        }
        return ChoiceExercise(type, word.id, prompt, options)
    }

    /** Recover the current prompt visibly; replace queued listening types using the normal filter. */
    fun withoutSpeech(exercise: Exercise, showWord: Boolean, environment: Environment = Environment({ false }, true)): Exercise {
        val needsSpeech = (exercise is ChoiceExercise && exercise.prompt == Prompt.Listening) ||
            (exercise is TilesExercise && exercise.prompt == Prompt.Listening) ||
            (exercise is TypingExercise && exercise.prompt == Prompt.Listening)
        if (!needsSpeech) return exercise
        val home = requireNotNull(catalog.word(exercise.wordId))
        if (showWord) return when (exercise) {
            is ChoiceExercise -> exercise.copy(
                type = if (exercise.options.first() is Option.Picture) ExerciseType.WORD_TO_PICTURE else ExerciseType.PICTURE_TO_WORD,
                prompt = Prompt.Text(home.word.text),
            )
            is TilesExercise -> exercise.copy(prompt = Prompt.Text(home.word.text))
            is TypingExercise -> exercise.copy(prompt = Prompt.Text(home.word.definition ?: home.word.text))
            is PairsExercise -> exercise
        }
        val types = ExerciseType.entries.filter {
            it != ExerciseType.MATCH_PAIRS &&
                Capabilities.supports(it, home.word, home.gradePack.band, environment.copy(speechAvailable = { false }), home.gradePack.words)
        }
        return types.firstOrNull()?.let { exercise(home, it, emptyList(), Random(0), environment.copy(speechAvailable = { false })) } ?: withoutSpeech(exercise, true, environment)
    }

    companion object { const val UNIT_LESSON_EXERCISES = 10 }
}
