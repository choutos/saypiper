package eu.wanderclan.wordquest.audio

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.wanderclan.wordquest.AppContainer
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.data.WordQuestDatabase
import eu.wanderclan.wordquest.game.*
import eu.wanderclan.wordquest.ui.LessonViewModel
import eu.wanderclan.wordquest.ui.TestWordQuestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(application = TestWordQuestApplication::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClipRecoveryTest {
    @Test fun `failure disables only that source in a mixed practice session and a fresh session retries`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WordQuestDatabase::class.java).build()
        try {
            for (clipFails in listOf(true, false)) {
                val content = ContentLoader.load(Fixtures.source)
                val speech = ClipSpeech(content.audioWordIds,
                    FakeSpeech(if (clipFails) FakeSpeech.Mode.SILENT else FakeSpeech.Mode.FAILING), object : ClipPlayer {
                        override fun play(path: String, completed: (SpeechResult) -> Unit) {
                            completed(if (clipFails) SpeechResult.FAILED else SpeechResult.SPOKEN)
                        }
                        override fun stop() {}
                    }, StandardTestDispatcher(testScheduler))
                val container = AppContainer(content, Fixtures.source, database, speech,
                    { LocalDate.of(2026, 11, 11) }, { 1 })
                val request = LessonRequest.Practice(listOf("rabbit", "mouse", "chair", "bed"))
                val vm = LessonViewModel(container, 0, request, Environment(speech::canSpeak, true))
                vm.say(if (clipFails) "rabbit" else "chair")
                runCurrent()
                val disabled = vm.session.value.unavailableSpeechWordIds
                assertEquals(clipFails, "rabbit" in disabled)
                assertEquals(!clipFails, "chair" in disabled)
                assertTrue(vm.session.value.queue.filter { it.wordId in disabled }.none { it.type == ExerciseType.LISTEN_AND_PICK })
                val fresh = LessonViewModel(container, 0, request, Environment(speech::canSpeak, true))
                assertTrue(fresh.session.value.unavailableSpeechWordIds.isEmpty())
                assertTrue(speech.canSpeak(container.catalog.word("rabbit")!!.word))
                speech.close()
            }
        } finally {
            database.close()
            Dispatchers.resetMain()
        }
    }
}
