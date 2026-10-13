package com.forxa.pianocascada.core

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun songsJson(): String {
    val candidates = listOf("../app/src/main/assets/songs.json", "app/src/main/assets/songs.json", "android/app/src/main/assets/songs.json")
    val f = candidates.map { File(it) }.first { it.exists() }
    return f.readText()
}

class JsonTest {
    @Test fun parsesNestedValues() {
        val v = Json.parse("""{"a":[1,2.5,-3e2],"b":{"c":"x\nyé"},"d":true,"e":null}""").asMap()
        assertEquals(listOf(1.0, 2.5, -300.0), v["a"].asList())
        assertEquals("x\nyé", v["b"].asMap()["c"])
        assertEquals(true, v["d"])
        assertNull(v["e"])
    }

    @Test fun rejectsGarbage() {
        var failed = false
        try { Json.parse("{\"a\":1} basura") } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }
}

class NotesTest {
    @Test fun namesUseLetters() {
        assertEquals("C", Notes.name(60)); assertEquals("C#", Notes.name(61)); assertEquals("A", Notes.name(69))
        assertEquals("C4", Notes.nameWithOctave(60)); assertEquals("A0", Notes.nameWithOctave(21)); assertEquals("C8", Notes.nameWithOctave(108))
    }

    @Test fun whiteIndexIsConsistent() {
        assertEquals(0, Notes.whiteIndex(21)); assertEquals(51, Notes.whiteIndex(108)); assertEquals(23, Notes.whiteIndex(60))
        var count = 0
        for (p in Notes.MIN..Notes.MAX) if (!Notes.isBlack(p)) {
            assertEquals(count, Notes.whiteIndex(p)); assertEquals(p, Notes.pitchOfWhiteIndex(count)); count++
        }
        assertEquals(52, count)
    }
}

class SongLibraryTest {
    private val songs = SongLibrary.parse(songsJson())

    @Test fun loadsAllSongsWithUniqueIds() {
        assertTrue(songs.size >= 17, "hay ${songs.size} canciones")
        assertEquals(songs.size, songs.map { it.id }.toSet().size)
    }

    @Test fun everySongIsWellFormed() {
        for (s in songs) {
            assertTrue(s.bars > 0 && s.totalBeats > 0f, "${s.id}: sin compases")
            assertTrue(s.rh.isNotEmpty(), "${s.id}: sin mano derecha")
            val all = s.rh + s.lh + s.bass + (s.melody ?: emptyList())
            for (n in all) {
                assertTrue(n.pitch in Notes.MIN..Notes.MAX, "${s.id}: nota fuera del piano ${n.pitch}")
                assertTrue(n.dur > 0f, "${s.id}: duración no positiva")
                assertTrue(n.start >= 0f && n.end <= s.totalBeats + 0.02f, "${s.id}: nota fuera de la canción (${n.start}..${n.end} de ${s.totalBeats})")
            }
            // los compases están ordenados y cubren la canción
            assertEquals(0f, s.barStarts.first(), "${s.id}")
            for (i in 1 until s.barStarts.size) assertTrue(s.barStarts[i] > s.barStarts[i - 1], "${s.id}: compases desordenados")
        }
    }

    @Test fun levelsGrowInContent() {
        for (s in songs) {
            val n1 = s.notes(1).size; val n2 = s.notes(2).size; val n3 = s.notes(3).size
            assertTrue(n1 <= n2 + 0, "${s.id}: nivel 2 < nivel 1")
            if (s.lh.isNotEmpty()) assertTrue(n2 > n1 && n3 >= n1, "${s.id}: niveles sin diferencias")
            assertEquals(s.notes(3).sortedWith(compareBy({ it.start }, { it.pitch })), s.notes(3), "${s.id}: nivel 3 desordenado")
        }
    }

    @Test fun odeToJoyIsExact() {
        val s = songs.first { it.id == "himno-alegria" }
        val first = s.notes(1).take(8).map { Notes.name(it.pitch) }
        assertEquals(listOf("E", "E", "F", "G", "G", "F", "E", "D"), first)
        assertEquals(16, s.bars)
        assertEquals(4f, s.beatsPerBar)
    }

    @Test fun furElisePickupIsBarZero() {
        val s = songs.first { it.id == "para-elisa" }
        assertTrue(s.pickup > 0f)
        assertEquals(0, s.barLabel(0)); assertEquals(1, s.barLabel(1))
        assertEquals("E", Notes.name(s.rh.first().pitch)); assertEquals(76, s.rh.first().pitch)
    }

