package com.forxa.pianocascada.omr

import com.forxa.pianocascada.core.Json
import com.forxa.pianocascada.core.asList
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** La lectura completa de una página frente a los símbolos que devuelve homr. */
class Stage3Test {
    @Test fun symbolsMatchHomr() {
        assumeTrue(TestEnv.models != null && TestEnv.golden != null && TestEnv.pages != null)
        TestEnv.loadOpenCv()
        val page = System.getenv("OMR_PAGE") ?: "p3"
        val engine = OmrEngine(OmrModels(TestEnv.modelFile("segnet"), TestEnv.modelFile("encoder"), TestEnv.modelFile("decoder")), threads = 4)
        val t0 = System.nanoTime()
        val result = engine.use { it.readPage(readImage("${TestEnv.pages}/$page.png"), { p, m -> if ((p * 100).toInt() % 10 == 0) println("  ${(p * 100).toInt()} % $m") }) }
        println("lectura completa: ${(System.nanoTime() - t0) / 1e9} s")
        val ref = Json.parse(File("${TestEnv.golden}/$page/voices.json").readText()).asList().map { v -> v.asList().map { it as String } }
        println("voces: Kotlin=${result.voices.size} homr=${ref.size}; avisos=${result.warnings}")
        var totalRef = 0; var same = 0
        for ((i, rv) in ref.withIndex()) {
            val kv = result.voices.getOrNull(i)?.map { it.str() } ?: emptyList()
            // alineación por posición: se cuentan los símbolos idénticos en el mismo índice
            totalRef += rv.size
            for (k in rv.indices) if (k < kv.size && kv[k] == rv[k]) same++
            println("voz $i: homr ${rv.size} símbolos, Kotlin ${kv.size}")
            if (kv != rv) {
                for (k in 0 until minOf(rv.size, kv.size)) if (kv[k] != rv[k]) { println("  primera diferencia en $k: homr='${rv[k]}' kotlin='${kv[k]}'"); break }
            }
        }
        println("símbolos idénticos: $same de $totalRef")
        assertTrue(same == totalRef && result.voices.size == ref.size, "la lectura no coincide con homr")
    }
}
