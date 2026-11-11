package eu.wanderclan.wordquest.ui

import androidx.room.Room
import eu.wanderclan.wordquest.AppContainer
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.WordQuestApplication
import eu.wanderclan.wordquest.audio.FakeSpeech
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.data.WordQuestDatabase
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate

/** The app on fixture content, an in-memory database, no speech, a fixed day and a fixed lesson seed. */
class TestWordQuestApplication : WordQuestApplication() {
    override fun createContainer(): AppContainer {
        val source = Fixtures.source
        return AppContainer(
            content = ContentLoader.load(source),
            contentSource = source,
            database = Room.inMemoryDatabaseBuilder(this, WordQuestDatabase::class.java).build(),
            speech = FakeSpeech(FakeSpeech.Mode.UNAVAILABLE),
            clock = { LocalDate.of(2026, 11, 10) },
            nextSeed = { SEED },
            pictureDecodeDispatcher = Dispatchers.Unconfined,
        )
    }

    companion object {
        const val SEED = 2026L
    }
}