    @Test fun bassLevelHasOneNotePerBarAtMost() {
        for (s in songs.filter { it.lh.isNotEmpty() }) {
            assertTrue(s.bass.size <= s.bars, "${s.id}: bajo con ${s.bass.size} notas para ${s.bars} compases")
        }
    }

    @Test fun sequenceMatchesSongTiming() {
        val s = songs.first { it.id == "himno-alegria" }
        val seq = Sequencer.build(s, 3)
        val notes = s.notes(3)
        assertEquals(notes.size, seq.size)
        val msPerBeat = 60000.0 / s.bpm
        for (i in 0 until seq.size) {
            assertEquals(notes[i].start * msPerBeat, seq.startMs[i], 1e-3, "inicio de la nota $i")
            assertEquals(notes[i].dur * msPerBeat * 0.96, seq.durMs[i], 1e-3, "duración de la nota $i")
            assertEquals(notes[i].pitch, seq.keys[i])
        }
        assertTrue(seq.startMs.toList().zipWithNext().all { (a, b) -> a <= b }, "las notas deben salir ordenadas")
    }

    @Test fun levelOneHasOnlyTheRightHand() {
        // el controlador fuerza «Ambas» en el nivel 1: con «Izquierda» no quedaría ninguna nota que tocar
        for (s in songs) assertTrue(s.notes(1).all { it.hand == Hand.RIGHT }, s.id)
        for (s in songs.filter { it.lh.isNotEmpty() }) {
            val session = PracticeSession(s.notes(1).filter { it.hand == Hand.LEFT }, 60000.0 / s.bpm, PracticeMode.WAIT)
            assertEquals(0, session.total, s.id)
            assertTrue(session.isFinished(), "una sesión sin notas está «terminada» desde el primer instante: ${s.id}")
        }
    }
}

class TransposerTest {
    @Test fun bruteForceFindsTheMinimum() {
        val songs = SongLibrary.parse(songsJson())
        for (s in songs) {
            val pitches = (s.rh + s.lh).map { it.pitch }
            val plan = Transposer.bestWhiteKeyShift(pitches)
            for (shift in -6..5) {
                if (pitches.any { it + shift !in Notes.MIN..Notes.MAX }) continue
                val b = pitches.count { Notes.isBlack(it + shift) }
                assertTrue(plan.blackAfter <= b, "${s.id}: shift ${plan.shift} deja ${plan.blackAfter} negras, pero $shift deja $b")
            }
            assertTrue(plan.blackAfter <= plan.blackBefore, "${s.id}")
        }
    }

    @Test fun majorKeysMapToWhiteKeys() {
        val d = SongLibrary.parse(songsJson()).first { it.id == "coral-castillo-fuerte" } // Re mayor (2 sostenidos)
        val plan = Transposer.bestWhiteKeyShift((d.rh + d.lh).map { it.pitch })
        assertEquals(-2, plan.shift)
        assertTrue(plan.blackAfter < plan.blackBefore)
        assertEquals("C", Transposer.transposeKeyName("D", plan.shift))
    }

    @Test fun keyNames() {
        assertEquals("C", Transposer.transposeKeyName("Bb", 2))
        assertEquals("Am", Transposer.transposeKeyName("Bm", -2))
        assertEquals("D mayor", Transposer.displayKey("D"))
        assertEquals("A menor", Transposer.displayKey("Am"))
        assertEquals("Bb mayor", Transposer.displayKey("Bb"))
    }

    @Test fun transposedSongShiftsEveryNote() {
        val s = SongLibrary.parse(songsJson()).first { it.id == "himno-alegria" }
        val t = s.transposed(2)
        assertEquals(s.rh.map { it.pitch + 2 }, t.rh.map { it.pitch })
        assertEquals(2, t.transposeShift)
        assertEquals("D", t.key)
    }
}

