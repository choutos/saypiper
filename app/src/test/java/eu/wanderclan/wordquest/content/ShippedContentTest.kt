package eu.wanderclan.wordquest.content

import eu.wanderclan.wordquest.FileContentSource
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.game.Playability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The only tests that read `assets/content/`: a vocabulary change can fail only here (ADR section 7). */
class ShippedContentTest {
    private val source = FileContentSource(Fixtures.shippedContentDir)
    private val loaded = ContentLoader.load(source)

    @Test
    fun `the shipped content passes every content rule`() {
        assertEquals(emptyList<Problem>(), loaded.problems)
    }

    @Test
    fun `grades json lists all twelve classes`() {
        val grades = loaded.catalog.grades
        assertEquals(listOf("p1", "p2", "p3", "p4", "p5", "s1", "s2", "s3", "s4", "s5", "s6", "s7"), grades.map { it.id })
        assertEquals(List(5) { Cycle.PRIMARY } + List(7) { Cycle.SECONDARY }, grades.map { it.cycle })
        assertEquals("AABBBCCCDDDD".map { Band.valueOf(it.toString()) }, grades.map { it.band })
    }

    @Test
    fun `rule 10 - every word of every shipped pack is playable with speech and without`() {
        assertTrue(loaded.catalog.packs.isNotEmpty())
        for (gradePack in loaded.catalog.packs) {
            assertEquals(emptyList<Playability.Gap>(), Playability.gaps(gradePack))
        }
        assertEquals(setOf(true, false), Playability.requiredEnvironments.map { it.speechAvailable }.toSet())
    }

    @Test
    fun `every shipped picture is an OpenMoji PNG that something uses`() {
        val files = source.list(ContentLoader.PICTURES_DIR)
        assertTrue(files.isNotEmpty())
        files.forEach { name ->
            val bytes = source.read("${ContentLoader.PICTURES_DIR}/$name")!!
            assertTrue("$name is not a PNG", bytes.take(4) == listOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()))
        }
    }

    @Test
    fun `the picture licence notice ships in the app`() {
        val notice = File(Fixtures.shippedContentDir.parentFile, "licences/openmoji.txt").readText()
        assertTrue(notice.contains("OpenMoji"))
        assertTrue(notice.contains("Attribution-ShareAlike 4.0"))
    }
}
