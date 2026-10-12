package com.forxa.pianocascada.transcribe

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/** Re-muestreo por interpolación sinc con ventana de Blackman (calidad alta, sin dependencias). */
object Resampler {
    fun resample(x: FloatArray, srcRate: Int, dstRate: Int, halfTaps: Int = 24): FloatArray {
        if (srcRate == dstRate) return x.copyOf()
        val ratio = srcRate.toDouble() / dstRate           // muestras de entrada por muestra de salida
        val outLen = floor(x.size / ratio).toInt()
        val out = FloatArray(outLen)
        // al reducir la frecuencia hay que recortar el espectro por debajo de la nueva Nyquist
        val fc = min(1.0, dstRate.toDouble() / srcRate) * 0.96
        val support = halfTaps / fc                          // semiancho del núcleo en muestras de entrada
        for (n in 0 until outLen) {
            val t = n * ratio
            val k0 = ceil(t - support).toInt().coerceAtLeast(0)
            val k1 = floor(t + support).toInt().coerceAtMost(x.size - 1)
            var acc = 0.0
            var norm = 0.0
            for (k in k0..k1) {
                val d = t - k
                val u = d * fc
                val s = if (abs(u) < 1e-9) 1.0 else sin(PI * u) / (PI * u)
                val w = d / support
                val win = if (abs(w) >= 1.0) 0.0 else 0.42 + 0.5 * cos(PI * w) + 0.08 * cos(2 * PI * w)
                val h = s * win * fc
                acc += x[k] * h
                norm += h
            }
            out[n] = (if (norm > 1e-6) acc / norm else acc).toFloat()
        }
        return out
    }

    /** Convierte PCM intercalado de varios canales a mono. */
    fun toMono(interleaved: ShortArray, channels: Int): FloatArray {
        if (channels <= 1) return FloatArray(interleaved.size) { interleaved[it] / 32768f }
        val n = interleaved.size / channels
        return FloatArray(n) { i ->
            var s = 0f
            for (c in 0 until channels) s += interleaved[i * channels + c]
            s / (channels * 32768f)
        }
    }
}
