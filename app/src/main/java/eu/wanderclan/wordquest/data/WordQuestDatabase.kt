package eu.wanderclan.wordquest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schemas are exported to app/schemas. Until the first build is installed on a child's device the
 * schema may change freely; from then on every change ships with a tested migration (ADR section 7).
 */
@Database(
    entities = [ProfileEntity::class, WordProgressEntity::class, LessonResultEntity::class, DailyActivityEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WordQuestDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun progress(): ProgressDao

    companion object {
        fun create(context: Context): WordQuestDatabase =
            Room.databaseBuilder(context, WordQuestDatabase::class.java, "word-quest.db").build()
    }
}
