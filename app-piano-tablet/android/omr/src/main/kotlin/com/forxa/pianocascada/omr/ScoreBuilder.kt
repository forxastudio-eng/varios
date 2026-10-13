package com.forxa.pianocascada.omr

import com.forxa.pianocascada.core.Hand
import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.core.SongNote
import com.forxa.pianocascada.core.SongSource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * De los símbolos del modelo a una canción de la app: tiempos exactos con fracciones, compases, tonalidad,
 * reparto en manos (melodía de la voz, mano derecha, mano izquierda) y niveles.
 * Hace el mismo trabajo que el generador de MusicXML de homr y la conversión del servidor, sin XML de por medio.
 */
object ScoreBuilder {
    class Result(val song: Song, val warnings: List<String>)

    private class Ev(val measure: Int, val t: Frac, val dur: Frac, val midi: Int, val lower: Boolean, val slur: String)

    private class VoiceParse(
        val events: List<Ev>, val measureDurations: List<Frac>, val timeDenominator: Int?, val fifths: Int?,
    ) {
        /** Compases con al menos una nota (los de solo silencios no sirven para medir el compás). */
        val noteMeasures: Set<Int> = events.map { it.measure }.toSet()
    }

    private val STEP = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
    private val LIFT = mapOf("#" to 1, "##" to 2, "b" to -1, "bb" to -2, "N" to 0)
    private val MAJOR_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    private fun midiOf(sym: EncodedSymbol): Int? {
        if (sym.pitch == EMPTY || sym.pitch == NONOTE || sym.pitch.length < 2) return null
        val step = STEP[sym.pitch[0]] ?: return null
        val octave = sym.pitch.substring(1).toIntOrNull() ?: return null
        return (octave + 1) * 12 + step + (LIFT[sym.lift] ?: 0)
    }

    private fun parseVoice(tokens: List<EncodedSymbol>): VoiceParse {
        val events = ArrayList<Ev>()
        val measureDurations = ArrayList<Frac>()
        var measure = 0
        var t = Frac.ZERO
        var timeDen: Int? = null
        var fifths: Int? = null
        var sawContent = false
        var hadRest = false
        for (group in TokenOps.sortTokenChords(tokens)) {
            if (group.isEmpty()) continue
            val first = group[0].rhythm
            when {
                first.startsWith("note") || first.startsWith("rest") -> {
                    val timed = group.filter { it.rhythm.startsWith("note") || it.rhythm.startsWith("rest") }
                    // el silencio de compás completo (rest_0) no tiene duración propia: ocupa el compás entero
                    fun eff(x: EncodedSymbol) = if (x.rhythm.startsWith("rest_0")) Frac.ZERO else x.duration().fraction
                    val groupDur = timed.minOf { eff(it) }
                    val maxDur = timed.maxOf { eff(it) }
                    for (s in timed) {
                        if (!s.rhythm.startsWith("note")) continue
                        if ('G' in s.rhythm) continue                 // notas de adorno
                        val midi = midiOf(s) ?: continue
                        var d = eff(s)
                        if (d == Frac.ZERO) d = maxDur
                        if (d == Frac.ZERO) continue
                        events.add(Ev(measure, t, d, midi, s.position == "lower", s.slur))
                    }
                    t += groupDur
                    sawContent = true
                    if (timed.all { it.rhythm.startsWith("rest") }) hadRest = true
                }
                first.contains("barline") || first.startsWith("repeat") -> {
                    // una barra al principio de un compás vacío (o «repeatStart») abre el compás, no lo cierra
                    val opens = first == "repeatStart" || (!sawContent && t == Frac.ZERO && !hadRest)
                    if (!opens) { measureDurations.add(t); measure++; t = Frac.ZERO; sawContent = false; hadRest = false }
                }
                first.startsWith("timeSignature") -> if (timeDen == null) timeDen = first.substringAfter("/").toIntOrNull()
                first.startsWith("keySignature") -> if (fifths == null) fifths = first.substringAfter("_").toIntOrNull()
                else -> {}
            }
        }
        if (sawContent || t > Frac.ZERO) measureDurations.add(t)
        return VoiceParse(events, measureDurations, timeDen, fifths)
    }

