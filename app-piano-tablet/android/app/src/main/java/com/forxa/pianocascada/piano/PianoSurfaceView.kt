package com.forxa.pianocascada.piano

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.forxa.pianocascada.core.Hand
import com.forxa.pianocascada.core.KeyRow
import com.forxa.pianocascada.core.KeyboardPlanner
import com.forxa.pianocascada.core.Notes
import com.forxa.pianocascada.data.ColorScheme
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
    /** Devuelve el color de luz de la tecla (0 = luz cálida por defecto). */
    fun onKeyDown(pitch: Int, sizeMm: Float): Int
    fun onKeyUp(pitch: Int)
    fun onBackgroundTap()
}

/**
 * Cascada de notas + teclado(s) con luz, dibujados a la frecuencia de refresco de la pantalla.
 * Una sola vista con Canvas acelerado por hardware, sin reservas de memoria por fotograma.
 */
class PianoSurfaceView(context: Context) : View(context) {
    var source: FrameSource? = null
        set(v) { field = v; manualLeft = -1f }
    var listener: KeyListener? = null
    var layoutMode = LayoutMode.REDUCED
        set(v) { field = v; rebuild = true }
    var keyboardSettings = KeyboardSettings()
        set(v) { field = v; rebuild = true }
    var display = DisplaySettings()
    var reducedSpan = 15
        set(v) { field = v; rebuild = true }
    var split = 65
        set(v) { field = v; rebuild = true }
    /** Reduce el teclado a una franja inferior (para vistas previas pequeñas, p. ej. en ajustes o tutorial). */
    /** Rango (nota más grave y más aguda) de la pieza: con él el modo de dos filas se ajusta a medida. -1 = sin ajuste. */
    var fitMin = -1
        set(v) { field = v; rebuild = true }
    var fitMax = -1
        set(v) { field = v; rebuild = true }
    var keyboardOnly = false
        set(v) { field = v; rebuild = true }
    var staticLeft: Float = -1f      // si >= 0, ventana fija del teclado reducido
    var highlight: IntArray = IntArray(0)   // teclas a resaltar (tutorial)
    var showLabelsOverride: KeyLetters? = null

    companion object {
        private const val PAN_NONE = 0
        private const val PAN_WATERFALL = 1
        private const val PAN_MINIMAP = 2
        private const val PAN_BUTTON = 3
        private const val PAN_AUTO = 4
    }

    private val density = resources.displayMetrics.density
    private val xdpi = resources.displayMetrics.xdpi
    private fun dp(v: Float) = v * density

    // ---- geometría
    private class RowGeom(var kb: KeyRow, val wfTop: Float, val wfBottom: Float, val fixedRange: Boolean)
    private val rows = ArrayList<RowGeom>(2)
    private var rebuild = true
    private var curLeft = -1f
    private var lastBuiltLeft = -999f
    private var topInset = 0f
    private var bandTop = 0f
    private var bandBottom = 0f

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
    /** >= 0: el usuario ha movido la ventana del teclado a mano (índice de tecla blanca del borde izquierdo); se deja de seguir las notas. */
    private var manualLeft = -1f
    private var panId = -1
    private var panStartX = 0f
    private var panStartLeft = 0f
    private var panMoved = false
    private var panKind = PAN_NONE      // qué se está arrastrando: la cascada, el minimapa o un botón lateral
    private var panButton = 0           // -1 botón izquierdo, +1 botón derecho
    private val slop = dp(8f)

