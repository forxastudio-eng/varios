package com.forxa.pianocascada.omr

import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.core.SongLibrary
import com.forxa.pianocascada.core.SongNote
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Lee varias páginas de verdad y compara la canción con una referencia revisada a mano (OMR_E2E_PAGES, OMR_E2E_REF). */
class EndToEndTest {
    private fun score(got: List<SongNote>, ref: List<SongNote>): Triple<Int, Int, Int> {
        val used = BooleanArray(got.size)
        var hit = 0
        for (r in ref) {
            val i = got.indices.firstOrNull { !used[it] && got[it].pitch == r.pitch && abs(got[it].start - r.start) < 0.02f }
            if (i != null) { used[i] = true; hit++ }
        }
        return Triple(hit, ref.size, got.size)
    }

    @Test fun glimpseOfUs() {
        val pagesDir = System.getenv("OMR_E2E_PAGES"); val refPath = System.getenv("OMR_E2E_REF")
        assumeTrue(TestEnv.models != null && pagesDir != null && refPath != null)
        TestEnv.loadOpenCv()
        val pages = File(pagesDir).listFiles { f -> f.name.matches(Regex("p\\d+\\.png")) }!!.sortedBy { it.name.substring(1, it.name.length - 4).toInt() }
        val engine = OmrEngine(OmrModels(TestEnv.modelFile("segnet"), TestEnv.modelFile("encoder"), TestEnv.modelFile("decoder")), threads = 4)
        val results = ArrayList<List<SystemTokens>>()
        engine.use { e ->
            for ((i, f) in pages.withIndex()) {
                val t0 = System.nanoTime()
                val r = e.readPage(readImage(f.absolutePath))
                println("${f.name}: ${r.systems.map { s -> s.staves.map { it.tokens.size } }} símbolos, ${(System.nanoTime() - t0) / 1e9} s, avisos=${r.warnings}")
                results.add(r.systems)
            }
        }
        val built = ScoreBuilder.build(results, "e2e", "Prueba")
        val song = built.song
        println("avisos de la canción: ${built.warnings}")
        println("compás ${song.meter}, ${song.bars} compases, anacrusa ${song.pickup}, tonalidad ${song.key}, mano derecha ${song.rh.size}, izquierda ${song.lh.size}, melodía ${song.melody?.size}")
        val ref: Song = SongLibrary.parse(File(refPath).readText()).first()
        val (mh, mr, mg) = score(song.melodyNotes, ref.melodyNotes)
        val (rh, rr, rg) = score(song.rh, ref.rh)
        val (lh, lr, lg) = score(song.lh, ref.lh)
        println("melodía: ${mh}/${mr} de la referencia (leídas $mg)")
        println("mano derecha: ${rh}/${rr} (leídas $rg)")
        println("mano izquierda: ${lh}/${lr} (leídas $lg)")
        assertTrue(mh > mr * 0.9, "melodía")
    }
}
