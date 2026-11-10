package eu.wanderclan.wordquest

import eu.wanderclan.wordquest.content.Catalog
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.content.ContentSet
import eu.wanderclan.wordquest.content.ContentSource
import eu.wanderclan.wordquest.content.GradePack
import java.io.File

/** Reads content from a directory laid out like `assets/content/`. */
class FileContentSource(private val root: File) : ContentSource {
    override fun read(path: String): ByteArray? = File(root, path).takeIf { it.isFile }?.readBytes()
    override fun list(dir: String): List<String> = File(root, dir).list()?.toList().orEmpty()
}

/**
 * Test content. Code tests use the fixture packs under test resources (ADR section 7); only the
 * shipped-content tests read `src/main/assets/content`.
 */
object Fixtures {
    val contentDir: File = File(requireNotNull(Fixtures::class.java.getResource("/fixtures/content")).toURI())
    val source: ContentSource get() = FileContentSource(contentDir)

    /** The fixture content as parsed, before validation. */
    fun contentSet(): ContentSet = ContentLoader.read(source).first

    /** Every fixture pack, whether or not it passes validation: generator tests need the unplayable ones too. */
    fun catalog(): Catalog {
        val set = contentSet()
        val packs = set.grades.grades.mapNotNull { grade -> set.packs[grade.id]?.let { GradePack(grade, it) } }
        return Catalog(set.grades.grades, packs, set.avatars.avatars)
    }

    /** The shipped content, read from the source tree (unit tests run in the module directory). */
    val shippedContentDir = File("src/main/assets/content")
}