class TwoRowsFitTest {
    private fun check(min: Int, max: Int): Int {
        val (low, high, m) = KeyboardPlanner.twoRowsFit(min, max)
        assertEquals(m, low.whiteCount, "fila baja"); assertEquals(m, high.whiteCount, "fila alta")
        assertFalse(Notes.isBlack(low.lo) || Notes.isBlack(low.hi) || Notes.isBlack(high.lo) || Notes.isBlack(high.hi), "extremos blancos")
        assertEquals(1, high.lo - low.hi, "el corte no deja ninguna tecla negra suelta ($min..$max)")
        assertTrue(low.lo <= Notes.whiteAtOrBelow(min) && high.hi >= Notes.whiteAtOrAbove(max), "cubre toda la pieza $min..$max")
        assertTrue(low.lo >= Notes.MIN && high.hi <= Notes.MAX, "dentro del piano")
        return m
    }

    @Test fun fourOctavesGiveFourteenKeysPerRow() {
        val m = check(45, 88)          // A2 … E6, el rango de «Para Elisa»
        assertTrue(m in 13..15, "teclas por fila: $m")
    }

    @Test fun moonlightRange() { assertTrue(check(37, 80) <= 14) }      // C#2 … G#5

    @Test fun extremesStillWork() {
        assertEquals(26, check(21, 108))     // piano completo
        check(21, 30); check(100, 108); check(60, 60); check(21, 22)
    }

    @Test fun everyPossibleRangeIsCoveredWithoutOrphans() {
        for (lo in 21..100 step 3) for (hi in (lo + 5)..108 step 4) check(lo, hi)
    }
}

class KeyboardTest {
    private fun row(lo: Int, hi: Int, w: Float = 1000f) = KeyRow(lo, hi, 0f, 0f, w, 300f)

    @Test fun twoRowsSplitEvenly() {
        val (low, high) = KeyboardPlanner.twoRows(65)
        assertEquals(26, low.whiteCount); assertEquals(26, high.whiteCount)
        assertEquals(Notes.MIN, low.lo); assertEquals(64, low.hi); assertEquals(65, high.lo); assertEquals(Notes.MAX, high.hi)
    }

    @Test fun everyKeyIsHitAtItsCenterAndOnlyOnce() {
        val (low, high) = KeyboardPlanner.twoRows(65)
        for (r in listOf(KeyRow(low.lo, low.hi, 0f, 0f, 2960f, 340f), KeyRow(high.lo, high.hi, 0f, 0f, 2960f, 340f))) {
            for (p in r.lo..r.hi) {
                val rc = assertNotNull(r.rect(p), "rect de $p")
                val x = rc.centerX
                val y = if (Notes.isBlack(p)) rc.top + rc.height * 0.4f else rc.bottom - 20f
                assertEquals(p, r.pitchAt(x, y), "tecla $p")
            }
        }
    }

    @Test fun blackKeysSitOnTopOfWhiteBoundaries() {
        val r = row(60, 72)
        val csharp = r.rect(61)!!; val c = r.rect(60)!!; val d = r.rect(62)!!
        assertEquals(c.right, csharp.centerX, 0.01f); assertEquals(d.left, csharp.centerX, 0.01f)
        assertTrue(csharp.bottom < c.bottom)
        // justo bajo la tecla negra se toca la blanca
        assertEquals(60, r.pitchAt(c.left + 2f, 250f)); assertEquals(61, r.pitchAt(csharp.centerX, 100f))
        assertEquals(62, r.pitchAt(d.centerX + 1f, 250f))
        assertEquals(60, r.pitchAt(csharp.left - 1f, 100f))   // justo a la izquierda de la negra, a su altura: es la blanca
        assertEquals(62, r.pitchAt(csharp.right + 1f, 100f))
    }

    @Test fun eAndFHaveNoBlackKeyBetween() {
        val r = row(60, 72)
        val e = r.rect(64)!!; val f = r.rect(65)!!
        assertEquals(e.right, f.left, 0.01f)
        assertEquals(64, r.pitchAt(e.right - 1f, 20f)); assertEquals(65, r.pitchAt(f.left + 1f, 20f))
    }

    @Test fun outsideReturnsNull() {
        val r = row(60, 72)
        assertNull(r.pitchAt(-1f, 10f)); assertNull(r.pitchAt(10f, 301f)); assertNull(r.pitchAt(1000f, 10f))
    }

    @Test fun whiteKeyWidthMatchesTheTablet() {
        // Tab S8 Ultra: 2960 px de ancho → 26 teclas blancas ≈ 113 px (≈ 12 mm a 9,4 px/mm)
        val (low, _) = KeyboardPlanner.twoRows(65)
        val r = KeyRow(low.lo, low.hi, 0f, 0f, 2960f, 340f)
        assertEquals(113.8f, r.whiteWidth, 0.2f)
        assertTrue(r.whiteWidth / 9.4f > 11.5f)
    }

