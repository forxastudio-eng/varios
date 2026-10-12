package com.forxa.pianocascada.transcribe

import com.forxa.pianocascada.core.Hand
import com.forxa.pianocascada.core.Simplifier
import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.core.SongNote
import com.forxa.pianocascada.core.SongSource
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

data class TranscribeOptions(
    val extract: ExtractParams = ExtractParams(onsetThresh = 0.5f, frameThresh = 0.3f, minNoteLen = 6),
    val clean: CleanConfig = CleanConfig(),
    val quantize: Boolean = true,
)

class TranscriptionResult(
    val song: Song,
    val rawNotes: List<RawNote>,
    val finalNotes: List<RawNote>,
    val grid: BeatGrid,
    val quantized: Boolean,
    val seconds: Double,
)

/** Audio → canción: resample → Basic Pitch → notas → limpieza → ritmo → manos → niveles. */
class Transcriber(private val model: BasicPitch) {

    fun transcribe(
        audio: FloatArray, sampleRate: Int, id: String, title: String,
        source: SongSource = SongSource.AUDIO, opts: TranscribeOptions = TranscribeOptions(),
        progress: (Float, String) -> Unit = { _, _ -> },
    ): TranscriptionResult {
        progress(0.02f, "Preparando el audio…")
        val a22 = Resampler.resample(audio, sampleRate, BasicPitch.SAMPLE_RATE)
        progress(0.08f, "Escuchando la música…")
        val out = model.run(a22) { progress(0.08f + 0.72f * it, "Escuchando la música…") }
        progress(0.82f, "Buscando las notas…")
        val raw = NoteExtractor.extract(out, opts.extract)
        val clean = Analysis.clean(raw, out, opts.clean)
        progress(0.9f, "Midiendo el ritmo…")
        val env = Analysis.onsetEnvelope(out)
        val grid = Analysis.beatGrid(env, clean)
        val beats = Analysis.toBeats(clean, grid, opts.quantize)
        progress(0.95f, "Preparando tu versión fácil…")
        val song = SongBuilder.build(id, title, source, beats, grid, a22.size.toDouble() / BasicPitch.SAMPLE_RATE)
        progress(1f, "Listo")
        return TranscriptionResult(song, raw, clean, grid, opts.quantize, a22.size.toDouble() / BasicPitch.SAMPLE_RATE)
    }
}

object SongBuilder {
    fun build(id: String, title: String, source: SongSource, notes: List<BeatNote>, grid: BeatGrid, durationSec: Double): Song {
        val bpb = grid.beatsPerBar
        val right = Analysis.splitHands(notes)
        val key = Analysis.detectKey(notes)
        val earliest = notes.minOfOrNull { it.start } ?: 0.0
        // primer compás que empieza en o antes de la primera nota (los compases caen en shift + k·bpb)
        val shift = grid.downbeatShift
        val firstBar = shift + floor((earliest - shift) / bpb) * bpb
        val origin: Double
        var pickup = 0.0
        if (earliest - firstBar < bpb * 0.5) {
            origin = firstBar
        } else {
            val nextBar = firstBar + bpb
            origin = floor(earliest * 4.0) / 4.0
            pickup = nextBar - origin
            if (pickup >= bpb - 1e-6) pickup = 0.0
        }
        val rel = notes.map { it.copy(start = max(0.0, it.start - origin)) }
        val lastEnd = rel.maxOfOrNull { it.end } ?: 4.0
        // compases: [0 (anacrusa)] , pickup, pickup + bpb, …
        val starts = ArrayList<Float>()
        var t = 0.0
        if (pickup > 0) { starts.add(0f); t = pickup }
        while (t < lastEnd - 1e-6) { starts.add(t.toFloat()); t += bpb }
        if (starts.isEmpty()) starts.add(0f)
        val total = (if (pickup > 0) pickup else 0.0) + ceil(max(1.0, (lastEnd - (if (pickup > 0) pickup else 0.0)) / bpb)) * bpb
        val rhN = ArrayList<SongNote>(); val lhN = ArrayList<SongNote>()
        for ((i, n) in rel.withIndex()) {
            val sn = SongNote(n.start.toFloat(), n.dur.toFloat().coerceAtLeast(0.1f), n.pitch, if (right[i]) Hand.RIGHT else Hand.LEFT)
            if (right[i]) rhN.add(sn) else lhN.add(sn)
        }
        val polyphonicRight = rhN.groupBy { (it.start * 8f).roundToInt() }.values.any { it.size > 1 }
        val melody = if (polyphonicRight) Simplifier.skyline(rhN) else null
        val bass = Simplifier.bassPerBar(lhN, starts, total.toFloat())
        val bpm = grid.bpm.roundToInt().coerceIn(40, 200)
        val seconds = (total * 60.0 / bpm).toFloat()
        val density = notes.size / max(1.0, durationSec)
        val difficulty = when { density < 1.5 -> 1; density < 3.5 -> 2; density < 6.0 -> 3; else -> 4 }
        val prefix = when (source) { SongSource.PDF -> "Leída de un PDF"; else -> "Transcrita automáticamente del audio" }
        return Song(
            id = id, title = title, composer = "Mis canciones", era = "", work = "", key = key, originalKey = key,
            meter = "$bpb/4", bpm = bpm, difficulty = difficulty,
            tip = "$prefix. Si alguna nota no suena bien, empieza por el nivel 1 y baja la velocidad.", fun_ = "",
            source = source, pulse = 1f, beatsPerBar = bpb.toFloat(), pickup = pickup.toFloat(),
            totalBeats = total.toFloat(), durationSec = seconds, barStarts = starts,
            rh = rhN.sortedWith(compareBy({ it.start }, { it.pitch })), melody = melody,
            lh = lhN.sortedWith(compareBy({ it.start }, { it.pitch })), bass = bass, order = 1000,
        )
    }
}
