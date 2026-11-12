package eu.wanderclan.wordquest.audio

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.wanderclan.wordquest.AppContainer
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.data.SettingsRepository
import eu.wanderclan.wordquest.data.WordQuestDatabase
import eu.wanderclan.wordquest.game.*
import eu.wanderclan.wordquest.ui.LessonViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SoundSettingsTest {
    @Test fun `sound off blocks clips and device voice and on restores both`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WordQuestDatabase::class.java).build()
        try {
            val settings = SettingsRepository(db)
            val voice = FakeSpeech(FakeSpeech.Mode.SILENT)
            var clips = 0
            val player = object : ClipPlayer {
                override fun play(path: String, completed: (SpeechResult) -> Unit) { clips++; completed(SpeechResult.SPOKEN) }
                override fun stop() {}
            }
            val speech = ClipSpeech(setOf("rabbit"), voice, player, StandardTestDispatcher(testScheduler))
            val container = AppContainer(ContentLoader.load(Fixtures.source), Fixtures.source, db, speech, { LocalDate.of(2026, 11, 11) }, nextSeed = { 5 })
            assertTrue(settings.settings().first().soundEnabled)
            for (enabled in listOf(false, true)) {
                settings.setSound(enabled)
                // A new repository reads the saved setting; each real lesson snapshots it before generating.
                val sound = SettingsRepository(db).settings().first().soundEnabled
                assertEquals(enabled, sound)
                for ((unit, word) in listOf("fx-pets" to "rabbit", "fx-home" to "chair")) {
                    val vm = LessonViewModel(container, 0, LessonRequest.UnitLesson(unit, 1),
                        Environment({ sound && speech.canSpeak(it) }, true), sound)
                    val lesson = vm.session.value.lesson
                    assertTrue(lesson.exercises.isNotEmpty())
                    assertTrue(lesson.skippedWordIds.isEmpty())
                    assertEquals(enabled, lesson.exercises.any { it.type == ExerciseType.LISTEN_AND_PICK })
                    vm.say(word)
                    runCurrent()
                }
                assertEquals(if (enabled) 1 else 0, clips)
                assertEquals(if (enabled) listOf("chair") else emptyList<String>(), voice.spoken)
            }
        } finally { db.close(); Dispatchers.resetMain() }
    }
}
