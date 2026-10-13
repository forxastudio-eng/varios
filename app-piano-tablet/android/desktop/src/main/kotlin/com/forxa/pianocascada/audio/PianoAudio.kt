package com.forxa.pianocascada.audio

import com.forxa.pianocascada.core.SequenceData
import com.forxa.pianocascada.engine.PianoEngine
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/** Identificadores de parámetros del motor (los mismos que en la tablet). */
object P {
    const val MASTER = 0
    const val EQ0 = 1
    const val REVERB_MIX = 6
    const val REVERB_SIZE = 7
    const val REVERB_DAMP = 8
    const val TUNING_A4 = 9
    const val DECAY_SCALE = 10
    const val BRIGHTNESS = 11
    const val RELEASE_SCALE = 12
    const val SPEAKER = 13
    const val VEL_EXP = 14
}

/**
 * Salida de audio del PC: un hilo que pide bloques al motor (el mismo que usa la tablet, en Kotlin) y los envía
 * a la tarjeta de sonido con javax.sound. Misma interfaz que en Android, así el resto de la app no cambia.
 */
class PianoAudio {
    private val engine = PianoEngine()
    @Volatile var ready = false
        private set
    @Volatile private var started = false
    private var line: SourceDataLine? = null
    private var thread: Thread? = null
    @Volatile var speed = 1f
        private set
    var lastError: String? = null
        private set

    fun init(): Boolean {
        if (ready) return true
        return try {
            val bytes = PianoAudio::class.java.getResourceAsStream("/piano.bin")?.use { it.readBytes() }
                ?: run { lastError = "falta el banco de sonidos del piano"; return false }
            if (!engine.loadBank(bytes)) { lastError = "banco de piano inválido"; return false }
            ready = true
            true
        } catch (e: Throwable) {
            lastError = e.toString()
            false
        }
    }

    fun start() {
        if (!ready || started) return
        val rate = 48000
        val fmt = AudioFormat(rate.toFloat(), 16, 2, true, false)
        // sin tarjeta de sonido la app sigue funcionando (en silencio) con un reloj propio
        val l = try {
            (AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, fmt)) as SourceDataLine).also { it.open(fmt, 1536 * 4); it.start() }
        } catch (e: Throwable) {
            lastError = "no se pudo abrir la salida de audio: ${e.message}"
            null
        }
        line = l
        engine.setSampleRate(rate.toFloat())
        started = true
        thread = Thread({ loop(l, rate) }, "audio-piano").also { it.isDaemon = true; it.priority = Thread.MAX_PRIORITY; it.start() }
    }

    private fun loop(l: SourceDataLine?, rate: Int) {
        val frames = 256
        val buf = FloatArray(frames * 2)
        val bytes = ByteArray(frames * 4)
        val queueBytes = 1536 * 4
        val start = System.nanoTime()
        var blocks = 0L
        while (started) {
            engine.render(buf, frames)
            for (i in 0 until frames * 2) {
                val v = (buf[i] * 32767f).toInt().coerceIn(-32768, 32767)
                bytes[2 * i] = v.toByte(); bytes[2 * i + 1] = (v shr 8).toByte()
            }
            if (l != null) {
                l.write(bytes, 0, bytes.size)
                // lo que queda por sonar en la tarjeta de sonido = latencia hasta oír el final de este bloque
                val queued = (l.bufferSize - l.available()).coerceIn(0, queueBytes)
                engine.latencyMs = queued / 4f / rate * 1000f + 6f
            } else {
                val next = start + (++blocks) * frames * 1_000_000_000L / rate
                val wait = (next - System.nanoTime()) / 1_000_000L
                if (wait > 0) Thread.sleep(wait)
                engine.latencyMs = 0f
            }
        }
    }

    fun stop() {
        if (!started) return
        engine.seqPlay(false)
        engine.allNotesOff()
        started = false
        runCatching { thread?.join(500) }
        line?.let { runCatching { it.stop(); it.flush(); it.close() } }
        line = null
        thread = null
    }

    val running: Boolean get() = started
    val latencyMs: Float get() = engine.latencyMs

    // ---- tocar
    fun noteOn(pitch: Int, velocity: Int) = engine.noteOn(pitch, velocity)
    fun noteOff(pitch: Int) = engine.noteOff(pitch)
    fun pedal(down: Boolean) = engine.setPedal(down)
    fun allOff() = engine.allNotesOff()
    fun setParam(id: Int, value: Float) = engine.setParam(id, value)

    // ---- secuenciador
    fun load(seq: SequenceData) = engine.loadSequence(seq.keys, seq.vels, seq.hands, seq.startMs, seq.durMs, seq.size)

    fun play(play: Boolean) {
        engine.seqPlay(play)
        if (!play) engine.seqStopNotes()
    }

    fun seek(ms: Double) = engine.seqSeek(ms)

    fun setSpeed(s: Float) {
        speed = s
        engine.seqSpeed(s)
    }

    fun loop(startMs: Double, endMs: Double, on: Boolean) = engine.seqLoop(startMs, endMs, on)
    fun gate(ms: Double) = engine.seqGate(ms)
    fun handGain(hand: Int, gain: Float) = engine.setHandGain(hand, gain)
    fun stopSequenceNotes() = engine.seqStopNotes()
    val isPlaying: Boolean get() = engine.isPlaying
    val isWaiting: Boolean get() = engine.isWaiting

    /** Posición de la canción que se está OYENDO ahora (ms a velocidad 100 %), compensando la latencia de salida. */
    fun heardPositionMs(): Double {
        val p = engine.positionMs
        if (!engine.isPlaying || engine.isWaiting) return p
        val dtMs = (System.nanoTime() - engine.stampNs) / 1e6
        return p + (dtMs - engine.latencyMs.toDouble()).coerceIn(-300.0, 300.0) * speed
    }

    fun release() {
        stop()
        ready = false
    }
}