    // ---- pinturas reutilizables
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); textAlign = Paint.Align.CENTER }
    private val rf = RectF()
    private val rf2 = RectF()
    private val path = Path()
    private var ivoryShader: LinearGradient? = null
    private var blackShader: LinearGradient? = null
    private val glowShader = RadialGradient(0f, 0f, 100f, intArrayOf(0xFFFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    private val shaderMatrix = android.graphics.Matrix()
    private val names = Array(128) { Notes.name(it) }
    private val octNames = Array(128) { Notes.nameWithOctave(it) }

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!attached) return
            source?.onFrame(frameTimeNanos)
            updateLights(frameTimeNanos)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private var attached = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        lastFrameNs = 0
        Choreographer.getInstance().postFrameCallback(frameCb)
    }

    override fun onDetachedFromWindow() {
        attached = false
        releaseAll()
        Choreographer.getInstance().removeFrameCallback(frameCb)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild = true
    }

    // ------------------------------------------------------------------ geometría
    private fun buildRows() {
        rows.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val hs = keyboardSettings.heightScale
        val bw = keyboardSettings.blackWidth
        if (keyboardOnly) {
            val kbH = h * 0.92f
            val kb = reducedRow(0f, h - kbH, w, kbH, bw)
            rows.add(RowGeom(kb, 0f, 0f, false))
            topInset = 0f; bandTop = 0f; bandBottom = 0f
        } else if (layoutMode != LayoutMode.REDUCED) {
            val (low, high) = if (layoutMode == LayoutMode.TWO_ROWS && fitMin >= 0 && fitMax >= fitMin) {
                KeyboardPlanner.twoRowsFit(fitMin, fitMax).let { it.first to it.second }
            } else KeyboardPlanner.twoRows(split)
            val top = dp(54f)                          // barra de controles arriba, igual que en el teclado reducido
            val bottomMargin = dp(10f)                 // la palma al tocar abajo no debe activar los gestos del borde
            val avail = h - top - bottomMargin
            val kb = min(avail * 0.235f * hs, avail * 0.36f)   // teclas más altas que antes (casi 4 cm en la Tab S8 Ultra)
            val gap = dp(6f)
            val wf = (avail - 2f * kb - gap) / 2f
            val hiY = top + wf
            val loWfTop = hiY + kb + gap
            val loY = loWfTop + wf
            val hiKb = KeyRow(high.lo, high.hi, 0f, hiY, w, kb, bw, 0.62f)
            val loKb = KeyRow(low.lo, low.hi, 0f, loY, w, kb, bw, 0.62f)
            rows.add(RowGeom(hiKb, top, hiY, true))
            rows.add(RowGeom(loKb, loWfTop, loY, true))
            topInset = top
            bandTop = 0f; bandBottom = 0f
        } else {
            val kbH = h * 0.30f * hs
            val top = dp(54f)
            val wfBottom = h - kbH
            val kb = reducedRow(0f, wfBottom, w, kbH, bw)
            rows.add(RowGeom(kb, top + dp(18f), wfBottom, false))
            topInset = top
            bandTop = 0f; bandBottom = 0f
        }
        rebuildShaders()
        rebuild = false
    }

    private fun reducedRow(x: Float, y: Float, w: Float, h: Float, bw: Float): KeyRow {
        val left = if (curLeft < 0f) (if (staticLeft >= 0f) staticLeft else 23f) else curLeft
        val r = KeyRow.window(left, reducedSpan.toFloat(), x, y, w, h, bw, 0.62f)
        lastBuiltLeft = left
        return r
    }

    private fun rebuildShaders() {
        // degradados de altura 1: en cada fila se escalan y trasladan con una matriz
        ivoryShader = LinearGradient(0f, 0f, 0f, 1f, intArrayOf(0xFFF2F0EA.toInt(), 0xFFE4E1D9.toInt(), 0xFFD3CFC5.toInt()), floatArrayOf(0f, 0.8f, 1f), Shader.TileMode.CLAMP)
        blackShader = LinearGradient(0f, 0f, 0f, 1f, intArrayOf(0xFF2A2A31.toInt(), 0xFF141418.toInt(), 0xFF0B0B0E.toInt()), floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP)
    }

    private fun placeShader(sh: LinearGradient?, y: Float, h: Float) {
        if (sh == null) return
        shaderMatrix.reset()
        shaderMatrix.postScale(1f, h)
        shaderMatrix.postTranslate(0f, y)
        sh.setLocalMatrix(shaderMatrix)
    }

    // ------------------------------------------------------------------ luz y animación
    private fun updateLights(nowNs: Long) {
        val dt = if (lastFrameNs == 0L) 0.016f else ((nowNs - lastFrameNs) / 1e9f).coerceIn(0.001f, 0.1f)
        lastFrameNs = nowNs
        // desplazamiento suave de la ventana del teclado reducido
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
        if (rebuild) {
            buildRows()
        }
    }

    private fun contains(a: IntArray, v: Int): Boolean { for (x in a) if (x == v) return true; return false }

    fun flashKey(pitch: Int, color: Int) { if (pitch in 0..127) { flash[pitch] = 1f; flashColor[pitch] = color } }

    // ------------------------------------------------------------------ dibujo
    override fun onDraw(canvas: Canvas) {
        if (rebuild) buildRows()
        canvas.drawColor(0xFF07070A.toInt())
        val src = source
        val pos = src?.posMs ?: 0.0
        for (r in rows) {
            if (!keyboardOnly) drawWaterfall(canvas, r, pos, src)
            drawKeyboard(canvas, r)
        }
        if (!keyboardOnly && layoutMode == LayoutMode.REDUCED) drawMiniMap(canvas, pos, src)
    }

    private fun drawWaterfall(c: Canvas, r: RowGeom, pos: Double, src: FrameSource?) {
        val kb = r.kb
        val top = r.wfTop
        val lineY = r.wfBottom
        val hgt = lineY - top
        if (hgt <= 4f) return
        c.save()
        c.clipRect(0f, top, width.toFloat(), lineY)
        // guías verticales entre teclas blancas
        if (display.guides) {
            p.style = Paint.Style.FILL
            for (i in 0 until kb.whiteCount) {
                val pit = kb.whitePitches[i]
                val rc = kb.rect(pit) ?: continue
                p.color = if (pit % 12 == 0) 0x14FFFFFF else 0x08FFFFFF
                c.drawRect(rc.left, top, rc.left + 1f, lineY, p)
            }
        }
        val fall = display.fallMs.toDouble().coerceAtLeast(800.0)
        val pxPerMs = (hgt / fall).toFloat()
        // líneas de compás
        if (src != null) {
            val bars = src.barTimesMs
            p.style = Paint.Style.FILL
            for (t in bars) {
                if (t < pos - 50 || t > pos + fall) continue
                val y = lineY - ((t - pos) * pxPerMs).toFloat()
                p.color = 0x22FFFFFF
                c.drawRect(0f, y - 1f, width.toFloat(), y, p)
            }
        }
        // notas
        if (src != null) {
            val notes = src.songNotes
            val r0 = dp(7f)
            for (n in notes) {
                if (n.startMs > pos + fall) break
                val endMs = n.startMs + n.durMs
                if (endMs < pos) continue
                if (!kb.contains(n.pitch)) continue
                val key = kb.rect(n.pitch) ?: continue
                val black = Notes.isBlack(n.pitch)
                val bw = if (black) key.width * 0.9f else key.width * 0.74f
                val cx = key.centerX
                val bottom = min(lineY - ((n.startMs - pos) * pxPerMs).toFloat(), lineY)
                val topY = lineY - ((endMs - pos) * pxPerMs).toFloat()
                if (topY > lineY) continue
                val res = if (n.sessionIdx >= 0) src.resultOf(n.sessionIdx) else 0
                var col = Palette.note(n.pitch, n.hand, display.colors)
                var alpha = 1f
                when (res) {
                    3 -> { col = 0xFF6A6A76.toInt(); alpha = 0.55f }
                    4 -> { col = 0xFF6A6A76.toInt(); alpha = 0.7f }
                    1, 2 -> col = Palette.mix(col, 0xFFFFFFFF.toInt(), 0.45f)
                }
                val left = cx - bw / 2f
                val right = cx + bw / 2f
                // halo
                rf.set(left - dp(4f), topY - dp(4f), right + dp(4f), bottom + dp(2f))
                p.style = Paint.Style.FILL; p.shader = null
                p.color = Palette.withAlpha(col, 0.16f * alpha)
                c.drawRoundRect(rf, r0 + dp(3f), r0 + dp(3f), p)
                // cuerpo
                rf.set(left, topY, right, bottom)
                p.color = Palette.withAlpha(col, 0.92f * alpha)
                c.drawRoundRect(rf, r0, r0, p)
                // brillo interior
                rf2.set(left + dp(2f), topY + dp(2f), left + max(dp(4f), bw * 0.28f), max(topY + dp(4f), bottom - dp(2f)))
                p.color = Palette.withAlpha(0xFFFFFFFF.toInt(), 0.22f * alpha)
                c.drawRoundRect(rf2, dp(3f), dp(3f), p)
                // etiqueta de la nota
                if (display.noteLetters && bw >= dp(16f) && bottom - topY >= dp(10f)) {
                    val side = min(bw * 0.9f, dp(38f))
                    val cyl = bottom - side / 2f - dp(3f)
                    rf2.set(cx - side / 2f, cyl - side / 2f, cx + side / 2f, cyl + side / 2f)
                    p.color = 0xE6090A12.toInt()
                    c.drawRoundRect(rf2, side * 0.3f, side * 0.3f, p)
                    p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.4f)
                    p.color = Palette.withAlpha(col, alpha)
                    c.drawRoundRect(rf2, side * 0.3f, side * 0.3f, p)
                    p.style = Paint.Style.FILL
                    tp.color = Palette.withAlpha(0xFFFFFFFF.toInt(), alpha)
                    tp.textSize = side * (if (black) 0.36f else 0.5f)
                    c.drawText(names[n.pitch], cx, cyl + tp.textSize * 0.35f, tp)
                }
            }
        }
        // línea de impacto luminosa
        p.shader = null; p.style = Paint.Style.FILL
        p.color = 0x55FFFFFF
        c.drawRect(0f, lineY - dp(1.5f), width.toFloat(), lineY, p)
        // destellos al acertar
        for (k in kb.lo..kb.hi) {
            val f = flash[k]
            if (f > 0.01f) {
                val rc = kb.rect(k) ?: continue
                drawGlow(c, rc.centerX, lineY, kb.whiteWidth * 1.6f, flashColor[k], f * 0.8f)
            }
            if (light[k] > 0.05f && (userDown[k] || autoColor[k] != 0)) {
                val rc = kb.rect(k) ?: continue
                drawGlow(c, rc.centerX, lineY, kb.whiteWidth * 1.3f, lightColor[k], light[k] * 0.55f)
            }
        }
        // flechas de los bordes (teclado reducido)
        if (canPan()) {
            for (side in intArrayOf(-1, 1)) {
                buttonRect(side, top, hgt, rf)
                val need = if (side < 0) needLeftArrow else needRightArrow
                p.style = Paint.Style.FILL
                p.color = if (need != 0) Palette.withAlpha(need, 0.28f) else 0x55000000
                c.drawRoundRect(rf, rf.width() / 2f, rf.width() / 2f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.5f)
                p.color = if (need != 0) Palette.withAlpha(need, 0.9f) else 0x40FFFFFF
                c.drawRoundRect(rf, rf.width() / 2f, rf.width() / 2f, p)
                p.style = Paint.Style.FILL
                drawArrow(c, rf.centerX(), rf.centerY(), side, if (need != 0) need else 0xCCFFFFFF.toInt())
            }
            if (manualLeft >= 0f) {
                autoRect.set(width / 2f - dp(100f), top + dp(10f), width / 2f + dp(100f), top + dp(46f))
                p.style = Paint.Style.FILL; p.color = 0xCC1D2433.toInt()
                c.drawRoundRect(autoRect, dp(18f), dp(18f), p)
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.5f); p.color = 0xFF5AB8FF.toInt()
                c.drawRoundRect(autoRect, dp(18f), dp(18f), p)
                p.style = Paint.Style.FILL
                tp.color = 0xFFFFFFFF.toInt(); tp.textSize = dp(14f)
                c.drawText("Seguir las notas", autoRect.centerX(), autoRect.centerY() + tp.textSize * 0.35f, tp)
            }
        }
        c.restore()
    }

    private val autoRect = RectF()

    /** El desplazamiento a mano solo existe en el teclado reducido de las pantallas de práctica. */
    private fun canPan() = layoutMode == LayoutMode.REDUCED && !keyboardOnly && rows.isNotEmpty()

    private fun buttonRect(side: Int, top: Float, hgt: Float, out: RectF) {
        val rad = dp(30f)
        val cy = top + hgt * 0.62f
        val cx = if (side < 0) dp(12f) + rad else width - dp(12f) - rad
        out.set(cx - rad, cy - rad, cx + rad, cy + rad)
    }

    private fun hitButton(x: Float, y: Float): Int {
        val r0 = rows.firstOrNull() ?: return 0
        val top = r0.wfTop; val hgt = r0.wfBottom - r0.wfTop
        for (side in intArrayOf(-1, 1)) { buttonRect(side, top, hgt, rf2); if (rf2.contains(x, y)) return side }
        return 0
    }

    private fun inMiniMap(y: Float) = y >= dp(50f) && y <= dp(74f)

    private fun miniMapLeft(x: Float): Float {
        val wk = (width - dp(48f)) / Notes.WHITE_KEYS
        return (x - dp(24f)) / wk - reducedSpan / 2f
    }

    private fun setManual(left: Float) {
        manualLeft = KeyboardPlanner.clampLeft(left, reducedSpan)
        // el movimiento con el dedo es inmediato; el de los botones se suaviza en updateLights
        if (panKind != PAN_BUTTON) { curLeft = manualLeft; rebuild = true }
    }

    private fun drawGlow(c: Canvas, cx: Float, cy: Float, radius: Float, color: Int, amt: Float) {
        shaderMatrix.reset()
        shaderMatrix.postScale(radius / 100f, radius / 100f)
        shaderMatrix.postTranslate(cx, cy)
        glowShader.setLocalMatrix(shaderMatrix)
        p.shader = glowShader
        p.colorFilter = android.graphics.PorterDuffColorFilter(Palette.withAlpha(color, amt), android.graphics.PorterDuff.Mode.SRC_IN)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy, radius, p)
        p.colorFilter = null
        p.shader = null
    }

    private fun drawArrow(c: Canvas, x: Float, y: Float, dir: Int, color: Int) {
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(4f); p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.color = Palette.withAlpha(color, 0.9f)
        path.reset()
        path.moveTo(x - dir * dp(8f), y - dp(16f)); path.lineTo(x + dir * dp(8f), y); path.lineTo(x - dir * dp(8f), y + dp(16f))
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    private fun drawKeyboard(c: Canvas, r: RowGeom) {
        val kb = r.kb
        val gap = max(1f, dp(1.2f))
        val showKeyLetters = showLabelsOverride ?: display.keyLetters
        // fondo del teclado
        p.shader = null; p.style = Paint.Style.FILL
        p.color = 0xFF0A0A0D.toInt()
        c.save()
        c.clipRect(0f, kb.y - dp(2f), width.toFloat(), kb.y + kb.height + dp(2f))
        c.drawRect(0f, kb.y - dp(2f), width.toFloat(), kb.y + kb.height + dp(2f), p)
        val radius = dp(7f)
        placeShader(ivoryShader, kb.y, kb.height)
        placeShader(blackShader, kb.y, kb.blackHeight)
        // teclas blancas
        for (i in 0 until kb.whiteCount) {
            val pit = kb.whitePitches[i]
            val rc = kb.rect(pit) ?: continue
            if (rc.right < 0f || rc.left > width) continue
            val lv = light[pit]
            val sink = lv * dp(2.5f)
            rf.set(rc.left, rc.top - dp(8f), rc.right - gap, rc.bottom - sink)
            p.shader = ivoryShader
            p.color = 0xFFFFFFFF.toInt()
            c.drawRoundRect(rf, radius, radius, p)
            p.shader = null
            if (lv > 0.01f) {
                val col = lightColor[pit]
                // tinte y tira de luz superior
                rf2.set(rc.left, rc.top, rc.right - gap, rc.bottom - sink)
                p.color = Palette.withAlpha(col, 0.55f * lv)
                c.drawRoundRect(rf2, radius, radius, p)
                rf2.set(rc.left, rc.top, rc.right - gap, rc.top + max(dp(6f), kb.height * 0.04f))
                p.color = Palette.withAlpha(col, 0.95f * lv)
                c.drawRect(rf2, p)
            }
            // sombra inferior sutil
            p.color = 0x14000000
            c.drawRect(rc.left, rc.bottom - dp(6f), rc.right - gap, rc.bottom - dp(5f), p)
            // letras
            if (showKeyLetters != KeyLetters.NONE) {
                val isC = pit % 12 == 0
                if (showKeyLetters == KeyLetters.ALL || isC) {
                    tp.color = if (lv > 0.4f) 0xFF111116.toInt() else if (isC) 0xFF4A4A55.toInt() else 0xFF8A8A94.toInt()
                    tp.textSize = min(rc.width * 0.34f, dp(17f))
                    c.drawText(if (isC) octNames[pit] else names[pit], rc.centerX - gap / 2f, rc.bottom - dp(12f) - sink, tp)
                }
            }
            if (pit == Notes.MIDDLE_C) {
                p.color = Palette.withAlpha(Palette.RIGHT, 0.9f)
                c.drawCircle(rc.centerX - gap / 2f, rc.bottom - dp(34f) - sink, dp(3.2f), p)
            }
        }
        // teclas negras
        for (pit in kb.lo..kb.hi) {
            if (!Notes.isBlack(pit)) continue
            val rc = kb.rect(pit) ?: continue
            if (rc.right < 0f || rc.left > width) continue
            val lv = light[pit]
            rf.set(rc.left, rc.top - dp(8f), rc.right, rc.bottom - lv * dp(2f))
            // sombra proyectada sobre las blancas
            rf2.set(rc.left - dp(1f), rc.top, rc.right + dp(3f), rc.bottom + dp(3f))
            p.color = 0x30000000
            c.drawRoundRect(rf2, dp(5f), dp(5f), p)
            p.shader = blackShader
            p.color = 0xFFFFFFFF.toInt()
            c.drawRoundRect(rf, dp(5f), dp(5f), p)
            p.shader = null
            if (lv > 0.01f) {
                val col = lightColor[pit]
                p.color = Palette.withAlpha(col, 0.78f * lv)
                c.drawRoundRect(rf, dp(5f), dp(5f), p)
                rf2.set(rc.left, rc.top, rc.right, rc.top + dp(5f))
                p.color = Palette.withAlpha(col, lv)
                c.drawRect(rf2, p)
                tp.color = 0xFFFFFFFF.toInt()
                tp.textSize = min(rc.width * 0.42f, dp(13f))
                c.drawText(names[pit], rc.centerX, rc.bottom - dp(10f), tp)
            } else {
                // brillo satinado
                p.color = 0x14FFFFFF
                c.drawRect(rc.left + dp(2f), rc.top, rc.left + dp(3.5f), rc.bottom - dp(8f), p)
            }
        }
        c.restore()
    }

    private fun drawMiniMap(c: Canvas, pos: Double, src: FrameSource?) {
        if (rows.isEmpty()) return
        val y = dp(57f)
        val hh = dp(11f)
        val mw = width - dp(48f)
        val x0 = dp(24f)
        val wk = mw / Notes.WHITE_KEYS
        p.shader = null; p.style = Paint.Style.FILL
        p.color = 0x22FFFFFF
        for (i in 0 until Notes.WHITE_KEYS) c.drawRect(x0 + i * wk, y, x0 + i * wk + max(1f, wk - 1f), y + hh, p)
        // ventana visible
        val leftIdx = (curLeft.takeIf { it >= 0f } ?: 23f)
        p.color = 0x445AB8FF
        rf.set(x0 + leftIdx * wk, y - dp(2f), x0 + (leftIdx + reducedSpan) * wk, y + hh + dp(2f))
        c.drawRoundRect(rf, dp(4f), dp(4f), p)
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(1.2f); p.color = 0xFF5AB8FF.toInt()
        c.drawRoundRect(rf, dp(4f), dp(4f), p)
        p.style = Paint.Style.FILL
        // notas próximas
        if (src != null) {
            for (n in src.songNotes) {
                if (n.startMs > pos + 4000.0) break
                if (n.startMs + n.durMs < pos) continue
                val wi = Notes.whiteIndex(Notes.whiteAtOrBelow(n.pitch))
                p.color = Palette.withAlpha(Palette.note(n.pitch, n.hand, display.colors), 0.9f)
                c.drawCircle(x0 + (wi + 0.5f) * wk, y + hh / 2f, dp(2.4f), p)
            }
        }
    }

    // ------------------------------------------------------------------ tacto
    private fun pitchAt(x: Float, y: Float): Int? {
        for (r in rows) {
            val p = r.kb.pitchAt(x, y)
            if (p != null) return p
        }
        return null
    }

    private fun touchSizeMm(e: MotionEvent, idx: Int): Float {
        val major = e.getTouchMajor(idx)
        val v = if (major > 0f) major else e.getSize(idx) * 40f * density
        return v / (xdpi / 25.4f)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val l = listener
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                if (!startPan(e, idx)) press(e, idx, l)
            }
            MotionEvent.ACTION_MOVE -> {
                if (panId >= 0) {
                    val i = e.findPointerIndex(panId)
                    if (i >= 0) movePan(e.getX(i), e.getY(i))
                }
                if (keyboardSettings.slide) {
                    for (i in 0 until e.pointerCount) {
                        val id = e.getPointerId(i)
                        if (id >= ptrPitch.size) continue
                        val cur = ptrPitch[id]
                        if (cur < 0) continue
                        val np = pitchAt(e.getX(i), e.getY(i))
                        if (np != null && np != cur) {
                            ptrPitch[id] = -1
                            release(cur, l)
                            press(e, i, l)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = e.actionIndex
                val id = e.getPointerId(idx)
                if (id == panId) endPan(e.getX(idx), e.getY(idx))
                else if (id < ptrPitch.size && ptrPitch[id] >= 0) { val pit = ptrPitch[id]; ptrPitch[id] = -1; release(pit, l) }
                if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                panId = -1; panKind = PAN_NONE
                for (i in ptrPitch.indices) if (ptrPitch[i] >= 0) { val pit = ptrPitch[i]; ptrPitch[i] = -1; release(pit, l) }
            }
        }
        return true
    }

    /** Empieza un arrastre si el dedo cae sobre la cascada, el minimapa o un botón lateral. Devuelve true si lo consume. */
    private fun startPan(e: MotionEvent, idx: Int): Boolean {
        if (!canPan() || panId >= 0) return false
        val x = e.getX(idx); val y = e.getY(idx)
        val kind: Int
        when {
            manualLeft >= 0f && autoRect.contains(x, y) -> { kind = PAN_AUTO }
            hitButton(x, y) != 0 -> { kind = PAN_BUTTON; panButton = hitButton(x, y) }
            inMiniMap(y) -> kind = PAN_MINIMAP
            pitchAt(x, y) == null && y < rows[0].wfBottom -> kind = PAN_WATERFALL
            else -> return false
        }
        panId = e.getPointerId(idx); panKind = kind; panStartX = x; panMoved = false
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
        setManual(panStartLeft - dx / wk)      // el teclado sigue al dedo, como arrastrar un papel
    }

    private fun endPan(x: Float, y: Float) {
        val kind = panKind
        panId = -1; panKind = PAN_NONE
        when (kind) {
            PAN_AUTO -> if (autoRect.contains(x, y)) manualLeft = -1f
            PAN_BUTTON -> if (hitButton(x, y) == panButton) {
                val base = if (manualLeft >= 0f) manualLeft else curLeft.takeIf { it >= 0f } ?: 23f
                panKind = PAN_BUTTON
                setManual(base + panButton * 7f)   // una octava (7 teclas blancas)
                panKind = PAN_NONE
            }
            else -> {}
        }
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun press(e: MotionEvent, idx: Int, l: KeyListener?) {
        val id = e.getPointerId(idx)
        if (id >= ptrPitch.size) return
        if (ptrPitch[id] >= 0) { val old = ptrPitch[id]; ptrPitch[id] = -1; release(old, l) }   // un ACTION_UP perdido
        val pitch = pitchAt(e.getX(idx), e.getY(idx))
        if (pitch == null) { l?.onBackgroundTap(); return }
        val mm = touchSizeMm(e, idx)
        if (keyboardSettings.palmReject && mm > 30f) return        // palma o antebrazo
        ptrPitch[id] = pitch
        userDown[pitch] = true
        val col = l?.onKeyDown(pitch, mm) ?: 0
        userColor[pitch] = col
        flash[pitch] = 1f; flashColor[pitch] = if (col != 0) col else Palette.WARM
    }

    private fun release(pitch: Int, l: KeyListener?) {
        // otra pulsación puede seguir sujetando la misma tecla
        var still = false
        for (v in ptrPitch) if (v == pitch) still = true
        if (still) return
        userDown[pitch] = false
        l?.onKeyUp(pitch)
    }

    /** Suelta todo lo que hubiera pulsado (al salir de la pantalla o al pasar la app a segundo plano). */
    fun releaseAll() {
        val l = listener
        for (i in ptrPitch.indices) if (ptrPitch[i] >= 0) { val pit = ptrPitch[i]; ptrPitch[i] = -1; release(pit, l) }
        java.util.Arrays.fill(userDown, false)
    }
}
