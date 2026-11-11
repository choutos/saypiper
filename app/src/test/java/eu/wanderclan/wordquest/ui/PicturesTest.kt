package eu.wanderclan.wordquest.ui

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.wanderclan.wordquest.Fixtures
import eu.wanderclan.wordquest.content.ContentLoader
import eu.wanderclan.wordquest.content.ContentSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestWordQuestApplication::class)
class PicturesTest {
    @Test fun `images are read and decoded off main at requested size then cached by size`() = runBlocking {
        var reads = 0
        val delegate = Fixtures.source
        val source = object : ContentSource {
            override fun read(path: String): ByteArray? {
                assertNotEquals(Looper.getMainLooper().thread, Thread.currentThread())
                reads++
                return delegate.read(path)
            }
            override fun list(dir: String) = delegate.list(dir)
        }
        val pictures = Pictures(source, ContentLoader.read(delegate).first.pictureIndex)
        val small = pictures.load("🐰", 48)!!
        assertEquals(48, small.width)
        assertEquals(48, small.height)
        assertSame(small, pictures.load("🐰", 48))
        assertEquals(1, reads)
        assertEquals(128, pictures.load("🐰", 128)!!.width)
        assertEquals(2, reads)
    }
}
