package com.forxa.pianocascada.omr

import ai.onnxruntime.OrtEnvironment
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar

/** Rutas de los tres modelos (los mismos de homr, formato ONNX). */
class OmrModels(val segnet: String, val encoder: String, val decoder: String)

/** Símbolos leídos de un pentagrama; [isGrand] indica que agrupa dos pentagramas (mano derecha e izquierda). */
class StaffTokens(val tokens: List<EncodedSymbol>, val isGrand: Boolean)

/** Un sistema (línea de la partitura): sus pentagramas de arriba abajo. */
class SystemTokens(val staves: List<StaffTokens>)

/** Resultado de leer una página. */
class PageResult(val systems: List<SystemTokens>, val warnings: List<String>) {
    /** Como lo agrupa homr: un flujo de símbolos por voz (el pentagrama n de cada sistema), ya limpio. */
    val voices: List<List<EncodedSymbol>> by lazy {
        if (systems.isEmpty()) return@lazy emptyList()
        val count = systems[0].staves.size
        (0 until count).map { v ->
            val all = ArrayList<EncodedSymbol>()
            for (s in systems) {
                val t = s.staves.getOrNull(v)?.tokens ?: continue
                if (t.isEmpty()) continue
                all.addAll(t); all.add(EncodedSymbol("newline"))
            }
            TokenOps.removeDuplicatedSymbols(all)
        }
    }
}

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
        val regions = StaffRegions(systems)
        val total = systems.sumOf { it.staffs.size }
        var done = 0
        val result = ArrayList<SystemTokens>()
        for (system in systems) {
            val staves = ArrayList<StaffTokens>()
            for (staff in system.staffs) {
                if (cancelled()) throw InterruptedException("cancelado")
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
                staves.add(StaffTokens(symbols, staff.isGrandstaff))
            }
            result.add(SystemTokens(staves))
        }
        onProgress(base + span, "Página leída")
        return PageResult(result, warnings)
    }
}
