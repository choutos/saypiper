package eu.wanderclan.wordquest.ui

import android.content.Context
import androidx.room.Room
import eu.wanderclan.wordquest.AppContainer
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.WordQuestApplication
import eu.wanderclan.wordquest.audio.FakeSpeech
import eu.wanderclan.wordquest.audio.ClipSpeech
import eu.wanderclan.wordquest.audio.ClipPlayer
import eu.wanderclan.wordquest.audio.SpeechResult
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.data.WordQuestDatabase
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.util.concurrent.Executor

/**
 * Keeps Room work on its caller, the Compose test thread. Test effects use an unconfined dispatcher;
 * a Flow collected on Room's executor can otherwise apply a snapshot during Compose 1.8.0's dispatch
 * of earlier changes, losing the invalidation.
 *
 * Transactions also start on the test thread, requiring main-thread queries here. Keep DAO methods
 * suspend or Flow-based: this setting would hide a blocking DAO call that production rejects.
 * These UI tests skip database thread boundaries; the emulator smoke test uses production executors.
 */
fun testDatabase(context: Context): WordQuestDatabase {
    val callingThread = Executor { it.run() }
    return Room.inMemoryDatabaseBuilder(context, WordQuestDatabase::class.java)
        .setQueryExecutor(callingThread)
        .setTransactionExecutor(callingThread)
        .allowMainThreadQueries()
        .build()
}

/** The app on fixture content, an in-memory database, fake clip playback without a device voice, a fake day and a fixed lesson seed. */
class TestWordQuestApplication : WordQuestApplication() {
    /** The fake clock: tests move it to play on another day. */
    var today: LocalDate = LocalDate.of(2026, 11, 10)
    var lessonSeed: Long = SEED
    val deviceVoice = FakeSpeech()

    override fun createContainer(): AppContainer {
        val source = Fixtures.source
        return AppContainer(
            content = ContentLoader.load(source),
            contentSource = source,
            database = testDatabase(this),
            speech = ClipSpeech(ContentLoader.load(source).audioWordIds, deviceVoice, object : ClipPlayer {
                override fun play(path: String, completed: (SpeechResult) -> Unit) { completed(SpeechResult.SPOKEN) }
                override fun stop() {}
            }, Dispatchers.Unconfined),
            clock = { today },
            nextSeed = { lessonSeed },
            pictureDecodeDispatcher = Dispatchers.Unconfined,
        )
    }

    companion object {
        const val SEED = 2026L
    }
}
