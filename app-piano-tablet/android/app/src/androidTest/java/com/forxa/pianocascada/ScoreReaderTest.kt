package com.forxa.pianocascada

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.forxa.pianocascada.importer.ScoreReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Prueba en un dispositivo (emulador) de que el lector de partituras funciona dentro de Android: PDF → notas. */
@RunWith(AndroidJUnit4::class)
class ScoreReaderTest {
    @Test fun readsAPdfPageOffline() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val ctx = inst.targetContext
        val pdf = File(ctx.cacheDir, "prueba.pdf")
        inst.context.assets.open("prueba.pdf").use { i -> pdf.outputStream().use { i.copyTo(it) } }
        val t0 = System.nanoTime()
        val out = ScoreReader(ctx).read(Uri.fromFile(pdf), "prueba", "Prueba", { p, m -> Log.i("OMR", "${(p * 100).toInt()} % $m") }, { false })
        Log.i("OMR", "lectura: ${(System.nanoTime() - t0) / 1e9} s, ${out.song.rh.size} notas derecha, ${out.song.lh.size} izquierda, avisos ${out.warnings}")
        assertEquals(1, out.pages)
        assertEquals("4/4", out.song.meter)
        assertTrue("pocas notas: ${out.song.rh.size + out.song.lh.size}", out.song.rh.size + out.song.lh.size > 150)
    }
}
