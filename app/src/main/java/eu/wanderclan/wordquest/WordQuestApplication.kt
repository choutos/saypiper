package eu.wanderclan.wordquest

import android.app.Application
import android.util.Log
import eu.wanderclan.wordquest.audio.DeviceSpeech
import eu.wanderclan.wordquest.audio.AndroidClipPlayer
import eu.wanderclan.wordquest.audio.ClipSpeech
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
            speech = ClipSpeech(content.audioWordIds, DeviceSpeech(this), AndroidClipPlayer({ assets.openFd(it) })),
            clock = { LocalDate.now() },
        )
    }
}
