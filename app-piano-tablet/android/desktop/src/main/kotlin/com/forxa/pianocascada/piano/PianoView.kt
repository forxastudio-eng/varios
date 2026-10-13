package com.forxa.pianocascada.piano

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.forxa.pianocascada.core.Hand
import com.forxa.pianocascada.core.KeyRow
import com.forxa.pianocascada.core.KeyboardPlanner
import com.forxa.pianocascada.core.Notes
import com.forxa.pianocascada.data.DisplaySettings
import com.forxa.pianocascada.data.KeyLetters
import com.forxa.pianocascada.data.KeyboardSettings
import com.forxa.pianocascada.data.LayoutMode
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Nota para dibujar en la cascada. [auto]: el motor la toca solo (la otra mano o el modo Escuchar). */
class DispNote(val startMs: Double, val durMs: Double, val pitch: Int, val hand: Hand, val sessionIdx: Int, val auto: Boolean)

/** Lo que la vista necesita de la lógica de práctica; se lee una vez por fotograma. */
interface FrameSource {
    fun onFrame(nowNs: Long)
    val posMs: Double
    val songNotes: Array<DispNote>
    val barTimesMs: DoubleArray
    val beatMs: Double
    /** 0 pendiente · 1 acertada y mantenida · 2 acertada · 3 fallada · 4 soltada antes de tiempo */
    fun resultOf(sessionIdx: Int): Int
    val pendingPitches: IntArray
    val followLeft: Float          // borde izquierdo ideal del teclado reducido (índice de tecla blanca)
}

interface KeyListener {
    /** Devuelve el color de luz de la tecla (0 = luz cálida por defecto). [sizeMm] hace de «fuerza» de la pulsación. */
    fun onKeyDown(pitch: Int, sizeMm: Float): Int
    /** Pulsación con velocidad MIDI conocida (teclado MIDI). */
    fun onKeyDownVelocity(pitch: Int, velocity: Int): Int
    fun onKeyUp(pitch: Int)
    fun onBackgroundTap()
    /** Pedal de sostenido del piano MIDI. */
    fun onPedal(down: Boolean) {}
}

/**
 * Cascada de notas + teclado(s) con luz. Misma lógica que la vista de la tablet, dibujada con Compose.
 * Se maneja con ratón, con el teclado del PC o con un piano MIDI.
 */
class PianoState {
    var source: FrameSource? = null
        set(v) { field = v; manualLeft = -1f }
    var listener: KeyListener? = null
    var layoutMode = LayoutMode.REDUCED
        set(v) { if (field != v) { field = v; rebuild = true } }
    var keyboardSettings = KeyboardSettings()
        set(v) { if (field != v) { field = v; rebuild = true } }
    var display = DisplaySettings()
    var reducedSpan = 15
        set(v) { if (field != v) { field = v; rebuild = true } }
    var split = 65
        set(v) { if (field != v) { field = v; rebuild = true } }
    var fitMin = -1
        set(v) { if (field != v) { field = v; rebuild = true } }
    var fitMax = -1
        set(v) { if (field != v) { field = v; rebuild = true } }
    var keyboardOnly = false
        set(v) { if (field != v) { field = v; rebuild = true } }
    var staticLeft: Float = -1f
    var highlight: IntArray = IntArray(0)
    var showLabelsOverride: KeyLetters? = null

    var width = 0f
        set(v) { if (field != v) { field = v; rebuild = true } }
    var height = 0f
        set(v) { if (field != v) { field = v; rebuild = true } }
    var density = 1f

    private companion object {
        const val PAN_NONE = 0
        const val PAN_WATERFALL = 1
        const val PAN_MINIMAP = 2
        const val PAN_BUTTON = 3
        const val PAN_AUTO = 4
    }

    private fun dp(v: Float) = v * density

    // ---- geometría
    private class RowGeom(val kb: KeyRow, val wfTop: Float, val wfBottom: Float)
    private val rows = ArrayList<RowGeom>(2)
    private var rebuild = true
    private var curLeft = -1f
    private var lastBuiltLeft = -999f
    private var ivory: Brush? = null
    private var black: Brush? = null

    // ---- estado de luz por tecla
    private val light = FloatArray(128)
    private val lightColor = IntArray(128)
    private val flash = FloatArray(128)
    private val flashColor = IntArray(128)
    private val userDown = BooleanArray(128)
    private val userColor = IntArray(128)
    private val autoColor = IntArray(128)
    private val warnColor = IntArray(128)
    private val warnAmt = FloatArray(128)
    private val ptrPitch = IntArray(20) { -1 }
    private var lastFrameNs = 0L
    private var needLeftArrow = 0
    private var needRightArrow = 0

    // ---- desplazamiento manual del teclado reducido
    private var manualLeft = -1f
    private var panId = -1
    private var panStartX = 0f
    private var panStartLeft = 0f
    private var panMoved = false
    private var panKind = PAN_NONE
    private var panButton = 0
    private val slop get() = dp(8f)

