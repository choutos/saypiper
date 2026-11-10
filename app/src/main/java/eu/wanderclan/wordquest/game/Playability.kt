package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.content.Catalog
import eu.wanderclan.wordquest.content.GradePack

/** Content rule 10: the real generator can build every lesson of a pack, with speech and without. */
object Playability {

    /** The environments a shipped pack must be playable in. */
    val requiredEnvironments = listOf(
        Environment(speechAvailable = true, picturesPerceivable = true),
        Environment(speechAvailable = false, picturesPerceivable = true),
    )

    data class Gap(val unitId: String, val lesson: Int, val environment: Environment, val missingWordIds: List<String>)

    fun gaps(gradePack: GradePack): List<Gap> {
        val generator = LessonGenerator(Catalog(listOf(gradePack.grade), listOf(gradePack)))
        return gradePack.pack.units.flatMap { unit ->
            if (unit.words.size !in LessonPlan.MIN_UNIT_WORDS..LessonPlan.MAX_UNIT_WORDS) return@flatMap emptyList()
            LessonPlan.lessons(unit).withIndex().flatMap { (index, words) ->
                requiredEnvironments.mapNotNull { environment ->
                    val lesson = generator.generate(LessonRequest.UnitLesson(unit.id, index + 1), environment, seed = 0)
                    val missing = words.map { it.id } - lesson.wordIds.toSet()
                    if (missing.isEmpty()) null else Gap(unit.id, index + 1, environment, missing)
                }
            }
        }
    }
}
