package com.forxa.pianocascada.importer

import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.data.AppDirs
import com.forxa.pianocascada.omr.OmrEngine
import com.forxa.pianocascada.omr.OmrException
import com.forxa.pianocascada.omr.OmrModels
import com.forxa.pianocascada.omr.ScoreBuilder
import com.forxa.pianocascada.omr.SystemTokens
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import javax.imageio.ImageIO

/** Lee una partitura (PDF o foto) con el lector que va dentro de la app: sin internet y sin servidor. */
class ScoreReader {
    class Output(val song: Song, val warnings: List<String>, val pages: Int)

    companion object {
        private const val MAX_PAGES = 40
        private const val PDF_WIDTH = 2400          // ancho al que se dibuja cada página del PDF
        private const val MAX_PHOTO_SIDE = 4200
        private const val MODEL_VERSION = 1
        private var openCvLoaded = false

        @Synchronized fun loadOpenCv() {
            if (openCvLoaded) return
            try {
                nu.pattern.OpenCV.loadLocally()
            } catch (e: Throwable) {
                throw IllegalStateException("No se pudo cargar OpenCV en este ordenador: ${e.message}")
            }
            openCvLoaded = true
        }
    }

    /** Copia los modelos incluidos en la app a la carpeta de datos (una sola vez) para que ONNX Runtime los abra por ruta. */
    private fun ensureModels(onProgress: (Float, String) -> Unit): OmrModels {
        val dir = AppDirs.models
        val names = listOf("segnet.onnx", "encoder.onnx", "decoder.onnx")
        val marker = File(dir, "version")
        val upToDate = marker.exists() && marker.readText().trim() == MODEL_VERSION.toString() && names.all { File(dir, it).length() > 1_000_000 }
        if (!upToDate) {
            for ((i, n) in names.withIndex()) {
                onProgress(0.01f + 0.03f * i / names.size, "Preparando el lector de partituras (solo la primera vez)…")
                val tmp = File(dir, "$n.tmp")
                val input = ScoreReader::class.java.getResourceAsStream("/omr/$n")
                    ?: throw OmrException("Esta versión de la app no incluye el lector de partituras.")
                input.use { i2 -> tmp.outputStream().use { i2.copyTo(it, 1 shl 20) } }
                tmp.renameTo(File(dir, n))
            }
            marker.writeText(MODEL_VERSION.toString())
        }
        return OmrModels(File(dir, "segnet.onnx").path, File(dir, "encoder.onnx").path, File(dir, "decoder.onnx").path)
    }

    /** Imagen de Java → Mat BGR de OpenCV, sobre fondo blanco. */
    private fun toMat(src: BufferedImage): Mat {
        val bgr = BufferedImage(src.width, src.height, BufferedImage.TYPE_3BYTE_BGR)
        val g = bgr.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, src.width, src.height)
        g.drawImage(src, 0, 0, null)
        g.dispose()
        val data = (bgr.raster.dataBuffer as DataBufferByte).data
        val m = Mat(bgr.height, bgr.width, CvType.CV_8UC3)
        m.put(0, 0, data)
        return m
    }

    private class Source(val count: Int, val render: (Int) -> Mat, val close: () -> Unit)

    private fun isPdf(file: File): Boolean {
        if (file.extension.equals("pdf", true)) return true
        val head = ByteArray(5)
        val n = file.inputStream().use { it.read(head) }
        return n == 5 && String(head, Charsets.ISO_8859_1) == "%PDF-"
    }

    private fun openPdf(file: File): Source {
        val doc = Loader.loadPDF(file)
        val renderer = PDFRenderer(doc)
        val count = minOf(doc.numberOfPages, MAX_PAGES)
        return Source(count, { index ->
            val box = doc.getPage(index).cropBox
            val scale = PDF_WIDTH / box.width
            val img = renderer.renderImage(index, scale, ImageType.RGB)
            toMat(img)
        }, { doc.close() })
    }

    private fun openPhoto(file: File): Source = Source(1, {
        val img = ImageIO.read(file) ?: throw OmrException("No se pudo abrir la imagen")
        val m = toMat(img)
        val side = maxOf(m.rows(), m.cols())
        if (side > MAX_PHOTO_SIDE) {
            val k = MAX_PHOTO_SIDE.toDouble() / side
            val small = Mat()
            Imgproc.resize(m, small, Size(m.cols() * k, m.rows() * k), 0.0, 0.0, Imgproc.INTER_AREA)
            m.release()
            small
        } else m
    }, {})

    fun read(file: File, id: String, title: String, onProgress: (Float, String) -> Unit, cancelled: () -> Boolean): Output {
        loadOpenCv()
        val models = ensureModels(onProgress)
        val source = if (isPdf(file)) openPdf(file) else openPhoto(file)
        val pages = ArrayList<List<SystemTokens>>()
        val warnings = ArrayList<String>()
        try {
            if (source.count == 0) throw OmrException("El PDF no tiene páginas")
            OmrEngine(models, threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)).use { engine ->
                for (i in 0 until source.count) {
                    if (cancelled()) throw InterruptedException("cancelado")
                    val label = if (source.count > 1) "Página ${i + 1} de ${source.count}: " else ""
                    val baseP = 0.05f + 0.90f * i / source.count
                    val spanP = 0.90f / source.count
                    onProgress(baseP, label + "dibujando la página…")
                    val page = source.render(i)
                    try {
                        val r = engine.readPage(page, { p, m -> onProgress(baseP + spanP * p, label + m.replaceFirstChar { it.lowercase() }) }, cancelled)
                        if (r.systems.any { sys -> sys.staves.any { it.tokens.isNotEmpty() } }) pages.add(r.systems)
                        else warnings.add("La página ${i + 1} no tiene música reconocible.")
                        warnings.addAll(r.warnings.map { "Página ${i + 1}: $it" })
                    } catch (e: OmrException) {
                        warnings.add("Página ${i + 1}: ${e.message}")
                    } finally { page.release() }
                }
            }
        } finally { source.close() }
        if (pages.isEmpty()) throw OmrException("No se ha encontrado música en la partitura. Comprueba que la imagen es nítida y que se ven los pentagramas completos.")
        onProgress(0.97f, "Preparando tu versión fácil…")
        val built = ScoreBuilder.build(pages, id, title)
        return Output(built.song, warnings + built.warnings, source.count)
    }
}
