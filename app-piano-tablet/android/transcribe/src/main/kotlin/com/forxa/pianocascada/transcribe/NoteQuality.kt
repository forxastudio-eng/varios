package com.forxa.pianocascada.transcribe

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Filtro de calidad de notas: estima la probabilidad de que una nota detectada sea real a partir de varias
 * características del propio modelo (activación, ataque, duración, relación con las octavas vecinas…).
 * Los pesos se entrenan fuera de la app (ver tools/train_note_filter.py) con validación cruzada por canción.
 */
object NoteQuality {
    const val N_FEATURES = 12

    /** Calcula el vector de características de cada nota (mismo orden que la lista de entrada). */
    fun features(notes: List<RawNote>, out: ModelOutput): Array<FloatArray> {
        val amps = notes.map { it.amp }.sorted()
        val p90 = if (amps.isEmpty()) 1f else amps[min(amps.size - 1, (amps.size * 0.9).toInt())].coerceAtLeast(0.2f)
        val byPitch = notes.indices.groupBy { notes[it].pitch }
        fun partner(n: RawNote, gap: Int): RawNote? {
            var best: RawNote? = null
            for (i in byPitch[n.pitch - gap] ?: return null) {
                val m = notes[i]
                val ov = min(m.end, n.end) - max(m.start, n.start)
                if (ov > 0.5 * n.dur && abs(m.start - n.start) < 0.1 && (best == null || m.amp > best.amp)) best = m
            }
            return best
        }
        return Array(notes.size) { i ->
            val n = notes[i]
            val on = onsetSupport(out, n)
            val p12 = partner(n, 12); val p19 = partner(n, 19); val p24 = partner(n, 24)
            val par = listOfNotNull(p12, p19, p24).maxByOrNull { it.amp }
            val above12 = (byPitch[n.pitch + 12] ?: emptyList()).any { val m = notes[it]; min(m.end, n.end) - max(m.start, n.start) > 0.5 * m.dur && abs(m.start - n.start) < 0.1 }
            val neigh = max(onsetSupportAt(out, n.start, n.pitch - 1), onsetSupportAt(out, n.start, n.pitch + 1))
            var poly = 0
            for (m in notes) if (m !== n && m.start <= n.start + 0.02 && m.end > n.start + 0.02) poly++
            floatArrayOf(
                n.amp,
                on,
                ln(max(0.05, n.dur)).toFloat(),
                n.amp / p90,
                if (par != null) 1f else 0f,
                if (par != null) min(3f, n.amp / max(0.05f, par.amp)) else 0f,
                if (par != null) min(3f, on / max(0.05f, onsetSupportOf(out, par))) else 0f,
                if (p12 != null) 1f else 0f,
                if (above12) 1f else 0f,
                neigh,
                min(8, poly).toFloat() / 8f,
                if (n.onsetStrength > 0f) 1f else 0f,   // 1 = nota nacida de un ataque; 0 = recuperada por «melodia»
            )
        }
    }

    private fun onsetSupportOf(out: ModelOutput, n: RawNote) = onsetSupport(out, n)
    private fun onsetSupport(out: ModelOutput, n: RawNote): Float = onsetSupportAt(out, n.start, n.pitch)

    private fun onsetSupportAt(out: ModelOutput, tSec: Double, pitch: Int): Float {
        val idx = pitch - 21
        if (idx !in 0 until ModelOutput.BINS) return 0f
        val c = (tSec * BasicPitch.FPS).toInt()
        var best = 0f
        for (t in (c - 3)..(c + 3)) if (t in 0 until out.frames) best = max(best, out.onset(t, idx))
        return best
    }

    // Se rellenan tras el entrenamiento (tools/train_note_filter.py escribe este bloque)
    var weights: FloatArray = FloatArray(N_FEATURES)
    var bias: Float = 0f
    var mean: FloatArray = FloatArray(N_FEATURES)
    var scale: FloatArray = FloatArray(N_FEATURES) { 1f }
    var trained: Boolean = false

    fun probability(f: FloatArray): Float {
        if (!trained) return 1f
        var z = bias
        for (i in 0 until N_FEATURES) z += weights[i] * ((f[i] - mean[i]) / scale[i])
        return (1.0 / (1.0 + exp(-z.toDouble()))).toFloat()
    }

    /** Descarta las notas cuya probabilidad de ser reales queda por debajo de [threshold]. */
    fun filter(notes: List<RawNote>, out: ModelOutput, threshold: Float): List<RawNote> {
        if (!trained || notes.isEmpty()) return notes
        val feats = features(notes, out)
        return notes.filterIndexed { i, _ -> probability(feats[i]) >= threshold }
    }
}
