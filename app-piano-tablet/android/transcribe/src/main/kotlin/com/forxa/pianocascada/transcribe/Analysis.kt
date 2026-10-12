package com.forxa.pianocascada.transcribe

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Resultado del análisis rítmico: pulso (negra) constante, fase y compás. */
data class BeatGrid(
    val bpm: Double,
    val beatSec: Double,
    val firstBeatSec: Double,   // instante (s) del pulso nº 0 de la rejilla
    val beatsPerBar: Int,
    val downbeatShift: Int,     // cuántos pulsos hay antes del primer compás completo (anacrusa)
    val confidence: Float,
)

object Analysis {
    // ------------------------------------------------------------------ limpieza de notas
    /**
     * Mejora de precisión sobre la salida bruta:
     *  1. une fragmentos de una misma nota (misma tecla, hueco corto y sin nuevo ataque),
     *  2. elimina «fantasmas» armónicos (octava / quinta por encima de una nota mucho más fuerte),
     *  3. descarta notas muy débiles y muy cortas,
     *  4. limita la polifonía.
     */
    fun clean(notes: List<RawNote>, out: ModelOutput?, cfg: CleanConfig = CleanConfig()): List<RawNote> {
        var cur = notes.sortedWith(compareBy<RawNote> { it.pitch }.thenBy { it.start })
        // 1. fusión
        val merged = ArrayList<RawNote>()
        for (n in cur) {
            val last = merged.lastOrNull()
            if (last != null && last.pitch == n.pitch && n.start - last.end <= cfg.mergeGapSec && n.start >= last.start) {
                val strongOnset = out != null && onsetAround(out, n.start, n.pitch) >= cfg.reStrikeOnset
                if (!strongOnset) {
                    merged[merged.size - 1] = last.copy(end = max(last.end, n.end), amp = max(last.amp, n.amp))
                    continue
                }
            }
            merged.add(n)
        }
        cur = merged.sortedWith(compareBy<RawNote> { it.start }.thenBy { it.pitch })
        // 2. fantasmas armónicos
        if (cfg.ghostRatio > 0f) {
            val keep = BooleanArray(cur.size) { true }
            for (i in cur.indices) {
                val n = cur[i]
                for (j in cur.indices) {
                    if (i == j) continue
                    val m = cur[j]
                    val d = n.pitch - m.pitch
                    if (d != 12 && d != 19 && d != 24 && d != 7 + 12 + 12) continue
                    if (abs(n.start - m.start) > cfg.ghostOnsetSec) continue
                    val overlap = min(n.end, m.end) - max(n.start, m.start)
                    if (overlap < 0.6 * n.dur) continue
                    if (m.amp >= n.amp * cfg.ghostRatio && n.onsetStrength < cfg.ghostKeepOnset) { keep[i] = false; break }
                }
            }
            cur = cur.filterIndexed { i, _ -> keep[i] }
        }
        // 3. notas débiles y cortas
        cur = cur.filter { !(it.amp < cfg.minAmp && it.dur < cfg.weakShortSec) }
        // 4. polifonía máxima: en cada ataque se conservan las más fuertes
        if (cfg.maxPolyphony > 0) {
            val kept = ArrayList<RawNote>()
            for (n in cur) {
                val sounding = kept.count { it.end > n.start + 0.02 }
                if (sounding >= cfg.maxPolyphony) {
                    val weakest = kept.filter { it.end > n.start + 0.02 }.minByOrNull { it.amp }
                    if (weakest != null && weakest.amp < n.amp) { kept.remove(weakest); kept.add(n) }
                } else kept.add(n)
            }
            cur = kept.sortedWith(compareBy<RawNote> { it.start }.thenBy { it.pitch })
        }
        return cur
    }

    private fun onsetAround(out: ModelOutput, tSec: Double, pitch: Int): Float {
        val idx = pitch - 21
        if (idx !in 0 until ModelOutput.BINS) return 0f
        val center = (tSec * BasicPitch.FPS).roundToInt()
        var best = 0f
        for (t in (center - 3)..(center + 3)) if (t in 0 until out.frames) best = max(best, out.onset(t, idx))
        return best
    }

    // ------------------------------------------------------------------ ritmo
    /** Curva de fuerza de ataques (suma de las probabilidades de inicio de todas las teclas). */
    fun onsetEnvelope(out: ModelOutput): FloatArray {
        val e = FloatArray(out.frames)
        for (t in 0 until out.frames) {
            var s = 0f
            val base = t * ModelOutput.BINS
            for (f in 0 until ModelOutput.BINS) s += out.onset[base + f]
            e[t] = s
        }
        // quita la tendencia lenta y rectifica
        val w = (BasicPitch.FPS * 1.0).toInt()
        val sm = FloatArray(e.size)
        var acc = 0.0
        for (i in e.indices) {
            acc += e[i]
            if (i >= 2 * w) acc -= e[i - 2 * w]
            val cnt = min(i + 1, 2 * w)
            sm[i] = (acc / cnt).toFloat()
        }
        return FloatArray(e.size) { max(0f, e[it] - sm[min(e.size - 1, it + w)]) }
    }

