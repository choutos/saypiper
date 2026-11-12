package eu.wanderclan.wordquest.content

import eu.wanderclan.wordquest.FileContentSource
import eu.wanderclan.wordquest.Fixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Released ids, as recorded in app/released-ids.json by scripts/release/released-ids.py (ADR section 4). */
@Serializable
data class ReleasedIds(val units: Map<String, List<String>>, val retired: List<String> = emptyList())

/** Why the shipped content breaks a released id: progress is stored against unit and word ids. */
object ReleasedIdsCheck {
    fun problems(released: ReleasedIds, shipped: Map<String, List<String>>): List<String> {
        val shippedWordUnit = shipped.flatMap { (unit, words) -> words.map { it to unit } }.toMap()
        val retired = released.retired.toSet()
        val problems = mutableListOf<String>()
        for ((unit, words) in released.units) {
            if (unit !in shipped && unit !in retired) problems += "released unit $unit is gone without being retired"
            for (word in words) {
                val now = shippedWordUnit[word]
                when {
                    now == null && word !in retired -> problems += "released word $word ($unit) is gone without being retired"
                    now != null && now != unit -> problems += "released word $word moved from $unit to $now"
                }
            }
        }
        for (id in retired) {
            if (id in shipped || id in shippedWordUnit) problems += "retired id $id is used again"
        }
        return problems
    }
}

class ReleasedIdsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `no released unit or word id disappears, is reused or moves to another unit`() {
        val released = json.decodeFromString<ReleasedIds>(File("released-ids.json").readText())
        assertTrue(released.units.isNotEmpty())
        val catalog = ContentLoader.load(FileContentSource(Fixtures.shippedContentDir)).catalog
        val shipped = catalog.packs.flatMap { it.pack.units }.associate { unit -> unit.id to unit.words.map { it.id } }
        assertEquals(emptyList<String>(), ReleasedIdsCheck.problems(released, shipped))
    }

    private val released = ReleasedIds(mapOf("u1" to listOf("cat", "dog"), "u2" to listOf("red")), retired = listOf("old"))

    @Test
    fun `the record matches unchanged content, and new units and words are allowed`() {
        val shipped = mapOf("u1" to listOf("cat", "dog", "fish"), "u2" to listOf("red"), "u3" to listOf("blue"))
        assertEquals(emptyList<String>(), ReleasedIdsCheck.problems(released, shipped))
    }

    @Test
    fun `a removed word fails unless it is retired`() {
        val shipped = mapOf("u1" to listOf("cat"), "u2" to listOf("red"))
        assertEquals(listOf("released word dog (u1) is gone without being retired"), ReleasedIdsCheck.problems(released, shipped))
        assertEquals(emptyList<String>(), ReleasedIdsCheck.problems(released.copy(retired = listOf("old", "dog")), shipped))
    }

    @Test
    fun `a removed unit fails unless it and its words are retired`() {
        val shipped = mapOf("u1" to listOf("cat", "dog"))
        assertEquals(
            listOf("released unit u2 is gone without being retired", "released word red (u2) is gone without being retired"),
            ReleasedIdsCheck.problems(released, shipped),
        )
        assertEquals(emptyList<String>(), ReleasedIdsCheck.problems(released.copy(retired = listOf("old", "u2", "red")), shipped))
    }

    @Test
    fun `a word that moves to another unit fails`() {
        val shipped = mapOf("u1" to listOf("cat"), "u2" to listOf("red", "dog"))
        assertEquals(listOf("released word dog moved from u1 to u2"), ReleasedIdsCheck.problems(released, shipped))
    }

    @Test
    fun `a retired id used again fails, as a word or as a unit`() {
        val shipped = mapOf("u1" to listOf("cat", "dog", "old"), "u2" to listOf("red"))
        assertEquals(listOf("retired id old is used again"), ReleasedIdsCheck.problems(released, shipped))
        val asUnit = mapOf("u1" to listOf("cat", "dog"), "u2" to listOf("red"), "old" to listOf("green"))
        assertEquals(listOf("retired id old is used again"), ReleasedIdsCheck.problems(released, asUnit))
    }
}
