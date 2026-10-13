package com.forxa.pianocascada.omr

import com.forxa.pianocascada.core.SongJson
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test

/**
 * Lee las páginas de cada subcarpeta de OMR_BENCH_DIR (p-1.png, p-2.png…) y escribe la canción resultante en OMR_BENCH_OUT/<carpeta>.json.
 * La evaluación (contra la partitura original) la hace tools/evaluar_lector.py.
 */
class BenchTest {
    @Test fun readPiecesForEvaluation() {
        val dir = System.getenv("OMR_BENCH_DIR"); val out = System.getenv("OMR_BENCH_OUT")
        assumeTrue(TestEnv.models != null && dir != null && out != null)
        TestEnv.loadOpenCv()
        File(out).mkdirs()
        val engine = OmrEngine(OmrModels(TestEnv.modelFile("segnet"), TestEnv.modelFile("encoder"), TestEnv.modelFile("decoder")), threads = 4)
        engine.use { e ->
            for (piece in File(dir).listFiles { f -> f.isDirectory }!!.sortedBy { it.name }) {
                val t0 = System.nanoTime()
                val pages = piece.listFiles { f -> f.name.endsWith(".png") }!!.sortedBy { it.name }
                val results = ArrayList<List<SystemTokens>>()
                val notes = ArrayList<String>()
                for (p in pages) {
                    val r = e.readPage(readImage(p.absolutePath))
                    if (r.systems.any { s -> s.staves.any { it.tokens.isNotEmpty() } }) results.add(r.systems)
                    notes.addAll(r.warnings)
                }
                try {
                    val built = ScoreBuilder.build(results, piece.name, piece.name)
                    File(out, "${piece.name}.json").writeText(SongJson.write(built.song))
                    println("${piece.name}: ${pages.size} páginas, ${built.song.rh.size + built.song.lh.size} notas, ${(System.nanoTime() - t0) / 1e9} s, avisos=${built.warnings.size}")
                } catch (ex: Exception) {
                    println("${piece.name}: ERROR ${ex.message}")
                }
            }
        }
    }
}
