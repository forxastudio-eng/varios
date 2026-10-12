package com.forxa.pianocascada.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.forxa.pianocascada.core.SequenceData

/** Identificadores de parámetros del motor (deben coincidir con engine.h). */
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

/** Capa de alto nivel sobre el motor nativo: arranque, parámetros, secuenciador y reloj de audio. */
class PianoAudio(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    var ready = false
        private set
    private var started = false
    var speed = 1f
        private set

    private val monitor = object : Runnable {
        override fun run() {
            if (started) {
                NativeEngine.nUpdateLatency()
                if (NativeEngine.nNeedsRestart()) {
                    Log.i(TAG, "reiniciando salida de audio")
                    NativeEngine.nStart()
                }
                handler.postDelayed(this, 250)
            }
        }
    }

    fun init(): Boolean {
        if (ready) return true
        return try {
            NativeEngine.nCreate()
            val bytes = context.assets.open("piano.bin").use { it.readBytes() }
            if (!NativeEngine.nLoadBank(bytes)) {
                Log.e(TAG, "banco de piano inválido")
                return false
            }
            ready = true
            true
        } catch (e: Throwable) {
            Log.e(TAG, "no se pudo iniciar el audio", e)
            false
        }
    }

    fun start() {
        if (!ready) return
        if (!started) {
            started = NativeEngine.nStart()
            if (started) handler.post(monitor)
        }
    }

    fun stop() {
        if (started) {
            NativeEngine.nSeqPlay(false)
            NativeEngine.nAllOff()
            NativeEngine.nStop()
            started = false
            handler.removeCallbacks(monitor)
        }
    }

    val running: Boolean get() = started
    val latencyMs: Float get() = NativeEngine.nLatencyMs()

    // ---- tocar
    fun noteOn(pitch: Int, velocity: Int) = NativeEngine.nNoteOn(pitch, velocity)
    fun noteOff(pitch: Int) = NativeEngine.nNoteOff(pitch)
    fun pedal(down: Boolean) = NativeEngine.nPedal(down)
    fun allOff() = NativeEngine.nAllOff()
    fun setParam(id: Int, value: Float) = NativeEngine.nSetParam(id, value)

    // ---- secuenciador
    fun load(seq: SequenceData) {
        NativeEngine.nLoadSeq(seq.keys, seq.vels, seq.hands, seq.startMs, seq.durMs, seq.size)
    }

    fun play(play: Boolean) {
        NativeEngine.nSeqPlay(play)
        if (!play) NativeEngine.nSeqStopNotes()
    }

    fun seek(ms: Double) = NativeEngine.nSeqSeek(ms)

    fun setSpeed(s: Float) {
        speed = s
        NativeEngine.nSeqSpeed(s)
    }

    fun loop(startMs: Double, endMs: Double, on: Boolean) = NativeEngine.nSeqLoop(startMs, endMs, on)
    fun gate(ms: Double) = NativeEngine.nSeqGate(ms)
    fun handGain(hand: Int, gain: Float) = NativeEngine.nHandGain(hand, gain)
    fun stopSequenceNotes() = NativeEngine.nSeqStopNotes()
    val isPlaying: Boolean get() = NativeEngine.nPlaying()
    val isWaiting: Boolean get() = NativeEngine.nWaiting()

    /**
     * Posición de la canción que se está OYENDO ahora (ms a velocidad 100 %): la posición del motor al final del
     * último bloque, más el tiempo transcurrido, menos la latencia de salida. Así lo que se ve coincide con lo que suena.
     */
    fun heardPositionMs(): Double {
        val p = NativeEngine.nPositionMs()
        if (!NativeEngine.nPlaying() || NativeEngine.nWaiting()) return p
        val dtMs = (System.nanoTime() - NativeEngine.nStampNs()) / 1e6
        val lat = NativeEngine.nLatencyMs().toDouble()
        return p + (dtMs - lat).coerceIn(-300.0, 300.0) * speed
    }

    fun release() {
        stop()
        NativeEngine.nDestroy()
        ready = false
    }

    companion object { private const val TAG = "PianoAudio" }
}