    private val names = Array(128) { Notes.name(it) }
    private val octNames = Array(128) { Notes.nameWithOctave(it) }

    private class R(var l: Float = 0f, var t: Float = 0f, var r: Float = 0f, var b: Float = 0f) {
        fun set(l: Float, t: Float, r: Float, b: Float) { this.l = l; this.t = t; this.r = r; this.b = b }
        fun contains(x: Float, y: Float) = x >= l && x < r && y >= t && y < b
        val cx get() = (l + r) / 2f
        val cy get() = (t + b) / 2f
        val w get() = r - l
    }

    private val autoRect = R()

    // ------------------------------------------------------------------ geometría
    private fun buildRows() {
        rows.clear()
        val w = width; val h = height
        if (w <= 0f || h <= 0f) { rebuild = false; return }
        val hs = keyboardSettings.heightScale
        val bw = keyboardSettings.blackWidth
        if (keyboardOnly) {
            val kbH = h * 0.92f
            rows.add(RowGeom(reducedRow(0f, h - kbH, w, kbH, bw), 0f, 0f))
        } else if (layoutMode != LayoutMode.REDUCED) {
            val (low, high) = if (layoutMode == LayoutMode.TWO_ROWS && fitMin >= 0 && fitMax >= fitMin) {
                KeyboardPlanner.twoRowsFit(fitMin, fitMax).let { it.first to it.second }
            } else KeyboardPlanner.twoRows(split)
            val top = dp(54f)
            val bottomMargin = dp(10f)
            val avail = h - top - bottomMargin
            val kb = min(avail * 0.235f * hs, avail * 0.36f)
            val gap = dp(6f)
            val wf = (avail - 2f * kb - gap) / 2f
            val hiY = top + wf
            val loWfTop = hiY + kb + gap
            val loY = loWfTop + wf
            rows.add(RowGeom(KeyRow(high.lo, high.hi, 0f, hiY, w, kb, bw, 0.62f), top, hiY))
            rows.add(RowGeom(KeyRow(low.lo, low.hi, 0f, loY, w, kb, bw, 0.62f), loWfTop, loY))
        } else {
            val kbH = h * 0.30f * hs
            val top = dp(54f)
            val wfBottom = h - kbH
            rows.add(RowGeom(reducedRow(0f, wfBottom, w, kbH, bw), top + dp(18f), wfBottom))
        }
        rebuildBrushes()
        rebuild = false
    }

    private fun reducedRow(x: Float, y: Float, w: Float, h: Float, bw: Float): KeyRow {
        val left = if (curLeft < 0f) (if (staticLeft >= 0f) staticLeft else 23f) else curLeft
        val r = KeyRow.window(left, reducedSpan.toFloat(), x, y, w, h, bw, 0.62f)
        lastBuiltLeft = left
        return r
    }

    private fun rebuildBrushes() {
        val r = rows.firstOrNull() ?: return
        ivory = Brush.verticalGradient(
            0f to Color(0xFFF2F0EA), 0.8f to Color(0xFFE4E1D9), 1f to Color(0xFFD3CFC5),
            startY = r.kb.y, endY = r.kb.y + r.kb.height,
        )
        black = Brush.verticalGradient(
            0f to Color(0xFF2A2A31), 0.85f to Color(0xFF141418), 1f to Color(0xFF0B0B0E),
            startY = r.kb.y, endY = r.kb.y + r.kb.blackHeight,
        )
    }

    // ------------------------------------------------------------------ luz y animación (una vez por fotograma)
    fun frame(nowNs: Long) {
        source?.onFrame(nowNs)
        updateLights(nowNs)
    }

