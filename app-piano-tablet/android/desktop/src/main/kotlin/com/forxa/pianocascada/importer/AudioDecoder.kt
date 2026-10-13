package com.forxa.pianocascada.importer

import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/** Decodifica MP3, WAV, AIFF… a PCM mono en coma flotante (MP3 con la biblioteca JLayer incluida en la app). */
object AudioDecoder {
    class Decoded(val pcm: FloatArray, val sampleRate: Int) {
        val seconds: Double get() = pcm.size.toDouble() / sampleRate
    }

    class TooLong(val seconds: Int) : Exception("El audio dura más de ${seconds / 60} minutos")

    fun decode(file: File, maxSeconds: Int = 15 * 60, onProgress: (Float) -> Unit = {}, cancelled: () -> Boolean = { false }): Decoded {
        val raw: AudioInputStream = try {
            AudioSystem.getAudioInputStream(file)
        } catch (e: javax.sound.sampled.UnsupportedAudioFileException) {
            throw IllegalArgumentException("Este formato de audio no se puede leer en el ordenador. Usa MP3 o WAV (si tienes un M4A, conviértelo a MP3).")
        }
        raw.use { src ->
            val base = src.format
            val sr = base.sampleRate.toInt().takeIf { it > 0 } ?: 44100
            val ch = base.channels.coerceAtLeast(1)
            val pcmFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sr.toFloat(), 16, ch, ch * 2, sr.toFloat(), false)
            val ais = if (base.encoding == AudioFormat.Encoding.PCM_SIGNED && base.sampleSizeInBits == 16 && !base.isBigEndian) src
            else AudioSystem.getAudioInputStream(pcmFormat, src)
            val total = if (src.frameLength > 0) src.frameLength else -1L
            var out = FloatArray(if (total > 0) total.toInt() else sr * 60)
            var n = 0
            val buf = ByteArray(ch * 2 * 8192)
            while (true) {
                if (cancelled()) throw InterruptedException("cancelado")
                val r = ais.read(buf, 0, buf.size)
                if (r <= 0) break
                val frames = r / (ch * 2)
                if (n + frames > out.size) out = out.copyOf(maxOf(out.size * 2, n + frames))
                var p = 0
                for (f in 0 until frames) {
                    var sum = 0
                    for (c in 0 until ch) { sum += (buf[p].toInt() and 0xFF) or (buf[p + 1].toInt() shl 8); p += 2 }
                    out[n++] = sum / (ch * 32768f)
                }
                if (n > maxSeconds * sr) throw TooLong(maxSeconds)
                if (total > 0) onProgress((n.toFloat() / total).coerceIn(0f, 1f))
            }
            return Decoded(out.copyOf(n), sr)
        }
    }
}
