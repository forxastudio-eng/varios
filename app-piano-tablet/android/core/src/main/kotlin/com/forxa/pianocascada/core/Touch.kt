package com.forxa.pianocascada.core

enum class CurveKind { SOFT, NORMAL, HARD, FIXED }

/** Convierte "cuánto de fuerte tocas" (0..1) en velocidad MIDI (1..127) según el ecualizador del teclado. */
class VelocityMapper(
    var kind: CurveKind = CurveKind.NORMAL,
    var fixed: Int = 90,
    var lowZone: Float = 1f,   // graves  (< C3)
    var midZone: Float = 1f,   // centro
    var highZone: Float = 1f,  // agudos  (> B5)
) {
    fun velocity(strength: Float, pitch: Int): Int {
        val x = strength.coerceIn(0f, 1f)
        val base = when (kind) {
            CurveKind.FIXED -> fixed.toFloat()
            CurveKind.SOFT -> 28f + 99f * Math.pow(x.toDouble(), 0.65).toFloat()
            CurveKind.NORMAL -> 22f + 105f * x
            CurveKind.HARD -> 18f + 109f * Math.pow(x.toDouble(), 1.6).toFloat()
        }
        val zone = when {
            pitch < 48 -> lowZone
            pitch > 83 -> highZone
            else -> midZone
        }
        return Math.round(base * zone).coerceIn(1, 127)
    }

    companion object {
        /**
         * Fuerza a partir del tamaño del contacto del dedo: más superficie ≈ pulsación más fuerte.
         * [minSize] y [maxSize] salen de la calibración inicial ("toca suave… ahora fuerte").
         */
        fun strengthFromContact(size: Float, minSize: Float, maxSize: Float): Float {
            if (maxSize - minSize < 1e-4f) return 0.6f
            return ((size - minSize) / (maxSize - minSize)).coerceIn(0f, 1f)
        }
    }
}

/**
 * Autocalibración de la fuerza del toque: aprende el rango habitual del tamaño de contacto del dedo (en mm)
 * para que "tocar suave" y "tocar fuerte" usen todo el rango de velocidades sin configurar nada.
 */
class AdaptiveStrength(var lo: Float = 5f, var hi: Float = 13f) {
    fun observe(sizeMm: Float): Float {
        if (sizeMm < lo) lo = sizeMm else lo += (sizeMm - lo) * 0.01f
        if (sizeMm > hi) hi = sizeMm else hi -= (hi - sizeMm) * 0.01f
        if (hi - lo < 3f) { val mid = (hi + lo) / 2f; lo = mid - 1.5f; hi = mid + 1.5f }
        return ((sizeMm - lo) / (hi - lo)).coerceIn(0f, 1f)
    }
}
