package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.content.VocabUnit

data class PathLesson(val number: Int, val isReview: Boolean, val stars: Int, val isOpen: Boolean)
data class PathUnit(val unit: VocabUnit, val lessons: List<PathLesson>, val hasCrown: Boolean, val isOpen: Boolean)

/**
 * Crowns and unlocking in order (ADR section 6), from the best stars per (unit id, lesson number).
 * The one place that decides what is open, so that an adult's override has one place to change.
 *
 * The first unit is open. A lesson opens when the lesson before it has a star, and a unit opens when
 * the unit before it has its crown: every one of its lessons has at least one star.
 */
object Progression {
    fun hasCrown(unit: VocabUnit, bestStars: Map<Pair<String, Int>, Int>): Boolean =
        (1..LessonPlan.lessonCount(unit)).all { (bestStars[unit.id to it] ?: 0) > 0 }

    fun path(units: List<VocabUnit>, bestStars: Map<Pair<String, Int>, Int>): List<PathUnit> {
        var previousCrowned = true
        return units.map { unit ->
            val unitOpen = previousCrowned
            val count = LessonPlan.lessonCount(unit)
            var previousStarred = true
            val lessons = (1..count).map { number ->
                val stars = bestStars[unit.id to number] ?: 0
                PathLesson(number, number == count, stars, unitOpen && previousStarred).also { previousStarred = stars > 0 }
            }
            val crowned = hasCrown(unit, bestStars)
            previousCrowned = crowned
            PathUnit(unit, lessons, crowned, unitOpen)
        }
    }
}
