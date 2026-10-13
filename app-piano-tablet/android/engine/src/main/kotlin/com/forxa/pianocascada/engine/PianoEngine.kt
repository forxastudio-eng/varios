package com.forxa.pianocascada.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Motor de piano de la versión de escritorio: sampler multi-zona con bucles, pedal, ecualizador, reverb, limitador y
 * secuenciador con reloj de audio. Traducción a Kotlin de engine.cpp (el de Android); los dos se comparan en las pruebas.
 */

class Zone(
    val keyLo: Int, val keyHi: Int, val velLo: Int, val velHi: Int, val root: Int, val fineCents: Int,
    val sampleRate: Int, val offset: Int, val frames: Int, val loopStart: Int, var loopEnd: Int,
    val decay100: Float, val gain: Float,
)

class SampleBank(val zones: List<Zone>, val pcm: ShortArray) {
    fun find(key: Int, vel: Int): Zone? {
        var best: Zone? = null
        var bestDist = 1 shl 20
        for (z in zones) {
            if (key >= z.keyLo && key <= z.keyHi && vel >= z.velLo && vel <= z.velHi) return z
            val dist = if (key < z.keyLo) z.keyLo - key else if (key > z.keyHi) key - z.keyHi else 0
            if (dist < bestDist) { bestDist = dist; best = z }
        }
        return best
    }

    companion object {
        fun load(data: ByteArray): SampleBank? {
            if (data.size < 12 || String(data, 0, 4, Charsets.US_ASCII) != "PNO1") return null
            val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val nz = bb.getInt(4); val total = bb.getInt(8)
            val recSize = 40
            if (nz <= 0 || nz > 512) return null
            val pcmOff = 12 + nz * recSize
            if (data.size.toLong() < pcmOff.toLong() + total.toLong() * 4) return null
            val zs = ArrayList<Zone>(nz)
            for (i in 0 until nz) {
                val r = 12 + i * recSize
                val z = Zone(
                    keyLo = data[r].toInt() and 0xFF, keyHi = data[r + 1].toInt() and 0xFF,
                    velLo = data[r + 2].toInt() and 0xFF, velHi = data[r + 3].toInt() and 0xFF, root = data[r + 4].toInt() and 0xFF,
                    fineCents = bb.getShort(r + 6).toInt(), sampleRate = bb.getInt(r + 8), offset = bb.getInt(r + 12),
                    frames = bb.getInt(r + 16), loopStart = bb.getInt(r + 20), loopEnd = bb.getInt(r + 24),
                    decay100 = bb.getFloat(r + 28), gain = bb.getFloat(r + 32),
                )
                if (z.offset.toLong() + z.frames > total || z.frames < 8) return null
                if (z.loopEnd > z.frames) z.loopEnd = z.frames
                zs.add(z)
            }
            val pcm = ShortArray(total * 2)
            bb.position(pcmOff)
            bb.asShortBuffer().get(pcm)
            return SampleBank(zs, pcm)
        }
    }
}

/** Identificadores de parámetros (los mismos que engine.h). */
object Param {
    const val MASTER = 0
    const val EQ0 = 1
    const val EQ1 = 2
    const val EQ2 = 3
    const val EQ3 = 4
    const val EQ4 = 5
    const val REVERB_MIX = 6
    const val REVERB_SIZE = 7
    const val REVERB_DAMP = 8
    const val TUNING_A4 = 9
    const val DECAY_SCALE = 10
    const val BRIGHTNESS = 11
    const val RELEASE_SCALE = 12
    const val SPEAKER = 13
    const val VEL_EXP = 14
    const val COUNT = 15
}

private class SeqEvent(val timeMs: Double, val on: Boolean, val key: Int, val vel: Int, val hand: Int)
private class Sequence(val ev: List<SeqEvent>)

private sealed interface Cmd {
    class NoteOn(val key: Int, val vel: Int) : Cmd
    class NoteOff(val key: Int) : Cmd
    class Pedal(val down: Boolean) : Cmd
    object AllOff : Cmd
    class SetParam(val id: Int, val v: Float) : Cmd
    class SeqSet(val seq: Sequence) : Cmd
    class SeqPlay(val play: Boolean) : Cmd
    class SeqSeek(val ms: Double) : Cmd
    class SeqSpeed(val s: Float) : Cmd
    class SeqLoop(val a: Double, val b: Double, val on: Boolean) : Cmd
    class SeqGate(val ms: Double) : Cmd
    class HandGain(val hand: Int, val g: Float) : Cmd
    object SeqNotesOff : Cmd
}

