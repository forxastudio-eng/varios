package com.forxa.pianocascada.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.omr.SystemTokens
import com.forxa.pianocascada.omr.OmrEngine
import com.forxa.pianocascada.omr.OmrException
import com.forxa.pianocascada.omr.OmrModels
import com.forxa.pianocascada.omr.ScoreBuilder
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.nio.ByteBuffer

/** Lee una partitura (PDF o foto) con el lector que va dentro de la app: sin internet y sin servidor. */
class ScoreReader(private val context: Context) {
    class Output(val song: Song, val warnings: List<String>, val pages: Int)

    companion object {
        private const val MAX_PAGES = 40
        private const val PDF_WIDTH = 2400          // ancho al que se dibuja cada página del PDF
        private const val MAX_PHOTO_SIDE = 4200
        private const val MODEL_VERSION = 1
        private var openCvLoaded = false

        @Synchronized fun loadOpenCv() {
            if (openCvLoaded) return
            try { System.loadLibrary("opencv_java4") } catch (e: UnsatisfiedLinkError) {
                throw IllegalStateException("No se pudo cargar OpenCV en este dispositivo")
            }
            openCvLoaded = true
        }
    }

    /** Copia los modelos del APK a la carpeta de la app (una sola vez) para que ONNX Runtime los abra por ruta. */
    private fun ensureModels(onProgress: (Float, String) -> Unit): OmrModels {
        val dir = File(context.filesDir, "omr").apply { mkdirs() }
        val names = listOf("segnet.onnx", "encoder.onnx", "decoder.onnx")
        val marker = File(dir, "version")
        val upToDate = marker.exists() && marker.readText().trim() == MODEL_VERSION.toString() && names.all { File(dir, it).length() > 1_000_000 }
        if (!upToDate) {
            for ((i, n) in names.withIndex()) {
                onProgress(0.01f + 0.03f * i / names.size, "Preparando el lector de partituras (solo la primera vez)…")
                val tmp = File(dir, "$n.tmp")
                try {
                    context.assets.open("omr/$n").use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 20) } }
                } catch (e: java.io.FileNotFoundException) {
                    throw OmrException("Esta versión de la app no incluye el lector de partituras.")
                }
                tmp.renameTo(File(dir, n))
            }
            marker.writeText(MODEL_VERSION.toString())
        }
        return OmrModels(File(dir, "segnet.onnx").path, File(dir, "encoder.onnx").path, File(dir, "decoder.onnx").path)
    }

    private fun bitmapToMat(bmp: Bitmap): Mat {
        val buf = ByteBuffer.allocate(bmp.byteCount)
        bmp.copyPixelsToBuffer(buf)
        val rgba = Mat(bmp.height, bmp.width, CvType.CV_8UC4)
        rgba.put(0, 0, buf.array())
        val bgr = Mat()
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        rgba.release()
        return bgr
    }

    private class Source(val count: Int, val render: (Int) -> Mat, val close: () -> Unit)

    private fun isPdf(uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri) ?: ""
        if (mime.contains("pdf")) return true
        val head = ByteArray(5)
        val n = context.contentResolver.openInputStream(uri)?.use { it.read(head) } ?: 0
        return n == 5 && String(head, Charsets.ISO_8859_1) == "%PDF-"
    }

    private fun openPdf(uri: Uri): Source {
        val tmp = File(context.cacheDir, "partitura.pdf")
        context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it, 1 shl 20) } }
        val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val count = minOf(renderer.pageCount, MAX_PAGES)
        return Source(count, { index ->
            val page = renderer.openPage(index)
            try {
                val w = PDF_WIDTH
                val h = (page.height.toFloat() * w / page.width).toInt().coerceIn(200, 6000)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val m = bitmapToMat(bmp)
                bmp.recycle()
                m
            } finally { page.close() }
        }, { renderer.close(); pfd.close(); tmp.delete() })
    }

    private fun openPhoto(uri: Uri): Source {
        return Source(1, {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_PHOTO_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw OmrException("No se pudo abrir la imagen")
            val rotation = context.contentResolver.openInputStream(uri)!!.use { s ->
                runCatching {
                    when (android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }.getOrDefault(0f)
            }
            if (rotation != 0f) {
                val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
                if (r !== bmp) bmp.recycle()
                bmp = r
            }
            // sobre fondo blanco por si la imagen tiene transparencia
            val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            flat.eraseColor(Color.WHITE)
            android.graphics.Canvas(flat).drawBitmap(bmp, 0f, 0f, null)
            bmp.recycle()
            val m = bitmapToMat(flat)
            flat.recycle()
            m
        }, {})
    }

    fun read(uri: Uri, id: String, title: String, onProgress: (Float, String) -> Unit, cancelled: () -> Boolean): Output {
        loadOpenCv()
        val models = ensureModels(onProgress)
        val source = if (isPdf(uri)) openPdf(uri) else openPhoto(uri)
        val pages = ArrayList<List<SystemTokens>>()
        val warnings = ArrayList<String>()
        try {
            if (source.count == 0) throw OmrException("El PDF no tiene páginas")
            OmrEngine(models, threads = 4).use { engine ->
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
