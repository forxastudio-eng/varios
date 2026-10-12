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
