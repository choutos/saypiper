package eu.wanderclan.wordquest.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.content.ContentSource
import eu.wanderclan.wordquest.content.PictureIndex
import eu.wanderclan.wordquest.content.normaliseEmoji

/**
 * The only place that knows where pictures come from (ADR D4): the OpenMoji PNG that `pictures.json`
 * names for an emoji. Emoji are never drawn as text.
 */
class Pictures(private val source: ContentSource, private val index: PictureIndex) {
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun load(emoji: String): ImageBitmap? {
        val file = index.pictures[normaliseEmoji(emoji)] ?: return null
        cache.get(file)?.let { return it }
        val bytes = source.read("${ContentLoader.PICTURES_DIR}/$file") ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() ?: return null
        cache.put(file, bitmap)
        return bitmap
    }

    private companion object {
        const val CACHE_BYTES = 24 * 1024 * 1024
    }
}

val LocalPictures = staticCompositionLocalOf<Pictures> { error("Pictures are not provided") }

/** Draws the picture for [emoji]. [contentDescription] must not give away a quiz answer. */
@Composable
fun Picture(emoji: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val pictures = LocalPictures.current
    val bitmap = remember(emoji) { pictures.load(emoji) }
    if (bitmap != null) Image(bitmap, contentDescription, modifier) else Box(modifier)
}
