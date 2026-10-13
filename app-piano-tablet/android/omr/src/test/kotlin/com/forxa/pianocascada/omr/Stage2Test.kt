package com.forxa.pianocascada.omr

import ai.onnxruntime.OrtEnvironment
import com.forxa.pianocascada.core.Json
import com.forxa.pianocascada.core.asList
import com.forxa.pianocascada.core.asMap
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.opencv.core.Core
import org.opencv.core.Mat
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Detección de pentagramas frente al volcado de homr. */
class Stage2Test {
    companion object {
        /** Predicciones completas de una página (preprocesado + segmentación + ruido). */
        fun predictionsFor(page: String): Pair<Predictions, Mat> {
            var img = readImage("${TestEnv.pages}/$page.png")
            img = Preprocess.autocrop(img)
            img = Preprocess.resizeImage(img)
            val pre = Preprocess.applyClahe(img)
            val env = OrtEnvironment.getEnvironment()
            val m = Segnet(env, TestEnv.modelFile("segnet"), 4).use { it.extract(pre) }
            fun times255(x: Mat): Mat { val o = Mat(); Core.multiply(x, org.opencv.core.Scalar(255.0), o); return o }
            val noise = Preprocess.createNoiseMask(times255(m.staff))
            fun masked(x: Mat): Mat { if (noise == null) return x; val o = Mat(); Core.bitwise_and(x, x, o, noise); return o }
            val staff = Preprocess.makeLinesStronger(masked(m.staff), 1, 2)
            return Pair(Predictions(staff, masked(m.symbols), masked(m.stemsRests), masked(m.notehead), masked(m.clefsKeys)), img)
        }
    }

    @Test fun staffDetectionMatchesHomr() {
        assumeTrue(TestEnv.models != null && TestEnv.golden != null && TestEnv.pages != null)
        TestEnv.loadOpenCv()
        val page = System.getenv("OMR_PAGE") ?: "p3"
        val (pred, _) = predictionsFor(page)
        val t0 = System.nanoTime()
        val systems = Systems.detect(pred)
        println("detección de pentagramas: ${(System.nanoTime() - t0) / 1e9} s")
        val ref = Json.parse(File("${TestEnv.golden}/$page/staffs.json").readText()).asMap()
        val refSystems = ref["systems"].asList()
        println("sistemas: Kotlin=${systems.size} homr=${refSystems.size}")
        println("Kotlin: " + systems.map { ms -> ms.staffs.map { Triple(it.isGrandstaff, Math.round(it.minY), it.grid.size) } })
        assertEquals(refSystems.size, systems.size)
        for ((si, rs) in refSystems.withIndex()) {
            val rstaffs = rs.asList()
            assertEquals(rstaffs.size, systems[si].staffs.size, "pentagramas del sistema $si")
            for ((i, rst) in rstaffs.withIndex()) {
                val r = rst.asMap(); val s = systems[si].staffs[i]
                assertEquals(r["grand"], s.isGrandstaff)
                assertTrue(abs((r["minY"] as Double) - s.minY) < 1e-3, "minY $si/$i: ${r["minY"]} vs ${s.minY}")
                val grid = r["grid"].asList()
                assertEquals(grid.size, s.grid.size, "puntos de la rejilla $si/$i")
                var worst = 0.0
                for ((k, gp) in grid.withIndex()) {
                    val g = gp.asList()
                    val ys = g[1].asList()
                    worst = maxOf(worst, abs((g[0] as Double) - s.grid[k].x))
                    for (j in ys.indices) worst = maxOf(worst, abs((ys[j] as Double) - s.grid[k].y[j]))
                }
                println("sistema $si pentagrama $i: ${grid.size} puntos, diferencia máxima $worst")
                assertTrue(worst < 1e-3)
            }
        }
    }
}
