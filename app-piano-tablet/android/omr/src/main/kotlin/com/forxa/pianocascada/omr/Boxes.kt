package com.forxa.pianocascada.omr

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.RotatedRect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * Cajas delimitadoras: traducción de homr/bounding_boxes.py (lector de partituras homr, AGPL-3.0).
 * Los cálculos con números en coma flotante de 32 bits imitan a OpenCV para obtener los mismos resultados.
 */

/** Contorno de OpenCV: pares x,y enteros consecutivos. */
class Contour(val xy: IntArray) {
    val size: Int get() = xy.size / 2
    fun x(i: Int) = xy[2 * i]
    fun y(i: Int) = xy[2 * i + 1]

    companion object {
        fun concat(parts: List<Contour>): Contour {
            var n = 0
            for (p in parts) n += p.xy.size
            val out = IntArray(n)
            var o = 0
            for (p in parts) { System.arraycopy(p.xy, 0, out, o, p.xy.size); o += p.xy.size }
            return Contour(out)
        }
    }
}

/** Rectángulo girado (centro, tamaño, ángulo en grados) como lo devuelve cv2.minAreaRect. */
data class RRect(val cx: Double, val cy: Double, val w: Double, val h: Double, val angle: Double)

fun minAreaRect(c: Contour): RRect {
    val n = c.size
    val m = Mat(n, 1, CvType.CV_32FC2)
    val f = FloatArray(n * 2) { c.xy[it].toFloat() }
    m.put(0, 0, f)
    val mat2f = org.opencv.core.MatOfPoint2f(m)
    val r = Imgproc.minAreaRect(mat2f)
    m.release()
    return RRect(r.center.x, r.center.y, r.size.width, r.size.height, r.angle)
}

fun contoursOf(img: Mat, mode: Int = Imgproc.RETR_TREE): List<Contour> {
    val list = ArrayList<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(img, list, hierarchy, mode, Imgproc.CHAIN_APPROX_SIMPLE)
    hierarchy.release()
    val out = ArrayList<Contour>(list.size)
    for (mp in list) {
        val n = mp.rows()
        val buf = IntArray(n * 2)
        mp.get(0, 0, buf)
        out.add(Contour(buf))
        mp.release()
    }
    return out
}

/** cv2.boxPoints(box).astype(np.int64): 4 puntos con aritmética de 32 bits como OpenCV y truncados a entero. */
fun boxPointsInt(box: RRect): IntArray {
    val cx = box.cx.toFloat(); val cy = box.cy.toFloat()
    val w = box.w.toFloat(); val h = box.h.toFloat()
    val ang = box.angle * PI / 180.0
    val b = cos(ang).toFloat() * 0.5f
    val a = sin(ang).toFloat() * 0.5f
    val p0x = cx - a * h - b * w
    val p0y = cy + b * h - a * w
    val p1x = cx + a * h - b * w
    val p1y = cy - b * h - a * w
    val p2x = 2 * cx - p0x
    val p2y = 2 * cy - p0y
    val p3x = 2 * cx - p1x
    val p3y = 2 * cy - p1y
    return intArrayOf(p0x.toInt(), p0y.toInt(), p1x.toInt(), p1y.toInt(), p2x.toInt(), p2y.toInt(), p3x.toInt(), p3y.toInt())
}

/** cv2.ellipse2Poly((cx,cy),(a,b),angle,0,360,1) */
fun ellipse2Poly(cx: Int, cy: Int, a: Int, b: Int, angle: Int): IntArray {
    val mp = MatOfPoint()
    Imgproc.ellipse2Poly(Point(cx.toDouble(), cy.toDouble()), Size(a.toDouble(), b.toDouble()), angle, 0, 360, 1, mp)
    val n = mp.rows()
    val buf = IntArray(n * 2)
    if (n > 0) mp.get(0, 0, buf)
    mp.release()
    return buf
}

