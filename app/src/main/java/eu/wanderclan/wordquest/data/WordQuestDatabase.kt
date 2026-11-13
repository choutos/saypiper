package eu.wanderclan.wordquest.data

import android.content.Context
import androidx.room.Database
import androidx.room.AutoMigration
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schemas are exported to app/schemas. Release 0.1.0 shipped schema version 2 and is published, so it
 * has to be assumed installed: every schema change ships with a tested migration (ADR section 7).
 */
@Database(
    entities = [ProfileEntity::class, WordProgressEntity::class, LessonResultEntity::class, DailyActivityEntity::class, AppSettingsEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
abstract class WordQuestDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun progress(): ProgressDao
    abstract fun settings(): SettingsDao

    companion object {
        fun create(context: Context): WordQuestDatabase =
            Room.databaseBuilder(context, WordQuestDatabase::class.java, "word-quest.db").build()
    }
}
