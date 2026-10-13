package com.forxa.pianocascada.omr

import com.forxa.pianocascada.core.Json
import com.forxa.pianocascada.core.asMap

/* Símbolos que devuelve el modelo y su limpieza: traducción de homr/transformer/vocabulary.py. */

const val NONOTE = "."
const val EMPTY = "_"

/** Fracción exacta (en redondas). */
class Frac private constructor(val num: Long, val den: Long) : Comparable<Frac> {
    companion object {
        val ZERO = Frac(0, 1)
        fun of(n: Long, d: Long = 1): Frac {
            require(d != 0L)
            var nn = n; var dd = d
            if (dd < 0) { nn = -nn; dd = -dd }
            val g = gcd(Math.abs(nn), dd)
            return if (g > 1) Frac(nn / g, dd / g) else Frac(nn, dd)
        }
        private fun gcd(a: Long, b: Long): Long { var x = a; var y = b; while (y != 0L) { val t = x % y; x = y; y = t }; return if (x == 0L) 1 else x }
    }
    operator fun plus(o: Frac) = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: Frac) = of(num * o.den - o.num * den, den * o.den)
    operator fun times(o: Frac) = of(num * o.num, den * o.den)
    operator fun div(o: Frac) = of(num * o.den, den * o.num)
    override fun compareTo(other: Frac): Int = (num * other.den).compareTo(other.num * den)
    override fun equals(other: Any?) = other is Frac && num == other.num && den == other.den
    override fun hashCode() = num.hashCode() * 31 + den.hashCode()
    fun toDouble() = num.toDouble() / den
    override fun toString() = "$num/$den"
}

class SymbolDuration(val baseDuration: Frac, val dots: Int, actualNotes: Int, normalNotes: Int, val kern: Int) {
    val actualNotes: Int
    val normalNotes: Int
    val fraction: Frac

    init {
        val an = Frac.of(actualNotes.toLong(), normalNotes.toLong())
        this.actualNotes = an.num.toInt()
        this.normalNotes = an.den.toInt()
        var dur = baseDuration
        var add = dur / Frac.of(2)
        repeat(dots) { dur += add; add /= Frac.of(2) }
        if (this.actualNotes != this.normalNotes) dur *= Frac.of(this.normalNotes.toLong(), this.actualNotes.toLong())
        fraction = dur
    }
}

private fun priorPowerOfTwo(n: Int): Int = if (n < 1) 1 else Integer.highestOneBit(n)

fun kernToSymbolDuration(kern: String): SymbolDuration {
    var i = 0
    while (i < kern.length && kern[i].isDigit()) i++
    val baseStr = kern.substring(0, i)
    val rest = kern.substring(i)
    val base = if (baseStr.isEmpty()) 4 else baseStr.toInt()
    val dots = rest.count { it == '.' }
    if ('G' in kern) return SymbolDuration(Frac.ZERO, dots, 1, 1, base)
    if (base == 0) return SymbolDuration(Frac.of(1), dots, 1, 1, base)
    if (base and (base - 1) == 0) return SymbolDuration(Frac.of(1, base.toLong()), dots, 1, 1, base)
    val normal = priorPowerOfTwo(base)
    return SymbolDuration(Frac.of(1, normal.toLong()), dots, base, normal, normal)
}

fun hasRhythmSymbolAPosition(rhythm: String) = rhythm.startsWith("note") || rhythm.startsWith("rest") || rhythm.startsWith("clef")

/** Un símbolo musical dividido en las ramas del decodificador. */
class EncodedSymbol(
    val rhythm: String,
    val pitch: String = NONOTE,
    val lift: String = NONOTE,
    val articulation: String = NONOTE,
    val slur: String = NONOTE,
    val position: String = NONOTE,
) {
    private var cachedDuration: SymbolDuration? = null

    fun duration(): SymbolDuration {
        cachedDuration?.let { return it }
        if (!rhythm.startsWith("note") && !rhythm.startsWith("rest")) return SymbolDuration(Frac.ZERO, 0, 1, 1, 1)
        val d = kernToSymbolDuration(rhythm.split("_")[1])
        cachedDuration = d
        return d
    }

    fun copy(rhythm: String = this.rhythm, position: String = this.position) =
        EncodedSymbol(rhythm, pitch, lift, articulation, slur, position)

    fun removeTuplet(): EncodedSymbol {
        val m = Regex("(note|rest)_(\\d+)(.*)").matchEntire(rhythm) ?: return this
        var duration = m.groupValues[2].toInt()
        duration = when {
            duration % 3 == 0 -> duration / 3 * 2
            duration % 5 == 0 -> duration / 5 * 4
            duration % 7 == 0 -> duration / 7 * 4
            else -> return this
        }
        return copy(rhythm = m.groupValues[1] + "_" + duration + m.groupValues[3])
    }

    fun toUpperPosition(): EncodedSymbol = if (position != "lower") this else copy(position = "upper")

    fun str() = "$rhythm $pitch $lift $articulation $slur $position"
    override fun toString() = str()
}

object TokenOps {
    private fun groupIntoChords(symbols: List<EncodedSymbol>): MutableList<MutableList<EncodedSymbol>> {
        val chords = ArrayList<MutableList<EncodedSymbol>>()
        var inChord = false
        for (s in symbols) {
            if (s.rhythm == "chord") inChord = true
            else if (inChord && chords.isNotEmpty()) { chords.last().add(s); inChord = false }
            else chords.add(arrayListOf(s))
        }
        return chords
    }

    private fun flattenChords(chords: List<List<EncodedSymbol>>): List<EncodedSymbol> {
        val result = ArrayList<EncodedSymbol>()
        for (chord in chords) {
            var inChord = false
            for (s in chord) {
                if (inChord) result.add(EncodedSymbol("chord"))
                result.add(s)
                inChord = true
            }
        }
        return result
    }

