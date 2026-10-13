package com.forxa.pianocascada.engine

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/* Bloques de señal del motor de piano: traducción de dsp.h (C++) a Kotlin para la versión de escritorio. */

const val PI_F = 3.14159265358979323846f

class Biquad {
    var b0 = 1f; var b1 = 0f; var b2 = 0f; var a1 = 0f; var a2 = 0f
    private var z1 = 0f; private var z2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun setIdentity() { b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f }

    private fun set(nb0: Float, nb1: Float, nb2: Float, na0: Float, na1: Float, na2: Float) {
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0
        a1 = na1 / na0; a2 = na2 / na0
    }

    private fun p10(x: Float): Float = 10.0.pow(x.toDouble()).toFloat()

    fun setPeaking(sr: Float, f: Float, q: Float, gainDb: Float) {
        if (abs(gainDb) < 0.01f) { setIdentity(); return }
        val a = p10(gainDb / 40f)
        val w0 = 2 * PI_F * f / sr
        val c = cos(w0); val alpha = sin(w0) / (2 * q)
        set(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
    }

    fun setLowShelf(sr: Float, f: Float, gainDb: Float) {
        if (abs(gainDb) < 0.01f) { setIdentity(); return }
        val a = p10(gainDb / 40f)
        val w0 = 2 * PI_F * f / sr
        val c = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0f)
        val sA = 2 * sqrt(a) * alpha
        set(a * ((a + 1) - (a - 1) * c + sA), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sA),
            (a + 1) + (a - 1) * c + sA, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sA)
    }

    fun setHighShelf(sr: Float, f: Float, gainDb: Float) {
        if (abs(gainDb) < 0.01f) { setIdentity(); return }
        val a = p10(gainDb / 40f)
        val w0 = 2 * PI_F * f / sr
        val c = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0f)
        val sA = 2 * sqrt(a) * alpha
        set(a * ((a + 1) + (a - 1) * c + sA), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sA),
            (a + 1) - (a - 1) * c + sA, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sA)
    }

    fun setHighpass(sr: Float, f: Float, q: Float) {
        val w0 = 2 * PI_F * f / sr
        val c = cos(w0); val alpha = sin(w0) / (2 * q)
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }
}

/** Reverb estéreo de tipo Freeverb (8 peines con amortiguación + 4 pasa-todo por canal). */
class Reverb {
    private class Comb(size: Int) { val buf = FloatArray(size); var idx = 0; var store = 0f }
    private class AllPass(size: Int) { val buf = FloatArray(size); var idx = 0 }
    private var comb: Array<Array<Comb>> = emptyArray()
    private var ap: Array<Array<AllPass>> = emptyArray()
    private var feedback = 0.84f
    private var damp = 0.3f

    fun init(sr: Float) {
        val combT = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val apT = intArrayOf(556, 441, 341, 225)
        val spread = 23
        val k = sr / 44100.0f
        comb = Array(2) { ch -> Array(8) { i -> Comb(max(8, ((combT[i] + ch * spread) * k).toInt())) } }
        ap = Array(2) { ch -> Array(4) { i -> AllPass(max(8, ((apT[i] + ch * spread) * k).toInt())) } }
        setParams(0.5f, 0.4f)
    }

    fun setParams(size: Float, dampIn: Float) {
        feedback = 0.70f + 0.28f * size.coerceIn(0f, 1f)
        damp = 0.05f + 0.75f * dampIn.coerceIn(0f, 1f)
    }

    fun process(inL: FloatArray, inR: FloatArray, wetL: FloatArray, wetR: FloatArray, n: Int) {
        for (i in 0 until n) {
            val input = (inL[i] + inR[i]) * 0.015f
            for (ch in 0 until 2) {
                var s = 0f
                for (c in 0 until 8) {
                    val cb = comb[ch][c]
                    val out = cb.buf[cb.idx]
                    cb.store = out * (1 - damp) + cb.store * damp
                    cb.buf[cb.idx] = input + cb.store * feedback
                    if (++cb.idx >= cb.buf.size) cb.idx = 0
                    s += out
                }
                for (a in 0 until 4) {
                    val p = ap[ch][a]
                    val bo = p.buf[p.idx]
                    val o = -s + bo
                    p.buf[p.idx] = s + bo * 0.5f
                    if (++p.idx >= p.buf.size) p.idx = 0
                    s = o
                }
                if (ch == 0) wetL[i] = s * 3.0f else wetR[i] = s * 3.0f
            }
        }
    }
}

/** Limitador de picos con ataque instantáneo y recuperación suave, más un recorte suave final. */
class Limiter {
    private var release = 0.999f
    private var gain = 1.0f
    private val ceiling = 0.95f
    var outL = 0f; var outR = 0f

    fun init(sr: Float) { release = exp(-1.0f / (0.12f * sr)); gain = 1.0f }

    fun process(l: Float, r: Float) {
        val peak = max(abs(l), abs(r))
        val need = if (peak > ceiling) ceiling / peak else 1.0f
        gain = if (need < gain) need else 1.0f - (1.0f - gain) * release
        outL = softClip(l * gain)
        outR = softClip(r * gain)
    }

    private fun softClip(x: Float): Float {
        val t = 0.9f
        val a = abs(x)
        if (a <= t) return x
        val s = if (x < 0) -1.0f else 1.0f
        val over = (a - t) / (1.0f - t)
        return s * (t + (1.0f - t) * tanh(over))
    }
}
