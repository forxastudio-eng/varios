package com.forxa.pianocascada.transcribe

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.floor

/** Salida del modelo: probabilidad de nota activa y de inicio de nota para cada una de las 88 teclas y fotograma. */
class ModelOutput(val frames: Int, val note: FloatArray, val onset: FloatArray) {
    fun note(t: Int, f: Int) = note[t * BINS + f]
    fun onset(t: Int, f: Int) = onset[t * BINS + f]
    companion object { const val BINS = 88 }
}

/**
 * Basic Pitch (Spotify, Apache 2.0) con ONNX Runtime. Audio a 22 050 Hz mono, ventanas de 43 844 muestras
 * con 30 fotogramas de solape; se descarta la mitad del solape a cada lado, como en la implementación de referencia.
 */
class BasicPitch(modelBytes: ByteArray, threads: Int = 4) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String
    private val noteName: String
    private val onsetName: String

    init {
        val opts = OrtSession.SessionOptions()
        opts.setIntraOpNumThreads(threads)
        session = env.createSession(modelBytes, opts)
        inputName = session.inputNames.first()
        val outs = session.outputNames.toList()
        // el modelo exporta: contour (…:0), note (…:1), onset (…:2)
        noteName = outs.firstOrNull { it.endsWith(":1") } ?: outs[1]
        onsetName = outs.firstOrNull { it.endsWith(":2") } ?: outs[2]
    }

    fun run(audio22k: FloatArray, progress: (Float) -> Unit = {}): ModelOutput {
        val overlapFrames = 30
        val overlapLen = overlapFrames * FFT_HOP
        val hop = WINDOW - overlapLen
        val padded = FloatArray(overlapLen / 2 + audio22k.size)
        System.arraycopy(audio22k, 0, padded, overlapLen / 2, audio22k.size)
        val nWin = (padded.size + hop - 1) / hop
        val keep = WINDOW_FRAMES - overlapFrames
        val noteAll = FloatArray(nWin * keep * ModelOutput.BINS)
        val onsetAll = FloatArray(nWin * keep * ModelOutput.BINS)
        val win = FloatArray(WINDOW)
        val shape = longArrayOf(1, WINDOW.toLong(), 1)
        for (w in 0 until nWin) {
            val start = w * hop
            java.util.Arrays.fill(win, 0f)
            val n = minOf(WINDOW, padded.size - start)
            if (n > 0) System.arraycopy(padded, start, win, 0, n)
            OnnxTensor.createTensor(env, FloatBuffer.wrap(win), shape).use { input ->
                session.run(mapOf(inputName to input), setOf(noteName, onsetName)).use { res ->
                    val note = (res.get(noteName).get() as OnnxTensor).floatBuffer
                    val onset = (res.get(onsetName).get() as OnnxTensor).floatBuffer
                    val off = overlapFrames / 2
                    val rows = note.remaining() / ModelOutput.BINS
                    for (r in 0 until keep) {
                        val src = (r + off).coerceAtMost(rows - 1) * ModelOutput.BINS
                        val dst = (w * keep + r) * ModelOutput.BINS
                        note.position(src); note.get(noteAll, dst, ModelOutput.BINS)
                        onset.position(src); onset.get(onsetAll, dst, ModelOutput.BINS)
                    }
                }
            }
            progress((w + 1f) / nWin)
        }
        val total = floor(audio22k.size / FFT_HOP.toDouble()).toInt().coerceAtMost(nWin * keep)
        return ModelOutput(total, noteAll.copyOf(total * ModelOutput.BINS), onsetAll.copyOf(total * ModelOutput.BINS))
    }

    override fun close() { session.close() }

    companion object {
        const val SAMPLE_RATE = 22050
        const val FFT_HOP = 256
        const val WINDOW = 43844
        const val WINDOW_FRAMES = 172
        const val FPS = SAMPLE_RATE.toDouble() / FFT_HOP

        /**
         * Tiempo (s) del fotograma [j] de la salida concatenada. Se deduce de la geometría de las ventanas:
         * ventana w, fotograma f = (j % 142) + 15 → muestra w·hop + f·256 − overlap/2.
         */
        fun frameTime(j: Int): Double {
            val keep = WINDOW_FRAMES - 30
            val hop = WINDOW - 30 * FFT_HOP
            val w = j / keep
            val f = (j % keep) + 15
            return (w.toDouble() * hop + f * FFT_HOP - 15 * FFT_HOP) / SAMPLE_RATE
        }
    }
}