    private fun median(v: List<Frac>): Frac {
        val s = v.sorted()
        return s[s.size / 2]
    }

    private class Stream(val notes: MutableList<AbsNote>)
    private class AbsNote(var start: Frac, var dur: Frac, val midi: Int, val slur: String)

    /** Un pentagrama de un sistema, ya interpretado. */
    private class Line(val parse: VoiceParse, val isGrand: Boolean)
    private class SystemParse(val lines: List<Line>, val bars: Int, val streamCount: Int)

    /** @param pages por página, los sistemas con los símbolos que devuelve [OmrEngine.readPage]. */
    fun build(pages: List<List<SystemTokens>>, id: String, title: String, bpmOverride: Int? = null): Result {
        val warnings = ArrayList<String>()
        val systems = pages.flatten().mapNotNull { sys ->
            val lines = sys.staves.filter { it.tokens.isNotEmpty() }.map { st ->
                Line(parseVoice(TokenOps.removeDuplicatedSymbols(st.tokens + EncodedSymbol("newline"))), st.isGrand)
            }
            if (lines.isEmpty()) null
            else SystemParse(lines, lines.maxOf { it.parse.measureDurations.size }.coerceAtLeast(1), lines.map { if (it.isGrand) 2 else 1 }.sum())
        }
        if (systems.isEmpty()) throw OmrException("No se ha encontrado música en la partitura")

        // número de pentagramas «normal» (el más frecuente); los sistemas distintos se leen por orden con un aviso
        val streamTarget = systems.groupBy { it.streamCount }.maxByOrNull { it.value.size }!!.key
        for ((i, sp) in systems.withIndex()) {
            if (sp.streamCount != streamTarget) warnings.add("El sistema ${i + 1} tiene ${sp.streamCount} pentagramas y se esperaban $streamTarget; revisa esa parte.")
        }

        // compás: denominador del símbolo de compás y duración típica de los compases
        val allParses = systems.flatMap { s -> s.lines.map { it.parse } }
        val allDurations = allParses.flatMap { p -> p.measureDurations.filterIndexed { i, _ -> i in p.noteMeasures } }.filter { it > Frac.ZERO }
        if (allDurations.isEmpty()) throw OmrException("No se ha encontrado música en la partitura")
        val typical = median(allDurations)
        val den = allParses.firstNotNullOfOrNull { it.timeDenominator } ?: 4
        val num = Math.round(typical.toDouble() * den).toInt().coerceAtLeast(1)
        val nominal = Frac.of(num.toLong(), den.toLong())
        val fifths = allParses.firstNotNullOfOrNull { it.fifths } ?: 0

        // compases (globales) de cada sistema; un compás partido entre dos líneas cuenta como uno solo
        val barOffsets = ArrayList<Int>()
        val shifts = ArrayList<Frac>()      // desplazamiento del primer compás de un sistema que continúa el anterior
        val continued = ArrayList<Boolean>()
        var acc = 0
        for ((i, sp) in systems.withIndex()) {
            var cont = false
            var shift = Frac.ZERO
            if (i > 0) {
                val prevLast = systems[i - 1].lines.mapNotNull { it.parse.measureDurations.lastOrNull() }.maxOrNull() ?: Frac.ZERO
                val firstCur = sp.lines.mapNotNull { it.parse.measureDurations.firstOrNull() }.maxOrNull() ?: Frac.ZERO
                if (prevLast > Frac.ZERO && prevLast < nominal && firstCur > Frac.ZERO && prevLast + firstCur == nominal) { cont = true; shift = prevLast }
            }
            continued.add(cont); shifts.add(shift)
            barOffsets.add(if (cont) acc - 1 else acc)
            acc += if (cont) sp.bars - 1 else sp.bars
        }
        val barCount = acc

        // anacrusa: el primer compás de la partitura es más corto que el nominal y el último lo completa
        val d0 = systems[0].lines.mapNotNull { it.parse.measureDurations.firstOrNull() }.maxOrNull() ?: Frac.ZERO
        var pickup = Frac.ZERO
        if (barCount > 1 && d0 > Frac.ZERO && d0 < nominal) {
            val lastDurs = systems.last().lines.mapNotNull { it.parse.measureDurations.lastOrNull() }
            // anacrusa si el último compás la completa o si es claramente corta (un compás inicial muy corto casi siempre es una anacrusa)
            if (lastDurs.any { it + d0 == nominal } || d0 * Frac.of(2) <= nominal) pickup = d0
        }
        fun barStart(k: Int): Frac = if (pickup > Frac.ZERO) (if (k == 0) Frac.ZERO else pickup + nominal * Frac.of((k - 1).toLong())) else nominal * Frac.of(k.toLong())

        // compases irregulares (el modelo a veces lee mal una figura)
        var irregular = 0
        for ((si, s) in systems.withIndex()) {
            for ((li, line) in s.lines.withIndex()) {
                for ((mi, d) in line.parse.measureDurations.withIndex()) {
                    val global = barOffsets[si] + mi
                    val expected = if (global == 0 && pickup > Frac.ZERO) pickup else nominal
                    val last = si == systems.size - 1 && mi == line.parse.measureDurations.size - 1
                    val splitBar = (mi == 0 && continued[si]) || (mi == line.parse.measureDurations.size - 1 && si + 1 < systems.size && continued[si + 1])
                    if (mi in line.parse.noteMeasures && d != expected && d > Frac.ZERO && !(last && d < expected) && !splitBar) {
                        if (irregular++ < 8) warnings.add("Compás ${global + 1} (pentagrama ${li + 1}): dura ${fmt(d)} y debería durar ${fmt(expected)}. Revisa esa parte.")
                    }
                }
            }
        }
        if (irregular > 8) warnings.add("…y ${irregular - 8} compases irregulares más.")

        // flujos de notas (voz o mano) con tiempos absolutos, alineados por posición dentro del sistema
        val streams = List(streamTarget) { Stream(ArrayList()) }
        for ((si, s) in systems.withIndex()) {
            var idx = 0
            for (line in s.lines) {
                val sides = if (line.isGrand) listOf(false, true) else listOf(false)
                for (lower in sides) {
                    val target = streams.getOrNull(idx++) ?: continue
                    for (e in line.parse.events) {
                        if (line.isGrand && e.lower != lower) continue
                        target.notes.add(AbsNote(barStart(barOffsets[si] + e.measure) + e.t + (if (e.measure == 0) shifts[si] else Frac.ZERO), e.dur, e.midi, e.slur))
                    }
                }
            }
        }
        if (streams.all { it.notes.isEmpty() }) throw OmrException("No se ha detectado ninguna nota")
        for (st in streams) mergeTies(st)

        // reparto en melodía / mano derecha / mano izquierda
        var melody: Stream? = null
        val piano: List<Stream>
        if (streams.size == 3 && isMonophonic(streams[0])) { melody = streams[0]; piano = streams.drop(1) } else piano = streams
        val rhStreams: List<Stream>; val lhStreams: List<Stream>
        when {
            piano.size == 1 -> { rhStreams = piano; lhStreams = emptyList() }
            piano.size == 2 -> { rhStreams = listOf(piano[0]); lhStreams = listOf(piano[1]) }
            else -> { val half = (piano.size + 1) / 2; rhStreams = piano.take(half); lhStreams = piano.drop(half) }
        }

        fun toBeats(f: Frac): Float = (Math.round(f.toDouble() * 4.0 * 96) / 96.0).toFloat()
        fun notesOf(ss: List<Stream>, hand: Hand): List<SongNote> =
            ss.flatMap { s -> s.notes.filter { it.midi in 21..108 }.map { SongNote(toBeats(it.start), toBeats(it.dur), it.midi, hand) } }
                .sortedWith(compareBy({ it.start }, { it.pitch }))
        val rh = notesOf(rhStreams, Hand.RIGHT)
        val lh = notesOf(lhStreams, Hand.LEFT)
        var mel = melody?.let { skyline(notesOf(listOf(it), Hand.RIGHT)) }
        if (mel == null && rh.groupBy { it.start }.any { it.value.size > 1 }) mel = skyline(rh)
        if (rh.isEmpty() && mel.isNullOrEmpty()) throw OmrException("No se ha detectado ninguna nota")

        val starts = (0 until barCount).map { toBeats(barStart(it)) }
        val total = toBeats(barStart(barCount - 1) + (if (barCount == 1 && pickup > Frac.ZERO) pickup else nominal))
        val bass = bassPerBar(lh, starts, total)

        val beatsPerBar = toBeats(nominal)
        val compound = den == 8 && num in listOf(6, 9, 12)
        val bpm = bpmOverride ?: 80
        val all = rh + lh + (mel ?: emptyList())
        val dens = all.size / max(1.0, total * 60.0 / bpm)
        val difficulty = if (dens < 1.5) 1 else if (dens < 3.5) 2 else if (dens < 6) 3 else 4
        val key = MAJOR_NAMES[((fifths * 7) % 12 + 12) % 12]
        val song = Song(
            id = id, title = title, composer = "Partitura importada", era = "", work = "", key = key, originalKey = key,
            meter = "$num/$den", bpm = bpm, difficulty = difficulty,
            tip = "Leída de una partitura. Empieza por el nivel 1 y baja la velocidad.", fun_ = "",
            source = SongSource.PDF, pulse = if (compound) 1.5f else 1f, beatsPerBar = beatsPerBar, pickup = toBeats(pickup),
            totalBeats = total, durationSec = (total * 60f / bpm * 10).let { Math.round(it) / 10f }, barStarts = starts,
            rh = rh, melody = mel, lh = lh, bass = bass, order = 1000,
        )
        return Result(song, warnings)
    }

