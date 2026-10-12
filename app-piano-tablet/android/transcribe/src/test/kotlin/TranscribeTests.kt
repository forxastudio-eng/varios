package com.forxa.pianocascada.transcribe

import com.forxa.pianocascada.core.Hand
import com.forxa.pianocascada.core.SongJson
import com.forxa.pianocascada.core.SongLibrary
import com.forxa.pianocascada.core.SongSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResamplerTest {
    private fun sine(freq: Double, sr: Int, sec: Double) = FloatArray((sr * sec).toInt()) { sin(2 * PI * freq * it / sr).toFloat() }

    @Test fun downsamplingKeepsTheTone() {
        val x = sine(440.0, 44100, 1.0)
        val y = Resampler.resample(x, 44100, 22050)
        assertTrue(abs(y.size - 22050) <= 1)
        // cruces por cero por segundo ≈ 2 × frecuencia
        var z = 0
        for (i in 1 until y.size) if (y[i - 1] < 0 && y[i] >= 0) z++
        assertTrue(abs(z - 440) <= 3, "cruces por cero: $z")
        assertTrue(y.drop(100).take(20000).maxOf { abs(it) } in 0.95f..1.05f)
    }

    @Test fun removesFrequenciesAboveTheNewNyquist() {
        val x = sine(15000.0, 44100, 0.5)      // por encima de 11 025 Hz: debe desaparecer
        val y = Resampler.resample(x, 44100, 22050)
        assertTrue(y.drop(200).take(5000).maxOf { abs(it) } < 0.1f)
    }

    @Test fun sameRateIsIdentity() {
        val x = sine(100.0, 22050, 0.1)
        assertEquals(x.toList(), Resampler.resample(x, 22050, 22050).toList())
    }

    @Test fun stereoToMono() {
        val m = Resampler.toMono(shortArrayOf(16384, -16384, 8192, 8192), 2)
        assertEquals(2, m.size); assertEquals(0f, m[0], 1e-4f); assertEquals(0.25f, m[1], 1e-3f)
    }
}

class ExtractionTest {
    private fun output(frames: Int, build: (note: FloatArray, onset: FloatArray) -> Unit): ModelOutput {
        val n = FloatArray(frames * 88); val o = FloatArray(frames * 88)
        build(n, o)
        return ModelOutput(frames, n, o)
    }

    @Test fun findsASimpleNote() {
        // nota 60 (índice 39) entre los fotogramas 20 y 80
        val out = output(120) { n, o ->
            for (t in 20 until 80) n[t * 88 + 39] = 0.8f
            o[20 * 88 + 39] = 0.9f
        }
        val notes = NoteExtractor.extract(out, ExtractParams())
        assertEquals(1, notes.count { it.pitch == 60 })
        val note = notes.first { it.pitch == 60 }
        assertEquals(BasicPitch.frameTime(20), note.start, 0.02)
        assertTrue(note.end - note.start > 0.5)
    }

    @Test fun ignoresShortBlips() {
        val out = output(120) { n, o -> for (t in 20 until 24) n[t * 88 + 39] = 0.8f; o[20 * 88 + 39] = 0.9f }
        assertTrue(NoteExtractor.extract(out, ExtractParams(minNoteLen = 11)).isEmpty())
    }

    @Test fun twoRepeatedNotesStaySeparate() {
        val out = output(200) { n, o ->
            for (t in 20 until 70) n[t * 88 + 39] = 0.8f; o[20 * 88 + 39] = 0.9f
            for (t in 74 until 130) n[t * 88 + 39] = 0.8f; o[74 * 88 + 39] = 0.9f
        }
        // hay un hueco de 4 fotogramas entre ambas, por debajo de la tolerancia de energía: se separan por el segundo ataque
        val notes = NoteExtractor.extract(out, ExtractParams(minNoteLen = 5, energyTol = 3))
        assertEquals(2, notes.count { it.pitch == 60 })
    }

