package eu.wanderclan.wordquest.data

import androidx.room.withTransaction
import eu.wanderclan.wordquest.game.DayActivity
import eu.wanderclan.wordquest.game.DayClock
import eu.wanderclan.wordquest.game.Leitner
import eu.wanderclan.wordquest.game.LessonRequest
import eu.wanderclan.wordquest.game.LessonResult
import eu.wanderclan.wordquest.game.ScheduledWord
import eu.wanderclan.wordquest.game.Scoring
import eu.wanderclan.wordquest.game.WordProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class ProgressRepository(private val db: WordQuestDatabase, private val clock: DayClock) {
    private val dao = db.progress()
    private val leitner = Leitner(clock)

    /**
     * Stores a finished session: the lesson row, the word progress and the day's points, in one
     * transaction. A session id that is already stored is ignored, so a result is never awarded twice.
     * Returns false when the session was already stored.
     */
    suspend fun save(profileId: Long, result: LessonResult): Boolean = db.withTransaction {
        val today = clock.today().toEpochDay()
        val unit = result.request as? LessonRequest.UnitLesson
        val row = LessonResultEntity(result.sessionId, profileId, unit?.unitId, unit?.number, result.stars, today)
        if (dao.insertLessonResult(row) == -1L) return@withTransaction false

        val previous = dao.wordProgress(profileId, result.outcomes.map { it.wordId }).associateBy { it.wordId }
        val updated = result.outcomes.map { outcome ->
            val before = previous[outcome.wordId]?.let {
                WordProgress(it.box, LocalDate.ofEpochDay(it.dueDay), it.correctCount, it.wrongCount)
            }
            val after = leitner.apply(before, outcome.correct)
            WordProgressEntity(profileId, outcome.wordId, after.box, after.dueDay.toEpochDay(), after.correctCount, after.wrongCount)
        }
        dao.upsertWordProgress(updated)
        val points = (dao.points(profileId, today) ?: 0) + Scoring.points(result)
        dao.upsertDailyActivity(DailyActivityEntity(profileId, today, points))
        true
    }

    /** Best stars per (unit id, lesson number). */
    fun bestStars(profileId: Long): Flow<Map<Pair<String, Int>, Int>> =
        dao.observeBestStars(profileId).map { rows -> rows.associate { (it.unitId to it.lessonNumber) to it.stars } }

    /** Every word with history and the day it is due again. */
    fun schedule(profileId: Long): Flow<List<ScheduledWord>> =
        dao.observeSchedule(profileId).map { rows -> rows.map { ScheduledWord(it.wordId, LocalDate.ofEpochDay(it.dueDay)) } }

    /** The points of every day played. */
    fun activity(profileId: Long): Flow<List<DayActivity>> =
        dao.observeDailyActivity(profileId).map { rows -> rows.map { DayActivity(LocalDate.ofEpochDay(it.day), it.points) } }
}

class ProfileRepository(private val db: WordQuestDatabase) {
    private val dao = db.profiles()

    fun profiles(): Flow<List<ProfileEntity>> = dao.observeAll()
    fun profile(id: Long): Flow<ProfileEntity?> = dao.observe(id)
    suspend fun create(nickname: String, avatarId: String): Long = dao.insert(ProfileEntity(nickname = nickname.trim(), avatarId = avatarId))

    /** Changing class keeps all progress: progress is stored against word and unit ids, not the class. */
    suspend fun setGrade(profileId: Long, gradeId: String) {
        val profile = dao.get(profileId) ?: return
        dao.update(profile.copy(gradeId = gradeId))
    }

    suspend fun setAvatar(profileId: Long, avatarId: String) {
        dao.get(profileId)?.let { dao.update(it.copy(avatarId = avatarId)) }
    }

    suspend fun rename(profileId: Long, nickname: String) {
        require(nickname.isNotBlank())
        dao.get(profileId)?.let { dao.update(it.copy(nickname = nickname.trim().take(20))) }
    }

    suspend fun openAllUnits(profileId: Long, enabled: Boolean) {
        dao.get(profileId)?.let { dao.update(it.copy(openUnitsGradeId = if (enabled) it.gradeId else null)) }
    }

    suspend fun resetProgress(profileId: Long) = db.withTransaction {
        db.progress().clearResults(profileId)
        db.progress().clearWords(profileId)
        db.progress().clearActivity(profileId)
    }

    suspend fun delete(profileId: Long) {
        dao.get(profileId)?.let { dao.delete(it) }
    }
}