    @Test fun windowKeepsSmoothScrollingGeometry() {
        val w = KeyRow.window(23.4f, 15f, 0f, 0f, 1500f, 300f)
        val c4 = w.rect(60)!!   // C4 = tecla blanca 23
        assertTrue(c4.left < 0f + 100f * 0.0f + 1f)   // medio recorte por la izquierda
        assertEquals(100f, w.whiteWidth, 0.01f)
        assertTrue(w.rect(w.hi)!!.right >= 1500f)
    }

    @Test fun reducedWindowCoversTheSongRange() {
        val left = KeyboardPlanner.reducedLeft(60, 79, 15)
        val lo = Notes.pitchOfWhiteIndex(kotlin.math.floor(left).toInt())
        val hiW = Notes.pitchOfWhiteIndex(kotlin.math.ceil(left + 15).toInt() - 1)
        assertTrue(lo <= 60 && hiW >= 79, "ventana $lo..$hiW")
    }

    @Test fun followMovesOnlyWhenNeeded() {
        val left = KeyboardPlanner.reducedLeft(60, 72, 15)
        assertEquals(left, KeyboardPlanner.followLeft(listOf(62, 64), left, 15))
        val moved = KeyboardPlanner.followLeft(listOf(90, 92), left, 15)
        assertTrue(moved > left)
        assertEquals(left, KeyboardPlanner.followLeft(emptyList(), left, 15))
    }
}

class PracticeTest {
    private fun n(start: Float, pitch: Int, dur: Float = 1f) = SongNote(start, dur, pitch, Hand.RIGHT)
    private val tune = listOf(n(0f, 60), n(1f, 62), n(2f, 64), n(3f, 65))   // 1 pulso = 500 ms

    @Test fun waitModeStopsAtEachNoteAndAdvances() {
        val s = PracticeSession(tune, 500.0, PracticeMode.WAIT)
        assertEquals(0.0, s.gateMs)
        assertEquals(listOf(60), s.pendingPitches())
        assertEquals(Feedback.WRONG, s.onKeyDown(61, 0.0).feedback)
        assertEquals(Feedback.CORRECT, s.onKeyDown(60, 10.0).feedback)
        assertEquals(500.0, s.gateMs)
        assertEquals(Feedback.CORRECT, s.onKeyDown(62, 500.0).feedback)
        assertEquals(Feedback.CORRECT, s.onKeyDown(64, 1000.0).feedback)
        assertFalse(s.isFinished())
        assertEquals(Feedback.CORRECT, s.onKeyDown(65, 1500.0).feedback)
        assertTrue(s.isFinished())
        assertEquals(1e18, s.gateMs)
        // la primera nota tuvo un error: 3 de 4 limpias
        assertEquals(0.75f, s.accuracy(), 1e-6f)
        assertEquals(1, s.wrongPresses)
        assertEquals(1, s.stars())   // 75 % → 1 estrella
    }

    @Test fun waitModeIgnoresPressesFarTooEarly() {
        val s = PracticeSession(tune, 500.0, PracticeMode.WAIT, earlyAcceptMs = 300.0)
        s.onKeyDown(60, 0.0)
        assertEquals(Feedback.EARLY_IGNORED, s.onKeyDown(62, 100.0).feedback)   // faltan 400 ms: demasiado pronto
        assertEquals(0, s.wrongPresses)
        assertEquals(Feedback.CORRECT, s.onKeyDown(62, 250.0).feedback)
    }

    @Test fun waitModeNeedsFullChord() {
        val chord = listOf(n(0f, 60), n(0f, 64), n(0f, 67), n(1f, 72))
        val s = PracticeSession(chord, 500.0, PracticeMode.WAIT)
        assertEquals(setOf(60, 64, 67), s.pendingPitches().toSet())
        s.onKeyDown(60, 0.0); s.onKeyDown(64, 0.0)
        assertEquals(0.0, s.gateMs)                                    // todavía falta la 67
        assertEquals(listOf(67), s.pendingPitches())
        s.onKeyDown(67, 0.0)
        assertEquals(500.0, s.gateMs)
    }

