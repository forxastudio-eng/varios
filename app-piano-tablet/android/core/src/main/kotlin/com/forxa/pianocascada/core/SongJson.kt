package com.forxa.pianocascada.core

/** Escritura de canciones en el mismo formato que `songs.json` (para guardar las importadas). */
object SongJson {
    private fun esc(s: String) = buildString {
        for (c in s) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }

    private fun num(f: Float): String = if (f == f.toLong().toFloat()) f.toLong().toString() else "%.4f".format(java.util.Locale.ROOT, f).trimEnd('0').trimEnd('.')

    private fun notes(l: List<SongNote>) = l.joinToString(",", "[", "]") { "[${num(it.start)},${num(it.dur)},${it.pitch}]" }

    fun write(s: Song): String = buildString {
        append("{\"version\":1,\"canciones\":[{")
        append("\"id\":\"${esc(s.id)}\",\"titulo\":\"${esc(s.title)}\",\"compositor\":\"${esc(s.composer)}\",\"epoca\":\"${esc(s.era)}\",")
        append("\"obra\":\"${esc(s.work)}\",\"tonalidad\":\"${esc(s.key)}\",\"tonalidadOriginal\":\"${esc(s.originalKey)}\",")
        append("\"compas\":\"${esc(s.meter)}\",\"bpm\":${s.bpm},\"dificultad\":${s.difficulty},")
        append("\"consejo\":\"${esc(s.tip)}\",\"curiosidad\":\"${esc(s.fun_)}\",")
        append("\"fuente\":\"${when (s.source) { SongSource.SCORE -> "partitura"; SongSource.AUDIO -> "audio"; SongSource.PDF -> "pdf"; SongSource.IMPORTED -> "importada"; else -> "transcripcion" }}\",")
        append("\"pulso\":${num(s.pulse)},\"pulsosCompas\":${num(s.beatsPerBar)},\"anacrusa\":${num(s.pickup)},")
        append("\"compases\":${s.bars},\"pulsosTotales\":${num(s.totalBeats)},\"duracionSeg\":${"%.1f".format(java.util.Locale.ROOT, s.durationSec)},")
        append("\"notaMin\":${s.minPitch},\"notaMax\":${s.maxPitch},")
        append("\"inicioCompases\":${s.barStarts.joinToString(",", "[", "]") { num(it) }},")
        append("\"mdNotas\":${notes(s.rh)},\"miNotas\":${notes(s.lh)},\"bajoNotas\":${notes(s.bass)}")
        if (s.melody != null) append(",\"melNotas\":${notes(s.melody)}")
        append(",\"orden\":${s.order}}]}")
    }
}

object Simplifier {
    /** Nivel 2: una sola nota de bajo por compás (la más grave del primer ataque), sostenida todo el compás. */
    fun bassPerBar(lh: List<SongNote>, barStarts: List<Float>, totalBeats: Float): List<SongNote> {
        val out = ArrayList<SongNote>()
        for (i in barStarts.indices) {
            val a = barStarts[i]
            val b = if (i + 1 < barStarts.size) barStarts[i + 1] else totalBeats
            val inBar = lh.filter { it.start >= a - 1e-4f && it.start < b - 1e-4f }
            if (inBar.isEmpty()) continue
            val first = inBar.minOf { it.start }
            val low = inBar.filter { it.start == first }.minOf { it.pitch }
            val st = if (first - a < 0.5f) a else first
            out.add(SongNote(st, b - st, low, Hand.LEFT))
        }
        return out
    }

    /** Melodía a una sola voz: la nota más aguda de cada ataque, recortada para no solaparse con la siguiente. */
    fun skyline(rh: List<SongNote>): List<SongNote> {
        val sorted = rh.sortedWith(compareBy<SongNote>({ it.start }, { -it.pitch }))
        val tops = ArrayList<SongNote>()
        var i = 0
        while (i < sorted.size) {
            var j = i
            var top = sorted[i]
            while (j < sorted.size && sorted[j].start - sorted[i].start < 0.12f) { if (sorted[j].pitch > top.pitch) top = sorted[j]; j++ }
            tops.add(top)
            i = j
        }
        return tops.mapIndexed { k, n ->
            val next = tops.getOrNull(k + 1)
            if (next != null && n.end > next.start) n.copy(dur = (next.start - n.start).coerceAtLeast(0.1f)) else n
        }
    }
}