    private fun fmt(f: Frac): String {
        val beats = f.toDouble() * 4
        return if (beats == Math.rint(beats)) "${beats.toInt()} pulsos" else "%.2f pulsos".format(beats)
    }

    /** Une las notas ligadas: misma altura, la segunda empieza donde acaba la primera y el modelo marca la ligadura. */
    private fun mergeTies(s: Stream) {
        s.notes.sortWith(compareBy<AbsNote>({ it.start.toDouble() }, { it.midi }))
        var i = 0
        while (i < s.notes.size) {
            val a = s.notes[i]
            if (a.slur.contains("slurStart")) {
                val end = a.start + a.dur
                val j = s.notes.indexOfFirst { it !== a && it.midi == a.midi && it.start == end && it.slur.contains("slurStop") }
                if (j >= 0) {
                    val b = s.notes[j]
                    a.dur = a.dur + b.dur
                    s.notes.removeAt(j)
                    continue
                }
            }
            i++
        }
    }

    private fun isMonophonic(s: Stream): Boolean {
        val byStart = s.notes.groupBy { it.start }
        val chords = byStart.values.count { it.size > 1 }
        return byStart.isNotEmpty() && chords <= byStart.size * 0.05
    }

    /** La nota más aguda de cada instante, sin solapes. */
    private fun skyline(notes: List<SongNote>): List<SongNote> {
        val top = notes.groupBy { it.start }.map { (_, g) -> g.maxByOrNull { it.pitch }!! }.sortedBy { it.start }.toMutableList()
        for (i in top.indices) {
            if (i + 1 < top.size && top[i].end > top[i + 1].start) top[i] = top[i].copy(dur = max(0.1f, top[i + 1].start - top[i].start))
        }
        return top
    }

    /** Una nota de bajo por compás: la más grave del primer ataque (nivel 2). */
    private fun bassPerBar(lh: List<SongNote>, starts: List<Float>, total: Float): List<SongNote> {
        val bass = ArrayList<SongNote>()
        for ((i, a) in starts.withIndex()) {
            val b = if (i + 1 < starts.size) starts[i + 1] else total
            val inside = lh.filter { it.start >= a - 1e-4f && it.start < b - 1e-4f }
            if (inside.isEmpty()) continue
            val first = inside.minOf { it.start }
            val lowest = inside.filter { abs(it.start - first) < 1e-4f }.minOf { it.pitch }
            val ini = if (first - a < 0.5f) a else first
            bass.add(SongNote(ini, b - ini, lowest, Hand.LEFT))
        }
        return bass
    }
}
