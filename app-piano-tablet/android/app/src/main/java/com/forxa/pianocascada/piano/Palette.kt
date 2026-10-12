package com.forxa.pianocascada.piano

import android.graphics.Color
import com.forxa.pianocascada.core.Hand
import com.forxa.pianocascada.data.ColorScheme

object Palette {
    const val RIGHT = 0xFF5AB8FF.toInt()
    const val LEFT = 0xFFFFB454.toInt()
    const val OK = 0xFF3DDC97.toInt()
    const val MISS = 0xFFFF6B6B.toInt()
    const val WARM = 0xFFFFE9B8.toInt()
    const val MONO = 0xFFE8EEF7.toInt()
    private val hsv = FloatArray(3)

    fun note(pitch: Int, hand: Hand, scheme: ColorScheme): Int = when (scheme) {
        ColorScheme.BY_HAND -> if (hand == Hand.RIGHT) RIGHT else LEFT
        ColorScheme.MONO -> MONO
        ColorScheme.BY_NOTE -> {
            // un matiz por cada una de las 12 notas: C rojo → B violeta
            synchronized(hsv) { hsv[0] = (pitch % 12) * 30f; hsv[1] = 0.62f; hsv[2] = 1f; Color.HSVToColor(hsv) }
        }
        ColorScheme.BY_OCTAVE -> {
            val oct = (pitch / 12 - 1).coerceIn(0, 8)
            synchronized(hsv) { hsv[0] = 25f + oct * 32f; hsv[1] = 0.6f; hsv[2] = 1f; Color.HSVToColor(hsv) }
        }
    }

    fun withAlpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24)

    fun mix(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        val r = (Color.red(a) * (1 - u) + Color.red(b) * u).toInt()
        val g = (Color.green(a) * (1 - u) + Color.green(b) * u).toInt()
        val bl = (Color.blue(a) * (1 - u) + Color.blue(b) * u).toInt()
        return Color.argb(255, r, g, bl)
    }
}
