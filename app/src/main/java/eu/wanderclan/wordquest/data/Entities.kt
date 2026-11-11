package eu.wanderclan.wordquest.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Days are local dates stored as epoch days (LocalDate.toEpochDay()).

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val avatarId: String,
    /** The class the child picked; null until they pick one. */
    val gradeId: String? = null,
    /** The adult override applies only to this class, never to another profile. */
    val openUnitsGradeId: String? = null,
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(@PrimaryKey val id: Int = 1, val soundEnabled: Boolean = true)

@Entity(
    tableName = "word_progress",
    primaryKeys = ["profileId", "wordId"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId", "dueDay")],
)
data class WordProgressEntity(
    val profileId: Long,
    val wordId: String,
    val box: Int,
    val dueDay: Long,
    val correctCount: Int,
    val wrongCount: Int,
)

/** One row per finished session; unit and lesson are null for practice. Best stars are a query. */
@Entity(
    tableName = "lesson_result",
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId", "unitId")],
)
data class LessonResultEntity(
    @PrimaryKey val sessionId: String,
    val profileId: Long,
    val unitId: String?,
    val lessonNumber: Int?,
    val stars: Int,
    val day: Long,
)

@Entity(
    tableName = "daily_activity",
    primaryKeys = ["profileId", "day"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class DailyActivityEntity(val profileId: Long, val day: Long, val points: Int)
