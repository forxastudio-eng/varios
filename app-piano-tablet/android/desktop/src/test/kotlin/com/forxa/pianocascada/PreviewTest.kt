package com.forxa.pianocascada

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.forxa.pianocascada.core.PracticeMode
import com.forxa.pianocascada.data.LayoutMode
import com.forxa.pianocascada.piano.HandsMode
import com.forxa.pianocascada.piano.PracticeController
import com.forxa.pianocascada.ui.screens.PracticeParams
import com.forxa.pianocascada.ui.theme.PianoTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Dibuja las pantallas de la app sin ventana (Compose sin cabeza) y guarda capturas PNG en build/preview.
 * Sirve para comprobar de verdad que la interfaz arranca y se ve bien, también en el servidor de compilación.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PreviewTest {
    private val out = File("build/preview").also { it.mkdirs() }

    private fun shoot(app: PianoApp, name: String, start: Screen?, seconds: Double = 0.4, w: Int = 1366, h: Int = 860, afterFirst: () -> Unit = {}) {
        val scene = ImageComposeScene(w, h, Density(1f)) { PianoTheme { AppRoot(app, start) } }
        try {
            var t = 0L
            fun frame() { scene.render(t); t += 16_000_000L }
            repeat(3) { frame() }
            afterFirst()
            val end = System.nanoTime() + (seconds * 1e9).toLong()
            while (System.nanoTime() < end) { frame(); Thread.sleep(16) }
            frame()
            val img = scene.render(t)
            val bytes = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "$name.png").writeBytes(bytes)
            assertTrue(bytes.size > 2000, "captura vacía: $name")
        } finally { scene.close() }
    }

    @Test
    fun capturas() {
        val dir = File("build/preview-home").also { it.mkdirs() }
        System.setProperty("user.home", dir.absolutePath)
        val app = PianoApp()
        app.audio.start()
        val first = app.songs.songs.value.firstOrNull() ?: error("no hay canciones")
        shoot(app, "01-inicio", Screen.Home)
        shoot(app, "02-biblioteca", Screen.Library)
        shoot(app, "03-detalle", Screen.Detail(first.id))
        shoot(app, "04-onboarding", Screen.Onboarding)
        shoot(app, "05-ajustes", Screen.Settings)
        shoot(app, "06-importar", Screen.Import)
        for ((tag, size) in listOf("1366" to (1366 to 860), "1920" to (1920 to 1080))) {
            var ctrl: PracticeController? = null
            app.onPracticeController = { ctrl = it }
            val params = PracticeParams(first.id, 1, HandsMode.BOTH, PracticeMode.LISTEN, 1f, false, LayoutMode.FULL_88)
            shoot(app, "07-practica-$tag", Screen.Practice(params), seconds = 7.0, w = size.first, h = size.second, afterFirst = { ctrl?.play() })
        }
        app.audio.stop()
    }
}
