package eu.wanderclan.wordquest.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Query("SELECT * FROM profile ORDER BY id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profile WHERE id = :id")
    fun observe(id: Long): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?
}

data class LessonStars(val unitId: String, val lessonNumber: Int, val stars: Int)
data class WordDue(val wordId: String, val dueDay: Long)

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLessonResult(result: LessonResultEntity): Long

    @Query("SELECT * FROM word_progress WHERE profileId = :profileId AND wordId IN (:wordIds)")
    suspend fun wordProgress(profileId: Long, wordIds: List<String>): List<WordProgressEntity>

    @Upsert
    suspend fun upsertWordProgress(progress: List<WordProgressEntity>)

    // Not an UPSERT statement: SQLite on API 24 predates ON CONFLICT ... DO UPDATE (3.24).
    @Query("SELECT points FROM daily_activity WHERE profileId = :profileId AND day = :day")
    suspend fun points(profileId: Long, day: Long): Int?

    @Upsert
    suspend fun upsertDailyActivity(activity: DailyActivityEntity)

    @Query(
        "SELECT unitId, lessonNumber, MAX(stars) AS stars FROM lesson_result " +
            "WHERE profileId = :profileId AND unitId IS NOT NULL AND lessonNumber IS NOT NULL " +
            "GROUP BY unitId, lessonNumber",
    )
    fun observeBestStars(profileId: Long): Flow<List<LessonStars>>

    @Query("SELECT wordId, dueDay FROM word_progress WHERE profileId = :profileId")
    fun observeSchedule(profileId: Long): Flow<List<WordDue>>

    @Query("SELECT * FROM daily_activity WHERE profileId = :profileId")
    fun observeDailyActivity(profileId: Long): Flow<List<DailyActivityEntity>>

    @Query("SELECT COUNT(*) FROM lesson_result WHERE profileId = :profileId")
    suspend fun lessonResultCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM word_progress WHERE profileId = :profileId")
    suspend fun wordProgressCount(profileId: Long): Int

    @Query("SELECT * FROM daily_activity WHERE profileId = :profileId")
    suspend fun dailyActivity(profileId: Long): List<DailyActivityEntity>
}
