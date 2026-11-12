package eu.wanderclan.wordquest.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.game.DayActivity
import eu.wanderclan.wordquest.game.DayClock
import eu.wanderclan.wordquest.game.LessonRequest
import eu.wanderclan.wordquest.game.LessonResult
import eu.wanderclan.wordquest.game.Practice
import eu.wanderclan.wordquest.game.Progression
import eu.wanderclan.wordquest.game.Rewards
import eu.wanderclan.wordquest.game.ScheduledWord
import eu.wanderclan.wordquest.game.Scoring
import eu.wanderclan.wordquest.game.WordOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ProgressRepositoryTest {
    private val today = LocalDate.of(2026, 11, 10)
    private var day = today
    private lateinit var db: WordQuestDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var progress: ProgressRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WordQuestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profiles = ProfileRepository(db)
        progress = ProgressRepository(db, DayClock { day })
    }

    @After
    fun tearDown() = db.close()

    private fun result(sessionId: String, stars: Int = 3, vararg outcomes: Pair<String, Boolean>) =
        LessonResult(sessionId, LessonRequest.UnitLesson("p1-animals", 1), outcomes.map { WordOutcome(it.first, it.second) }, stars)

    @Test
    fun `saving the same session twice stores one lesson row and awards points once`() = runTest {
        val id = profiles.create("Mia", "fox")
        val result = result("session-1", 3, "dog" to true, "cat" to false)
        assertTrue(progress.save(id, result))
        assertFalse(progress.save(id, result))

        val dao = db.progress()
        assertEquals(1, dao.lessonResultCount(id))
        assertEquals(listOf(DailyActivityEntity(id, today.toEpochDay(), Scoring.points(result))), dao.dailyActivity(id))
        val words = dao.wordProgress(id, listOf("dog", "cat")).associateBy { it.wordId }
        assertEquals(WordProgressEntity(id, "dog", 2, today.plusDays(1).toEpochDay(), 1, 0), words["dog"])
        assertEquals(WordProgressEntity(id, "cat", 1, today.toEpochDay(), 0, 1), words["cat"])
    }

    @Test
    fun `points of the day add up and best stars are kept per lesson`() = runTest {
        val id = profiles.create("Mia", "fox")
        progress.save(id, result("a", 1, "dog" to false))
        progress.save(id, result("b", 3, "dog" to true))
        progress.save(id, result("c", 2, "dog" to true))
        assertEquals(Scoring.points(result("x", 1)) + Scoring.points(result("x", 3)) + Scoring.points(result("x", 2)),
            db.progress().dailyActivity(id).single().points)
        assertEquals(mapOf(("p1-animals" to 1) to 3), progress.bestStars(id).first())
    }

    @Test
    fun `practice results are stored without a unit`() = runTest {
        val id = profiles.create("Mia", "fox")
        progress.save(id, LessonResult("p", LessonRequest.Practice(listOf("dog")), listOf(WordOutcome("dog", true)), 3))
        assertEquals(1, db.progress().lessonResultCount(id))
        assertEquals(emptyMap<Pair<String, Int>, Int>(), progress.bestStars(id).first())
    }

    @Test
    fun `deleting a profile leaves no rows in the other tables`() = runTest {
        val id = profiles.create("Mia", "fox")
        val other = profiles.create("Leo", "owl")
        progress.save(id, result("s1", 3, "dog" to true))
        progress.save(other, result("s2", 3, "dog" to true))
        val otherWords = db.progress().wordProgress(other, listOf("dog"))
        val otherActivity = db.progress().dailyActivity(other)
        profiles.delete(id)

        val dao = db.progress()
        assertEquals(0, dao.lessonResultCount(id))
        assertEquals(0, dao.wordProgressCount(id))
        assertEquals(emptyList<DailyActivityEntity>(), dao.dailyActivity(id))
        assertEquals(1, dao.lessonResultCount(other))
        assertEquals(otherWords, dao.wordProgress(other, listOf("dog")))
        assertEquals(otherActivity, dao.dailyActivity(other))
        assertEquals(mapOf(("p1-animals" to 1) to 3), progress.bestStars(other).first())
        assertEquals(null, db.profiles().get(id))
    }

    @Test
    fun `changing class keeps progress`() = runTest {
        val id = profiles.create("Mia", "fox")
        profiles.setGrade(id, "p1")
        progress.save(id, result("s1", 3, "dog" to true))
        profiles.setGrade(id, "s3")
        profiles.setAvatar(id, "owl")
        profiles.rename(id, "Mimi")
        assertEquals("s3", db.profiles().get(id)!!.gradeId)
        assertEquals("owl", db.profiles().get(id)!!.avatarId)
        assertEquals("Mimi", db.profiles().get(id)!!.nickname)
        assertEquals(mapOf(("p1-animals" to 1) to 3), progress.bestStars(id).first())
    }

    @Test
    fun `reset removes every kind of progress only for the selected profile`() = runTest {
        val mia = profiles.create("Mia", "fox")
        val leo = profiles.create("Leo", "owl")
        progress.save(mia, result("mia", 3, "dog" to true))
        progress.save(leo, result("leo", 2, "cat" to true))
        profiles.resetProgress(mia)
        assertEquals(0, db.progress().lessonResultCount(mia))
        assertEquals(0, db.progress().wordProgressCount(mia))
        assertTrue(db.progress().dailyActivity(mia).isEmpty())
        assertEquals("Mia", db.profiles().get(mia)!!.nickname)
        assertEquals(1, db.progress().lessonResultCount(leo))
        assertEquals(1, db.progress().wordProgressCount(leo))
        assertEquals(1, db.progress().dailyActivity(leo).size)
    }

    @Test
    fun `adult override is per profile and class and switching off restores earned path`() = runTest {
        val mia = profiles.create("Mia", "fox")
        val leo = profiles.create("Leo", "owl")
        profiles.setGrade(mia, "p1")
        profiles.setGrade(leo, "p1")
        val units = Fixtures.catalog().pack("p1")!!.pack.units
        suspend fun path(id: Long) = Progression.path(units, progress.bestStars(id).first(),
            db.profiles().get(id)!!.let { it.openUnitsGradeId == it.gradeId })
        val earned = path(mia)
        profiles.openAllUnits(mia, true)
        assertTrue(path(mia).all { it.isOpen && it.lessons.all { l -> l.isOpen } })
        assertEquals(earned, path(leo))
        profiles.setGrade(mia, "p3")
        assertEquals("p1", db.profiles().get(mia)!!.openUnitsGradeId)
        profiles.setGrade(mia, "p1")
        profiles.openAllUnits(mia, false)
        assertEquals(earned, path(mia))
    }

    @Test
    fun `finishing the same practice session twice awards its points once`() = runTest {
        val id = profiles.create("Mia", "fox")
        val practice = LessonResult("practice-1", LessonRequest.Practice(listOf("dog")), listOf(WordOutcome("dog", true)), 2)
        assertTrue(progress.save(id, practice))
        assertFalse(progress.save(id, practice))
        assertEquals(listOf(DayActivity(today, Scoring.points(practice))), progress.activity(id).first())
        assertEquals(listOf(ScheduledWord("dog", today.plusDays(1))), progress.schedule(id).first())
    }

    @Test
    fun `progress and rewards are per profile, a second profile starts at zero`() = runTest {
        val catalog = Fixtures.catalog()
        val mia = profiles.create("Mia", "fox")
        progress.save(mia, LessonResult("s1", LessonRequest.UnitLesson("fx-pets", 1), listOf(WordOutcome("rabbit", false)), 1))
        val leo = profiles.create("Leo", "owl")

        assertEquals(listOf("rabbit"), Practice.dueWords(progress.schedule(mia).first(), today, catalog))
        assertEquals(1, Rewards.streak(progress.activity(mia).first(), today))

        assertEquals(emptyList<String>(), Practice.dueWords(progress.schedule(leo).first(), today, catalog))
        assertEquals(0, Rewards.totalPoints(progress.activity(leo).first()))
        assertEquals(0, Rewards.streak(progress.activity(leo).first(), today))
        val units = catalog.pack("p1")!!.pack.units
        val open = Progression.path(units, progress.bestStars(leo).first()).flatMap { u -> u.lessons.filter { it.isOpen }.map { u.unit.id to it.number } }
        assertEquals(listOf("fx-pets" to 1), open)
    }

    @Test
    fun `a device clock moved backwards does not crash, inflate the streak or promote a word`() = runTest {
        val id = profiles.create("Mia", "fox")
        day = today.plusDays(2)
        progress.save(id, result("a", 3, "dog" to true)) // box 2, due on the 13th
        day = today.plusDays(3)
        progress.save(id, result("b", 3, "dog" to true)) // box 3, due on the 16th
        day = today // the clock goes back three days
        assertTrue(progress.save(id, result("c", 3, "dog" to true)))

        val dog = db.progress().wordProgress(id, listOf("dog")).single()
        assertEquals(3, dog.box)
        assertEquals(today.plusDays(6).toEpochDay(), dog.dueDay)
        assertEquals(1, Rewards.streak(progress.activity(id).first(), today))
        assertEquals(3, db.progress().lessonResultCount(id))
    }
}