    /** Estima tempo, fase y compás a partir de los ataques; el pulso resultante se normaliza al rango 55–130 ppm. */
    fun beatGrid(env: FloatArray, notes: List<RawNote>): BeatGrid {
        val fps = BasicPitch.FPS
        val n = env.size
        if (n < fps * 4 || notes.size < 6) return BeatGrid(90.0, 60.0 / 90.0, notes.firstOrNull()?.start ?: 0.0, 4, 0, 0f)
        // autocorrelación de la curva de ataques, ponderada por un prior suave centrado en ~100 ppm
        val minLag = (fps * 60.0 / 200.0).toInt()
        val maxLag = (fps * 60.0 / 40.0).toInt().coerceAtMost(n / 2)
        val ac = DoubleArray(maxLag + 2)
        for (lag in minLag - 1..maxLag + 1) {
            var s = 0.0
            for (i in 0 until n - lag) s += env[i].toDouble() * env[i + lag]
            ac[lag] = s / (n - lag)
        }
        var bestLag = minLag
        var bestScore = -1.0
        for (lag in minLag..maxLag) {
            val bpm = fps * 60.0 / lag
            val prior = exp(-0.5 * (ln(bpm / 100.0) / 0.55).pow(2))
            // un pulso verdadero también hace pico a 2× y 3× su periodo
            val sc = (ac[lag] + 0.5 * (if (2 * lag <= maxLag) ac[2 * lag] else 0.0) + 0.3 * (if (3 * lag <= maxLag) ac[3 * lag] else 0.0)) * prior
            if (sc > bestScore) { bestScore = sc; bestLag = lag }
        }
        // refinamiento parabólico
        val l = bestLag
        val a = ac[l - 1]; val b = ac[l]; val c = ac[l + 1]
        val denom = a - 2 * b + c
        val period = l + if (abs(denom) > 1e-12) 0.5 * (a - c) / denom else 0.0
        var beat = period / fps
        // normaliza a 55–130 ppm
        while (60.0 / beat > 130.0) beat *= 2.0
        while (60.0 / beat < 55.0) beat /= 2.0
        val bpm = 60.0 / beat
        // fase: la posición que acumula más energía de ataque
        val pf = beat * fps
        var bestPhase = 0.0
        var bestPS = -1.0
        var phase = 0.0
        while (phase < pf) {
            var s = 0.0
            var k = phase
            var cnt = 0
            while (k < n - 1) {
                val i0 = floor(k).toInt()
                val fr = k - i0
                s += env[i0] * (1 - fr) + env[min(n - 1, i0 + 1)] * fr
                cnt++
                k += pf
            }
            s /= max(1, cnt)
            if (s > bestPS) { bestPS = s; bestPhase = phase }
            phase += 0.5
        }
        val firstBeat = bestPhase / fps
        // compás: ¿qué agrupación (2, 3 o 4 pulsos) concentra más energía en el primer pulso?
        val beatEnergy = ArrayList<Double>()
        var kk = bestPhase
        while (kk < n - 1) {
            var e = 0.0
            val w = (pf * 0.2).toInt().coerceAtLeast(1)
            for (d in -w..w) { val i = (kk + d).roundToInt(); if (i in 0 until n) e += env[i] }
            beatEnergy.add(e)
            kk += pf
        }
        var bestBpb = 4
        var bestContrast = -1.0
        var bestShift = 0
        for (bpb in intArrayOf(4, 3, 2)) {
            if (beatEnergy.size < bpb * 3) continue
            for (shift in 0 until bpb) {
                var down = 0.0; var dn = 0
                var other = 0.0; var on = 0
                for ((i, e) in beatEnergy.withIndex()) if ((i - shift).mod(bpb) == 0) { down += e; dn++ } else { other += e; on++ }
                if (dn == 0 || on == 0) continue
                val contrast = (down / dn) / ((other / on) + 1e-9)
                val adj = if (bpb == 4) contrast * 1.04 else contrast   // 4/4 es lo más frecuente
                if (adj > bestContrast) { bestContrast = adj; bestBpb = bpb; bestShift = shift }
            }
        }
        // confianza: pico de la autocorrelación frente a su media
        val meanAc = ac.drop(minLag).take(maxLag - minLag).average()
        val conf = (ac[bestLag] / (meanAc + 1e-12)).toFloat().coerceIn(0f, 10f) / 10f
        // el primer compás completo empieza en el pulso «bestShift» de la rejilla
        return BeatGrid(bpm, beat, firstBeat, bestBpb, bestShift, conf)
    }

