package com.forxa.pianocascada.transcribe

import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.core.SongSource
import kotlin.math.floor

/** Importa un archivo MIDI estándar (formato 0 o 1) como canción: tempo, compás, manos y tonalidad. */
object MidiImporter {
    private class Ev(val tick: Long, val on: Boolean, val key: Int, val vel: Int)

    private val SHARP = arrayOf("C", "G", "D", "A", "E", "B", "F#", "C#")
    private val FLAT = arrayOf("C", "F", "Bb", "Eb", "Ab", "Db", "Gb", "Cb")
    private val SHARP_M = arrayOf("Am", "Em", "Bm", "F#m", "C#m", "G#m", "D#m", "A#m")
    private val FLAT_M = arrayOf("Am", "Dm", "Gm", "Cm", "Fm", "Bbm", "Ebm", "Abm")

    fun parse(data: ByteArray, id: String, title: String): Song {
        var p = 0
        fun u8() = data[p++].toInt() and 0xFF
        fun u16() = (u8() shl 8) or u8()
        fun u32() = (u16().toLong() shl 16) or u16().toLong()
        require(data.size > 14 && String(data, 0, 4) == "MThd") { "No es un archivo MIDI" }
        p = 8
        val format = u16(); val nTracks = u16(); val division = u16()
        require(division and 0x8000 == 0) { "MIDI con tiempo SMPTE no soportado" }
        val ppq = division.toDouble()
        var tempoUs = 500000.0
        var tempoSet = false
        var num = 4; var den = 4
        var keySig: String? = null
        val tracks = ArrayList<List<Ev>>()
        for (t in 0 until nTracks) {
            if (p + 8 > data.size) break
            val id4 = String(data, p, 4); p += 4
            val len = u32().toInt()
            val end = p + len
            if (id4 != "MTrk") { p = end; continue }
            val evs = ArrayList<Ev>()
            var tick = 0L
            var running = 0
            while (p < end) {
                var delta = 0L
                do { val b = u8(); delta = (delta shl 7) or (b and 0x7F).toLong(); if (b and 0x80 == 0) break } while (true)
                tick += delta
                var st = data[p].toInt() and 0xFF
                if (st == 0xFF) {
                    p++
                    val type = u8()
                    var l = 0
                    do { val b = u8(); l = (l shl 7) or (b and 0x7F); if (b and 0x80 == 0) break } while (true)
                    when (type) {
                        0x51 -> if (!tempoSet && l == 3) { tempoUs = ((data[p].toInt() and 0xFF) shl 16 or ((data[p + 1].toInt() and 0xFF) shl 8) or (data[p + 2].toInt() and 0xFF)).toDouble(); tempoSet = true }
                        0x58 -> if (l >= 2) { num = data[p].toInt() and 0xFF; den = 1 shl (data[p + 1].toInt() and 0xFF) }
                        0x59 -> if (l >= 2 && keySig == null) {
                            val sf = data[p].toInt(); val mi = data[p + 1].toInt()
                            keySig = if (sf >= 0) (if (mi == 0) SHARP[sf.coerceAtMost(7)] else SHARP_M[sf.coerceAtMost(7)]) else (if (mi == 0) FLAT[(-sf).coerceAtMost(7)] else FLAT_M[(-sf).coerceAtMost(7)])
                        }
                    }
                    p += l
                    continue
                }
                if (st == 0xF0 || st == 0xF7) {
                    p++
                    var l = 0
                    do { val b = u8(); l = (l shl 7) or (b and 0x7F); if (b and 0x80 == 0) break } while (true)
                    p += l; continue
                }
                if (st and 0x80 != 0) { running = st; p++ } else st = running
                val kind = st and 0xF0
                val ch = st and 0x0F
                when (kind) {
                    0x90, 0x80 -> {
                        val k = u8(); val v = u8()
                        if (ch != 9) evs.add(Ev(tick, kind == 0x90 && v > 0, k, v))   // el canal 10 es percusión
                    }
                    0xA0, 0xB0, 0xE0 -> p += 2
                    0xC0, 0xD0 -> p += 1
                    else -> p += 1
                }
            }
            p = end
            tracks.add(evs)
        }
        // emparejar notas por pista
        class N(val s: Long, val e: Long, val pitch: Int, val track: Int)
        val all = ArrayList<N>()
        for ((ti, evs) in tracks.withIndex()) {
            val open = HashMap<Int, ArrayDeque<Long>>()
            for (e in evs.sortedBy { it.tick }) {
                if (e.on) open.getOrPut(e.key) { ArrayDeque() }.addLast(e.tick)
                else open[e.key]?.removeFirstOrNull()?.let { s -> if (e.tick > s) all.add(N(s, e.tick, e.key, ti)) }
            }
        }
        require(all.isNotEmpty()) { "El MIDI no contiene notas" }
        val bpm = 60_000_000.0 / tempoUs
        val bpb = num * 4.0 / den
        val noteTracks = all.map { it.track }.distinct()
        // manos: con dos o más pistas, la de tesitura más aguda es la derecha; con una sola se reparte por altura
        val beatNotes = all.sortedWith(compareBy({ it.s }, { it.pitch })).map { n ->
            BeatNote(n.s / ppq, (n.e - n.s) / ppq, n.pitch, 0.7f)
        }
        val right: BooleanArray? = if (noteTracks.size >= 2) {
            val mean = noteTracks.associateWith { t -> all.filter { it.track == t }.map { it.pitch }.average() }
            val order = noteTracks.sortedByDescending { mean[it] }
            val rightTracks = if (noteTracks.size == 2) setOf(order[0]) else order.take((noteTracks.size + 1) / 2).toSet()
            val sorted = all.sortedWith(compareBy({ it.s }, { it.pitch }))
            BooleanArray(sorted.size) { sorted[it].track in rightTracks }
        } else null
        val meter = "$num/$den"
        val seconds = (beatNotes.maxOf { it.end } * 60.0 / bpm)
        return SongBuilder.build(
            id, title, SongSource.IMPORTED, beatNotes, bpm, bpb, meter, 0.0, seconds, keySig,
            composer = "Archivo importado", pickupIn = null, handsRight = right,
        ).also { @Suppress("UNUSED_EXPRESSION") floor(0.0) }
    }
}
