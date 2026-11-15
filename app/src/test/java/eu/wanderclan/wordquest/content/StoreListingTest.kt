package eu.wanderclan.wordquest.content

import eu.wanderclan.wordquest.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The F-Droid store listing in `fastlane/metadata/android`. F-Droid falls back to `en-US`, so it is an identical
 * copy of the British `en-GB` listing, and each release brings its changelog in both (README, "Releases").
 */
class StoreListingTest {
    private val metadata = File("../fastlane/metadata/android")
    private val locales = listOf("en-GB", "en-US")

    private fun files(locale: String): Map<String, File> {
        val root = File(metadata, locale)
        assertTrue("missing ${root.path}", root.isDirectory)
        return root.walkTopDown().filter { it.isFile }.associateBy { it.relativeTo(root).invariantSeparatorsPath }
    }

    /** Characters as a reader counts them, without the file's final newline. */
    private fun length(file: File): Int = file.readText().trimEnd().let { it.codePointCount(0, it.length) }

    @Test
    fun `en-US holds the same files as en-GB, byte for byte`() {
        val (gb, us) = locales.map(::files)
        assertEquals("files in en-GB and en-US", gb.keys.sorted(), us.keys.sorted())
        val differing = gb.keys.filterNot { gb.getValue(it).readBytes().contentEquals(us.getValue(it).readBytes()) }
        assertEquals("files whose en-GB and en-US copies differ", emptyList<String>(), differing.sorted())
    }

    @Test
    fun `every locale has the changelog of the version being built`() {
        for (locale in locales) {
            val changelog = File(metadata, "$locale/changelogs/${BuildConfig.VERSION_CODE}.txt")
            assertTrue("missing ${changelog.path}", changelog.isFile)
        }
    }

    @Test
    fun `texts are within F-Droid's limits`() {
        val limits = mapOf("title.txt" to 30, "short_description.txt" to 80, "full_description.txt" to 4000)
        val tooLong = mutableListOf<String>()
        for (locale in locales) {
            val files = files(locale)
            for ((name, limit) in limits) {
                val file = files[name]
                assertTrue("missing $locale/$name", file != null)
                if (length(file!!) > limit) tooLong += "$locale/$name: ${length(file)} > $limit"
            }
            files.filterKeys { it.startsWith("changelogs/") }.forEach { (name, file) ->
                if (length(file) > 500) tooLong += "$locale/$name: ${length(file)} > 500"
            }
        }
        assertEquals(emptyList<String>(), tooLong)
    }
}