    private fun groupIntoMeasures(chords: List<List<EncodedSymbol>>): List<List<List<EncodedSymbol>>> {
        val measures = ArrayList<List<List<EncodedSymbol>>>()
        var current = ArrayList<List<EncodedSymbol>>()
        for (c in chords) {
            current.add(c)
            if (c.isNotEmpty() && ("barline" in c[0].rhythm || "repeat" in c[0].rhythm)) { measures.add(current); current = ArrayList() }
        }
        if (current.isNotEmpty()) measures.add(current)
        return measures
    }

    private fun durationOfMeasure(measure: List<List<EncodedSymbol>>): Frac {
        var total = Frac.ZERO
        for (chord in measure) {
            var d = Frac.ZERO
            for (s in chord) {
                if (s.rhythm.startsWith("note") || s.rhythm.startsWith("rest")) {
                    val f = s.duration().fraction
                    if (f > Frac.ZERO && (f < d || d == Frac.ZERO)) d = f
                }
            }
            total += d
        }
        return total
    }

    private fun fixOverEagerTuplets(chords: List<List<EncodedSymbol>>): List<List<EncodedSymbol>> {
        val measures = groupIntoMeasures(chords)
        val durations = measures.map { durationOfMeasure(it) }
        val mean = if (durations.isEmpty()) Frac.ZERO else durations.sorted()[durations.size / 2]
        val result = ArrayList<List<EncodedSymbol>>()
        for (m in measures) {
            if (durationOfMeasure(m) < mean) result.addAll(m.map { chord -> chord.map { it.removeTuplet() } })
            else result.addAll(m)
        }
        return result
    }

    private fun onlyKeepLowerStaffIfThereIsAClef(chords: List<List<EncodedSymbol>>): List<List<EncodedSymbol>> {
        var hasLowerClef = false
        val all = ArrayList<List<EncodedSymbol>>()
        for ((i, chord) in chords.withIndex()) {
            val r = ArrayList<EncodedSymbol>()
            for (s in chord) {
                if (hasLowerClef) r.add(s)
                else if (i < 5 && s.rhythm.startsWith("clef") && s.position == "lower") { hasLowerClef = true; r.add(s) }
                else r.add(s.toUpperPosition())
            }
            all.add(r)
        }
        return all
    }

    private fun removeDuplicatedPitches(chord: List<EncodedSymbol>): List<EncodedSymbol> {
        if (chord.size <= 1 || !(chord[0].rhythm.startsWith("note") || chord[0].rhythm.startsWith("rest"))) return chord
        val seen = LinkedHashMap<String, EncodedSymbol>()
        for (s in chord) {
            val key = s.pitch + " " + s.position
            if (key !in seen) seen[key] = s   // (homr conserva siempre el primero)
        }
        return seen.values.toList()
    }

    private fun removeRedundantClefsKeysAndTimeSignatures(chords: List<List<EncodedSymbol>>): List<List<EncodedSymbol>> {
        var clefUpper = ""; var clefLower = ""; var key = ""; var time = ""
        val result = ArrayList<List<EncodedSymbol>>()
        for (chord in chords) {
            val r = ArrayList<EncodedSymbol>()
            for (s in chord) {
                if (s.rhythm.startsWith("clef")) {
                    if (s.position == "upper") { if (s.rhythm != clefUpper) { clefUpper = s.rhythm; r.add(s) } }
                    else if (s.rhythm != clefLower) { clefLower = s.rhythm; r.add(s) }
                } else if (s.rhythm.startsWith("keySignature")) {
                    if (s.rhythm != key) { key = s.rhythm; r.add(s) }
                } else if (s.rhythm.startsWith("timeSignature")) {
                    if (s.rhythm != time) { time = s.rhythm; r.add(s) }
                } else r.add(s)
            }
            result.add(r)
        }
        return result
    }

    fun removeDuplicatedSymbols(symbols: List<EncodedSymbol>): List<EncodedSymbol> {
        var chords: List<List<EncodedSymbol>> = groupIntoChords(symbols)
        chords = fixOverEagerTuplets(chords)
        chords = onlyKeepLowerStaffIfThereIsAClef(chords)
        chords = chords.map { removeDuplicatedPitches(it) }
        chords = removeRedundantClefsKeysAndTimeSignatures(chords)
        return flattenChords(chords)
    }

    /** Grupos (acordes) con sus símbolos ordenados como en homr (cadena descendente). */
    fun sortTokenChords(symbols: List<EncodedSymbol>): List<List<EncodedSymbol>> =
        groupIntoChords(symbols).map { g -> g.sortedWith(compareByDescending<EncodedSymbol> { it.str() }) }
}

/** Vocabulario del modelo (identificador ↔ símbolo) leído de homr_vocab.json. */
class Vocab private constructor(val rhythm: Array<String>, val pitch: Array<String>, val lift: Array<String>,
                                val articulation: Array<String>, val slur: Array<String>, val position: Array<String>) {
    companion object {
        fun load(): Vocab {
            val text = Vocab::class.java.getResourceAsStream("/homr_vocab.json")!!.use { String(it.readBytes(), Charsets.UTF_8) }
            val root = Json.parse(text).asMap()
            fun inv(name: String): Array<String> {
                val m = root[name].asMap()
                val arr = Array(m.size) { "" }
                for ((k, v) in m) arr[(v as Double).toInt()] = k
                return arr
            }
            return Vocab(inv("rhythm"), inv("pitch"), inv("lift"), inv("articulation"), inv("slur"), inv("position"))
        }
    }
}
