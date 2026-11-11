package eu.wanderclan.wordquest.ui

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

/** The app on fixture content, an in-memory database, fake clip playback without a device voice, a fake day and a fixed lesson seed. */
class TestWordQuestApplication : WordQuestApplication() {
    /** The fake clock: tests move it to play on another day. */
    var today: LocalDate = LocalDate.of(2026, 11, 10)

    override fun createContainer(): AppContainer {
        val source = Fixtures.source
        return AppContainer(
            content = ContentLoader.load(source),
            contentSource = source,
            database = Room.inMemoryDatabaseBuilder(this, WordQuestDatabase::class.java).build(),
            speech = ClipSpeech(ContentLoader.load(source).audioWordIds, FakeSpeech(), object : ClipPlayer {
                override fun play(path: String, completed: (SpeechResult) -> Unit) { completed(SpeechResult.SPOKEN) }
                override fun stop() {}
            }, Dispatchers.Unconfined),
            clock = { today },
            nextSeed = { SEED },
            pictureDecodeDispatcher = Dispatchers.Unconfined,
        )
    }

    companion object {
        const val SEED = 2026L
    }
}