    private fun updateLights(nowNs: Long) {
        val dt = if (lastFrameNs == 0L) 0.016f else ((nowNs - lastFrameNs) / 1e9f).coerceIn(0.001f, 0.1f)
        lastFrameNs = nowNs
        if (!keyboardOnly && layoutMode == LayoutMode.REDUCED) {
            val target = if (manualLeft >= 0f) manualLeft else if (staticLeft >= 0f) staticLeft else (source?.followLeft ?: 23f)
            val tl = KeyboardPlanner.clampLeft(target, reducedSpan)
            curLeft = if (curLeft < 0f) tl else curLeft + (tl - curLeft) * (1f - exp(-dt * 5f))
            if (abs(curLeft - lastBuiltLeft) > 0.002f) rebuild = true
        } else if (keyboardOnly) {
            val tl = if (staticLeft >= 0f) staticLeft else 23f
            if (abs(tl - lastBuiltLeft) > 0.002f) { curLeft = tl; rebuild = true }
        }
        val src = source
        val pos = src?.posMs ?: 0.0
        java.util.Arrays.fill(autoColor, 0)
        java.util.Arrays.fill(warnAmt, 0f)
        needLeftArrow = 0; needRightArrow = 0
        if (src != null) {
            val notes = src.songNotes
            val warn = display.preWarnMs.toDouble()
            val r0 = rows.firstOrNull()
            for (n in notes) {
                if (n.startMs > pos + 2500.0) break
                if (n.startMs + n.durMs < pos) continue
                val col = Palette.note(n.pitch, n.hand, display.colors)
                if (n.auto) {
                    if (n.startMs <= pos && pos < n.startMs + n.durMs) autoColor[n.pitch] = col
                } else if (n.startMs > pos && warn > 0 && n.startMs - pos <= warn) {
                    val a = (1.0 - (n.startMs - pos) / warn).toFloat()
                    if (a > warnAmt[n.pitch]) { warnAmt[n.pitch] = a; warnColor[n.pitch] = col }
                }
                if (r0 != null && layoutMode == LayoutMode.REDUCED && !keyboardOnly && n.startMs - pos < 2000.0) {
                    if (n.pitch < r0.kb.lo) needLeftArrow = col else if (n.pitch > r0.kb.hi) needRightArrow = col
                }
            }
        }
        val pend = src?.pendingPitches
        val aUp = 1f - exp(-dt / 0.02f)
        val aDown = 1f - exp(-dt / 0.16f)
        val aFlash = 1f - exp(-dt / 0.22f)
        for (k in 21..108) {
            var target = 0f
            var col = Palette.WARM
            if (userDown[k]) { target = 1f; col = if (userColor[k] != 0) userColor[k] else Palette.WARM }
            else if (autoColor[k] != 0) { target = 0.95f; col = autoColor[k] }
            else if (warnAmt[k] > 0f) { target = 0.12f + 0.38f * warnAmt[k]; col = warnColor[k] }
            else if (pend != null && pend.isNotEmpty() && contains(pend, k)) {
                val pulse = (0.5f + 0.5f * Math.sin(nowNs / 1e9 * 6.0).toFloat())
                target = 0.25f + 0.3f * pulse; col = Palette.WARM
            }
            if (highlight.isNotEmpty() && contains(highlight, k)) {
                val pulse = (0.5f + 0.5f * Math.sin(nowNs / 1e9 * 5.0).toFloat())
                target = max(target, 0.35f + 0.4f * pulse); col = Palette.RIGHT
            }
            val l = light[k]
            if (target > l) { light[k] = l + (target - l) * aUp; lightColor[k] = col }
            else { light[k] = l + (target - l) * aDown; if (target > 0f) lightColor[k] = col }
            if (flash[k] > 0.002f) flash[k] -= flash[k] * aFlash else flash[k] = 0f
        }
        if (rebuild) buildRows()
    }

    private fun contains(a: IntArray, v: Int): Boolean { for (x in a) if (x == v) return true; return false }

    // ------------------------------------------------------------------ dibujo
    fun draw(s: DrawScope, tm: TextMeasurer) {
        if (rebuild) buildRows()
        val src = source
        val pos = src?.posMs ?: 0.0
        for (r in rows) {
            if (!keyboardOnly) drawWaterfall(s, tm, r, pos, src)
            drawKeyboard(s, tm, r)
        }
        if (!keyboardOnly && layoutMode == LayoutMode.REDUCED) drawMiniMap(s, pos, src)
    }

    private fun col(c: Int) = Color(c)

    private fun DrawScope.rrect(c: Int, l: Float, t: Float, r: Float, b: Float, rad: Float) {
        if (r <= l || b <= t) return
        drawRoundRect(col(c), Offset(l, t), Size(r - l, b - t), CornerRadius(rad, rad))
    }

    private fun DrawScope.rect(c: Int, l: Float, t: Float, r: Float, b: Float) {
        if (r <= l || b <= t) return
        drawRect(col(c), Offset(l, t), Size(r - l, b - t))
    }

    /** Texto centrado en (cx, cy). */
    private fun DrawScope.label(tm: TextMeasurer, text: String, cx: Float, cy: Float, sizePx: Float, color: Int, bold: Boolean = true) {
        if (sizePx < 4f) return
        val layout = tm.measure(text, TextStyle(color = col(color), fontSize = sizePx.toSp(), fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal))
        drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
    }