/** cv2.pointPolygonTest(poly, (x,y), False) >= 0 con contorno entero (inclusive en el borde). */
fun pointInPolygonOrOnEdge(poly: IntArray, px: Int, py: Int): Boolean {
    val n = poly.size / 2
    if (n < 3 && n < 1) return false
    var counter = 0
    var v0x: Long; var v0y: Long
    var vx = poly[2 * (n - 1)].toLong()
    var vy = poly[2 * (n - 1) + 1].toLong()
    val ipx = px.toLong(); val ipy = py.toLong()
    for (i in 0 until n) {
        v0x = vx; v0y = vy
        vx = poly[2 * i].toLong(); vy = poly[2 * i + 1].toLong()
        if ((v0y <= ipy && vy <= ipy) || (v0y > ipy && vy > ipy) || (v0x < ipx && vx < ipx)) {
            if (ipy == vy && (ipx == vx || (ipy == v0y && ((v0x <= ipx && ipx <= vx) || (vx <= ipx && ipx <= v0x))))) return true
            continue
        }
        var dist = (ipy - v0y) * (vx - v0x) - (ipx - v0x) * (vy - v0y)
        if (dist == 0L) return true
        if (vy < v0y) dist = -dist
        if (dist > 0) counter++
    }
    return counter % 2 != 0
}

fun doPolygonsOverlap(poly1: IntArray, poly2: IntArray): Boolean {
    var i = 0
    while (i < poly1.size) {
        if (pointInPolygonOrOnEdge(poly2, poly1[i], poly1[i + 1])) return true
        i += 2
    }
    i = 0
    while (i < poly2.size) {
        if (pointInPolygonOrOnEdge(poly1, poly2[i], poly2[i + 1])) return true
        i += 2
    }
    return false
}

/** Los cuatro vértices de la caja (sin girar) que usa homr: arriba-izq, abajo-izq, arriba-der, abajo-der. */
private fun edgesOfRotatedRectangle(b: RRect): DoubleArray {
    val hw = b.w / 2; val hh = b.h / 2
    return doubleArrayOf(b.cx - hw, b.cy - hh, b.cx - hw, b.cy + hh, b.cx + hw, b.cy - hh, b.cx + hw, b.cy + hh)
}

/** Base de las cajas con ángulo (rectángulos girados y elipses). */
abstract class AngledBox(box: RRect, val contours: Contour, val polygon: IntArray, val debugId: Int = 0) {
    val box: RRect
    init {
        val angle = box.angle
        this.box = when {
            angle > 135 -> RRect(box.cx, box.cy, box.w, box.h, angle - 180)
            angle < -135 -> RRect(box.cx, box.cy, box.w, box.h, angle + 180)
            angle > 45 -> RRect(box.cx, box.cy, box.h, box.w, angle - 90)
            angle < -45 -> RRect(box.cx, box.cy, box.h, box.w, angle + 90)
            else -> box
        }
    }
    val centerX: Double get() = box.cx
    val centerY: Double get() = box.cy
    val sizeW: Double get() = box.w
    val sizeH: Double get() = box.h
    val angle: Double get() = box.angle
    private val edges = edgesOfRotatedRectangle(this.box)
    val topLeftX get() = edges[0]
    val topLeftY get() = edges[1]
    val bottomLeftX get() = edges[2]
    val bottomLeftY get() = edges[3]
    val topRightX get() = edges[4]
    val topRightY get() = edges[5]
    val bottomRightX get() = edges[6]
    val bottomRightY get() = edges[7]

    /** Centro y ejes tal como los usa la comprobación rápida de contacto. */
    open val touchCx: Double get() = box.cx
    open val touchCy: Double get() = box.cy
    open val touchW: Double get() = box.w
    open val touchH: Double get() = box.h

    fun canShapesPossiblyTouch(other: AngledBox): Boolean {
        val major1 = max(touchW, touchH)
        val major2 = max(other.touchW, other.touchH)
        val dx = touchCx - other.touchCx
        val dy = touchCy - other.touchCy
        val distance = sqrt(dx * dx + dy * dy)
        return distance <= major1 + major2
    }

    fun isOverlapping(other: AngledBox): Boolean {
        if (!canShapesPossiblyTouch(other)) return false
        return doPolygonsOverlap(polygon, other.polygon)
    }

    /** Solape con una caja alineada con los ejes (la comprobación rápida usa su centro y su tamaño). */
    fun isOverlapping(other: AxisBox): Boolean {
        val major1 = max(touchW, touchH)
        val major2 = max(other.w, other.h).toDouble()
        val dx = touchCx - other.centerX
        val dy = touchCy - other.centerY
        if (sqrt(dx * dx + dy * dy) > major1 + major2) return false
        return doPolygonsOverlap(polygon, other.polygon)
    }

