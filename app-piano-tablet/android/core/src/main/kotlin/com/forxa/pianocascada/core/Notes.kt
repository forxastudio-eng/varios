package com.forxa.pianocascada.core

/** Utilidades de notas MIDI (21 = A0 … 108 = C8). Los nombres usan letras: C D E F G A B (con #). */
object Notes {
    const val MIN = 21
    const val MAX = 108
    const val MIDDLE_C = 60
    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val WHITE_PC = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    fun isBlack(p: Int): Boolean = when (((p % 12) + 12) % 12) {
        1, 3, 6, 8, 10 -> true
        else -> false
    }

    fun name(p: Int): String = NAMES[((p % 12) + 12) % 12]
    fun octave(p: Int): Int = Math.floorDiv(p, 12) - 1
    fun nameWithOctave(p: Int): String = name(p) + octave(p)

    /** Índice de tecla blanca global: A0 = 0, B0 = 1, C1 = 2 … C8 = 51 (para teclas blancas). */
    fun whiteIndex(p: Int): Int {
        require(!isBlack(p)) { "no es una tecla blanca: $p" }
        val oct = Math.floorDiv(p, 12)
        val pc = ((p % 12) + 12) % 12
        val inOct = WHITE_PC.indexOf(pc)
        // C0 (pitch 12) tendría índice -2 respecto a A0 (pitch 21)... se calcula contando blancas desde A0
        return oct * 7 + inOct - (1 * 7 + 5)  // A0: oct 1, inOct 5 -> 0
    }

    fun pitchOfWhiteIndex(i: Int): Int {
        val k = i + 1 * 7 + 5
        val oct = Math.floorDiv(k, 7)
        val inOct = ((k % 7) + 7) % 7
        return oct * 12 + WHITE_PC[inOct]
    }

    const val WHITE_KEYS = 52

    fun whiteAtOrBelow(p: Int): Int = if (isBlack(p)) p - 1 else p
    fun whiteAtOrAbove(p: Int): Int = if (isBlack(p)) p + 1 else p

    fun whiteCount(lo: Int, hi: Int): Int = whiteIndex(whiteAtOrBelow(hi)) - whiteIndex(whiteAtOrAbove(lo)) + 1

    fun clampPiano(p: Int): Int = p.coerceIn(MIN, MAX)
}
