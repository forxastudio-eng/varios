package com.forxa.pianocascada.transcribe

import kotlin.math.PI
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
    val beatsPerBar: Int,           // pulsos (negras) por compás
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
        // 1. fusión (desactivada por defecto: la evaluación mostró que destruye notas repetidas)
        val merged = ArrayList<RawNote>()
        for (n in cur) {
            val last = merged.lastOrNull()
            if (cfg.mergeGapSec > 0.0 && last != null && last.pitch == n.pitch && n.start - last.end <= cfg.mergeGapSec && n.start >= last.start) {
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
        // refinado fino de tempo y fase usando los ataques de las notas (rejilla de semicorcheas)
        val refined = refineGrid(notes, beat, 0.0)
        beat = refined.first
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
        var firstBeat = bestPhase / fps
        // el ajuste con las notas fija la rejilla de semicorcheas; falta decidir cuál de sus 4 posiciones es el PULSO:
        // la de más energía de ataque, con una pequeña preferencia por que la primera nota caiga en un pulso
        val t0 = refineGrid(notes, beat, firstBeat).second
        val sx = beat / 4.0
        val firstNote = notes.minOf { it.start }
        var bestJ = 0
        var bestJScore = -1e18
        val base = t0 - Math.floor(t0 / beat) * beat
        for (j in 0 until 4) {
            val ph = base + j * sx
            var sc = 0.0
            var k = ph * fps
            var cnt = 0
            while (k < n - 1) { sc += env[Math.floor(k).toInt()]; cnt++; k += pf }
            sc /= max(1, cnt)
            val rel = ((firstNote - ph) / beat)
            val dist = abs(rel - Math.round(rel))
            if (dist < 0.12) sc *= 1.25
            if (sc > bestJScore) { bestJScore = sc; bestJ = j }
        }
        firstBeat = base + bestJ * sx
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
                val adj = when (bpb) { 4 -> contrast * 1.18; 3 -> contrast * 1.06; else -> contrast }   // 4/4 es lo más frecuente
                if (adj > bestContrast) { bestContrast = adj; bestBpb = bpb; bestShift = shift }
            }
        }
        // confianza: pico de la autocorrelación frente a su media
        val meanAc = ac.drop(minLag).take(maxLag - minLag).average()
        val conf = (ac[bestLag] / (meanAc + 1e-12)).toFloat().coerceIn(0f, 10f) / 10f
        // el primer compás completo empieza en el pulso «bestShift» de la rejilla
        return BeatGrid(bpm, beat, firstBeat, bestBpb, bestShift, conf)
    }

    /**
     * Ajusta (periodo del pulso, fase) para que los ataques caigan en una rejilla de semicorcheas:
     * maximiza Σ amp·cos(2π·4·(t − t0)/beat). Devuelve (pulso, t0) con t0 elegido entre los 4 candidatos de
     * semicorchea el más cercano a [phaseHint].
     */
    fun refineGrid(notes: List<RawNote>, beat: Double, phaseHint: Double): Pair<Double, Double> {
        if (notes.size < 8) return beat to phaseHint
        val w = notes.map { (it.amp.toDouble() + 0.3) * (if (it.onsetStrength > 0f) 1.0 else 0.5) }
        var bestBeat = beat
        var bestScore = -1e18
        var bestT0 = phaseHint
        val steps = 80
        for (si in -steps..steps) {
            val b = beat * (1.0 + 0.03 * si / steps)
            val sixteenth = b / 4.0
            for (pi in 0 until 48) {
                val t0 = pi * sixteenth / 48.0
                var sc = 0.0
                for ((k, n) in notes.withIndex()) sc += w[k] * cos(2 * PI * (n.start - t0) / sixteenth)
                if (sc > bestScore) { bestScore = sc; bestBeat = b; bestT0 = t0 }
            }
        }
        // t0 solo está definido módulo una semicorchea: se elige el que cae más cerca de la pista de fase
        val sx = bestBeat / 4.0
        val k = Math.round((phaseHint - bestT0) / sx)
        return bestBeat to (bestT0 + k * sx)
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
     * Reparte las notas entre mano izquierda y derecha (true = derecha).
     * En cada ataque se elige un umbral de altura que: mantiene a cada mano en un alcance razonable (≈ una octava),
     * respeta las notas que siguen sonando (cada una conserva su mano), cambia poco respecto al ataque anterior y
     * se mantiene cerca de la tesitura global (k-medias ponderadas por duración).
     */
    /** Pesos del reparto de manos (se barren en la evaluación): tramo, continuidad, tesitura global, retenidas. */
    var handSpanW = 0.5
    var handContW = 0.02
    var handPriorW = 0.3
    var handHeldW = 6.0

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
        val b0 = ((lo + hi) / 2.0).coerceIn(52.0, 66.0)
        val right = BooleanArray(notes.size)
        val order = notes.indices.sortedWith(compareBy({ notes[it].start }, { notes[it].pitch }))
        var theta = b0
        var i = 0
        val held = ArrayList<Int>()        // índices ya asignados que siguen sonando
        while (i < order.size) {
            var j = i
            val t0 = notes[order[i]].start
            while (j < order.size && notes[order[j]].start - t0 < 0.08) j++
            val group = order.subList(i, j)
            held.removeAll { notes[it].end <= t0 + 0.02 }
            val pitches = (group.map { notes[it].pitch } + held.map { notes[it].pitch }).sorted()
            // candidatos: puntos intermedios entre alturas consecutivas y los extremos
            val cands = ArrayList<Double>()
            cands.add(pitches.first() - 0.5)
            for (k in 0 until pitches.size - 1) if (pitches[k + 1] > pitches[k]) cands.add((pitches[k] + pitches[k + 1]) / 2.0)
            cands.add(pitches.last() + 0.5)
            var bestC = theta; var bestCost = Double.MAX_VALUE
            for (c in cands) {
                var cost = 0.0
                // notas retenidas que quedarían en la mano contraria
                for (h in held) { val onRight = notes[h].pitch > c; if (onRight != right[h]) cost += handHeldW }
                val leftP = pitches.filter { it < c }; val rightP = pitches.filter { it > c }
                if (leftP.size > 1) cost += max(0, leftP.max() - leftP.min() - 12) * handSpanW
                if (rightP.size > 1) cost += max(0, rightP.max() - rightP.min() - 12) * handSpanW
                cost += abs(c - theta) * handContW + abs(c - b0) * handPriorW
                if (cost < bestCost) { bestCost = cost; bestC = c }
            }
            theta = bestC
            for (g in group) { right[g] = notes[g].pitch > theta; held.add(g) }
            i = j
        }
        return right
    }
}

data class BeatNote(val start: Double, val dur: Double, val pitch: Int, val amp: Float) {
    val end: Double get() = start + dur
}

/** Limpieza geométrica ligera. El grueso de la precisión lo aporta [NoteQuality] (filtro entrenado). */
data class CleanConfig(
    val mergeGapSec: Double = 0.0,
    val reStrikeOnset: Float = 0.45f,
    val ghostRatio: Float = 0f,
    val ghostOnsetSec: Double = 0.07,
    val ghostKeepOnset: Float = 0.8f,
    val minAmp: Float = 0f,
    val weakShortSec: Double = 0.2,
    val maxPolyphony: Int = 10,
)

