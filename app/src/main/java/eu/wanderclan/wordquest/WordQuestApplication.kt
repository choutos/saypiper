package eu.wanderclan.wordquest

import android.app.Application
import android.util.Log
import eu.wanderclan.wordquest.audio.FakeSpeech
import eu.wanderclan.wordquest.content.AssetContentSource
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.data.WordQuestDatabase
import java.time.LocalDate

open class WordQuestApplication : Application() {
    val container: AppContainer by lazy { createContainer() }

    protected open fun createContainer(): AppContainer {
        val source = AssetContentSource(assets)
        val content = ContentLoader.load(source)
        content.problems.forEach { Log.w("WordQuest", "Content problem, pack not offered: $it") }
        return AppContainer(
            content = content,
            contentSource = source,
            database = WordQuestDatabase.create(this),
            // Device speech arrives with the primary exercise set; until then lessons are played by reading.
            speech = FakeSpeech(FakeSpeech.Mode.UNAVAILABLE),
            clock = { LocalDate.now() },
        )
    }
}