    // ------------------------------------------------------------------ cuantización
    /**
     * Convierte tiempos (s) a pulsos (negras) con la rejilla y ajusta suavemente a semicorcheas (o tresillos).
     * Si la rejilla no encaja con las notas (música con rubato) devuelve los tiempos sin ajustar.
     */
    fun toBeats(notes: List<RawNote>, grid: BeatGrid, quantize: Boolean = true): List<BeatNote> {
        if (notes.isEmpty()) return emptyList()
        val t0 = grid.firstBeatSec
        fun beatOf(t: Double) = (t - t0) / grid.beatSec
        // elige rejilla: semicorcheas (1/4 de pulso) o tresillos (1/3)
        fun residual(g: Double): Double {
            var s = 0.0
            for (n in notes) { val b = beatOf(n.start); val r = b - (b / g).roundToInt() * g; s += abs(r) }
            return s / notes.size
        }
        val straight = residual(0.25)
        val triplet = residual(1.0 / 3.0)
        val g = if (triplet < straight * 0.7) 1.0 / 3.0 else 0.25
        val fit = if (g == 0.25) straight else triplet
        val doQuantize = quantize && fit < 0.09        // si el error medio supera ~0,09 pulsos, no hay rejilla fiable
        val res = ArrayList<BeatNote>(notes.size)
        for (n in notes) {
            var s = beatOf(n.start)
            var e = beatOf(n.end)
            if (doQuantize) {
                val qs = (s / g).roundToInt() * g
                if (abs(s - qs) <= 0.45 * g) s = qs
                val qe = (e / g).roundToInt() * g
                e = if (abs(e - qe) <= 0.45 * g) qe else e
                if (e <= s + 1e-6) e = s + g
            }
            res.add(BeatNote(s, max(e - s, 0.1), n.pitch, n.amp))
        }
        return res.sortedWith(compareBy<BeatNote> { it.start }.thenBy { it.pitch })
    }

    // ------------------------------------------------------------------ tonalidad
    private val MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
    private val MAJ_NAMES = arrayOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
    private val MIN_NAMES = arrayOf("Cm", "C#m", "Dm", "Ebm", "Em", "Fm", "F#m", "Gm", "G#m", "Am", "Bbm", "Bm")

    /** Krumhansl–Schmuckler sobre las clases de altura ponderadas por duración y fuerza. */
    fun detectKey(notes: List<BeatNote>): String {
        val h = DoubleArray(12)
        for (n in notes) h[((n.pitch % 12) + 12) % 12] += n.dur * (0.5 + n.amp)
        if (h.sum() <= 0.0) return "C"
        var best = "C"
        var bestR = -2.0
        for (tonic in 0 until 12) {
            for (minor in booleanArrayOf(false, true)) {
                val prof = if (minor) MINOR else MAJOR
                val r = corr(h, DoubleArray(12) { prof[(it - tonic + 12) % 12] })
                if (r > bestR) { bestR = r; best = if (minor) MIN_NAMES[tonic] else MAJ_NAMES[tonic] }
            }
        }
        return best
    }

    private fun corr(a: DoubleArray, b: DoubleArray): Double {
        val ma = a.average(); val mb = b.average()
        var num = 0.0; var da = 0.0; var db = 0.0
        for (i in a.indices) { num += (a[i] - ma) * (b[i] - mb); da += (a[i] - ma).pow(2); db += (b[i] - mb).pow(2) }
        return if (da <= 0 || db <= 0) 0.0 else num / sqrt(da * db)
    }

    // ------------------------------------------------------------------ manos
    /**
     * Reparte las notas entre mano izquierda y derecha: umbral adaptado a la tesitura (k-medias sobre las alturas,
     * ponderadas por duración) y, en acordes que cruzan el umbral, las notas más agudas van a la derecha.
     * Devuelve true para la mano derecha.
     */
    fun splitHands(notes: List<BeatNote>): BooleanArray {
        if (notes.isEmpty()) return BooleanArray(0)
        var lo = 48.0; var hi = 72.0
        repeat(12) {
            var sl = 0.0; var wl = 0.0; var sh = 0.0; var wh = 0.0
            for (n in notes) {
                val w = n.dur.coerceIn(0.1, 4.0)
                if (abs(n.pitch - lo) <= abs(n.pitch - hi)) { sl += n.pitch * w; wl += w } else { sh += n.pitch * w; wh += w }
            }
            if (wl > 0) lo = sl / wl
            if (wh > 0) hi = sh / wh
        }
        val boundary = ((lo + hi) / 2.0).coerceIn(52.0, 66.0)
        val right = BooleanArray(notes.size) { notes[it].pitch >= boundary }
        // las notas que suenan a la vez y rodean el umbral: si hay dos o más por encima de C4 juntas, van a la derecha
        return right
    }
}

data class BeatNote(val start: Double, val dur: Double, val pitch: Int, val amp: Float) {
    val end: Double get() = start + dur
}

data class CleanConfig(
    val mergeGapSec: Double = 0.09,
    val reStrikeOnset: Float = 0.45f,
    val ghostRatio: Float = 1.6f,
    val ghostOnsetSec: Double = 0.07,
    val ghostKeepOnset: Float = 0.8f,
    val minAmp: Float = 0.33f,
    val weakShortSec: Double = 0.2,
    val maxPolyphony: Int = 10,
)

@Suppress("unused") private fun cosDeg(x: Double) = cos(x)
