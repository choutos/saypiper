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
    fun `a stored result keeps its lesson and unit open without the preceding crown`() {
        val stars = mapOf(("fx-pets" to 1) to 3, ("fx-pets" to 2) to 3, ("fx-fruit" to 1) to 3)
        assertEquals(listOf("fx-pets" to 1, "fx-pets" to 2, "fx-pets" to 3, "fx-fruit" to 1, "fx-fruit" to 2), open(stars))
    }

    @Test
    fun `a third unit stays open when the second unit gains a lesson`() {
        val original = units + units.last().copy(id = "third")
        val stars = original.take(2).flatMap { u -> (1..3).map { (u.id to it) to 1 } }.toMap() + (("third" to 1) to 1)
        val expanded = original.map { u -> if (u.id == units[1].id) u.copy(words = u.words + u.words.take(3)) else u }
        assertFalse(Progression.hasCrown(expanded[1], stars))
        val third = Progression.path(expanded, stars)[2]
        assertTrue(third.isOpen)
        assertTrue(third.lessons.first().isOpen)
    }
}
