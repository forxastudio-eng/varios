package com.forxa.pianocascada.omr

import ai.onnxruntime.OrtEnvironment
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar

/** Rutas de los tres modelos (los mismos de homr, formato ONNX). */
class OmrModels(val segnet: String, val encoder: String, val decoder: String)

/** Resultado de leer una página: un flujo de símbolos por voz (cada pentagrama de un sistema es una voz). */
class PageResult(val voices: List<List<EncodedSymbol>>, val warnings: List<String>)

/**
 * Lector de partituras sin conexión (traducción a Kotlin de homr, AGPL-3.0).
 * Sirve igual en el PC (pruebas) y en Android: solo necesita OpenCV y ONNX Runtime.
 */
class OmrEngine(private val models: OmrModels, private val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)) : AutoCloseable {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val vocab = Vocab.load()
    private var segnet: Segnet? = null
    private var tromr: Tromr? = null

    private fun segnet(): Segnet = segnet ?: Segnet(env, models.segnet, threads).also { segnet = it }
    private fun tromr(): Tromr = tromr ?: Tromr(env, models.encoder, models.decoder, vocab, threads).also { tromr = it }

    override fun close() { segnet?.close(); tromr?.close() }

    /**
     * @param page imagen BGR (CV_8UC3) de una página de partitura.
     * @param onProgress fracción 0..1 y mensaje.
     */
    fun readPage(page: Mat, onProgress: (Float, String) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false }): PageResult {
        onProgress(0f, "Preparando la página…")
        var img = Preprocess.autocrop(page)
        img = Preprocess.resizeImage(img)
        val pre = Preprocess.applyClahe(img)

        onProgress(0.02f, "Buscando los símbolos…")
        val masks = segnet().extract(pre, onProgress = { onProgress(0.02f + 0.38f * it, "Buscando los símbolos…") })
        if (cancelled()) throw InterruptedException("cancelado")

        val times255 = Mat(); Core.multiply(masks.staff, Scalar(255.0), times255)
        val noise = Preprocess.createNoiseMask(times255)
        times255.release()
        fun masked(m: Mat): Mat { if (noise == null) return m; val o = Mat(); Core.bitwise_and(m, m, o, noise); return o }
        val staff = Preprocess.makeLinesStronger(masked(masks.staff), 1, 2)
        val predictions = Predictions(staff, masked(masks.symbols), masked(masks.stemsRests), masked(masks.notehead), masked(masks.clefsKeys))

        onProgress(0.42f, "Localizando los pentagramas…")
        val systems = Systems.detect(predictions)
        return parseStaffs(systems, pre, onProgress, 0.45f, 0.55f, cancelled)
    }

    fun parseStaffs(
        systemsIn: List<MultiStaff>, image: Mat, onProgress: (Float, String) -> Unit, base: Float, span: Float, cancelled: () -> Boolean,
    ): PageResult {
        val warnings = ArrayList<String>()
        val systems = StaffParsing.ensureSameNumberOfStaffs(systemsIn, image.rows())
        val voices = systems[0].staffs.size
        val regions = StaffRegions(systems)
        val total = systems.size * voices
        var done = 0
        val result = ArrayList<List<EncodedSymbol>>()
        for (voice in 0 until voices) {
            val forVoice = ArrayList<EncodedSymbol>()
            for (system in systems) {
                if (cancelled()) throw InterruptedException("cancelado")
                val staff = system.staffs[voice]
                onProgress(base + span * done / total, "Leyendo el pentagrama ${done + 1} de $total…")
                val symbols = try {
                    val canvas = StaffParsing.prepareStaffImage(staff, image, regions)
                    val raw = tromr().predict(canvas)
                    canvas.release()
                    if (staff.isGrandstaff) raw else raw.filter { it.position != "lower" }
                } catch (e: InterruptedException) { throw e
                } catch (e: Exception) {
                    warnings.add("No se pudo leer el pentagrama ${done + 1}: ${e.message}")
                    emptyList()
                }
                done++
                if (symbols.isEmpty()) continue
                forVoice.addAll(symbols)
                forVoice.add(EncodedSymbol("newline"))
            }
            result.add(TokenOps.removeDuplicatedSymbols(forVoice))
        }
        onProgress(base + span, "Página leída")
        return PageResult(result, warnings)
    }
}
