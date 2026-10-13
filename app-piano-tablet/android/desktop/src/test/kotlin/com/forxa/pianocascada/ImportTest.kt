package com.forxa.pianocascada

import com.forxa.pianocascada.importer.AudioDecoder
import com.forxa.pianocascada.importer.ScoreReader
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Las importaciones que usa la versión de escritorio: partitura en PDF y audio. */
class ImportTest {
    @Test
    fun leePartituraPdf() {
        val pdf = File("../app/src/androidTest/assets/prueba.pdf")
        assertTrue(pdf.exists(), "falta el PDF de prueba")
        val t0 = System.currentTimeMillis()
        val out = ScoreReader().read(pdf, "prueba", "Prueba", { p, m -> println("  %3d %% %s".format((p * 100).toInt(), m)) }, { false })
        println("PDF leído en ${(System.currentTimeMillis() - t0) / 1000} s: ${out.song.notes(3).size} notas, ${out.song.bars} compases, ${out.song.meter}, avisos=${out.warnings}")
        assertTrue(out.song.notes(3).size > 100, "muy pocas notas: ${out.song.notes(3).size}")
        assertTrue(out.song.bars >= 4)
    }

    @Test
    fun decodificaWav() {
        val sr = 44100
        val n = sr * 2
        val bytes = ByteArray(n * 2)
        for (i in 0 until n) {
            val v = (Math.sin(2 * Math.PI * 440 * i / sr) * 12000).toInt()
            bytes[2 * i] = v.toByte(); bytes[2 * i + 1] = (v shr 8).toByte()
        }
        val f = File.createTempFile("tono", ".wav")
        AudioSystem.write(AudioInputStream(bytes.inputStream(), AudioFormat(sr.toFloat(), 16, 1, true, false), n.toLong()), AudioFileFormat.Type.WAVE, f)
        val d = AudioDecoder.decode(f)
        assertEquals(sr, d.sampleRate)
        assertTrue(Math.abs(d.pcm.size - n) < 100, "longitud ${d.pcm.size}")
        assertTrue(d.pcm.maxOrNull()!! in 0.3f..0.4f)
        f.delete()
    }
}
