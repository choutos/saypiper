package eu.wanderclan.wordquest.content

import eu.wanderclan.wordquest.game.LessonPlan
import eu.wanderclan.wordquest.game.Playability

/** Everything under `assets/content/`, parsed. [packs] maps a grade id to the pack at that grade's path. */
data class ContentSet(
    val grades: GradeList,
    val packs: Map<String, Pack>,
    val avatars: AvatarList,
    val pictureIndex: PictureIndex,
    val pictureFiles: Set<String>,
)

/**
 * A broken content rule (ADR section 4). [gradeId] names the pack at fault, null for shared files;
 * [avatarId] names the avatar at fault, if any.
 */
data class Problem(val rule: Int, val gradeId: String?, val message: String, val avatarId: String? = null) {
    override fun toString(): String = "rule $rule${gradeId?.let { " ($it)" } ?: ""}: $message"
}

/**
 * The one content validator (ADR section 4, rules 1 to 10). The app's loader calls it at start and does
 * not offer a pack with a problem; the content tests call it on the shipped packs and on fixtures.
 */
object ContentValidator {
    private val kebabCase = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
    private val partsOfSpeech = setOf("noun", "verb", "adjective", "adverb", "phrase", "other")
    const val MAX_TEXT_LENGTH = 24

    fun validate(content: ContentSet): List<Problem> {
        val problems = mutableListOf<Problem>()
        problems += structure(content)
        problems += uniqueIds(content)
        for ((gradeId, pack) in content.packs) {
            val band = content.grades.grades.firstOrNull { it.id == gradeId }?.band ?: continue
            problems += pack(gradeId, band, pack)
        }
        problems += pictures(content)
        problems += playability(content, problems)
        return problems
    }

