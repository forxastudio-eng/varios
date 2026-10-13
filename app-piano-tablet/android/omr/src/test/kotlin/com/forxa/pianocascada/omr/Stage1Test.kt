package com.forxa.pianocascada.omr

import ai.onnxruntime.OrtEnvironment
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Preprocesado y segmentación frente al volcado de homr (Python). Solo se ejecuta con OMR_MODELS, OMR_GOLDEN y OMR_PAGES. */
class Stage1Test {
    private fun scaled(m: Mat): Mat { val o = Mat(); Core.multiply(m, org.opencv.core.Scalar(255.0), o); return o }

    @Test fun preprocessAndSegmentationMatchHomr() {
        assumeTrue(TestEnv.models != null && TestEnv.golden != null && TestEnv.pages != null)
        TestEnv.loadOpenCv()
        val page = System.getenv("OMR_PAGE") ?: "p3"
        val g = "${TestEnv.golden}/$page"
        var img = readImage("${TestEnv.pages}/$page.png")
        img = Preprocess.autocrop(img)
        val d1 = diffFraction(img, readImage("$g/01_autocrop.png"))
        println("autocrop: píxeles distintos = $d1")
        assertTrue(d1 == 0.0)
        img = Preprocess.resizeImage(img)
        val ref2 = readImage("$g/02_resized.png")
        println("resize: distintos = ${diffFraction(img, ref2)}, diferencia máxima = ${maxDiff(img, ref2)}")
        assertTrue(maxDiff(img, ref2) <= 1.0)
        val pre = Preprocess.applyClahe(img)
        val ref3 = readImage("$g/03_clahe.png", Imgcodecs.IMREAD_GRAYSCALE)
        println("clahe: distintos = ${diffFraction(pre, ref3)}, máx = ${maxDiff(pre, ref3)}")

        val env = OrtEnvironment.getEnvironment()
        val t0 = System.nanoTime()
        val masks = Segnet(env, TestEnv.modelFile("segnet"), 4).use { it.extract(pre) }
        println("segmentación: ${(System.nanoTime() - t0) / 1e9} s")
        for ((name, m) in listOf("staff" to masks.staff, "symbols" to masks.symbols, "stems" to masks.stemsRests, "notehead" to masks.notehead, "clefs" to masks.clefsKeys)) {
            val ref = readImage("$g/04_seg_$name.png", Imgcodecs.IMREAD_GRAYSCALE)
            val f = diffFraction(scaled(m), ref)
            println("máscara $name: píxeles distintos = $f (de ${Core.countNonZero(ref)} activos)")
            assertTrue(f < 0.0005, "máscara $name difiere demasiado: $f")
        }

        // filtrado de ruido y líneas más gruesas
        val noise = Preprocess.createNoiseMask(scaled(masks.staff))
        println("máscara de ruido: ${if (noise == null) "ninguna" else "sí"}")
        fun masked(m: Mat): Mat { if (noise == null) return m; val o = Mat(); Core.bitwise_and(m, m, o, noise); return o }
        val staff = Preprocess.makeLinesStronger(masked(masks.staff), 1, 2)
        val refStaff = readImage("$g/05_pred_staff.png", Imgcodecs.IMREAD_GRAYSCALE)
        val fs = diffFraction(scaled(staff), refStaff)
        println("staff final: distintos = $fs")
        assertTrue(fs < 0.001)
        for ((name, m) in listOf("symbols" to masks.symbols, "stems" to masks.stemsRests, "notehead" to masks.notehead, "clefs" to masks.clefsKeys)) {
            val f = diffFraction(scaled(masked(m)), readImage("$g/05_pred_$name.png", Imgcodecs.IMREAD_GRAYSCALE))
            println("final $name: distintos = $f")
            assertTrue(f < 0.0005)
        }
    }
}
