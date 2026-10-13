package com.forxa.pianocascada.core

data class KeyRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
}

/**
 * Una fila de teclado con geometría real: teclas blancas iguales y negras más cortas, centradas en la frontera
 * entre dos blancas. [lo] y [hi] deben ser teclas blancas. [x] puede ser negativo (desplazamiento suave).
 */
class KeyRow(
    val lo: Int,
    val hi: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val blackWidthRatio: Float = 0.62f,
    val blackHeightRatio: Float = 0.62f,
) {
    val whitePitches: IntArray
    val whiteCount: Int
    val whiteWidth: Float
    val blackWidth: Float
    val blackHeight: Float
    private val whiteIdxOf = HashMap<Int, Int>()

    init {
        require(!Notes.isBlack(lo) && !Notes.isBlack(hi)) { "los extremos deben ser teclas blancas ($lo..$hi)" }
        require(hi > lo) { "rango vacío" }
        val l = ArrayList<Int>()
        for (p in lo..hi) if (!Notes.isBlack(p)) { whiteIdxOf[p] = l.size; l.add(p) }
        whitePitches = l.toIntArray()
        whiteCount = l.size
        whiteWidth = width / whiteCount
        blackWidth = whiteWidth * blackWidthRatio
        blackHeight = height * blackHeightRatio
    }

    fun contains(pitch: Int) = pitch in lo..hi

    /** x de la frontera izquierda de la tecla blanca número [i]. */
    private fun edge(i: Int) = x + i * whiteWidth

    fun rect(pitch: Int): KeyRect? {
        if (!contains(pitch)) return null
        return if (Notes.isBlack(pitch)) {
            val i = whiteIdxOf[pitch - 1] ?: return null
            val c = edge(i + 1)
            KeyRect(c - blackWidth / 2f, y, c + blackWidth / 2f, y + blackHeight)
        } else {
            val i = whiteIdxOf[pitch]!!
            KeyRect(edge(i), y, edge(i + 1), y + height)
        }
    }

    fun centerX(pitch: Int): Float = rect(pitch)?.centerX ?: Float.NaN

    /** Tecla que hay en (px, py), o null si está fuera. Las negras tienen prioridad en su zona. */
    fun pitchAt(px: Float, py: Float): Int? {
        if (py < y || py >= y + height || px < x || px >= x + width) return null
        val wi = ((px - x) / whiteWidth).toInt().coerceIn(0, whiteCount - 1)
        if (py < y + blackHeight) {
            // fronteras vecinas: la izquierda (wi) y la derecha (wi + 1)
            for (b in intArrayOf(wi, wi + 1)) {
                if (b <= 0 || b >= whiteCount) continue
                val left = whitePitches[b - 1]
                val right = whitePitches[b]
                if (right - left == 2) {
                    val c = edge(b)
                    if (px >= c - blackWidth / 2f && px < c + blackWidth / 2f) return left + 1
                }
            }
        }
        return whitePitches[wi]
    }

    companion object {
        /** Fila para una ventana que empieza en la tecla blanca global [leftWhite] (puede ser fraccionaria). */
        fun window(leftWhite: Float, spanWhite: Float, x: Float, y: Float, width: Float, height: Float,
                   blackW: Float = 0.62f, blackH: Float = 0.62f): KeyRow {
            val wk = width / spanWhite
            val first = kotlin.math.floor(leftWhite).toInt().coerceIn(0, Notes.WHITE_KEYS - 2)
            val last = kotlin.math.ceil(leftWhite + spanWhite).toInt().coerceIn(first + 1, Notes.WHITE_KEYS - 1)
            val lo = Notes.pitchOfWhiteIndex(first)
            val hi = Notes.pitchOfWhiteIndex(last)
            val rowW = wk * (last - first + 1)
            return KeyRow(lo, hi, x - (leftWhite - first) * wk, y, rowW, height, blackW, blackH)
        }
    }
}

data class PitchRange(val lo: Int, val hi: Int) {
    val whiteCount: Int get() = Notes.whiteCount(lo, hi)
}

object KeyboardPlanner {
    /** Las 88 teclas en dos filas iguales. Con split = F4 (65) quedan 26 teclas blancas en cada una. */
    fun twoRows(split: Int = 65): Pair<PitchRange, PitchRange> {
        require(!Notes.isBlack(split) && !Notes.isBlack(split - 1)) { "el corte debe caer entre dos teclas blancas" }
        return PitchRange(Notes.MIN, split - 1) to PitchRange(split, Notes.MAX)
    }

