package eu.wanderclan.wordquest.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.content.ContentSource
import eu.wanderclan.wordquest.content.PictureIndex
import eu.wanderclan.wordquest.content.normaliseEmoji
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reading, decoding and scaling happens off the main thread by default, cached by image and display size. */
class Pictures(
    private val source: ContentSource,
    private val index: PictureIndex,
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    suspend fun load(emoji: String, pixels: Int): ImageBitmap? = withContext(decodeDispatcher) {
        val file = index.pictures[normaliseEmoji(emoji)] ?: return@withContext null
        val size = pixels.coerceIn(1, 618)
        val key = "$file:$size"
        cache.get(key)?.let { return@withContext it }
        val bytes = source.read("${ContentLoader.PICTURES_DIR}/$file") ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (bounds.outWidth / (inSampleSize * 2) >= size && bounds.outHeight / (inSampleSize * 2) >= size) inSampleSize *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@withContext null
        val scale = minOf(1f, size.toFloat() / maxOf(decoded.width, decoded.height))
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true) else decoded
        if (bitmap !== decoded) decoded.recycle()
        bitmap.asImageBitmap().also { cache.put(key, it) }
    }
}

val LocalPictures = staticCompositionLocalOf<Pictures> { error("Pictures are not provided") }

/** Descriptions must not reveal a quiz answer. Emoji are never drawn as text. */
@Composable
fun Picture(emoji: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val pictures = LocalPictures.current
    BoxWithConstraints(modifier) {
        val pixels = minOf(constraints.maxWidth, constraints.maxHeight).coerceIn(1, 618)
        val bitmap by produceState<ImageBitmap?>(null, pictures, emoji, pixels) { value = pictures.load(emoji, pixels) }
        val loaded = bitmap
        if (loaded != null) Image(loaded, contentDescription, Modifier.matchParentSize()) else Box(Modifier.matchParentSize())
    }
}
