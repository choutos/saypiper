package eu.wanderclan.wordquest.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The schema versions this build understands (rule 8). */
const val SUPPORTED_SCHEMA_VERSION = 1

@Serializable
enum class Band {
    A, B, C, D;

    /** Options in a choice exercise: three for the youngest children, four otherwise (ADR section 6). */
    val choices: Int get() = if (this == A) 3 else 4
}

@Serializable
enum class Cycle {
    @SerialName("primary") PRIMARY,
    @SerialName("secondary") SECONDARY,
}

@Serializable
data class GradeList(val schemaVersion: Int, val grades: List<Grade>)

@Serializable
data class Grade(
    val id: String,
    val label: String,
    val cycle: Cycle,
    val cefr: String,
    val band: Band,
    val pack: String,
)

@Serializable
data class Pack(val schemaVersion: Int, val grade: String, val units: List<VocabUnit>)

@Serializable
data class VocabUnit(val id: String, val title: String, val emoji: String? = null, val words: List<Word>)

@Serializable
data class Word(
    val id: String,
    val text: String,
    val pos: String,
    val emoji: String? = null,
    val definition: String? = null,
    val example: String? = null,
)

@Serializable
data class AvatarList(val schemaVersion: Int, val avatars: List<Avatar>)

@Serializable
data class Avatar(val id: String, val emoji: String)

/** `pictures.json`, written by `scripts/sync-pictures`: emoji without U+FE0F to a file in `pictures/`. */
@Serializable
data class PictureIndex(val source: String, val pictures: Map<String, String>)

/** An example sentence split at its braces (rule 3). Display and gap-fill both use this form. */
data class Example(val before: String, val word: String, val after: String) {
    companion object {
        /** Returns null unless [example] holds exactly one `{` followed by exactly one `}`. */
        fun parse(example: String): Example? {
            val open = example.indexOf('{')
            val close = example.indexOf('}')
            if (open < 0 || close < open) return null
            if (example.count { it == '{' } != 1 || example.count { it == '}' } != 1) return null
            return Example(example.substring(0, open), example.substring(open + 1, close), example.substring(close + 1))
        }
    }
}

private const val VARIATION_SELECTOR_16 = '️'

/** The form in which emoji are compared and looked up: without U+FE0F (rule 5). */
fun normaliseEmoji(emoji: String): String = emoji.filterNot { it == VARIATION_SELECTOR_16 }

/** The form in which word texts are compared (rule 5). */
fun normaliseText(text: String): String = text.trim().lowercase()
