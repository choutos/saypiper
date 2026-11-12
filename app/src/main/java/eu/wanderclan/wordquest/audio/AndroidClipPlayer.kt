package eu.wanderclan.wordquest.audio

import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.IOException

/** One platform player at a time. All calls and callbacks belong to the main looper. */
class AndroidClipPlayer(
    private val openAsset: (String) -> AssetFileDescriptor,
    private val createPlayer: () -> MediaPlayer = { MediaPlayer() },
) : ClipPlayer {
    private var player: MediaPlayer? = null
    private var completed: ((SpeechResult) -> Unit)? = null

    override fun play(path: String, completed: (SpeechResult) -> Unit) {
        stop()
        val current = createPlayer()
        player = current
        this.completed = completed
        try {
            current.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            current.setOnPreparedListener {
                if (player === it) try { it.start() } catch (_: RuntimeException) { finish(it, SpeechResult.FAILED) }
            }
            current.setOnCompletionListener { finish(it, SpeechResult.SPOKEN) }
            current.setOnErrorListener { failed, _, _ -> finish(failed, SpeechResult.FAILED); true }
            openAsset(path).use { current.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            current.prepareAsync()
        } catch (_: IOException) {
            finish(current, SpeechResult.FAILED)
        } catch (_: RuntimeException) {
            finish(current, SpeechResult.FAILED)
        }
    }

    private fun finish(current: MediaPlayer, result: SpeechResult) {
        if (player !== current) return // Ignore callbacks arriving after stop/replay.
        val callback = completed
        player = null
        completed = null
        current.release()
        callback?.invoke(result)
    }

    override fun stop() {
        player?.let { finish(it, SpeechResult.INTERRUPTED) }
    }
}