    /**
     * Dos filas **a medida de la pieza**: cubren justo el rango [minPitch]..[maxPitch] y no las 88 teclas.
     * Las dos filas tienen el mismo número de teclas blancas, así que son del mismo tamaño y llenan todo el ancho
     * de la pantalla: una pieza de 4 octavas da 14 teclas por fila, de unos 2,2 cm en una tablet de 14,6 pulgadas
     * (casi como un piano de verdad), mientras que las 88 teclas en dos filas dan teclas de 1,2 cm.
     * El corte entre filas cae donde no hay tecla negra (entre E y F o entre B y C) para no dejar ninguna huérfana.
     * Devuelve (fila de abajo = graves, fila de arriba = agudos) y el número de teclas blancas por fila.
     */
    fun twoRowsFit(minPitch: Int, maxPitch: Int, minWhitePerRow: Int = 9): Triple<PitchRange, PitchRange, Int> {
        val a = Notes.whiteIndex(Notes.whiteAtOrBelow(Notes.clampPiano(minPitch)))
        val b = Notes.whiteIndex(Notes.whiteAtOrAbove(Notes.clampPiano(maxPitch)))
        val total = Notes.WHITE_KEYS
        fun cutOk(c: Int) = c in 1 until total && Notes.pitchOfWhiteIndex(c) - Notes.pitchOfWhiteIndex(c - 1) == 1
        val n = maxOf(1, b - a + 1)
        for (m in maxOf(minWhitePerRow, (n + 1) / 2)..26) {
            var best = -1
            var bestBalance = Int.MAX_VALUE
            for (c in m until total - m + 1) {
                if (!cutOk(c) || c - m > a || c + m - 1 < b) continue
                val balance = kotlin.math.abs((c - a) - (b - c + 1))
                if (balance < bestBalance) { bestBalance = balance; best = c }
            }
            if (best >= 0) return rowsFor(best, m)
        }
        return rowsFor(26, 26)       // las 88 teclas: corte en F4/E4
    }

    private fun rowsFor(cut: Int, m: Int): Triple<PitchRange, PitchRange, Int> = Triple(
        PitchRange(Notes.pitchOfWhiteIndex(cut - m), Notes.pitchOfWhiteIndex(cut - 1)),
        PitchRange(Notes.pitchOfWhiteIndex(cut), Notes.pitchOfWhiteIndex(cut + m - 1)),
        m,
    )

    /**
     * Ventana del teclado reducido: [spanWhite] teclas blancas centradas en el rango de la canción.
     * Devuelve el índice de tecla blanca global del borde izquierdo (se anima en la vista).
     */
    fun reducedLeft(minPitch: Int, maxPitch: Int, spanWhite: Int): Float {
        val a = Notes.whiteIndex(Notes.whiteAtOrBelow(minPitch)).toFloat()
        val b = Notes.whiteIndex(Notes.whiteAtOrAbove(maxPitch)).toFloat()
        val center = (a + b) / 2f + 0.5f
        return clampLeft(center - spanWhite / 2f, spanWhite)
    }

    fun clampLeft(left: Float, spanWhite: Int): Float =
        left.coerceIn(0f, (Notes.WHITE_KEYS - spanWhite).toFloat())

    /**
     * Seguimiento: dado el conjunto de notas que llegan pronto, devuelve el borde izquierdo ideal para que
     * todas queden visibles (o, si no caben, las más cercanas). [current] evita mover si ya se ven.
     */
    fun followLeft(upcoming: Collection<Int>, current: Float, spanWhite: Int): Float {
        if (upcoming.isEmpty()) return current
        val idx = upcoming.map { Notes.whiteIndex(Notes.whiteAtOrBelow(it)).toFloat() }
        val mn = idx.min()
        val mx = idx.max() + 1f
        val visible = mn >= current && mx <= current + spanWhite
        if (visible) return current
        return if (mx - mn <= spanWhite) clampLeft((mn + mx) / 2f - spanWhite / 2f, spanWhite)
        else clampLeft(mn - 1f, spanWhite)
    }
}
