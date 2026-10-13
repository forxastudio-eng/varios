package com.forxa.pianocascada.omr

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

/** Variables de entorno de las pruebas que comparan con homr (Python): rutas locales, no forman parte del repositorio. */
object TestEnv {
    val models: String? = System.getenv("OMR_MODELS")
    val golden: String? = System.getenv("OMR_GOLDEN")      // carpeta con una subcarpeta por página (volcado de homr)
    val pages: String? = System.getenv("OMR_PAGES")        // carpeta con las páginas de entrada (PNG)
    private var loaded = false
    fun loadOpenCv() { if (!loaded) { nu.pattern.OpenCV.loadLocally(); loaded = true } }
    fun modelFile(prefix: String): String =
        File(models!!).walkTopDown().first { it.name.startsWith(prefix) && it.name.endsWith(".onnx") && !it.name.contains("fp16") }.absolutePath
}

fun readImage(path: String, flags: Int = Imgcodecs.IMREAD_COLOR): Mat {
    val m = Imgcodecs.imread(path, flags)
    require(!m.empty()) { "No se pudo leer $path" }
    return m
}

/** Fracción de elementos distintos entre dos imágenes del mismo tamaño. */
fun diffFraction(a: Mat, b: Mat): Double {
    require(a.rows() == b.rows() && a.cols() == b.cols() && a.channels() == b.channels()) { "tamaños distintos ${a.rows()}x${a.cols()} vs ${b.rows()}x${b.cols()}" }
    val d = Mat()
    Core.absdiff(a, b, d)
    val one = if (d.channels() == 1) d else { val g = Mat(); org.opencv.imgproc.Imgproc.cvtColor(d, g, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY); g }
    val nz = Core.countNonZero(one)
    return nz.toDouble() / (a.rows() * a.cols())
}

fun maxDiff(a: Mat, b: Mat): Double {
    val d = Mat()
    Core.absdiff(a, b, d)
    val flat = d.reshape(1)
    return Core.minMaxLoc(flat).maxVal
}