    fun isOverlappingWithAny(others: List<AngledBox>): Boolean {
        for (o in others) if (isOverlapping(o)) return true
        return false
    }

    override fun equals(other: Any?): Boolean = other is AngledBox && box == other.box
    override fun hashCode(): Int = box.hashCode()
    override fun toString(): String = box.toString()
}

/** Caja alineada con los ejes (x1,y1,x2,y2). */
class AxisBox(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val contours: Contour = Contour(IntArray(0)), val debugId: Int = 0) {
    val centerX: Double = (x1 + x2) / 2.0
    val centerY: Double = (y1 + y2) / 2.0
    val w: Int = x2 - x1
    val h: Int = y2 - y1
    val polygon: IntArray = boxPointsInt(RRect(centerX, centerY, w.toDouble(), h.toDouble(), 0.0))
}

/** cv2.rotatedRectangleIntersection(a, b)[0] != INTERSECT_NONE */
fun rotatedRectsIntersect(a: RRect, b: RRect): Boolean {
    val ra = RotatedRect(Point(a.cx, a.cy), Size(a.w, a.h), a.angle)
    val rb = RotatedRect(Point(b.cx, b.cy), Size(b.w, b.h), b.angle)
    val res = Mat()
    val r = Imgproc.rotatedRectangleIntersection(ra, rb, res)
    res.release()
    return r != Imgproc.INTERSECT_NONE
}

class RotBox(box: RRect, contours: Contour, debugId: Int = 0) :
    AngledBox(box, contours, boxPointsInt(box), debugId) {

    fun isIntersecting(other: RotBox): Boolean {
        if (!canShapesPossiblyTouch(other)) return false
        return rotatedRectsIntersect(box, other.box)
    }

    fun makeBoxThicker(thickness: Int): RotBox {
        if (thickness <= 0) return this
        return RotBox(RRect(box.cx, box.cy, box.w + thickness, box.h + thickness, box.angle), contours, debugId)
    }

    fun moveToXHorizontalBy(xDelta: Int): RotBox =
        RotBox(RRect(centerX + xDelta, centerY, box.w, box.h, box.angle), contours, debugId)

    fun makeBoxTaller(thickness: Int): RotBox =
        RotBox(RRect(box.cx, box.cy, box.w, box.h + thickness, box.angle), contours, debugId)

    fun getCenterExtrapolated(x: Double): Double = (x - box.cx) * tan(box.angle / 180 * PI) + box.cy

    fun isOverlappingExtrapolated(other: RotBox, unitSize: Double): Boolean {
        val left: RotBox; val right: RotBox
        if (this.box.cx > other.box.cx) { left = other; right = this } else { left = this; right = other }
        val centerX = (left.centerX + right.centerX) * 0.5
        val tolerance = Constants.toleranceForStaffLineDetection(unitSize)
        val maxGap = Constants.maxLineGapSize(unitSize)
        if (centerX - left.centerX - floorHalf(left.sizeW) > maxGap ||
            right.centerX - centerX - floorHalf(right.sizeW) > maxGap) return false
        val leftAngle = tan(left.box.angle * PI / 180.0)
        val rightAngle = tan(right.box.angle * PI / 180.0)
        val leftY = (centerX - left.box.cx) * leftAngle + left.box.cy
        val rightY = (centerX - right.box.cx) * rightAngle + right.box.cy
        return abs(leftY - rightY) <= tolerance
    }

    fun toAxisBox(): AxisBox = AxisBox(topLeftX.toInt(), topLeftY.toInt(), bottomRightX.toInt(), bottomRightY.toInt(), contours, debugId)
}

/** Python: size // 2 con números en coma flotante. */
private fun floorHalf(v: Double): Double = kotlin.math.floor(v / 2.0)

class EllipseBox(box: RRect, contours: Contour, debugId: Int = 0) :
    AngledBox(box, contours, ellipse2Poly(box.cx.toInt(), box.cy.toInt(), (box.w / 2).toInt(), (box.h / 2).toInt(), box.angle.toInt()), debugId) {

    fun makeBoxThicker(thickness: Int): EllipseBox =
        EllipseBox(RRect(box.cx, box.cy, box.w + thickness, box.h + thickness, box.angle), contours, debugId)
}

