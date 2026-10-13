package com.forxa.pianocascada.piano

import com.forxa.pianocascada.core.Hand
import com.forxa.pianocascada.data.ColorScheme
import java.awt.Color as AwtColor

object Palette {
    const val RIGHT = 0xFF5AB8FF.toInt()
    const val LEFT = 0xFFFFB454.toInt()
    const val OK = 0xFF3DDC97.toInt()
    const val MISS = 0xFFFF6B6B.toInt()
    const val WARM = 0xFFFFE9B8.toInt()
    const val MONO = 0xFFE8EEF7.toInt()

    private fun hsv(h: Float, s: Float, v: Float): Int = AwtColor.HSBtoRGB(((h % 360f) + 360f) % 360f / 360f, s, v)

    fun note(pitch: Int, hand: Hand, scheme: ColorScheme): Int = when (scheme) {
        ColorScheme.BY_HAND -> if (hand == Hand.RIGHT) RIGHT else LEFT
        ColorScheme.MONO -> MONO
        // un matiz por cada una de las 12 notas: C rojo → B violeta
        ColorScheme.BY_NOTE -> hsv((pitch % 12) * 30f, 0.62f, 1f)
        ColorScheme.BY_OCTAVE -> hsv(25f + (pitch / 12 - 1).coerceIn(0, 8) * 32f, 0.6f, 1f)
    }

    fun withAlpha(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24)

    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF

    fun mix(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        val r = (red(a) * (1 - u) + red(b) * u).toInt()
        val g = (green(a) * (1 - u) + green(b) * u).toInt()
        val bl = (blue(a) * (1 - u) + blue(b) * u).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }
}
