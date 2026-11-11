package eu.wanderclan.wordquest.game

import eu.wanderclan.wordquest.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    // fx-pets: 6 words, lessons 1, 2 and the review 3. fx-fruit: 7 words, lessons 1, 2 and the review 3.
    private val units = Fixtures.catalog().pack("p1")!!.pack.units

    private fun open(stars: Map<Pair<String, Int>, Int>) =
        Progression.path(units, stars).flatMap { u -> u.lessons.filter { it.isOpen }.map { u.unit.id to it.number } }

    @Test
    fun `a new profile has only the first lesson of the first unit open`() {
        assertEquals(listOf("fx-pets" to 1), open(emptyMap()))
        val path = Progression.path(units, emptyMap())
        assertEquals(listOf(true, false), path.map { it.isOpen })
        assertEquals(listOf(false, false), path.map { it.hasCrown })
        assertEquals(listOf(false, false, true), path.first().lessons.map { it.isReview })
    }

    @Test
    fun `a star on a lesson opens the next one`() {
        assertEquals(listOf("fx-pets" to 1, "fx-pets" to 2), open(mapOf(("fx-pets" to 1) to 1)))
        assertEquals(
            listOf("fx-pets" to 1, "fx-pets" to 2, "fx-pets" to 3),
            open(mapOf(("fx-pets" to 1) to 3, ("fx-pets" to 2) to 1)),
        )
    }

    @Test
    fun `a whole unit earns its crown and opens the next unit`() {
        val stars = (1..3).associate { ("fx-pets" to it) to 1 }
        val path = Progression.path(units, stars)
        assertTrue(path[0].hasCrown)
        assertTrue(Progression.hasCrown(units[0], stars))
        assertTrue(path[1].isOpen)
        assertFalse(path[1].hasCrown)
        assertEquals(listOf("fx-pets" to 1, "fx-pets" to 2, "fx-pets" to 3, "fx-fruit" to 1), open(stars))
    }

    @Test
    fun `stars without the crown of the unit before do not open a unit`() {
        val stars = mapOf(("fx-pets" to 1) to 3, ("fx-pets" to 2) to 3, ("fx-fruit" to 1) to 3)
        assertEquals(listOf("fx-pets" to 1, "fx-pets" to 2, "fx-pets" to 3), open(stars))
    }
}
