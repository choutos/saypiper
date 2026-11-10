package eu.wanderclan.wordquest.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.wanderclan.wordquest.game.DayClock
import eu.wanderclan.wordquest.game.LessonRequest
import eu.wanderclan.wordquest.game.LessonResult
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
        profiles.delete(id)

        val dao = db.progress()
        assertEquals(0, dao.lessonResultCount(id))
        assertEquals(0, dao.wordProgressCount(id))
        assertEquals(emptyList<DailyActivityEntity>(), dao.dailyActivity(id))
        assertEquals(1, dao.lessonResultCount(other))
    }

    @Test
    fun `changing class keeps progress`() = runTest {
        val id = profiles.create("Mia", "fox")
        profiles.setGrade(id, "p1")
        progress.save(id, result("s1", 3, "dog" to true))
        profiles.setGrade(id, "s3")
        assertEquals("s3", db.profiles().get(id)!!.gradeId)
        assertEquals(mapOf(("p1-animals" to 1) to 3), progress.bestStars(id).first())
    }
}
