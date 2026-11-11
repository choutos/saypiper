package eu.wanderclan.wordquest.content

import eu.wanderclan.wordquest.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each content rule of ADR section 4 against a failing fixture. The fixtures are the valid fixture
 * content under test resources with one thing broken.
 */
class ContentValidatorTest {
    private val valid = Fixtures.contentSet()

    private fun ContentSet.editPack(gradeId: String, edit: (Pack) -> Pack) = copy(packs = packs + (gradeId to edit(packs.getValue(gradeId))))

    private fun ContentSet.editWord(gradeId: String, wordId: String, edit: (Word) -> Word) = editPack(gradeId) { pack ->
        pack.copy(units = pack.units.map { u -> u.copy(words = u.words.map { if (it.id == wordId) edit(it) else it }) })
    }

    private fun ContentSet.editUnit(gradeId: String, unitId: String, edit: (VocabUnit) -> VocabUnit) = editPack(gradeId) { pack ->
        pack.copy(units = pack.units.map { if (it.id == unitId) edit(it) else it })
    }

    private fun problems(content: ContentSet, rule: Int) = ContentValidator.validate(content).filter { it.rule == rule }

    @Test fun `rule 11 - a band A word without a clip fails`() {
        val source = object : ContentSource by Fixtures.source {
            override fun list(dir: String) = if (dir == "audio") emptyList() else Fixtures.source.list(dir)
        }
        assertFails(11, ContentLoader.read(source).first, "p1")
    }

    @Test fun `rule 11 - a clip without a word fails`() {
        val source = object : ContentSource by Fixtures.source {
            override fun list(dir: String) = Fixtures.source.list(dir) + if (dir == "audio") listOf("orphan.ogg") else emptyList()
        }
        assertFails(11, ContentLoader.read(source).first)
    }

    @Test fun `rule 11 - a clip for a word outside band A fails`() {
        assertFails(11, valid.copy(audioFiles = valid.audioFiles + "chair.ogg"))
    }

    @Test fun `the loader exposes clips only for band A words`() {
        val loaded = ContentLoader.build(valid.copy(audioFiles = valid.audioFiles + "chair.ogg"), emptyList())
        assertTrue(loaded.audioWordIds.contains("rabbit"))
        assertFalse(loaded.audioWordIds.contains("chair"))
        assertTrue(loaded.catalog.pack("p3") != null)
    }

    private fun assertFails(rule: Int, content: ContentSet, gradeId: String? = null) {
        val found = problems(content, rule)
        assertTrue("expected a rule $rule problem, got ${ContentValidator.validate(content)}", found.isNotEmpty())
        if (gradeId != null) assertTrue("expected it on $gradeId: $found", found.any { it.gradeId == gradeId })
    }

    @Test
    fun `the fixture content passes all rules except the pending secondary mixes`() {
        assertEquals(emptyList<Problem>(), ContentValidator.validate(valid).filter { it.rule != 10 })
    }

    @Test
    fun `rule 1 - ids are lowercase kebab-case`() {
        assertFails(1, valid.editWord("p1", "rabbit") { it.copy(id = "Rabbit") }, "p1")
        assertFails(1, valid.editWord("p1", "rabbit") { it.copy(id = "white_rabbit") }, "p1")
        assertFails(1, valid.editUnit("p1", "fx-pets") { it.copy(id = "fx pets") }, "p1")
    }

    @Test
    fun `rule 1 - ids are unique across all packs`() {
        val sameWordId = valid.editWord("p3", "chair") { it.copy(id = "rabbit") }
        assertFails(1, sameWordId, "p1")
        assertFails(1, sameWordId, "p3")
        assertFails(1, valid.editUnit("p3", "fx-home") { it.copy(id = "fx-pets") }, "p3")
    }

    @Test
    fun `rule 2 - band A words need a picture, other bands a picture or a definition and an example`() {
        assertFails(2, valid.editWord("p1", "rabbit") { it.copy(emoji = null, definition = "a small animal with long ears", example = "The {rabbit} eats.") }, "p1")
        assertFails(2, valid.editWord("s1", "proud") { it.copy(example = null) }, "s1")
        assertFails(2, valid.editWord("s4", "salary") { it.copy(definition = null) }, "s4")
        assertTrue(problems(valid.editWord("p3", "chair") { it.copy(example = null) }, 2).isEmpty())
    }

    @Test
    fun `rule 3 - an example has exactly one pair of braces around the word as written`() {
        assertFails(3, valid.editWord("s1", "calm") { it.copy(example = "Take a deep breath and stay calm.") }, "s1")
        assertFails(3, valid.editWord("s1", "calm") { it.copy(example = "Stay {calm} and {calm}.") }, "s1")
        assertFails(3, valid.editWord("s1", "calm") { it.copy(example = "Stay {calm}.}") }, "s1")
        assertFails(3, valid.editWord("s1", "calm") { it.copy(example = "Stay }calm{.") }, "s1")
        assertFails(3, valid.editWord("s1", "calm") { it.copy(example = "{Calm} down, please.") }, "s1")
        assertFails(3, valid.editWord("s1", "calm") { it.copy(example = "They were {calmer} than us.") }, "s1")
    }

    @Test
    fun `rule 3 - the example is parsed once into before, word and after`() {
        assertEquals(Example("We cook dinner in the ", "kitchen", "."), Example.parse("We cook dinner in the {kitchen}."))
    }

