package com.forxa.pianocascada.core

enum class Hand { RIGHT, LEFT }

/** Nota de partitura: inicio y duración en pulsos (negras). */
data class SongNote(val start: Float, val dur: Float, val pitch: Int, val hand: Hand) {
    val end: Float get() = start + dur
}

enum class SongSource { SCORE, TRANSCRIPTION, AUDIO, PDF, IMPORTED }

data class Song(
    val id: String,
    val title: String,
    val composer: String,
    val era: String,
    val work: String,
    val key: String,
    val originalKey: String,
    val meter: String,
    val bpm: Int,
    val difficulty: Int,
    val tip: String,
    val fun_: String,
    val source: SongSource,
    val pulse: Float,
    val beatsPerBar: Float,
    val pickup: Float,
    val totalBeats: Float,
    val durationSec: Float,
    val barStarts: List<Float>,
    val rh: List<SongNote>,
    val melody: List<SongNote>?,
    val lh: List<SongNote>,
    val bass: List<SongNote>,
    val order: Int = 0,
    val transposeShift: Int = 0,
) {
    val bars: Int get() = barStarts.size

    /** Melodía del nivel 1 (si no hay una voz aparte, la mano derecha completa). */
    val melodyNotes: List<SongNote> get() = melody ?: rh

    val levels: List<Int> get() = if (lh.isEmpty()) listOf(1) else listOf(1, 2, 3)

    fun notes(level: Int): List<SongNote> {
        val l = when (level) {
            1 -> melodyNotes
            2 -> melodyNotes + (if (bass.isNotEmpty()) bass else lh)
            else -> rh + lh
        }
        return l.sortedWith(compareBy<SongNote>({ it.start }, { it.pitch }))
    }

    fun levelName(level: Int): String = when (level) {
        1 -> "Solo melodía"
        2 -> "Melodía + bajo"
        else -> "Completa"
    }

    val minPitch: Int get() = notes(3).minOfOrNull { it.pitch } ?: 60
    val maxPitch: Int get() = notes(3).maxOfOrNull { it.pitch } ?: 72

    fun beatToMs(beat: Float): Double = beat.toDouble() * 60000.0 / bpm
    val totalMs: Double get() = beatToMs(totalBeats)

    fun barIndexAtBeat(beat: Float): Int {
        var lo = 0
        var hi = barStarts.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (barStarts[mid] <= beat + 1e-4f) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }

    /** Número de compás para mostrar (la anacrusa es el compás 0). */
    fun barLabel(index: Int): Int = if (pickup > 0f) index else index + 1

    fun transposed(shift: Int): Song {
        if (shift == 0) return this
        fun t(l: List<SongNote>) = l.map { it.copy(pitch = it.pitch + shift) }
        return copy(
            rh = t(rh), melody = melody?.let { t(it) }, lh = t(lh), bass = t(bass),
            key = Transposer.transposeKeyName(key, shift), transposeShift = transposeShift + shift,
        )
    }

    val isUserSong: Boolean get() = source == SongSource.AUDIO || source == SongSource.PDF || source == SongSource.IMPORTED
}

object SongLibrary {
    fun parse(json: String): List<Song> {
        val root = Json.parse(json).asMap()
        return root["canciones"].asList().map { fromMap(it.asMap()) }.sortedBy { it.order }
    }

    fun fromMap(m: Map<String, Any?>): Song {
        fun s(k: String) = (m[k] as? String) ?: ""
        fun f(k: String) = ((m[k] as? Double) ?: 0.0).toFloat()
        fun notes(k: String, hand: Hand): List<SongNote> =
            (m[k] as? List<*>)?.map {
                val a = it as List<*>
                SongNote((a[0] as Double).toFloat(), (a[1] as Double).toFloat(), (a[2] as Double).toInt(), hand)
            } ?: emptyList()
        val src = when (s("fuente")) {
            "partitura" -> SongSource.SCORE
            "audio" -> SongSource.AUDIO
            "pdf" -> SongSource.PDF
            "importada" -> SongSource.IMPORTED
            else -> SongSource.TRANSCRIPTION
        }
        return Song(
            id = s("id"), title = s("titulo"), composer = s("compositor"), era = s("epoca"), work = s("obra"),
            key = s("tonalidad"), originalKey = s("tonalidadOriginal").ifEmpty { s("tonalidad") },
            meter = s("compas"), bpm = f("bpm").toInt().coerceAtLeast(20), difficulty = f("dificultad").toInt(),
            tip = s("consejo"), fun_ = s("curiosidad"), source = src,
            pulse = f("pulso").takeIf { it > 0f } ?: 1f, beatsPerBar = f("pulsosCompas").takeIf { it > 0f } ?: 4f,
            pickup = f("anacrusa"), totalBeats = f("pulsosTotales"), durationSec = f("duracionSeg"),
            barStarts = (m["inicioCompases"] as? List<*>)?.map { (it as Double).toFloat() } ?: listOf(0f),
            rh = notes("mdNotas", Hand.RIGHT),
            melody = if (m.containsKey("melNotas")) notes("melNotas", Hand.RIGHT) else null,
            lh = notes("miNotas", Hand.LEFT),
            bass = notes("bajoNotas", Hand.LEFT),
            order = f("orden").toInt(),
        )
    }
}