private fun hasValidSize(b: RRect) = !b.w.isNaN() && !b.h.isNaN() && b.w > 0 && b.h > 0

/** Une cajas que se solapan mediante «union-find» (homr._merge_groups_optimized). */
private class UnionFind(n: Int) {
    val parent = IntArray(n) { it }
    val rank = IntArray(n)
    fun find(x: Int): Int {
        var r = x
        while (parent[r] != r) r = parent[r]
        var c = x
        while (parent[c] != r) { val nx = parent[c]; parent[c] = r; c = nx }
        return r
    }
    fun union(x: Int, y: Int) {
        val rx = find(x); val ry = find(y)
        if (rx != ry) {
            if (rank[rx] > rank[ry]) parent[ry] = rx
            else if (rank[rx] < rank[ry]) parent[rx] = ry
            else { parent[ry] = rx; rank[rx]++ }
        }
    }
}

fun <T : AngledBox> mergeOverlayingBoundingBoxes(boxes: List<T>): List<List<T>> {
    val n = boxes.size
    val uf = UnionFind(n)
    for (i in 0 until n) {
        val bi = boxes[i]
        for (j in i + 1 until n) {
            if (bi.isOverlapping(boxes[j])) uf.union(i, j)
        }
    }
    val groups = LinkedHashMap<Int, MutableList<T>>()
    for (i in 0 until n) groups.getOrPut(uf.find(i)) { ArrayList() }.add(boxes[i])
    return groups.values.toList()
}

private fun boxForWholeGroup(groups: List<List<AngledBox>>): List<RotBox> =
    groups.map { g ->
        val all = Contour.concat(g.map { it.contours })
        RotBox(minAreaRect(all), all)
    }

fun createRotatedBoundingBoxes(
    img: Mat,
    skipMerging: Boolean = false,
    minSize: Pair<Int, Int>? = null,
    maxSize: Pair<Int, Int>? = null,
    thickenBoxes: Int? = null,
): List<RotBox> {
    val contours = contoursOf(img)
    val boxes = ArrayList<RotBox>()
    for ((i, c) in contours.withIndex()) {
        if (c.size == 0) continue
        val fit = minAreaRect(c)
        if (!hasValidSize(fit)) continue
        val box = RotBox(fit, c, i)
        if (minSize != null && (box.sizeW < minSize.first || box.sizeH < minSize.second)) continue
        if (maxSize != null) {
            if (maxSize.first > 0 && box.sizeW > maxSize.first) continue
            if (maxSize.second > 0 && box.sizeH > maxSize.second) continue
        }
        boxes.add(box)
    }
    if (skipMerging) return boxes
    val thick = if (thickenBoxes != null) boxes.map { it.makeBoxThicker(thickenBoxes) } else boxes
    return boxForWholeGroup(mergeOverlayingBoundingBoxes(thick))
}

fun createRotatedBoundingBox(contour: Contour, debugId: Int): RotBox = RotBox(minAreaRect(contour), contour, debugId)

fun createBoundingEllipses(img: Mat, minSize: Pair<Int, Int>? = null, maxSize: Pair<Int, Int>? = null, skipMerging: Boolean = false): List<EllipseBox> {
    val contours = contoursOf(img)
    val boxes = ArrayList<EllipseBox>()
    for ((i, c) in contours.withIndex()) {
        if (c.size < 5) continue
        val fit = fitEllipse(c)
        if (!hasValidSize(fit)) continue
        val box = EllipseBox(fit, c, i)
        if (minSize != null && (box.sizeW < minSize.first || box.sizeH < minSize.second)) continue
        if (maxSize != null && (box.sizeW > maxSize.first || box.sizeH > maxSize.second)) continue
        boxes.add(box)
    }
    if (skipMerging) return boxes
    return mergeOverlayingBoundingBoxes(boxes).map { g ->
        val all = Contour.concat(g.map { it.contours })
        EllipseBox(minAreaRect(all), all)
    }
}

fun fitEllipse(c: Contour): RRect {
    val n = c.size
    val m = Mat(n, 1, CvType.CV_32FC2)
    m.put(0, 0, FloatArray(n * 2) { c.xy[it].toFloat() })
    val r = Imgproc.fitEllipse(org.opencv.core.MatOfPoint2f(m))
    m.release()
    return RRect(r.center.x, r.center.y, r.size.width, r.size.height, r.angle)
}

