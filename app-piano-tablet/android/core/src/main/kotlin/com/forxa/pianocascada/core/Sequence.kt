package com.forxa.pianocascada.core

/** Notas de una canción listas para el motor de audio (tiempos en ms a velocidad 100 %). */
class SequenceData(
    val keys: IntArray, val vels: IntArray, val hands: IntArray,
    val startMs: DoubleArray, val durMs: DoubleArray,
) {
    val size: Int get() = keys.size
}

object Sequencer {
    fun build(song: Song, level: Int): SequenceData {
        val notes = song.notes(level)
        val n = notes.size
        val keys = IntArray(n); val vels = IntArray(n); val hands = IntArray(n)
        val st = DoubleArray(n); val du = DoubleArray(n)
        for ((i, nt) in notes.withIndex()) {
            keys[i] = nt.pitch
            hands[i] = if (nt.hand == Hand.RIGHT) 0 else 1
            val onBar = song.barStarts.any { kotlin.math.abs(it - nt.start) < 1e-3f }
            vels[i] = (if (nt.hand == Hand.RIGHT) 88 else 72) + (if (onBar) 6 else 0)
            st[i] = song.beatToMs(nt.start)
            du[i] = song.beatToMs(nt.dur) * 0.96
        }
        return SequenceData(keys, vels, hands, st, du)
    }

    fun barStartMs(song: Song, bar: Int): Double =
        song.beatToMs(song.barStarts[bar.coerceIn(0, song.barStarts.size - 1)])

    /** Fin (exclusivo) de [count] compases a partir de [bar]. */
    fun barEndMs(song: Song, bar: Int, count: Int): Double {
        val endBar = bar + count
        return if (endBar >= song.barStarts.size) song.totalMs else song.beatToMs(song.barStarts[endBar])
    }
}