class PianoEngine {
    private class Voice {
        var active = false; var releasing = false; var sustained = false
        var key = -1; var tag = 0       // tag 0 = usuario, 1 = secuenciador
        var z: Zone? = null
        var pos = 0.0; var inc = 1.0
        var amp = 0f; var decayMul = 1f; var relMul = 1f; var vgain = 1f
        var lpA = 1f; var lpL = 0f; var lpR = 0f
        var age = 0L
    }

    companion object {
        const val MAX_VOICES = 96
        const val BLOCK = 256
        private fun hermite(y0: Float, y1: Float, y2: Float, y3: Float, t: Float): Float {
            val c1 = 0.5f * (y2 - y0)
            val c2 = y0 - 2.5f * y1 + 2.0f * y2 - 0.5f * y3
            val c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2)
            return ((c3 * t + c2) * t + c1) * t + y1
        }
    }

    private var bank: SampleBank? = null
    @Volatile private var bankReady = false
    private var sr = 48000f
    private val voices = Array(MAX_VOICES) { Voice() }
    private var ageCounter = 0L
    private var pedal = false

    private val params = FloatArray(Param.COUNT)
    private var eqDirty = true
    private var revDirty = true
    private var masterCur = 0.5f
    private val eq = Array(2) { Array(5) { Biquad() } }
    private val hpf = Array(2) { Biquad() }
    private val presence = Array(2) { Biquad() }
    private val reverb = Reverb()
    private val limiter = Limiter()
    private val dryL = FloatArray(BLOCK); private val dryR = FloatArray(BLOCK)
    private val wetL = FloatArray(BLOCK); private val wetR = FloatArray(BLOCK)

    // secuenciador
    private var seq: Sequence? = null
    private var idx = 0
    private var playing = false
    private var pos = 0.0
    private var gate = 1e18
    private var loopStart = 0.0; private var loopEnd = 0.0
    private var loopOn = false
    private var speed = 1.0f
    private val handGain = floatArrayOf(1f, 1f)
    private var waiting = false

    // salidas leídas desde otros hilos
    @Volatile var positionMs = 0.0; private set
    @Volatile var stampNs = 0L; private set
    @Volatile var isPlaying = false; private set
    @Volatile var isWaiting = false; private set
    @Volatile var activeVoices = 0; private set
    @Volatile var latencyMs = 0f

    private val queue = ConcurrentLinkedQueue<Cmd>()

    init {
        params[Param.MASTER] = 0.8f
        params[Param.REVERB_MIX] = 0.18f
        params[Param.REVERB_SIZE] = 0.5f
        params[Param.REVERB_DAMP] = 0.4f
        params[Param.TUNING_A4] = 440.0f
        params[Param.DECAY_SCALE] = 1.0f
        params[Param.BRIGHTNESS] = 1.0f
        params[Param.RELEASE_SCALE] = 1.0f
        params[Param.VEL_EXP] = 1.3f
        setSampleRate(48000f)
    }

    val sampleRate: Float get() = sr
    val ready: Boolean get() = bankReady

    fun loadBank(data: ByteArray): Boolean {
        val b = SampleBank.load(data) ?: return false
        bankReady = false
        bank = b
        for (v in voices) v.active = false
        bankReady = true
        return true
    }

    fun setSampleRate(rate: Float) {
        sr = rate
        reverb.init(rate)
        limiter.init(rate)
        eqDirty = true; revDirty = true
        for (v in voices) v.active = false
    }

    // ---- API de control (cualquier hilo)
    fun noteOn(key: Int, vel: Int) { queue.add(Cmd.NoteOn(key, vel)) }
    fun noteOff(key: Int) { queue.add(Cmd.NoteOff(key)) }
    fun setPedal(down: Boolean) { queue.add(Cmd.Pedal(down)) }
    fun allNotesOff() { queue.add(Cmd.AllOff) }
    fun setParam(id: Int, v: Float) { queue.add(Cmd.SetParam(id, v)) }
    fun seqPlay(play: Boolean) { queue.add(Cmd.SeqPlay(play)) }
    fun seqSeek(ms: Double) { queue.add(Cmd.SeqSeek(ms)) }
    fun seqSpeed(s: Float) { queue.add(Cmd.SeqSpeed(s)) }
    fun seqLoop(a: Double, b: Double, on: Boolean) { queue.add(Cmd.SeqLoop(a, b, on)) }
    fun seqGate(ms: Double) { queue.add(Cmd.SeqGate(ms)) }
    fun setHandGain(hand: Int, g: Float) { queue.add(Cmd.HandGain(hand, g)) }
    fun seqStopNotes() { queue.add(Cmd.SeqNotesOff) }

    fun loadSequence(key: IntArray, vel: IntArray, hand: IntArray, startMs: DoubleArray, durMs: DoubleArray, n: Int) {
        val ev = ArrayList<SeqEvent>(n * 2)
        for (i in 0 until n) {
            val h = hand[i] and 1
            val k = key[i] and 0xFF
            val v = vel[i].coerceIn(1, 127)
            ev.add(SeqEvent(startMs[i], true, k, v, h))
            ev.add(SeqEvent(startMs[i] + max(30.0, durMs[i]), false, k, 0, h))
        }
        // por tiempo; en empate, «off» antes que «on» (orden estable)
        ev.sortWith { a, b -> if (a.timeMs != b.timeMs) a.timeMs.compareTo(b.timeMs) else a.on.compareTo(b.on) }
        queue.add(Cmd.SeqSet(Sequence(ev)))
    }

    // ---- comandos
    private fun drainCommands() {
        while (true) execute(queue.poll() ?: break)
    }

    private fun execute(c: Cmd) {
        when (c) {
            is Cmd.NoteOn -> startNote(c.key, c.vel, 0)
            is Cmd.NoteOff -> for (v in voices) if (v.active && v.key == c.key && v.tag == 0 && !v.releasing) {
                if (pedal) v.sustained = true else startRelease(v, false)
            }
            is Cmd.Pedal -> {
                pedal = c.down
                if (!pedal) for (v in voices) if (v.active && v.sustained && !v.releasing) startRelease(v, false)
            }
            Cmd.AllOff -> for (v in voices) if (v.active && !v.releasing) startRelease(v, true)
            is Cmd.SetParam -> applyParam(c.id, c.v)
            is Cmd.SeqSet -> { seq = c.seq; playing = false; seekInternal(0.0) }
            is Cmd.SeqPlay -> playing = c.play && seq != null
            is Cmd.SeqSeek -> seekInternal(c.ms)
            is Cmd.SeqSpeed -> speed = c.s.coerceIn(0.1f, 4.0f)
            is Cmd.SeqLoop -> { loopStart = c.a; loopEnd = c.b; loopOn = c.on && c.b > c.a + 50.0 }
            is Cmd.SeqGate -> gate = c.ms
            is Cmd.HandGain -> handGain[c.hand and 1] = c.g
            Cmd.SeqNotesOff -> for (v in voices) if (v.active && v.tag == 1 && !v.releasing) startRelease(v, false)
        }
    }

    private fun applyParam(id: Int, v: Float) {
        if (id < 0 || id >= Param.COUNT) return
        params[id] = v
        if (id in Param.EQ0..Param.EQ4) eqDirty = true
        if (id == Param.SPEAKER) eqDirty = true
        if (id == Param.REVERB_SIZE || id == Param.REVERB_DAMP) revDirty = true
    }

    private fun updateFilters() {
        if (eqDirty) {
            for (ch in 0 until 2) {
                eq[ch][0].setLowShelf(sr, 80.0f, params[Param.EQ0])
                eq[ch][1].setPeaking(sr, 250.0f, 0.9f, params[Param.EQ1])
                eq[ch][2].setPeaking(sr, 1000.0f, 0.9f, params[Param.EQ2])
                eq[ch][3].setPeaking(sr, 4000.0f, 0.9f, params[Param.EQ3])
                eq[ch][4].setHighShelf(sr, min(10000.0f, sr * 0.4f), params[Param.EQ4])
                val spk = params[Param.SPEAKER] > 0.5f
                hpf[ch].setHighpass(sr, if (spk) 85.0f else 28.0f, 0.707f)
                if (spk) presence[ch].setPeaking(sr, 2800.0f, 0.8f, 2.5f) else presence[ch].setIdentity()
            }
            eqDirty = false
        }
        if (revDirty) {
            reverb.setParams(params[Param.REVERB_SIZE], params[Param.REVERB_DAMP])
            revDirty = false
        }
    }

    // ---- voces
    private fun allocVoice(): Voice {
        for (v in voices) if (!v.active) return v
        var best: Voice? = null
        var bestAmp = 1e9f
        for (v in voices) if (v.releasing && v.amp < bestAmp) { bestAmp = v.amp; best = v }
        if (best != null) return best
        var oldest = Long.MAX_VALUE
        for (v in voices) if (v.age < oldest) { oldest = v.age; best = v }
        return best!!
    }

    private fun startNote(key: Int, velIn: Int, tag: Int) {
        val b = bank
        if (!bankReady || b == null) return
        if (key < 0 || key > 127 || velIn <= 0) return
        val vel = min(velIn, 127)
        val z = b.find(key, vel) ?: return
        for (v in voices) if (v.active && v.key == key && v.tag == tag && !v.releasing) startRelease(v, true)
        val v = allocVoice()
        val tuneCents = 1200.0 * ln(max(400.0f, params[Param.TUNING_A4]) / 440.0) / ln(2.0)
        val semis = (key - z.root) + (z.fineCents + tuneCents) / 100.0
        v.active = true; v.releasing = false; v.sustained = false
        v.key = key; v.tag = tag; v.z = z; v.pos = 0.0
        v.inc = 2.0.pow(semis / 12.0) * z.sampleRate / sr
        val vn = vel / 127.0f
        v.vgain = z.gain * 0.9f * vn.toDouble().pow(max(0.5f, params[Param.VEL_EXP]).toDouble()).toFloat()
        v.amp = 1.0f
        val tau = max(0.5f, z.decay100 * max(0.2f, params[Param.DECAY_SCALE]))
        v.decayMul = 10.0.pow((-5.0f / (tau * sr)).toDouble()).toFloat()
        v.relMul = 1.0f
        var fc = (1800.0f + (19000.0f - 1800.0f) * vn.toDouble().pow(1.2).toFloat()) * max(0.2f, params[Param.BRIGHTNESS])
        fc = min(fc, sr * 0.45f)
        v.lpA = 1.0f - exp(-2.0f * PI_F * fc / sr)
        v.lpL = 0f; v.lpR = 0f
        v.age = ++ageCounter
    }

    private fun startRelease(v: Voice, fast: Boolean) {
        v.releasing = true; v.sustained = false
        val t: Float = if (fast) 0.03f else {
            val k = ((v.key - 21) / 87.0f).coerceIn(0f, 1f)
            (1.1f + (0.28f - 1.1f) * k) * max(0.2f, params[Param.RELEASE_SCALE])
        }
        v.relMul = 10.0.pow((-5.0f / (t * sr)).toDouble()).toFloat()
    }

    private fun renderVoice(v: Voice, l: FloatArray, r: FloatArray, n: Int) {
        val z = v.z!!
        val pcm = bank!!.pcm
        val base = z.offset * 2
        val loop = z.loopEnd > z.loopStart + 16
        val loopLen = (z.loopEnd - z.loopStart).toLong()
        val last = (z.frames - 1).toLong()
        val k = 1.0f / 32768.0f
        for (i in 0 until n) {
            if (loop) {
                while (v.pos >= z.loopEnd.toDouble()) v.pos -= loopLen.toDouble()
            } else if (v.pos >= (last - 1).toDouble()) {
                v.active = false
                return
            }
            val i1 = v.pos.toLong()
            val t = (v.pos - i1.toDouble()).toFloat()
            var i0 = i1 - 1; var i2 = i1 + 1; var i3 = i1 + 2
            if (loop) {
                if (i2 >= z.loopEnd) i2 -= loopLen
                if (i3 >= z.loopEnd) i3 -= loopLen
            } else {
                if (i2 > last) i2 = last
                if (i3 > last) i3 = last
            }
            if (i0 < 0) i0 = 0
            val xl = hermite(pcm[base + (i0 * 2).toInt()].toFloat(), pcm[base + (i1 * 2).toInt()].toFloat(),
                pcm[base + (i2 * 2).toInt()].toFloat(), pcm[base + (i3 * 2).toInt()].toFloat(), t) * k
            val xr = hermite(pcm[base + (i0 * 2 + 1).toInt()].toFloat(), pcm[base + (i1 * 2 + 1).toInt()].toFloat(),
                pcm[base + (i2 * 2 + 1).toInt()].toFloat(), pcm[base + (i3 * 2 + 1).toInt()].toFloat(), t) * k
            v.lpL += v.lpA * (xl - v.lpL)
            v.lpR += v.lpA * (xr - v.lpR)
            val g = v.amp * v.vgain
            l[i] += v.lpL * g
            r[i] += v.lpR * g
            v.amp *= if (v.releasing) v.relMul else v.decayMul
            v.pos += v.inc
            if (v.amp < 1e-5f) { v.active = false; return }
        }
    }

    private fun renderChunk(out: FloatArray, outOff: Int, n: Int) {
        updateFilters()
        java.util.Arrays.fill(dryL, 0, n, 0f)
        java.util.Arrays.fill(dryR, 0, n, 0f)
        var active = 0
        for (v in voices) if (v.active) { renderVoice(v, dryL, dryR, n); active++ }
        activeVoices = active

        for (i in 0 until n) {
            var l = dryL[i]; var r = dryR[i]
            for (b in 0 until 5) { l = eq[0][b].process(l); r = eq[1][b].process(r) }
            l = hpf[0].process(l); r = hpf[1].process(r)
            l = presence[0].process(l); r = presence[1].process(r)
            dryL[i] = l; dryR[i] = r
        }
        val mix = params[Param.REVERB_MIX].coerceIn(0f, 1f)
        if (mix > 0.001f) reverb.process(dryL, dryR, wetL, wetR, n)
        else { java.util.Arrays.fill(wetL, 0, n, 0f); java.util.Arrays.fill(wetR, 0, n, 0f) }
        var target = params[Param.MASTER].coerceIn(0f, 1f)
        target = target * target * 0.9f
        val step = (target - masterCur) / n.toFloat()
        for (i in 0 until n) {
            masterCur += step
            val l = (dryL[i] + wetL[i] * mix) * masterCur
            val r = (dryR[i] + wetR[i] * mix) * masterCur
            limiter.process(l, r)
            out[outOff + 2 * i] = limiter.outL
            out[outOff + 2 * i + 1] = limiter.outR
        }
    }

    // ---- secuenciador
    private fun seekInternal(ms: Double) {
        pos = max(0.0, ms)
        val s = seq
        idx = if (s != null) {
            var lo = 0; var hi = s.ev.size
            val t = pos - 1e-6
            while (lo < hi) { val mid = (lo + hi) ushr 1; if (s.ev[mid].timeMs < t) lo = mid + 1 else hi = mid }
            lo
        } else 0
        for (v in voices) if (v.active && v.tag == 1 && !v.releasing) startRelease(v, true)
    }

    private fun dispatchDue() {
        val s = seq ?: return
        while (idx < s.ev.size && s.ev[idx].timeMs <= pos + 1e-6) {
            val e = s.ev[idx++]
            val g = handGain[e.hand and 1]
            if (g <= 0.0f) continue
            if (e.on) {
                val vel = (e.vel * g).roundToInt().coerceIn(1, 127)
                startNote(e.key, vel, 1)
            } else {
                for (v in voices) if (v.active && v.key == e.key && v.tag == 1 && !v.releasing) startRelease(v, false)
            }
        }
    }

    /** Hilo de audio: escribe [frames] fotogramas estéreo intercalados en [out]. */
    fun render(out: FloatArray, frames: Int) {
        drainCommands()
        if (!bankReady) { java.util.Arrays.fill(out, 0, frames * 2, 0f); return }
        var done = 0
        while (done < frames) {
            var chunk = min(frames - done, BLOCK)
            val msPerFrame = 1000.0 * speed / sr
            val s = seq
            if (playing && s != null) {
                if (pos < gate - 1e-9) dispatchDue()
                var target = 1e18
                if (idx < s.ev.size) target = s.ev[idx].timeMs
                if (loopOn && loopEnd < target) target = loopEnd
                if (gate < target) target = gate
                if (target < 1e17 && pos < gate - 1e-9) {
                    var fr = ceil((target - pos) / msPerFrame)
                    if (fr < 1) fr = 1.0
                    if (fr < chunk) chunk = fr.toInt()
                }
            }
            renderChunk(out, done * 2, chunk)
            if (playing && s != null) {
                if (pos >= gate - 1e-9) {
                    pos = gate
                    waiting = true
                } else {
                    waiting = false
                    pos += chunk * msPerFrame
                    if (pos >= gate - 1e-9) { pos = gate; waiting = true }
                    if (loopOn && pos >= loopEnd - 1e-9) seekInternal(loopStart)
                }
            } else waiting = false
            done += chunk
        }
        positionMs = pos
        stampNs = System.nanoTime()
        isPlaying = playing
        isWaiting = waiting
    }
}
