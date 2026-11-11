package eu.wanderclan.wordquest

import eu.wanderclan.wordquest.audio.Speech
import eu.wanderclan.wordquest.content.ContentSource
import eu.wanderclan.wordquest.content.LoadedContent
import eu.wanderclan.wordquest.data.ProfileRepository
import eu.wanderclan.wordquest.data.ProgressRepository
import eu.wanderclan.wordquest.data.WordQuestDatabase
import eu.wanderclan.wordquest.game.DayClock
import eu.wanderclan.wordquest.game.LessonGenerator
import eu.wanderclan.wordquest.ui.Pictures
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.UUID

/** Manual dependency wiring (ADR section 7: no DI framework). Tests build one from fixtures. */
class AppContainer(
    val content: LoadedContent,
    contentSource: ContentSource,
    database: WordQuestDatabase,
    val speech: Speech,
    clock: DayClock,
    /** Seed for each new lesson. Fixed in tests so a lesson can be reproduced. */
    val nextSeed: () -> Long = { System.nanoTime() },
    val newSessionId: () -> String = { UUID.randomUUID().toString() },
    /** Coroutines started from composition must use background dispatchers that UI tests can replace. */
    pictureDecodeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val catalog get() = content.catalog
    val profiles = ProfileRepository(database)
    val progress = ProgressRepository(database, clock)
    val generator = LessonGenerator(content.catalog)
    val pictures = Pictures(contentSource, content.pictureIndex, pictureDecodeDispatcher)
}
