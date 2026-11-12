package eu.wanderclan.wordquest.content

/** A pack together with the grade it belongs to. */
data class GradePack(val grade: Grade, val pack: Pack) {
    val band: Band get() = grade.band
    val words: List<Word> by lazy { pack.units.flatMap { it.words } }
}

/** Where a word lives: needed to exercise it in its own band with distractors from its own pack. */
data class WordHome(val word: Word, val unit: VocabUnit, val gradePack: GradePack)

/** The playable content: every grade, and the packs that loaded and passed validation. */
class Catalog(val grades: List<Grade>, packs: List<GradePack>, val avatars: List<Avatar> = emptyList()) {
    private val packsByGrade: Map<String, GradePack> = packs.associateBy { it.grade.id }
    private val units: Map<String, Pair<VocabUnit, GradePack>> =
        packs.flatMap { gp -> gp.pack.units.map { it.id to (it to gp) } }.toMap()
    private val words: Map<String, WordHome> =
        packs.flatMap { gp -> gp.pack.units.flatMap { u -> u.words.map { it.id to WordHome(it, u, gp) } } }.toMap()

    val packs: List<GradePack> get() = packsByGrade.values.toList()

    /** Null means the class is "coming soon". */
    fun pack(gradeId: String): GradePack? = packsByGrade[gradeId]
    fun grade(gradeId: String): Grade? = grades.firstOrNull { it.id == gradeId }
    fun unit(unitId: String): Pair<VocabUnit, GradePack>? = units[unitId]
    fun word(wordId: String): WordHome? = words[wordId]
}