    @Test fun frameTimeIsMonotonicAndContinuous() {
        var prev = -1.0
        for (j in 0 until 1000) { val t = BasicPitch.frameTime(j); assertTrue(t > prev); prev = t }
        // las ventanas se solapan 0,73 fotogramas: en el límite el avance es menor que un fotograma (11,6 ms), nunca negativo
        val d = BasicPitch.frameTime(142) - BasicPitch.frameTime(141)
        assertTrue(d in 0.001..0.0125, "salto entre ventanas: $d")
        // 100 ventanas después, el tiempo debe seguir la geometría real (hop de 36 164 muestras por 142 fotogramas)
        assertEquals((100 * 36164 + 15 * 256 - 3840) / 22050.0, BasicPitch.frameTime(100 * 142), 1e-9)
    }
}

class AnalysisTest {
    private fun clicks(bpm: Double, seconds: Double, subdivision: Int = 1): Pair<FloatArray, List<RawNote>> {
        val fps = BasicPitch.FPS
        val frames = (seconds * fps).toInt()
        val env = FloatArray(frames)
        val notes = ArrayList<RawNote>()
        val period = 60.0 / bpm / subdivision
        var t = 0.25
        var i = 0
        while (t < seconds - 0.5) {
            val fr = (t * fps).toInt()
            val strong = i % (4 * subdivision) == 0
            env[fr] = if (strong) 3f else 1.5f
            notes.add(RawNote(t, t + 0.2, 60 + (i % 5), 0.6f, 0.9f))
            t += period; i++
        }
        return env to notes
    }

    @Test fun estimatesTempoFromClicks() {
        for (bpm in listOf(70.0, 90.0, 110.0)) {
            val (env, notes) = clicks(bpm, 30.0)
            val g = Analysis.beatGrid(env, notes)
            assertEquals(bpm, g.bpm, bpm * 0.03, "tempo $bpm")
        }
    }

    @Test fun detectsFourBeatBars() {
        val (env, notes) = clicks(100.0, 40.0)
        assertEquals(4, Analysis.beatGrid(env, notes).beatsPerBar)
    }

    @Test fun keyDetectionOnScales() {
        fun scale(root: Int, minor: Boolean): List<BeatNote> {
            val steps = if (minor) intArrayOf(0, 2, 3, 5, 7, 8, 10, 12) else intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)
            return steps.mapIndexed { i, s -> BeatNote(i.toDouble(), if (i == 0 || i == 7) 2.0 else 1.0, 60 + root + s, 0.6f) }
        }
        assertEquals("C", Analysis.detectKey(scale(0, false)))
        assertEquals("G", Analysis.detectKey(scale(7, false)))
        assertEquals("Am", Analysis.detectKey(scale(9, true)))
        assertEquals("Dm", Analysis.detectKey(scale(2, true)))
    }

    @Test fun handsSplitByRegister() {
        val notes = listOf(
            BeatNote(0.0, 1.0, 72, .6f), BeatNote(0.0, 1.0, 48, .6f),
            BeatNote(1.0, 1.0, 74, .6f), BeatNote(1.0, 1.0, 50, .6f),
            BeatNote(2.0, 1.0, 76, .6f), BeatNote(2.0, 1.0, 52, .6f),
        )
        val r = Analysis.splitHands(notes)
        assertEquals(listOf(true, false, true, false, true, false), r.toList())
    }

    @Test fun quantizationSnapsToTheGridAndKeepsRubatoUntouched() {
        val grid = BeatGrid(120.0, 0.5, 0.0, 4, 0, 0.8f)
        val jitter = listOf(0.012, -0.015, 0.02, -0.01, 0.018, -0.02, 0.005, -0.008, 0.01, -0.012)
        val notes = jitter.mapIndexed { i, j -> RawNote(i * 0.25 + j, i * 0.25 + 0.2 + j, 60, 0.6f, 0.9f) }
        val q = Analysis.toBeats(notes, grid, true)
        for ((i, n) in q.withIndex()) assertEquals(i * 0.5, n.start, 1e-6, "nota $i")
        // con ritmo irregular (rubato) no se ajusta: el error medio supera lo tolerable
        val rubato = (0 until 10).map { RawNote(it * 0.31 + (it % 3) * 0.07, it * 0.31 + 0.3, 62, .6f, .9f) }
        val r = Analysis.toBeats(rubato, grid, true)
        assertTrue(r.zip(rubato).any { (b, n) -> abs(b.start - n.start / 0.5) < 1e-6 })
    }

    @Test fun qualityFilterIsIdentityWhenUntrained() {
        NoteQuality.trained = false
        val ns = listOf(RawNote(0.0, 1.0, 60, 0.4f), RawNote(1.0, 2.0, 62, 0.4f))
        assertEquals(ns, NoteQuality.filter(ns, ModelOutput(10, FloatArray(880), FloatArray(880)), 0.9f))
    }
}

