package com.forxa.pianocascada.core

data class WhiteKeyPlan(val shift: Int, val blackBefore: Int, val blackAfter: Int) {
    val allWhite: Boolean get() = blackAfter == 0
    val helps: Boolean get() = shift != 0 && blackAfter < blackBefore
}

object Transposer {
    private val NAMES = arrayOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    fun blackCount(pitches: Collection<Int>): Int = pitches.count { Notes.isBlack(it) }

    /**
     * Busca el desplazamiento (−6…+5 semitonos) que deja menos teclas negras y mantiene todas las notas
     * dentro del piano. En empate gana el desplazamiento más pequeño.
     */
    fun bestWhiteKeyShift(pitches: Collection<Int>): WhiteKeyPlan {
        val before = blackCount(pitches)
        if (pitches.isEmpty()) return WhiteKeyPlan(0, 0, 0)
        var best = WhiteKeyPlan(0, before, before)
        for (shift in -6..5) {
            if (shift == 0) continue
            if (pitches.any { it + shift < Notes.MIN || it + shift > Notes.MAX }) continue
            val after = pitches.count { Notes.isBlack(it + shift) }
            val better = after < best.blackAfter ||
                (after == best.blackAfter && best.shift != 0 && kotlin.math.abs(shift) < kotlin.math.abs(best.shift))
            if (better) best = WhiteKeyPlan(shift, before, after)
        }
        return best
    }

    /** "Bb", "F#m", "C" → (tónica 0..11, menor?) */
    fun parseKey(key: String): Pair<Int, Boolean> {
        val minor = key.endsWith("m") && key.length > 1
        val root = if (minor) key.dropLast(1) else key
        val letter = when (root[0]) { 'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11; else -> 0 }
        var pc = letter
        if (root.length > 1) pc += when (root[1]) { '#' -> 1; 'b' -> -1; else -> 0 }
        return ((pc % 12) + 12) % 12 to minor
    }

    fun transposeKeyName(key: String, shift: Int): String {
        val (pc, minor) = parseKey(key)
        val n = NAMES[(((pc + shift) % 12) + 12) % 12]
        return if (minor) n + "m" else n
    }

    /** Texto para la interfaz: "D mayor", "A menor". */
    fun displayKey(key: String): String {
        val (pc, minor) = parseKey(key)
        return NAMES[pc] + if (minor) " menor" else " mayor"
    }
}