    // Rule 8.
    private fun structure(content: ContentSet): List<Problem> = buildList {
        if (content.grades.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            add(Problem(8, null, "grades.json has unsupported schemaVersion ${content.grades.schemaVersion}"))
        }
        if (content.avatars.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            add(Problem(8, null, "avatars.json has unsupported schemaVersion ${content.avatars.schemaVersion}"))
        }
        content.grades.grades.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
            add(Problem(8, null, "grade id $it is listed more than once"))
        }
        content.grades.grades.groupBy { it.pack }.filterValues { it.size > 1 }.forEach { (path, grades) ->
            add(Problem(8, null, "pack path $path is used by ${grades.joinToString { it.id }}"))
        }
        for ((gradeId, pack) in content.packs) {
            if (pack.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                add(Problem(8, gradeId, "unsupported schemaVersion ${pack.schemaVersion}"))
            }
            if (pack.grade != gradeId) {
                add(Problem(8, gradeId, "pack says grade \"${pack.grade}\" but is the pack of grade $gradeId"))
            }
        }
    }

    // Rule 1: format, and uniqueness across all packs.
    private fun uniqueIds(content: ContentSet): List<Problem> = buildList {
        val unitOwners = mutableMapOf<String, MutableList<String>>()
        val wordOwners = mutableMapOf<String, MutableList<String>>()
        for ((gradeId, pack) in content.packs) {
            for (unit in pack.units) {
                if (!kebabCase.matches(unit.id)) add(Problem(1, gradeId, "unit id \"${unit.id}\" is not lowercase kebab-case"))
                unitOwners.getOrPut(unit.id) { mutableListOf() } += gradeId
                for (word in unit.words) {
                    if (!kebabCase.matches(word.id)) add(Problem(1, gradeId, "word id \"${word.id}\" is not lowercase kebab-case"))
                    wordOwners.getOrPut(word.id) { mutableListOf() } += gradeId
                }
            }
        }
        for ((kind, owners) in listOf("unit" to unitOwners, "word" to wordOwners)) {
            owners.filterValues { it.size > 1 }.forEach { (id, grades) ->
                grades.distinct().forEach { add(Problem(1, it, "$kind id \"$id\" is used more than once (${grades.joinToString()})")) }
            }
        }
    }

    // Rules 2 to 7, within one pack.
    private fun pack(gradeId: String, band: Band, pack: Pack): List<Problem> = buildList {
        fun problem(rule: Int, message: String) = add(Problem(rule, gradeId, message))
        val texts = mutableMapOf<String, String>()
        val emojis = mutableMapOf<String, String>()
        for (unit in pack.units) {
            if (unit.words.size !in LessonPlan.MIN_UNIT_WORDS..LessonPlan.MAX_UNIT_WORDS) {
                problem(6, "unit ${unit.id} has ${unit.words.size} words; a unit has 6 to 12")
            }
            for (word in unit.words) {
                val w = "word ${word.id}"
                // Rule 2.
                if (band == Band.A && word.emoji == null) problem(2, "$w has no emoji, which band A requires")
                if (band != Band.A && word.emoji == null && (word.definition == null || word.example == null)) {
                    problem(2, "$w needs an emoji, or both a definition and an example")
                }
                // Rule 3.
                if (word.example != null) {
                    val parsed = Example.parse(word.example)
                    when {
                        parsed == null -> problem(3, "$w: the example needs exactly one pair of braces and no other brace")
                        parsed.word != word.text -> problem(3, "$w: the example marks \"${parsed.word}\", not \"${word.text}\"")
                    }
                }
                // Rule 4.
                if (word.definition != null && containsWholePhrase(word.definition, word.text)) {
                    problem(4, "$w: the definition contains the word itself")
                }
                // Rule 5.
                texts.put(normaliseText(word.text), word.id)?.let { problem(5, "$w has the same text as word $it") }
                word.emoji?.let { emoji ->
                    emojis.put(normaliseEmoji(emoji), word.id)?.let { problem(5, "$w has the same emoji as word $it") }
                }
                // Rule 6.
                if (word.text.length > MAX_TEXT_LENGTH) problem(6, "$w: text is longer than $MAX_TEXT_LENGTH characters")
                // Rule 7.
                if (word.pos !in partsOfSpeech) problem(7, "$w: part of speech \"${word.pos}\" is not one of $partsOfSpeech")
            }
        }
    }

    /** Whole word or phrase, without case: "the" does not contain "he". */
    fun containsWholePhrase(text: String, phrase: String): Boolean {
        val pattern = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(phrase.trim()) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        return pattern.containsMatchIn(text)
    }

    // Rule 9.
    private fun pictures(content: ContentSet): List<Problem> = buildList {
        val usedFiles = mutableSetOf<String>()
        val usedEmoji = mutableSetOf<String>()
        fun check(emoji: String?, gradeId: String?, where: String, avatarId: String? = null) {
            if (emoji == null) return
            val key = normaliseEmoji(emoji)
            usedEmoji += key
            val file = content.pictureIndex.pictures[key]
            when {
                file == null -> add(Problem(9, gradeId, "$where: $emoji is not in pictures.json; run scripts/sync-pictures", avatarId))
                file !in content.pictureFiles -> add(Problem(9, gradeId, "$where: pictures/$file is missing; run scripts/sync-pictures", avatarId))
                else -> usedFiles += file
            }
        }
        for ((gradeId, pack) in content.packs) {
            for (unit in pack.units) {
                check(unit.emoji, gradeId, "unit ${unit.id}")
                unit.words.forEach { check(it.emoji, gradeId, "word ${it.id}") }
            }
        }
        content.avatars.avatars.forEach { check(it.emoji, null, "avatar ${it.id}", it.id) }
        (content.pictureFiles - usedFiles).sorted().forEach {
            add(Problem(9, null, "pictures/$it is not used by any word, unit or avatar"))
        }
        (content.pictureIndex.pictures.keys - usedEmoji).sorted().forEach {
            add(Problem(9, null, "pictures.json lists $it, which nothing uses"))
        }
    }

    // Rule 10, only for packs that pass the rules the generator relies on.
    private fun playability(content: ContentSet, found: List<Problem>): List<Problem> = buildList {
        val broken = found.mapNotNull { it.gradeId }.toSet()
        for ((gradeId, pack) in content.packs) {
            if (gradeId in broken) continue
            val grade = content.grades.grades.firstOrNull { it.id == gradeId } ?: continue
            for (gap in Playability.gaps(GradePack(grade, pack))) {
                val speech = if (gap.environment.speechAvailable) "with speech" else "without speech"
                add(Problem(10, gradeId, "unit ${gap.unitId} lesson ${gap.lesson} $speech cannot practise ${gap.missingWordIds}"))
            }
        }
    }
}
