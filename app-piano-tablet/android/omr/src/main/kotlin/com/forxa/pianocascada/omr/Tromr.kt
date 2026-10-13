package com.forxa.pianocascada.omr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.opencv.core.Mat
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * TrOMR: reconoce un pentagrama (imagen de 256×1280) y devuelve sus símbolos.
 * Traducción de homr/transformer (staff2score.py, encoder_inference.py, decoder_inference.py).
 */
class Tromr(private val env: OrtEnvironment, encoderPath: String, decoderPath: String, private val vocab: Vocab, threads: Int) : AutoCloseable {
    private val encoder: OrtSession
    private val decoder: OrtSession

    private val maxSeqLen = 608
    private val eosToken = 2
    private val heads = 8
    private val headDim = 512 / 8
    private val cacheCount = 8 * 4

    init {
        val opts = OrtSession.SessionOptions()
        opts.setIntraOpNumThreads(threads)
        encoder = env.createSession(encoderPath, opts)
        val opts2 = OrtSession.SessionOptions()
        opts2.setIntraOpNumThreads(threads)
        decoder = env.createSession(decoderPath, opts2)
    }

    override fun close() { encoder.close(); decoder.close() }

    companion object {
        const val HEIGHT = 256
        const val WIDTH = 1280
        private const val MEAN = 0.7931
        private const val STD = 0.1738
    }

    /** image: gris 256×1280 (CV_8UC1). */
    fun predict(image: Mat): List<EncodedSymbol> {
        require(image.rows() == HEIGHT && image.cols() == WIDTH && image.channels() == 1)
        val px = ByteArray(HEIGHT * WIDTH)
        image.get(0, 0, px)
        val x = FloatArray(HEIGHT * WIDTH) { (((px[it].toInt() and 0xFF) / 255.0 - MEAN) / STD).toFloat() }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(x), longArrayOf(1, 1, HEIGHT.toLong(), WIDTH.toLong())).use { input ->
            encoder.run(mapOf("input" to input)).use { enc ->
                val context = enc.get(0) as OnnxTensor
                return decode(context)
            }
        }
    }

    private fun longTensor(v: Long, shape: LongArray = longArrayOf(1, 1)): OnnxTensor =
        OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(v)), shape)

    private fun decode(context: OnnxTensor): List<EncodedSymbol> {
        val ctxShape = context.info.shape
        val ctxFull = context
        // tras el primer paso solo se pasa la primera posición del contexto
        val ctxData = context.floatBuffer
        val dim = ctxShape[2].toInt()
        val reduced = FloatArray(dim)
        for (i in 0 until dim) reduced[i] = ctxData.get(i)
        val ctxReduced = OnnxTensor.createTensor(env, FloatBuffer.wrap(reduced), longArrayOf(1, 1, dim.toLong()))

        var rhythm = 1L; var pitch = 0L; var lift = 0L; var articulation = 0L; var slur = 0L
        // caché inicial vacía (1, heads, 0, headDim)
        var cache: List<OnnxTensor> = List(cacheCount) {
            OnnxTensor.createTensor(env, FloatBuffer.allocate(0), longArrayOf(1, heads.toLong(), 0, headDim.toLong()))
        }
        var cacheResult: OrtSession.Result? = null
        val symbols = ArrayList<EncodedSymbol>()
        val outputNames = listOf("out_rhythms", "out_pitchs", "out_lifts", "out_positions", "out_articulations", "out_slurs") +
            (0 until cacheCount).map { "cache_out$it" }
        try {
            for (step in 0 until maxSeqLen) {
                val inputs = HashMap<String, OnnxTensor>()
                val toClose = ArrayList<OnnxTensor>()
                fun add(name: String, t: OnnxTensor, close: Boolean = true) { inputs[name] = t; if (close) toClose.add(t) }
                add("rhythms", longTensor(rhythm)); add("pitchs", longTensor(pitch)); add("lifts", longTensor(lift))
                add("articulations", longTensor(articulation)); add("slurs", longTensor(slur))
                add("context", if (step == 0) ctxFull else ctxReduced, close = false)
                add("cache_len", longTensor(step.toLong(), longArrayOf(1)))
                for (i in 0 until cacheCount) add("cache_in$i", cache[i], close = false)
                val res = decoder.run(inputs, outputNames.toSet())
                toClose.forEach { it.close() }
                // las cachés de la vuelta anterior ya no hacen falta
                if (cacheResult != null) cacheResult.close() else cache.forEach { it.close() }
                cacheResult = res
                cache = (0 until cacheCount).map { res.get("cache_out$it").get() as OnnxTensor }

                fun argmaxLast(name: String): Int {
                    val t = res.get(name).get() as OnnxTensor
                    val shape = t.info.shape
                    val n = shape[shape.size - 1].toInt()
                    val steps = if (shape.size >= 3) shape[1].toInt() else 1
                    val fb = t.floatBuffer
                    val base = (steps - 1) * n
                    var best = 0; var bv = fb.get(base)
                    for (k in 1 until n) { val v = fb.get(base + k); if (v > bv) { bv = v; best = k } }
                    return best
                }
                val r = argmaxLast("out_rhythms")
                val p = argmaxLast("out_pitchs")
                val l = argmaxLast("out_lifts")
                val a = argmaxLast("out_articulations")
                val s = argmaxLast("out_slurs")
                val pos = argmaxLast("out_positions")
                if (r == eosToken) break
                symbols.add(
                    EncodedSymbol(
                        rhythm = vocab.rhythm[r], pitch = vocab.pitch[p], lift = vocab.lift[l],
                        articulation = vocab.articulation[a], slur = vocab.slur[s], position = vocab.position[pos],
                    )
                )
                rhythm = r.toLong(); pitch = p.toLong(); lift = l.toLong(); articulation = a.toLong(); slur = s.toLong()
            }
        } finally {
            cacheResult?.close()
            ctxReduced.close()
        }
        return symbols
    }
}
