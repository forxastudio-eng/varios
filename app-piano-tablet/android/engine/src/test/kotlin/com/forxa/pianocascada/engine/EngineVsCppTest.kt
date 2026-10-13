package com.forxa.pianocascada.engine

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renderiza el mismo guion que el motor de C++ (el de Android) y compara el audio.
 * Se activa con PIANO_REF_RAW (audio de referencia), PIANO_BANK (piano.bin) y PIANO_SCRIPT (guion).
 */
class EngineVsCppTest {
    private fun runScript(bank: ByteArray, script: List<String>): FloatArray {
        val e = PianoEngine()
        e.setSampleRate(48000f)
        check(e.loadBank(bank))
        val out = ArrayList<FloatArray>()
        var i = 0
        while (i < script.size) {
            val parts = script[i++].trim().split(Regex("\\s+"))
            if (parts.isEmpty() || parts[0].isEmpty() || parts[0].startsWith("#")) continue
            when (parts[0]) {
                "param" -> e.setParam(parts[1].toInt(), parts[2].toFloat())
                "noteon" -> e.noteOn(parts[1].toInt(), parts[2].toInt())
                "noteoff" -> e.noteOff(parts[1].toInt())
                "pedal" -> e.setPedal(parts[1] != "0")
                "alloff" -> e.allNotesOff()
                "seqload" -> {
                    val n = parts[1].toInt()
                    val k = IntArray(n); val v = IntArray(n); val h = IntArray(n); val s = DoubleArray(n); val d = DoubleArray(n)
                    for (j in 0 until n) {
                        val r = script[i++].trim().split(Regex("\\s+"))
                        k[j] = r[0].toInt(); v[j] = r[1].toInt(); h[j] = r[2].toInt(); s[j] = r[3].toDouble(); d[j] = r[4].toDouble()
                    }
                    e.loadSequence(k, v, h, s, d, n)
                }
                "seqplay" -> e.seqPlay(parts[1] != "0")
                "seqseek" -> e.seqSeek(parts[1].toDouble())
                "seqspeed" -> e.seqSpeed(parts[1].toFloat())
                "seqgate" -> e.seqGate(parts[1].toDouble())
                "seqloop" -> e.seqLoop(parts[1].toDouble(), parts[2].toDouble(), parts[3] != "0")
                "handgain" -> e.setHandGain(parts[1].toInt(), parts[2].toFloat())
                "seqstop" -> e.seqStopNotes()
                "render" -> {
                    val frames = parts[1].toInt()
                    val buf = FloatArray(frames * 2)
                    var done = 0
                    val tmp = FloatArray(480 * 2)
                    while (done < frames) {
                        val n = minOf(480, frames - done)
                        e.render(tmp, n)
                        System.arraycopy(tmp, 0, buf, done * 2, n * 2)
                        done += n
                    }
                    out.add(buf)
                }
            }
        }
        val all = FloatArray(out.sumOf { it.size })
        var o = 0
        for (b in out) { System.arraycopy(b, 0, all, o, b.size); o += b.size }
        return all
    }

    @Test fun kotlinEngineSoundsLikeTheCppEngine() {
        val refPath = System.getenv("PIANO_REF_RAW"); val bankPath = System.getenv("PIANO_BANK"); val scriptPath = System.getenv("PIANO_SCRIPT")
        assumeTrue(refPath != null && bankPath != null && scriptPath != null)
        val ref = ByteBuffer.wrap(File(refPath).readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().let { fb -> FloatArray(fb.remaining()).also { fb.get(it) } }
        val got = runScript(File(bankPath).readBytes(), File(scriptPath).readLines())
        println("muestras: C++=${ref.size} Kotlin=${got.size}")
        assertTrue(ref.size == got.size, "distinta longitud")
        var errSq = 0.0; var refSq = 0.0; var maxErr = 0.0
        for (i in ref.indices) {
            val d = (ref[i] - got[i]).toDouble()
            errSq += d * d; refSq += ref[i].toDouble() * ref[i]
            maxErr = maxOf(maxErr, abs(d))
        }
        val rms = sqrt(refSq / ref.size)
        val snr = 10 * log10(refSq / maxOf(errSq, 1e-30))
        println("RMS referencia %.5f, error máximo %.6f, relación señal/error %.1f dB".format(rms, maxErr, snr))
        assertTrue(snr > 60.0, "el motor de Kotlin se aparta del de C++ (relación señal/error $snr dB)")
    }
}