    @Test fun rhythmModeScoresTimingWindow() {
        val s = PracticeSession(tune, 500.0, PracticeMode.RHYTHM, tolMs = 120.0)
        assertEquals(Feedback.CORRECT, s.onKeyDown(60, 40.0).feedback)           // +40 ms
        assertEquals(Feedback.WRONG, s.onKeyDown(62, 100.0).feedback)             // demasiado pronto: no hay nota 62 cerca
        s.update(700.0)
        assertEquals(NoteResult.MISSED, s.result[1])                              // la 62 de t=500 ya pasó (620 < 700)
        assertEquals(Feedback.CORRECT, s.onKeyDown(64, 1010.0).feedback)
        s.update(2000.0)
        assertEquals(NoteResult.MISSED, s.result[3])
        assertTrue(s.isFinished())
        assertEquals(0.5f, s.accuracy(), 1e-6f)
        assertEquals(0, s.stars())
    }

    @Test fun rhythmModePicksTheClosestRepeatedNote() {
        val rep = listOf(n(0f, 60), n(0.25f, 60))   // 125 ms
        val s = PracticeSession(rep, 500.0, PracticeMode.RHYTHM, tolMs = 120.0)
        val e = s.onKeyDown(60, 130.0)
        assertEquals(1, e.noteIndex)
    }

    @Test fun holdingTracksTheLengthOfTheBar() {
        val s = PracticeSession(listOf(n(0f, 60, 4f)), 500.0, PracticeMode.WAIT)   // 2000 ms
        s.onKeyDown(60, 0.0)
        assertEquals(0.25f, s.holdProgress(0, 500.0), 1e-6f)
        assertTrue(s.isHolding(0))
        s.onKeyUp(60, 1000.0)
        assertFalse(s.isHolding(0))
        assertEquals(0.5f, s.heldRatio[0], 1e-6f)
    }

    @Test fun duplicateNotesAreMerged() {
        val s = PracticeSession(listOf(n(0f, 60, 1f), n(0f, 60, 2f), n(1f, 62)), 500.0, PracticeMode.WAIT)
        assertEquals(2, s.total)
        assertEquals(1000.0, s.notes[0].durMs)
    }

    @Test fun listenModeNeverScores() {
        val s = PracticeSession(tune, 500.0, PracticeMode.LISTEN)
        assertEquals(Feedback.EXTRA, s.onKeyDown(60, 0.0).feedback)
        assertEquals(1e18, s.gateMs)
        assertFalse(s.isFinished())
    }

    @Test fun perfectRunGivesThreeStars() {
        val s = PracticeSession(tune, 500.0, PracticeMode.WAIT)
        tune.forEachIndexed { i, nt -> s.onKeyDown(nt.pitch, i * 500.0) }
        assertEquals(1f, s.accuracy(), 1e-6f); assertEquals(3, s.stars()); assertEquals(4, s.bestCombo)
    }
}

class TouchTest {
    @Test fun curvesAreMonotonicAndBounded() {
        for (k in CurveKind.values()) {
            val m = VelocityMapper(k)
            var prev = 0
            for (i in 0..20) {
                val v = m.velocity(i / 20f, 60)
                assertTrue(v in 1..127, "$k: $v")
                assertTrue(v >= prev, "$k no es monótona")
                prev = v
            }
        }
    }

    @Test fun softIsLouderThanHardAtLowPressure() {
        assertTrue(VelocityMapper(CurveKind.SOFT).velocity(0.3f, 60) > VelocityMapper(CurveKind.HARD).velocity(0.3f, 60))
        assertEquals(90, VelocityMapper(CurveKind.FIXED, fixed = 90).velocity(0.1f, 60))
    }

    @Test fun zonesScaleVelocity() {
        val m = VelocityMapper(CurveKind.NORMAL, lowZone = 0.5f, highZone = 1.0f)
        assertTrue(m.velocity(0.8f, 36) < m.velocity(0.8f, 60))
        assertEquals(m.velocity(0.8f, 60), m.velocity(0.8f, 96))
    }

    @Test fun contactSizeCalibration() {
        assertEquals(0f, VelocityMapper.strengthFromContact(0.1f, 0.1f, 0.5f), 1e-6f)
        assertEquals(1f, VelocityMapper.strengthFromContact(0.9f, 0.1f, 0.5f), 1e-6f)
        assertEquals(0.6f, VelocityMapper.strengthFromContact(0.3f, 0.3f, 0.3f), 1e-6f)
    }
}