    private fun drawWaterfall(s: DrawScope, tm: TextMeasurer, r: RowGeom, pos: Double, src: FrameSource?) = with(s) {
        val kb = r.kb
        val top = r.wfTop
        val lineY = r.wfBottom
        val hgt = lineY - top
        if (hgt <= 4f) return
        clipRect(0f, top, width, lineY) {
            if (display.guides) {
                for (i in 0 until kb.whiteCount) {
                    val pit = kb.whitePitches[i]
                    val rc = kb.rect(pit) ?: continue
                    rect(if (pit % 12 == 0) 0x14FFFFFF else 0x08FFFFFF, rc.left, top, rc.left + 1f, lineY)
                }
            }
            val fall = display.fallMs.toDouble().coerceAtLeast(800.0)
            val pxPerMs = (hgt / fall).toFloat()
            if (src != null) {
                for (t in src.barTimesMs) {
                    if (t < pos - 50 || t > pos + fall) continue
                    val y = lineY - ((t - pos) * pxPerMs).toFloat()
                    rect(0x22FFFFFF, 0f, y - 1f, width, y)
                }
            }
            if (src != null) {
                val rad = dp(7f)
                for (n in src.songNotes) {
                    if (n.startMs > pos + fall) break
                    val endMs = n.startMs + n.durMs
                    if (endMs < pos) continue
                    if (!kb.contains(n.pitch)) continue
                    val key = kb.rect(n.pitch) ?: continue
                    val isBlack = Notes.isBlack(n.pitch)
                    val bw = if (isBlack) key.width * 0.9f else key.width * 0.74f
                    val cx = key.centerX
                    val bottom = min(lineY - ((n.startMs - pos) * pxPerMs).toFloat(), lineY)
                    val topY = lineY - ((endMs - pos) * pxPerMs).toFloat()
                    if (topY > lineY) continue
                    val res = if (n.sessionIdx >= 0) src.resultOf(n.sessionIdx) else 0
                    var c = Palette.note(n.pitch, n.hand, display.colors)
                    var alpha = 1f
                    when (res) {
                        3 -> { c = 0xFF6A6A76.toInt(); alpha = 0.55f }
                        4 -> { c = 0xFF6A6A76.toInt(); alpha = 0.7f }
                        1, 2 -> c = Palette.mix(c, 0xFFFFFFFF.toInt(), 0.45f)
                    }
                    val left = cx - bw / 2f
                    val right = cx + bw / 2f
                    rrect(Palette.withAlpha(c, 0.16f * alpha), left - dp(4f), topY - dp(4f), right + dp(4f), bottom + dp(2f), rad + dp(3f))
                    rrect(Palette.withAlpha(c, 0.92f * alpha), left, topY, right, bottom, rad)
                    rrect(Palette.withAlpha(0xFFFFFFFF.toInt(), 0.22f * alpha), left + dp(2f), topY + dp(2f), left + max(dp(4f), bw * 0.28f), max(topY + dp(4f), bottom - dp(2f)), dp(3f))
                    if (display.noteLetters && bw >= dp(16f) && bottom - topY >= dp(10f)) {
                        val side = min(bw * 0.9f, dp(38f))
                        val cyl = bottom - side / 2f - dp(3f)
                        rrect(0xE6090A12.toInt(), cx - side / 2f, cyl - side / 2f, cx + side / 2f, cyl + side / 2f, side * 0.3f)
                        drawRoundRect(
                            col(Palette.withAlpha(c, alpha)), Offset(cx - side / 2f, cyl - side / 2f), Size(side, side),
                            CornerRadius(side * 0.3f), style = Stroke(dp(1.4f)),
                        )
                        label(tm, names[n.pitch], cx, cyl, side * (if (isBlack) 0.36f else 0.5f), Palette.withAlpha(0xFFFFFFFF.toInt(), alpha))
                    }
                }
            }
            rect(0x55FFFFFF, 0f, lineY - dp(1.5f), width, lineY)
            for (k in kb.lo..kb.hi) {
                val f = flash[k]
                if (f > 0.01f) {
                    val rc = kb.rect(k) ?: continue
                    glow(rc.centerX, lineY, kb.whiteWidth * 1.6f, flashColor[k], f * 0.8f)
                }
                if (light[k] > 0.05f && (userDown[k] || autoColor[k] != 0)) {
                    val rc = kb.rect(k) ?: continue
                    glow(rc.centerX, lineY, kb.whiteWidth * 1.3f, lightColor[k], light[k] * 0.55f)
                }
            }
            if (canPan()) {
                for (side in intArrayOf(-1, 1)) {
                    val b = buttonRect(side, top, hgt)
                    val need = if (side < 0) needLeftArrow else needRightArrow
                    val rad2 = b.w / 2f
                    drawRoundRect(col(if (need != 0) Palette.withAlpha(need, 0.28f) else 0x55000000), Offset(b.l, b.t), Size(b.w, b.w), CornerRadius(rad2))
                    drawRoundRect(
                        col(if (need != 0) Palette.withAlpha(need, 0.9f) else 0x40FFFFFF), Offset(b.l, b.t), Size(b.w, b.w),
                        CornerRadius(rad2), style = Stroke(dp(1.5f)),
                    )
                    arrow(b.cx, b.cy, side, if (need != 0) need else 0xCCFFFFFF.toInt())
                }
                if (manualLeft >= 0f) {
                    autoRect.set(width / 2f - dp(100f), top + dp(10f), width / 2f + dp(100f), top + dp(46f))
                    drawRoundRect(col(0xCC1D2433.toInt()), Offset(autoRect.l, autoRect.t), Size(autoRect.w, autoRect.b - autoRect.t), CornerRadius(dp(18f)))
                    drawRoundRect(col(0xFF5AB8FF.toInt()), Offset(autoRect.l, autoRect.t), Size(autoRect.w, autoRect.b - autoRect.t), CornerRadius(dp(18f)), style = Stroke(dp(1.5f)))
                    label(tm, "Seguir las notas", autoRect.cx, autoRect.cy, dp(14f), 0xFFFFFFFF.toInt())
                }
            }
        }
    }

