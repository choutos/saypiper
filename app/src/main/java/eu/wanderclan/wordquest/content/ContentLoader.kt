package eu.wanderclan.wordquest.content

import android.content.res.AssetManager
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException

/** Reads content files by path relative to the content root. */
interface ContentSource {
    /** The file's bytes, or null if it does not exist. */
    fun read(path: String): ByteArray?

    /** File names directly inside [dir]. */
    fun list(dir: String): List<String>
}

class AssetContentSource(private val assets: AssetManager, private val root: String = "content") : ContentSource {
    override fun read(path: String): ByteArray? =
        try {
            assets.open("$root/$path").use { it.readBytes() }
        } catch (_: FileNotFoundException) {
            null
        }

    override fun list(dir: String): List<String> = assets.list("$root/$dir")?.toList().orEmpty()
}

data class LoadedContent(val catalog: Catalog, val pictureIndex: PictureIndex, val problems: List<Problem>)

/**
 * Loads and validates the content. A pack with any problem is left out of the catalog; a class whose
 * pack is absent or left out shows as "coming soon". The shared files must parse.
 */
object ContentLoader {
    const val PICTURES_DIR = "pictures"
    private val json = Json { ignoreUnknownKeys = false }

    fun read(source: ContentSource): Pair<ContentSet, List<Problem>> {
        val parseProblems = mutableListOf<Problem>()
        val grades = parse<GradeList>(source, "grades.json")
        val packs = mutableMapOf<String, Pack>()
        for (grade in grades.grades) {
            val bytes = source.read(grade.pack) ?: continue
            try {
                packs[grade.id] = json.decodeFromString<Pack>(bytes.decodeToString())
            } catch (e: SerializationException) {
                parseProblems += Problem(8, grade.id, "${grade.pack} cannot be read: ${e.message}")
            } catch (e: IllegalArgumentException) {
                parseProblems += Problem(8, grade.id, "${grade.pack} cannot be read: ${e.message}")
            }
        }
        val content = ContentSet(
            grades = grades,
            packs = packs,
            avatars = parse(source, "avatars.json"),
            pictureIndex = parse(source, "pictures.json"),
            pictureFiles = source.list(PICTURES_DIR).toSet(),
        )
        return content to parseProblems
    }

    fun load(source: ContentSource): LoadedContent {
        val (content, parseProblems) = read(source)
        val problems = parseProblems + ContentValidator.validate(content)
        val broken = problems.mapNotNull { it.gradeId }.toSet()
        val packs = content.grades.grades
            .filter { it.id !in broken }
            .mapNotNull { grade -> content.packs[grade.id]?.let { GradePack(grade, it) } }
        return LoadedContent(Catalog(content.grades.grades, packs, content.avatars.avatars), content.pictureIndex, problems)
    }

    private inline fun <reified T> parse(source: ContentSource, path: String): T {
        val bytes = source.read(path) ?: error("Content file $path is missing")
        return json.decodeFromString<T>(bytes.decodeToString())
    }
}
