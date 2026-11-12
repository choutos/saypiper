package eu.wanderclan.wordquest.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @Test fun `version one upgrades with all existing profile and progress rows intact`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "upgrade-from-one.db"
        val schema = JSONObject(File("schemas/eu.wanderclan.wordquest.data.WordQuestDatabase/1.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO profile (id, nickname, avatarId, gradeId) VALUES (1, 'Mia', 'fox', 'p1')")
            old.execSQL("INSERT INTO word_progress VALUES (1, 'rabbit', 2, 100, 1, 0)")
            old.execSQL("INSERT INTO lesson_result VALUES ('old-session', 1, 'fx-pets', 1, 3, 99)")
            old.execSQL("INSERT INTO daily_activity VALUES (1, 99, 25)")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, WordQuestDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            assertEquals(ProfileEntity(1, "Mia", "fox", "p1", null), db.profiles().get(1))
            assertEquals(listOf(WordProgressEntity(1, "rabbit", 2, 100, 1, 0)), db.progress().wordProgress(1, listOf("rabbit")))
            assertEquals(1, db.progress().lessonResultCount(1))
            assertEquals(listOf(LessonStars("fx-pets", 1, 3)), db.progress().observeBestStars(1).first())
            assertEquals(listOf(DailyActivityEntity(1, 99, 25)), db.progress().dailyActivity(1))
            val settings = SettingsRepository(db)
            assertTrue(settings.settings().first().soundEnabled)
            settings.setSound(false)
            assertFalse(settings.settings().first().soundEnabled)
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
