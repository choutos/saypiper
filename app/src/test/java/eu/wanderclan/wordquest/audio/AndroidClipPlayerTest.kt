package eu.wanderclan.wordquest.audio

import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.ui.TestWordQuestApplication
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaPlayer
import java.io.File
import java.io.FileNotFoundException

@RunWith(AndroidJUnit4::class)
@Config(application = TestWordQuestApplication::class)
class AndroidClipPlayerTest {
    private val path = "content/audio/rabbit.ogg"
    private val players = mutableListOf<MediaPlayer>()
    private fun player(): AndroidClipPlayer {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(60_000, 0) }
        return AndroidClipPlayer({ requested ->
            assertEquals(path, requested)
            val file = File(Fixtures.contentDir, "audio/rabbit.ogg")
            AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length())
        }, { MediaPlayer().also { players += it } })
    }

    @Test fun `platform completion releases the player and reports spoken`() {
        val player = player()
        val results = mutableListOf<SpeechResult>()
        player.play(path, results::add)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(players.single().isPlaying)
        shadowOf(players.single()).invokeCompletionListener()
        assertEquals(listOf(SpeechResult.SPOKEN), results)
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(players.single()).state)
        player.stop()
        assertEquals(1, results.size)
    }

    @Test fun `platform error reports failed and a late callback cannot affect replay`() {
        val player = player()
        val results = mutableListOf<SpeechResult>()
        player.play(path, results::add)
        val first = shadowOf(players.single())
        val lateCompletion = first.onCompletionListener
        first.invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        assertEquals(listOf(SpeechResult.FAILED), results)
        player.play(path, results::add)
        lateCompletion.onCompletion(players.first())
        assertEquals(1, results.size)
        player.stop()
        assertEquals(listOf(SpeechResult.FAILED, SpeechResult.INTERRUPTED), results)
        assertTrue(players.all { shadowOf(it).state == ShadowMediaPlayer.State.END })
    }

    @Test fun `a missing asset fails and releases the player`() {
        val current = MediaPlayer()
        val player = AndroidClipPlayer({ throw FileNotFoundException("missing") }, { current })
        var result: SpeechResult? = null
        player.play(path) { result = it }
        assertEquals(SpeechResult.FAILED, result)
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(current).state)
    }
}