class SongBuilderTest {
    @Test fun buildsAPlayableSongWithLevelsAndSerializesIt() {
        val grid = BeatGrid(100.0, 0.6, 0.0, 4, 0, 0.8f)
        // melodía en la derecha, bajo en la izquierda, 4 compases
        val notes = ArrayList<BeatNote>()
        for (b in 0 until 16) notes.add(BeatNote(b.toDouble(), 1.0, 64 + (b % 4), 0.6f))
        for (bar in 0 until 4) notes.add(BeatNote(bar * 4.0, 4.0, 40 + bar, 0.6f))
        val s = SongBuilder.build("t1", "Prueba", SongSource.AUDIO, notes, grid, 10.0)
        assertEquals(4, s.bars)
        assertTrue(s.rh.isNotEmpty() && s.lh.isNotEmpty())
        assertTrue(s.rh.all { it.hand == Hand.RIGHT } && s.lh.all { it.hand == Hand.LEFT })
        assertEquals(4, s.bass.size)
        assertEquals(100, s.bpm)
        // ida y vuelta por JSON
        val back = SongLibrary.parse(SongJson.write(s)).single()
        assertEquals(s.rh.size, back.rh.size); assertEquals(s.lh.size, back.lh.size); assertEquals(s.bars, back.bars)
        assertEquals(SongSource.AUDIO, back.source); assertEquals(s.key, back.key)
    }

    @Test fun pickupBecomesBarZero() {
        val grid = BeatGrid(100.0, 0.6, 0.0, 4, 0, 0.8f)
        val notes = listOf(BeatNote(3.0, 1.0, 67, .6f)) + (0 until 8).map { BeatNote(4.0 + it, 1.0, 60 + it, .6f) }
        val s = SongBuilder.build("t2", "Con anacrusa", SongSource.AUDIO, notes, grid, 8.0)
        assertTrue(s.pickup > 0f, "pickup=${s.pickup}")
        assertEquals(0, s.barLabel(0))
    }
}

class MidiImporterTest {
    private fun midi(name: String): ByteArray {
        val f = listOf("../../repertorio/midi/$name", "../repertorio/midi/$name", "repertorio/midi/$name").map { java.io.File(it) }.first { it.exists() }
        return f.readBytes()
    }

    @Test fun importsOdeToJoyWithTheRightStructure() {
        val s = MidiImporter.parse(midi("01-himno-alegria.mid"), "himno", "Himno")
        assertEquals(108, s.bpm)
        assertEquals("4/4", s.meter)
        assertEquals(16, s.bars)
        assertEquals(62, s.rh.size)
        assertEquals(47, s.lh.size)
        assertEquals(listOf("E", "E", "F", "G"), s.rh.take(4).map { com.forxa.pianocascada.core.Notes.name(it.pitch) })
        assertTrue(s.rh.all { it.hand == Hand.RIGHT } && s.lh.all { it.hand == Hand.LEFT })
    }

    @Test fun importsAMultiTrackPieceWithItsPickup() {
        val s = MidiImporter.parse(midi("02-para-elisa.mid"), "elisa", "Elisa")
        assertEquals("3/8", s.meter)
        assertEquals(66, s.bpm)
        assertTrue(s.rh.size > 60 && s.lh.isNotEmpty())
    }

    @Test fun rejectsNonMidi() {
        var failed = false
        try { MidiImporter.parse(ByteArray(40), "x", "x") } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }
}
