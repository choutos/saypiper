package eu.wanderclan.wordquest

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Render the shipped vectors with Android's native renderer, without starting the game. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class LauncherIconTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val output = File("build/outputs/launcher-icons").apply { mkdirs() }

    private fun drawable(id: Int) = requireNotNull(context.getDrawable(id))

    private fun render(drawable: Drawable, size: Int = 432): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(it))
        }

    private fun save(bitmap: Bitmap, name: String) {
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun `colour and themed marks fit inside the adaptive safe circle`() {
        for ((id, name) in listOf(
            R.drawable.ic_launcher_foreground to "foreground",
            R.drawable.ic_launcher_monochrome to "monochrome",
        )) {
            val bitmap = render(drawable(id))
            var opaquePixels = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) continue
                opaquePixels++
                // 432px = 108dp at 4x. All artwork must fit the central 66dp circle,
                // which also leaves room inside a launcher's central 72dp mask.
                val dx = x + 0.5 - 216
                val dy = y + 0.5 - 216
                assertTrue("$name clipped at $x,$y", dx * dx + dy * dy <= 132 * 132)
            }
            assertTrue("$name must render visible artwork", opaquePixels > 10000)
            // The three pipe holes stay filled, with transparent space between them in the themed mark.
            for ((x, y) in listOf(46.206 to 55.5, 54.0 to 51.0, 61.794 to 46.5)) {
                assertEquals(255, Color.alpha(bitmap.getPixel((x * 4).toInt(), (y * 4).toInt())))
            }
            val betweenHoles = bitmap.getPixel(200, 213)
            if (name == "monochrome") assertEquals(0, Color.alpha(betweenHoles))
            else assertEquals(Color.WHITE, betweenHoles)
            save(bitmap, name)
        }
    }

    @Test fun `launcher resources render at small sizes and export the store icon`() {
        val adaptive = drawable(R.drawable.ic_launcher)
        assertTrue(adaptive is AdaptiveIconDrawable)
        assertNotNull((adaptive as AdaptiveIconDrawable).monochrome)
        for (size in listOf(24, 32, 48, 64)) save(render(adaptive, size), "adaptive-$size")

        // Read the unqualified vector explicitly: SDK 35 normally selects the adaptive resource.
        val legacy = context.assets.openXmlResourceParser("res/drawable/ic_launcher.xml").use { parser ->
            Drawable.createFromXml(context.resources, parser)
        }
        save(render(legacy, 192), "legacy")

        // Store artwork uses the same central 72dp framing as the adaptive icon, with a square background.
        val store = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(store)
        canvas.drawColor(Color.parseColor("#FBF8F2"))
        canvas.translate(-128f, -128f)
        drawable(R.drawable.ic_launcher_foreground).apply {
            setBounds(0, 0, 768, 768)
            draw(canvas)
        }
        save(store, "store-icon")
    }
}
