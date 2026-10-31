package ar.joaquin.airmix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

/**
 * Capturas de pantalla para revisar celular y tablet en cada versión (regla de CLAUDE.md).
 * Se generan en CI con Robolectric y se suben al Release (android-celular-…, android-tablet-…).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreensTest {
    private val out = File("build/screens").also { it.mkdirs() }

    private fun launch(): MainActivity {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences("airmix", Context.MODE_PRIVATE).edit()
            .putString("ip", "10.255.255.1").putString("model", "XR12").commit()
        // Nombres y colores de ejemplo, como si la consola ya hubiera respondido.
        val names = listOf("VOZ GARKO", "VOZ PALMI", "GUITARRA", "BAJO", "TECLADO L", "TECLADO R", "PISTA L", "PISTA R")
        for (i in 1..8) {
            App.store.incoming("/ch/${Fmt.two(i)}/config/name", names[i - 1])
            App.store.incoming("/ch/${Fmt.two(i)}/config/color", (i % 7) + 1)
            App.store.incoming("/ch/${Fmt.two(i)}/mix/fader", 0.55f + i * 0.03f)
            App.store.incoming("/ch/${Fmt.two(i)}/mix/on", 1)
        }
        App.store.incoming("/lr/mix/fader", 0.75f)
        App.store.incoming("/lr/mix/on", 1)
        val act = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        idle()
        return act
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))

    private fun call(act: MainActivity, name: String, vararg args: Any) {
        val m = MainActivity::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size }
        m.isAccessible = true
        m.invoke(act, *args)
        idle()
    }

    private fun shoot(act: MainActivity, name: String) {
        val v: View = act.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun all(prefix: String) {
        val act = launch()
        shoot(act, "$prefix-1-mezcla")
        call(act, "toggleNotes")
        shoot(act, "$prefix-2-notas")
        call(act, "toggleNotes")
        App.store.incoming(chLinkAddr(7), 1)
        idle()
        shoot(act, "$prefix-3-link-7-8")
        call(act, "openDetail", Strip(Kind.CH, 1))
        shoot(act, "$prefix-4-config")
        for ((i, name) in listOf("favoritos", "linecheck", "reloj", "corte").withIndex()) {
            act.debugScreen(name)
            idle()
            shoot(act, "$prefix-${5 + i}-$name")
        }
        App.store.incoming(chLinkAddr(7), 0)
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-xxhdpi")
    fun celular() = all("celular")

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tablet() = all("tablet")
}