    /** El desplazamiento a mano solo existe en el teclado reducido de las pantallas de práctica. */
    private fun canPan() = layoutMode == LayoutMode.REDUCED && !keyboardOnly && rows.isNotEmpty()

    private fun buttonRect(side: Int, top: Float, hgt: Float): R {
        val rad = dp(30f)
        val cy = top + hgt * 0.62f
        val cx = if (side < 0) dp(12f) + rad else width - dp(12f) - rad
        return R(cx - rad, cy - rad, cx + rad, cy + rad)
    }

    private fun hitButton(x: Float, y: Float): Int {
        val r0 = rows.firstOrNull() ?: return 0
        val top = r0.wfTop; val hgt = r0.wfBottom - r0.wfTop
        for (side in intArrayOf(-1, 1)) if (buttonRect(side, top, hgt).contains(x, y)) return side
        return 0
    }

    private fun inMiniMap(y: Float) = y >= dp(50f) && y <= dp(74f)

    private fun miniMapLeft(x: Float): Float {
        val wk = (width - dp(48f)) / Notes.WHITE_KEYS
        return (x - dp(24f)) / wk - reducedSpan / 2f
    }

    private fun setManual(left: Float) {
        manualLeft = KeyboardPlanner.clampLeft(left, reducedSpan)
        if (panKind != PAN_BUTTON) { curLeft = manualLeft; rebuild = true }
    }

    private fun DrawScope.glow(cx: Float, cy: Float, radius: Float, color: Int, amt: Float) {
        drawCircle(
            Brush.radialGradient(0f to col(Palette.withAlpha(color, amt)), 1f to col(Palette.withAlpha(color, 0f)), center = Offset(cx, cy), radius = radius),
            radius, Offset(cx, cy),
        )
    }

