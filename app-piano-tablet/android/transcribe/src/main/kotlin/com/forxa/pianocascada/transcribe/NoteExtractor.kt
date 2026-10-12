package com.forxa.pianocascada.transcribe

import kotlin.math.max
import kotlin.math.min

/** Nota detectada, en tiempo real (segundos). amp ∈ 0..1 es la activación media del modelo. */
data class RawNote(val start: Double, val end: Double, val pitch: Int, val amp: Float, val onsetStrength: Float = 0f) {
    val dur: Double get() = end - start
}

data class ExtractParams(
    val onsetThresh: Float = 0.5f,
    val frameThresh: Float = 0.3f,
    val minNoteLen: Int = 11,        // fotogramas (≈ 128 ms con el valor por defecto de Basic Pitch)
    val inferOnsets: Boolean = true,
    val melodia: Boolean = true,
    val energyTol: Int = 11,
)

/** Port del decodificador de notas polifónico de Basic Pitch (output_to_notes_polyphonic). */
object NoteExtractor {
    private const val B = ModelOutput.BINS
    private const val MIDI_OFFSET = 21

    fun extract(out: ModelOutput, p: ExtractParams = ExtractParams()): List<RawNote> {
        val n = out.frames
        if (n < 3) return emptyList()
        val frames = out.note
        var onsets = out.onset
        if (p.inferOnsets) onsets = inferredOnsets(onsets, frames, n)

        // picos de inicio: máximos locales estrictos en el tiempo por encima del umbral
        class Onset(val t: Int, val f: Int, val v: Float)
        val list = ArrayList<Onset>()
        for (t in 1 until n - 1) {
            val base = t * B
            for (f in 0 until B) {
                val v = onsets[base + f]
                if (v >= p.onsetThresh && v > onsets[base - B + f] && v > onsets[base + B + f]) list.add(Onset(t, f, v))
            }
        }
        // se procesan hacia atrás en el tiempo, como la referencia
        list.sortWith(compareByDescending<Onset> { it.t }.thenByDescending { it.f })

        val remaining = frames.copyOf(n * B)
        val notes = ArrayList<RawNote>()
        for (o in list) {
            if (o.t >= n - 1) continue
            var i = o.t + 1
            var k = 0
            while (i < n - 1 && k < p.energyTol) {
                if (remaining[i * B + o.f] < p.frameThresh) k++ else k = 0
                i++
            }
            i -= k
            if (i - o.t <= p.minNoteLen) continue
            for (t in o.t until i) {
                remaining[t * B + o.f] = 0f
                if (o.f < B - 1) remaining[t * B + o.f + 1] = 0f
                if (o.f > 0) remaining[t * B + o.f - 1] = 0f
            }
            var s = 0f
            for (t in o.t until i) s += frames[t * B + o.f]
            notes.add(RawNote(BasicPitch.frameTime(o.t), BasicPitch.frameTime(i), o.f + MIDI_OFFSET, s / (i - o.t), o.v))
        }
        if (p.melodia) melodia(remaining, frames, n, p, notes)
        notes.sortWith(compareBy<RawNote> { it.start }.thenBy { it.pitch })
        return notes
    }

    private fun inferredOnsets(onsets: FloatArray, frames: FloatArray, n: Int): FloatArray {
        val nDiff = 2
        val diff = FloatArray(n * B)
        var maxDiff = 0f
        for (t in 0 until n) for (f in 0 until B) {
            var mn = Float.MAX_VALUE
            for (d in 1..nDiff) {
                val prev = if (t - d >= 0) frames[(t - d) * B + f] else 0f
                mn = min(mn, frames[t * B + f] - prev)
            }
            if (mn < 0f || t < nDiff) mn = 0f
            diff[t * B + f] = mn
            if (mn > maxDiff) maxDiff = mn
        }
        var maxOn = 0f
        for (v in onsets) if (v > maxOn) maxOn = v
        val res = FloatArray(n * B)
        val scale = if (maxDiff > 0f) maxOn / maxDiff else 0f
        for (i in res.indices) res[i] = max(onsets[i], diff[i] * scale)
        return res
    }

    private fun melodia(rem: FloatArray, frames: FloatArray, n: Int, p: ExtractParams, notes: MutableList<RawNote>) {
        while (true) {
            var best = 0f
            var bi = -1
            for (i in rem.indices) if (rem[i] > best) { best = rem[i]; bi = i }
            if (bi < 0 || best <= p.frameThresh) break
            val mid = bi / B
            val f = bi % B
            rem[bi] = 0f
            var i = mid + 1
            var k = 0
            while (i < n - 1 && k < p.energyTol) {
                if (rem[i * B + f] < p.frameThresh) k++ else k = 0
                rem[i * B + f] = 0f
                if (f < B - 1) rem[i * B + f + 1] = 0f
                if (f > 0) rem[i * B + f - 1] = 0f
                i++
            }
            val end = i - 1 - k
            i = mid - 1
            k = 0
            while (i > 0 && k < p.energyTol) {
                if (rem[i * B + f] < p.frameThresh) k++ else k = 0
                rem[i * B + f] = 0f
                if (f < B - 1) rem[i * B + f + 1] = 0f
                if (f > 0) rem[i * B + f - 1] = 0f
                i--
            }
            val start = i + 1 + k
            if (end - start <= p.minNoteLen) continue
            var s = 0f
            for (t in start until end) s += frames[t * B + f]
            notes.add(RawNote(BasicPitch.frameTime(start), BasicPitch.frameTime(end), f + MIDI_OFFSET, s / (end - start), 0f))
        }
    }
}
