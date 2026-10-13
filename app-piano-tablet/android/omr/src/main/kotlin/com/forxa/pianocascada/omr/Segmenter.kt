package com.forxa.pianocascada.omr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.nio.FloatBuffer

/** Resultado de la segmentación: máscaras 0/1 del tamaño de la imagen. */
class Masks(val staff: Mat, val symbols: Mat, val stemsRests: Mat, val notehead: Mat, val clefsKeys: Mat)

/** Red de segmentación (UNet): traducción de homr/segmentation/inference_segnet.py. */
class Segnet(private val env: OrtEnvironment, modelPath: String, threads: Int) : AutoCloseable {
    private val session: OrtSession

    init {
        val opts = OrtSession.SessionOptions()
        opts.setIntraOpNumThreads(threads)
        session = env.createSession(modelPath, opts)
    }

    override fun close() = session.close()

    /** gray: imagen de un canal (CV_8UC1). onProgress recibe una fracción 0..1. */
    fun extract(gray: Mat, batchSize: Int = 8, winSize: Int = 320, step: Int = 320, onProgress: (Float) -> Unit = {}): Masks {
        val h = gray.rows(); val w = gray.cols()
        val pixels = ByteArray(h * w)
        gray.get(0, 0, pixels)

        // posiciones de las teselas
        val ys = ArrayList<Int>(); var yl = 0
        while (yl < maxOf(h, winSize)) { ys.add(minOf(yl, h - winSize)); yl += step }
        val xs = ArrayList<Int>(); var xl = 0
        while (xl < maxOf(w, winSize)) { xs.add(minOf(xl, w - winSize)); xl += step }
        val total = ys.size * xs.size

        val classes = ArrayList<ByteArray>(total)
        val inBuf = FloatArray(batchSize * 3 * winSize * winSize)
        var batchCount = 0
        val shapeFull = longArrayOf(batchSize.toLong(), 3, winSize.toLong(), winSize.toLong())

        fun runBatch(count: Int) {
            val shape = if (count == batchSize) shapeFull else longArrayOf(count.toLong(), 3, winSize.toLong(), winSize.toLong())
            val buf = FloatBuffer.wrap(inBuf, 0, count * 3 * winSize * winSize)
            OnnxTensor.createTensor(env, buf, shape).use { t ->
                session.run(mapOf("input" to t)).use { res ->
                    val out = res.get(0) as OnnxTensor
                    val fb = out.floatBuffer
                    val nClass = 6
                    val plane = winSize * winSize
                    for (b in 0 until count) {
                        val cls = ByteArray(plane)
                        val base = b * nClass * plane
                        for (p in 0 until plane) {
                            var best = 0; var bv = fb.get(base + p)
                            for (c in 1 until nClass) {
                                val v = fb.get(base + c * plane + p)
                                if (v > bv) { bv = v; best = c }
                            }
                            cls[p] = best.toByte()
                        }
                        classes.add(cls)
                    }
                }
            }
        }

        val plane = winSize * winSize
        for (y in ys) {
            for (x in xs) {
                // tesela (3, win, win) con relleno blanco fuera de la imagen; los tres canales son iguales
                val off = batchCount * 3 * plane
                for (py in 0 until winSize) {
                    val sy = y + py
                    for (px in 0 until winSize) {
                        val sx = x + px
                        val v = if (sy in 0 until h && sx in 0 until w) (pixels[sy * w + sx].toInt() and 0xFF).toFloat() else 255f
                        val i = py * winSize + px
                        inBuf[off + i] = v; inBuf[off + plane + i] = v; inBuf[off + 2 * plane + i] = v
                    }
                }
                batchCount++
                if (batchCount == batchSize) {
                    runBatch(batchCount); batchCount = 0
                    onProgress(classes.size.toFloat() / total)
                }
            }
        }
        if (batchCount > 0) { runBatch(batchCount); onProgress(1f) }

        // mezcla de teselas: media (entera) de las clases de cada píxel
        val sum = IntArray(h * w); val weight = IntArray(h * w)
        var idx = 0
        var iy = 0
        while (iy < h) {
            val y = minOf(iy, h - winSize); val y0 = maxOf(y, 0); val y1 = minOf(y + winSize, h)
            var ix = 0
            while (ix < w) {
                val x = minOf(ix, w - winSize); val x0 = maxOf(x, 0); val x1 = minOf(x + winSize, w)
                val patch = classes[idx++]
                for (yy in y0 until y1) {
                    val pr = (yy - y0) * winSize
                    for (xx in x0 until x1) {
                        val k = yy * w + xx
                        sum[k] += patch[pr + (xx - x0)].toInt(); weight[k]++
                    }
                }
                ix += step
            }
            iy += step
        }
        val staff = ByteArray(h * w); val symbols = ByteArray(h * w); val stems = ByteArray(h * w)
        val note = ByteArray(h * w); val clefs = ByteArray(h * w)
        for (k in 0 until h * w) {
            val m = sum[k] / (if (weight[k] == 0) 1 else weight[k])
            when (m) { 1 -> stems[k] = 1; 2 -> note[k] = 1; 3 -> clefs[k] = 1; 4 -> staff[k] = 1; 5 -> symbols[k] = 1 }
        }
        fun toMat(a: ByteArray): Mat { val m = Mat(h, w, CvType.CV_8UC1); m.put(0, 0, a); return m }
        return Masks(toMat(staff), toMat(symbols), toMat(stems), toMat(note), toMat(clefs))
    }
}
