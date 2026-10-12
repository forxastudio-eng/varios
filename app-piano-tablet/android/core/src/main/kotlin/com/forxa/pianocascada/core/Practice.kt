package com.forxa.pianocascada.core

enum class PracticeMode { WAIT, RHYTHM, LISTEN }

enum class NoteResult { PENDING, HIT, MISSED }

enum class Feedback { CORRECT, WRONG, EARLY_IGNORED, EXTRA }

/** Nota que el usuario debe tocar. Tiempos en ms de canción (a velocidad 100 %). */
data class PNote(val index: Int, val startMs: Double, val durMs: Double, val pitch: Int, val hand: Hand)

data class KeyEvent(val feedback: Feedback, val noteIndex: Int = -1, val deltaMs: Double = 0.0)

/**
 * Lógica de una sesión de práctica, sin dependencias de Android.
 *
 *  - WAIT: la música se detiene en [gateMs] hasta que se tocan las notas correctas (acorde completo).
 *  - RHYTHM: la música sigue; cada nota debe tocarse dentro de ±[tolMs] de su momento.
 *  - LISTEN: no hay puntuación.
 */
class PracticeSession(
    source: List<SongNote>,
    songMsPerBeat: Double,
    val mode: PracticeMode,
    val tolMs: Double = 120.0,
    val earlyAcceptMs: Double = 450.0,
) {
    val notes: List<PNote>
    val result: Array<NoteResult>
    val deltaMs: DoubleArray
    val heldRatio: FloatArray
    private val hitAtMs: DoubleArray
    private val events: List<IntArray>
    private val evStart: DoubleArray
    private val evError: BooleanArray
    private var cur = 0
    private var scan = 0
    private val holding = IntArray(128) { -1 }

    var wrongPresses = 0; private set
    var combo = 0; private set
    var bestCombo = 0; private set
    var hits = 0; private set
    var misses = 0; private set
    var perfect = 0; private set

    init {
        // quita duplicados exactos (misma tecla y mismo instante)
        val sorted = source.sortedWith(compareBy<SongNote>({ it.start }, { it.pitch }))
        val dedup = ArrayList<SongNote>()
        for (n in sorted) {
            val last = dedup.lastOrNull()
            if (last != null && last.pitch == n.pitch && kotlin.math.abs(last.start - n.start) < 1e-3f) {
                if (n.dur > last.dur) dedup[dedup.size - 1] = n
            } else dedup.add(n)
        }
        notes = dedup.mapIndexed { i, n ->
            PNote(i, n.start * songMsPerBeat, n.dur * songMsPerBeat, n.pitch, n.hand)
        }
        result = Array(notes.size) { NoteResult.PENDING }
        deltaMs = DoubleArray(notes.size)
        heldRatio = FloatArray(notes.size) { -1f }
        hitAtMs = DoubleArray(notes.size)
        // agrupa en eventos (acordes) por instante
        val ev = ArrayList<IntArray>()
        var group = ArrayList<Int>()
        for (n in notes) {
            if (group.isNotEmpty() && n.startMs - notes[group[0]].startMs > 25.0) {
                ev.add(group.toIntArray()); group = ArrayList()
            }
            group.add(n.index)
        }
        if (group.isNotEmpty()) ev.add(group.toIntArray())
        events = ev
        evStart = DoubleArray(ev.size) { notes[ev[it][0]].startMs }
        evError = BooleanArray(ev.size)
    }

    val total: Int get() = notes.size

    /** Punto donde la música debe detenerse (solo en modo WAIT). */
    val gateMs: Double get() = if (mode == PracticeMode.WAIT && cur < events.size) evStart[cur] else 1e18

    /** Notas (tecla) que se esperan ahora en modo WAIT, para resaltarlas en el teclado. */
    fun pendingPitches(): List<Int> =
        if (mode == PracticeMode.WAIT && cur < events.size)
            events[cur].filter { result[it] == NoteResult.PENDING }.map { notes[it].pitch }
        else emptyList()

    fun isFinished(): Boolean = when (mode) {
        PracticeMode.WAIT -> cur >= events.size
        PracticeMode.RHYTHM -> result.none { it == NoteResult.PENDING }
        PracticeMode.LISTEN -> false
    }

    fun onKeyDown(pitch: Int, posMs: Double): KeyEvent {
        when (mode) {
            PracticeMode.LISTEN -> return KeyEvent(Feedback.EXTRA)
            PracticeMode.WAIT -> {
                if (cur >= events.size) return KeyEvent(Feedback.EXTRA)
                val start = evStart[cur]
                val nearGate = posMs >= start - earlyAcceptMs
                val idx = events[cur].firstOrNull { result[it] == NoteResult.PENDING && notes[it].pitch == pitch }
                if (idx != null) {
                    if (!nearGate) return KeyEvent(Feedback.EARLY_IGNORED)
                    markHit(idx, posMs, posMs - start)
                    if (events[cur].all { result[it] != NoteResult.PENDING }) {
                        if (!evError[cur]) { combo++; bestCombo = maxOf(bestCombo, combo) } else combo = 0
                        cur++
                    }
                    return KeyEvent(Feedback.CORRECT, idx, posMs - start)
                }
                if (!nearGate) return KeyEvent(Feedback.EARLY_IGNORED)
                // tecla de otra nota: si ya está en la lista de este acorde (ya tocada) no es un error
                if (events[cur].any { notes[it].pitch == pitch }) return KeyEvent(Feedback.EXTRA)
                wrongPresses++
                evError[cur] = true
                combo = 0
                return KeyEvent(Feedback.WRONG)
            }
            PracticeMode.RHYTHM -> {
                var best = -1
                var bestAbs = Double.MAX_VALUE
                var i = scan
                while (i < notes.size && notes[i].startMs - tolMs <= posMs) {
                    val n = notes[i]
                    if (result[i] == NoteResult.PENDING && n.pitch == pitch) {
                        val d = kotlin.math.abs(posMs - n.startMs)
                        if (d <= tolMs && d < bestAbs) { best = i; bestAbs = d }
                    }
                    i++
                }
                if (best >= 0) {
                    markHit(best, posMs, posMs - notes[best].startMs)
                    combo++; bestCombo = maxOf(bestCombo, combo)
                    return KeyEvent(Feedback.CORRECT, best, posMs - notes[best].startMs)
                }
                wrongPresses++
                combo = 0
                return KeyEvent(Feedback.WRONG)
            }
        }
    }

    private fun markHit(idx: Int, posMs: Double, delta: Double) {
        result[idx] = NoteResult.HIT
        deltaMs[idx] = delta
        hitAtMs[idx] = posMs
        hits++
        if (kotlin.math.abs(delta) <= 50.0) perfect++
        holding[notes[idx].pitch] = idx
    }

    fun onKeyUp(pitch: Int, posMs: Double) {
        if (pitch !in 0..127) return
        val idx = holding[pitch]
        if (idx < 0) return
        holding[pitch] = -1
        val n = notes[idx]
        heldRatio[idx] = ((posMs - n.startMs) / n.durMs).toFloat().coerceIn(0f, 1f)
    }

    /** Fracción de la barra ya "consumida" por la tecla mantenida (0..1), o -1 si no se mantiene. */
    fun holdProgress(idx: Int, posMs: Double): Float {
        val n = notes[idx]
        if (holding[n.pitch] != idx) return -1f
        return ((posMs - n.startMs) / n.durMs).toFloat().coerceIn(0f, 1f)
    }

    fun isHolding(idx: Int): Boolean = holding[notes[idx].pitch] == idx

    /** Llamar cada fotograma. En modo RITMO marca como falladas las notas que ya pasaron. */
    fun update(posMs: Double) {
        if (mode != PracticeMode.RHYTHM) return
        while (scan < notes.size && notes[scan].startMs + tolMs < posMs) {
            if (result[scan] == NoteResult.PENDING) {
                result[scan] = NoteResult.MISSED
                misses++
                combo = 0
            }
            scan++
        }
        // el puntero no avanza sobre notas pendientes dentro de su ventana
    }

    fun accuracy(): Float {
        if (notes.isEmpty()) return 0f
        return when (mode) {
            PracticeMode.WAIT -> {
                var clean = 0
                for (e in events.indices) if (!evError[e] && events[e].all { result[it] == NoteResult.HIT }) clean += events[e].size
                clean.toFloat() / notes.size
            }
            PracticeMode.RHYTHM -> hits.toFloat() / notes.size
            PracticeMode.LISTEN -> 0f
        }
    }

    fun stars(): Int {
        if (!isFinished()) return 0
        val a = accuracy()
        return when {
            a >= 0.95f -> 3
            a >= 0.80f -> 2
            a >= 0.60f -> 1
            else -> 0
        }
    }
}