    private fun DrawScope.arrow(x: Float, y: Float, dir: Int, color: Int) {
        val path = Path().apply {
            moveTo(x - dir * dp(8f), y - dp(16f)); lineTo(x + dir * dp(8f), y); lineTo(x - dir * dp(8f), y + dp(16f))
        }
        drawPath(path, col(Palette.withAlpha(color, 0.9f)), style = Stroke(dp(4f), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    private fun drawKeyboard(s: DrawScope, tm: TextMeasurer, r: RowGeom) = with(s) {
        val kb = r.kb
        val gap = max(1f, dp(1.2f))
        val showKeyLetters = showLabelsOverride ?: display.keyLetters
        clipRect(0f, kb.y - dp(2f), width, kb.y + kb.height + dp(2f)) {
            rect(0xFF0A0A0D.toInt(), 0f, kb.y - dp(2f), width, kb.y + kb.height + dp(2f))
            val radius = dp(7f)
            val ivoryBrush = ivory
            for (i in 0 until kb.whiteCount) {
                val pit = kb.whitePitches[i]
                val rc = kb.rect(pit) ?: continue
                if (rc.right < 0f || rc.left > width) continue
                val lv = light[pit]
                val sink = lv * dp(2.5f)
                val h = rc.bottom - sink - (rc.top - dp(8f))
                if (ivoryBrush != null) {
                    drawRoundRect(ivoryBrush, Offset(rc.left, rc.top - dp(8f)), Size(rc.right - gap - rc.left, h), CornerRadius(radius))
                } else rrect(0xFFE4E1D9.toInt(), rc.left, rc.top - dp(8f), rc.right - gap, rc.bottom - sink, radius)
                if (lv > 0.01f) {
                    val c = lightColor[pit]
                    rrect(Palette.withAlpha(c, 0.55f * lv), rc.left, rc.top, rc.right - gap, rc.bottom - sink, radius)
                    rect(Palette.withAlpha(c, 0.95f * lv), rc.left, rc.top, rc.right - gap, rc.top + max(dp(6f), kb.height * 0.04f))
                }
                rect(0x14000000, rc.left, rc.bottom - dp(6f), rc.right - gap, rc.bottom - dp(5f))
                if (showKeyLetters != KeyLetters.NONE) {
                    val isC = pit % 12 == 0
                    if (showKeyLetters == KeyLetters.ALL || isC) {
                        val c = if (lv > 0.4f) 0xFF111116.toInt() else if (isC) 0xFF4A4A55.toInt() else 0xFF8A8A94.toInt()
                        val size = min(rc.width * 0.34f, dp(17f))
                        label(tm, if (isC) octNames[pit] else names[pit], rc.centerX - gap / 2f, rc.bottom - dp(12f) - sink - size * 0.35f, size, c)
                    }
                }
                if (pit == Notes.MIDDLE_C) {
                    drawCircle(col(Palette.withAlpha(Palette.RIGHT, 0.9f)), dp(3.2f), Offset(rc.centerX - gap / 2f, rc.bottom - dp(34f) - sink))
                }
            }
            val blackBrush = black
            for (pit in kb.lo..kb.hi) {
                if (!Notes.isBlack(pit)) continue
                val rc = kb.rect(pit) ?: continue
                if (rc.right < 0f || rc.left > width) continue
                val lv = light[pit]
                val bottom = rc.bottom - lv * dp(2f)
                rrect(0x30000000, rc.left - dp(1f), rc.top, rc.right + dp(3f), rc.bottom + dp(3f), dp(5f))
                if (blackBrush != null) {
                    drawRoundRect(blackBrush, Offset(rc.left, rc.top - dp(8f)), Size(rc.width, bottom - (rc.top - dp(8f))), CornerRadius(dp(5f)))
                } else rrect(0xFF141418.toInt(), rc.left, rc.top - dp(8f), rc.right, bottom, dp(5f))
                if (lv > 0.01f) {
                    val c = lightColor[pit]
                    rrect(Palette.withAlpha(c, 0.78f * lv), rc.left, rc.top - dp(8f), rc.right, bottom, dp(5f))
                    rect(Palette.withAlpha(c, lv), rc.left, rc.top, rc.right, rc.top + dp(5f))
                    val size = min(rc.width * 0.42f, dp(13f))
                    label(tm, names[pit], rc.centerX, rc.bottom - dp(10f) - size * 0.35f, size, 0xFFFFFFFF.toInt())
                } else {
                    rect(0x14FFFFFF, rc.left + dp(2f), rc.top, rc.left + dp(3.5f), rc.bottom - dp(8f))
                }
            }
        }
    }

    private fun drawMiniMap(s: DrawScope, pos: Double, src: FrameSource?) = with(s) {
        if (rows.isEmpty()) return
        val y = dp(57f)
        val hh = dp(11f)
        val mw = width - dp(48f)
        val x0 = dp(24f)
        val wk = mw / Notes.WHITE_KEYS
        for (i in 0 until Notes.WHITE_KEYS) rect(0x22FFFFFF, x0 + i * wk, y, x0 + i * wk + max(1f, wk - 1f), y + hh)
        val leftIdx = (curLeft.takeIf { it >= 0f } ?: 23f)
        val l = x0 + leftIdx * wk; val rr = x0 + (leftIdx + reducedSpan) * wk
        drawRoundRect(col(0x445AB8FF), Offset(l, y - dp(2f)), Size(rr - l, hh + dp(4f)), CornerRadius(dp(4f)))
        drawRoundRect(col(0xFF5AB8FF.toInt()), Offset(l, y - dp(2f)), Size(rr - l, hh + dp(4f)), CornerRadius(dp(4f)), style = Stroke(dp(1.2f)))
        if (src != null) {
            for (n in src.songNotes) {
                if (n.startMs > pos + 4000.0) break
                if (n.startMs + n.durMs < pos) continue
                val wi = Notes.whiteIndex(Notes.whiteAtOrBelow(n.pitch))
                drawCircle(col(Palette.withAlpha(Palette.note(n.pitch, n.hand, display.colors), 0.9f)), dp(2.4f), Offset(x0 + (wi + 0.5f) * wk, y + hh / 2f))
            }
        }
    }

    // ------------------------------------------------------------------ entradas
    private fun pitchAt(x: Float, y: Float): Int? {
        for (r in rows) { val p = r.kb.pitchAt(x, y); if (p != null) return p }
        return null
    }

    /** Con ratón no hay «tamaño del dedo»: la fuerza sale de dónde pulsas la tecla (más abajo = más fuerte). */
    private fun mouseStrengthMm(pitch: Int, y: Float): Float {
        val rc = rows.firstOrNull { it.kb.contains(pitch) }?.kb?.rect(pitch) ?: return 9f
        val rel = ((y - rc.top) / max(1f, rc.height)).coerceIn(0f, 1f)
        return 5f + 8f * rel
    }

    fun pointerDown(slot: Int, x: Float, y: Float) {
        if (!startPan(slot, x, y)) press(slot, x, y)
    }

    fun pointerMove(slot: Int, x: Float, y: Float) {
        if (panId == slot) movePan(x, y)
        if (keyboardSettings.slide && slot in ptrPitch.indices) {
            val cur = ptrPitch[slot]
            if (cur >= 0) {
                val np = pitchAt(x, y)
                if (np != null && np != cur) {
                    ptrPitch[slot] = -1
                    release(cur)
                    press(slot, x, y)
                }
            }
        }
    }

    fun pointerUp(slot: Int, x: Float, y: Float) {
        if (slot == panId) endPan(x, y)
        else if (slot in ptrPitch.indices && ptrPitch[slot] >= 0) { val pit = ptrPitch[slot]; ptrPitch[slot] = -1; release(pit) }
    }

    /** Rueda del ratón: desplaza el teclado reducido (una tecla blanca por «clic»). */
    fun scroll(dx: Float, dy: Float) {
        if (!canPan()) return
        val base = if (manualLeft >= 0f) manualLeft else curLeft.takeIf { it >= 0f } ?: 23f
        panKind = PAN_WATERFALL
        setManual(base + (dy + dx) * 2f)
        panKind = PAN_NONE
    }

    private fun startPan(slot: Int, x: Float, y: Float): Boolean {
        if (!canPan() || panId >= 0) return false
        val kind: Int
        when {
            manualLeft >= 0f && autoRect.contains(x, y) -> kind = PAN_AUTO
            hitButton(x, y) != 0 -> { kind = PAN_BUTTON; panButton = hitButton(x, y) }
            inMiniMap(y) -> kind = PAN_MINIMAP
            pitchAt(x, y) == null && y < rows[0].wfBottom -> kind = PAN_WATERFALL
            else -> return false
        }
        panId = slot; panKind = kind; panStartX = x; panMoved = false
        panStartLeft = if (curLeft >= 0f) curLeft else 23f
        if (kind == PAN_MINIMAP) { setManual(miniMapLeft(x)); panMoved = true }
        return true
    }

    private fun movePan(x: Float, y: Float) {
        if (panKind == PAN_MINIMAP) { setManual(miniMapLeft(x)); return }
        if (panKind != PAN_WATERFALL) return
        val dx = x - panStartX
        if (!panMoved && abs(dx) < slop) return
        panMoved = true
        val wk = (width / reducedSpan.toFloat()).coerceAtLeast(1f)
        setManual(panStartLeft - dx / wk)
    }

    private fun endPan(x: Float, y: Float) {
        val kind = panKind
        panId = -1; panKind = PAN_NONE
        when (kind) {
            PAN_AUTO -> if (autoRect.contains(x, y)) manualLeft = -1f
            PAN_BUTTON -> if (hitButton(x, y) == panButton) {
                val base = if (manualLeft >= 0f) manualLeft else curLeft.takeIf { it >= 0f } ?: 23f
                panKind = PAN_BUTTON
                setManual(base + panButton * 7f)
                panKind = PAN_NONE
            }
            else -> {}
        }
    }

    private fun press(slot: Int, x: Float, y: Float) {
        if (slot !in ptrPitch.indices) return
        val l = listener
        if (ptrPitch[slot] >= 0) { val old = ptrPitch[slot]; ptrPitch[slot] = -1; release(old) }
        val pitch = pitchAt(x, y)
        if (pitch == null) { l?.onBackgroundTap(); return }
        ptrPitch[slot] = pitch
        userDown[pitch] = true
        val c = l?.onKeyDown(pitch, mouseStrengthMm(pitch, y)) ?: 0
        userColor[pitch] = c
        flash[pitch] = 1f; flashColor[pitch] = if (c != 0) c else Palette.WARM
    }

    private fun release(pitch: Int) {
        for (v in ptrPitch) if (v == pitch) return          // otra pulsación sigue sujetando la misma tecla
        if (extHeld[pitch] > 0) return
        userDown[pitch] = false
        listener?.onKeyUp(pitch)
    }

    // ---- teclado del PC y piano MIDI (entran por aquí para que la tecla se ilumine igual)
    private val extHeld = IntArray(128)

    fun externalDown(pitch: Int, velocity: Int) {
        if (pitch !in 0..127) return
        extHeld[pitch]++
        userDown[pitch] = true
        val l = listener
        val c = if (velocity > 0) l?.onKeyDownVelocity(pitch, velocity) ?: 0 else l?.onKeyDown(pitch, 9f) ?: 0
        userColor[pitch] = c
        flash[pitch] = 1f; flashColor[pitch] = if (c != 0) c else Palette.WARM
    }

    fun externalPedal(down: Boolean) { listener?.onPedal(down) }

    fun externalUp(pitch: Int) {
        if (pitch !in 0..127 || extHeld[pitch] <= 0) return
        extHeld[pitch]--
        release(pitch)
    }

    /** Suelta todo lo que hubiera pulsado (al salir de la pantalla). */
    fun releaseAll() {
        for (i in ptrPitch.indices) if (ptrPitch[i] >= 0) { val pit = ptrPitch[i]; ptrPitch[i] = -1; release(pit) }
        for (k in 0 until 128) while (extHeld[k] > 0) { extHeld[k]--; release(k) }
        java.util.Arrays.fill(userDown, false)
    }
}

/** Correspondencia teclado del PC → nota (dos filas, como en cualquier piano virtual). */
object PcKeys {
    private val map: Map<Key, Int> = buildMap {
        // fila inferior: Z S X D C V G B H N J M , L . ; /
        val low = listOf(Key.Z, Key.S, Key.X, Key.D, Key.C, Key.V, Key.G, Key.B, Key.H, Key.N, Key.J, Key.M, Key.Comma, Key.L, Key.Period, Key.Semicolon, Key.Slash)
        low.forEachIndexed { i, k -> put(k, i) }
        // fila superior: Q 2 W 3 E R 5 T 6 Y 7 U I 9 O 0 P
        val up = listOf(Key.Q, Key.Two, Key.W, Key.Three, Key.E, Key.R, Key.Five, Key.T, Key.Six, Key.Y, Key.Seven, Key.U, Key.I, Key.Nine, Key.O, Key.Zero, Key.P)
        up.forEachIndexed { i, k -> put(k, 12 + i) }
    }
    fun offset(k: Key): Int? = map[k]
}

@Composable
fun PianoView(
    state: PianoState,
    modifier: Modifier = Modifier,
    onSpace: ((Boolean) -> Unit)? = null,
    onEnter: (() -> Unit)? = null,
) {
    val density = LocalDensity.current.density
    state.density = density
    val tm = rememberTextMeasurer(cacheSize = 512)
    var tick by remember { mutableLongStateOf(0L) }
    val focus = remember { FocusRequester() }
    val slots = remember { HashMap<Long, Int>() }
    val pcDown = remember { HashMap<Key, Int>() }
    var octave by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { n -> state.frame(n); tick = n }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.compose.runtime.DisposableEffect(state) {
        MidiRouter.target = state
        onDispose { if (MidiRouter.target === state) MidiRouter.target = null; state.releaseAll() }
    }

    Canvas(
        modifier.fillMaxSize().clipToBounds()
            .onSizeChanged { state.width = it.width.toFloat(); state.height = it.height.toFloat() }
            .focusRequester(focus).focusable()
            .onKeyEvent { e ->
                val k = e.key
                when {
                    k == Key.Spacebar -> { onSpace?.invoke(e.type == KeyEventType.KeyDown); onSpace != null }
                    k == Key.Enter && e.type == KeyEventType.KeyDown -> { onEnter?.invoke(); onEnter != null }
                    k == Key.DirectionLeft && e.type == KeyEventType.KeyDown -> { octave = (octave - 1).coerceAtLeast(-3); true }
                    k == Key.DirectionRight && e.type == KeyEventType.KeyDown -> { octave = (octave + 1).coerceAtMost(3); true }
                    PcKeys.offset(k) != null -> {
                        if (e.type == KeyEventType.KeyDown) {
                            if (k !in pcDown) {
                                val pitch = (60 + octave.toInt() * 12 + PcKeys.offset(k)!!).coerceIn(21, 108)
                                pcDown[k] = pitch
                                state.externalDown(pitch, 0)
                            }
                        } else if (e.type == KeyEventType.KeyUp) {
                            pcDown.remove(k)?.let { state.externalUp(it) }
                        }
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.type == PointerEventType.Scroll) {
                            val d = ev.changes.firstOrNull()?.scrollDelta
                            if (d != null) state.scroll(d.x, d.y)
                            continue
                        }
                        for (ch in ev.changes) {
                            val id = ch.id.value
                            if (ch.pressed && !ch.previousPressed) {
                                val used = slots.values.toSet()
                                val slot = (0 until 20).firstOrNull { it !in used } ?: continue
                                slots[id] = slot
                                state.pointerDown(slot, ch.position.x, ch.position.y)
                                ch.consume()
                            } else if (ch.pressed) {
                                val slot = slots[id] ?: continue
                                if (ch.position != ch.previousPosition) state.pointerMove(slot, ch.position.x, ch.position.y)
                                ch.consume()
                            } else if (ch.previousPressed) {
                                val slot = slots.remove(id) ?: continue
                                state.pointerUp(slot, ch.position.x, ch.position.y)
                                ch.consume()
                            }
                        }
                    }
                }
            },
    ) {
        @Suppress("UNUSED_EXPRESSION") tick    // lee el fotograma: así Compose vuelve a dibujar en cada uno
        drawRect(Color(0xFF07070A))
        state.draw(this, tm)
    }
}