    @Test
    fun `rule 4 - a definition does not contain the word, compared as a whole word without case`() {
        assertFails(4, valid.editWord("s1", "calm") { it.copy(definition = "Calm and quiet") }, "s1")
        assertFails(4, valid.editWord("s4", "deadline") { it.copy(definition = "the date, or deadline, for work") }, "s4")
        // "the" contains "he" as a substring, not as a word.
        val he = valid.editWord("s4", "salary") {
            it.copy(id = "he", text = "he", pos = "other", definition = "the word for a boy or man already mentioned", example = "Tom said {he} was tired.")
        }
        assertTrue(problems(he, 4).isEmpty())
        assertFalse(ContentValidator.containsWholePhrase("the other one", "he"))
        assertTrue(ContentValidator.containsWholePhrase("you must look after it", "look after"))
    }

    @Test
    fun `rule 5 - no two words of a pack share a text`() {
        assertFails(5, valid.editWord("p1", "mouse") { it.copy(text = " Rabbit ") }, "p1")
    }

    @Test
    fun `rule 5 - the same emoji with and without U+FE0F is the same picture`() {
        // sofa is U+1F6CB U+FE0F; the same character without the selector must collide with it.
        val withoutSelector = valid.editWord("p3", "bed") { it.copy(emoji = "🛋") }
        assertEquals("🛋️", valid.packs.getValue("p3").units[0].words.first { it.id == "sofa" }.emoji)
        assertFails(5, withoutSelector, "p3")
    }

    @Test
    fun `rule 6 - a unit has 6 to 12 words and a text at most 24 characters`() {
        assertFails(6, valid.editUnit("p1", "fx-pets") { it.copy(words = it.words.take(5)) }, "p1")
        val thirteen = valid.editUnit("p1", "fx-fruit") { u ->
            u.copy(words = u.words + List(6) { Word("extra-$it", "extra $it", "noun", null) })
        }
        assertFails(6, thirteen, "p1")
        assertFails(6, valid.editWord("p1", "rabbit") { it.copy(text = "a".repeat(25)) }, "p1")
        assertTrue(problems(valid.editWord("p1", "rabbit") { it.copy(text = "a".repeat(24)) }, 6).isEmpty())
    }

    @Test
    fun `rule 7 - part of speech comes from a fixed list`() {
        assertFails(7, valid.editWord("p1", "rabbit") { it.copy(pos = "Noun") }, "p1")
        assertFails(7, valid.editWord("p1", "rabbit") { it.copy(pos = "pronoun") }, "p1")
    }

    @Test
    fun `rule 8 - structure`() {
        assertFails(8, valid.editPack("p1") { it.copy(schemaVersion = 2) }, "p1")
        assertFails(8, valid.editPack("p1") { it.copy(grade = "p2") }, "p1")
        assertFails(8, valid.copy(grades = valid.grades.copy(schemaVersion = 99)))
        assertFails(8, valid.copy(avatars = valid.avatars.copy(schemaVersion = 0)))
        val duplicate = valid.grades.grades + valid.grades.grades.first().copy(pack = "packs/other.json")
        assertFails(8, valid.copy(grades = valid.grades.copy(grades = duplicate)))
        val sharedPath = valid.grades.grades.map { if (it.id == "p2") it.copy(pack = "packs/p1.json") else it }
        assertFails(8, valid.copy(grades = valid.grades.copy(grades = sharedPath)))
    }

    @Test
    fun `rule 9 - an emoji in the content without a picture file fails`() {
        // Not in pictures.json at all.
        assertFails(9, valid.editWord("p1", "rabbit") { it.copy(emoji = "🦄") }, "p1")
        // Listed, but the file is missing.
        assertFails(9, valid.copy(pictureFiles = valid.pictureFiles - "1F430.png"), "p1")
        // An avatar's picture is checked too.
        assertFails(9, valid.copy(pictureFiles = valid.pictureFiles - "270F.png"))
    }

    @Test
    fun `rule 9 - a picture file that nothing uses fails`() {
        assertFails(9, valid.copy(pictureFiles = valid.pictureFiles + "1F984.png"))
        val unusedEntry = valid.pictureIndex.copy(pictures = valid.pictureIndex.pictures + ("🦄" to "1F984.png"))
        assertFails(9, valid.copy(pictureIndex = unusedEntry, pictureFiles = valid.pictureFiles + "1F984.png"))
    }

    @Test
    fun `rule 10 - a pack the generator cannot play fails`() {
        val found = problems(valid, 10)
        // Band B definitions now play. Secondary mixes still arrive in FRL-9.
        assertEquals(setOf("s1", "s4"), found.map { it.gradeId }.toSet())
    }

    @Test
    fun `the loader leaves out an avatar whose picture is missing and keeps the rest`() {
        val (content, _) = ContentLoader.read(Fixtures.source)
        val loaded = ContentLoader.build(content.copy(pictureFiles = content.pictureFiles - "270F.png"), emptyList())
        assertTrue(loaded.problems.any { it.rule == 9 && it.avatarId == "pencil" })
        assertEquals(listOf("fox", "sun"), loaded.catalog.avatars.map { it.id })
        assertEquals(listOf("p1", "p3"), loaded.catalog.packs.map { it.grade.id })
    }

    @Test
    fun `the loader leaves out packs with problems and keeps the rest`() {
        val loaded = ContentLoader.load(Fixtures.source)
        assertEquals(12, loaded.catalog.grades.size)
        assertEquals(listOf("p1", "p3"), loaded.catalog.packs.map { it.grade.id })
        assertEquals("p3", loaded.catalog.pack("p3")!!.grade.id)
    }
}
