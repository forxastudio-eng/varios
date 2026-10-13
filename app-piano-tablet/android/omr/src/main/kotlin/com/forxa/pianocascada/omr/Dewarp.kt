package com.forxa.pianocascada.omr

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat6
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Subdiv2D
import kotlin.math.abs

/* Enderezado de los pentagramas curvos: traducción de homr/staff_dewarping.py (solo lo que usa la lectura). */

/** Transformación afín por trozos sobre una triangulación de Delaunay. */
class PiecewiseAffine(private val src: Array<FloatArray>, private val dst: Array<FloatArray>) {
    private val simplices = ArrayList<IntArray>()
    private val matrices = ArrayList<Mat?>()

    init {
        triangulate()
        for (s in simplices) {
            val st = triangle(src, s); val dt = triangle(dst, s)
            if (degenerate(st) || degenerate(dt)) { matrices.add(null); continue }
            matrices.add(runCatching { affine(st, dt) }.getOrNull())
        }
    }

    private fun triangle(pts: Array<FloatArray>, s: IntArray) = Array(3) { pts[s[it]] }

    private fun triangulate() {
        val n = src.size
        if (n < 3) throw IllegalArgumentException("Need at least 3 points for triangulation")
        val m = Mat(n, 1, CvType.CV_32FC2)
        m.put(0, 0, FloatArray(n * 2) { src[it / 2][it % 2] })
        val r = boundingRectFloat(m)
        m.release()
        val rect = Rect(r.x - 10, r.y - 10, r.width + 20, r.height + 20)
        val subdiv = Subdiv2D(rect)
        for (p in src) subdiv.insert(Point(p[0].toDouble(), p[1].toDouble()))
        val tl = MatOfFloat6()
        subdiv.getTriangleList(tl)
        val arr = FloatArray(tl.rows() * 6)
        if (arr.isNotEmpty()) tl.get(0, 0, arr)
        for (t in 0 until tl.rows()) {
            val idx = IntArray(3) { v -> findPointIndex(arr[t * 6 + v * 2], arr[t * 6 + v * 2 + 1]) }
            if (idx.all { it >= 0 }) simplices.add(idx)
        }
        tl.release()
    }

    private fun findPointIndex(x: Float, y: Float, tolerance: Double = 1e-3): Int {
        var best = -1; var bd = Double.MAX_VALUE
        for ((i, p) in src.withIndex()) {
            val dx = (p[0] - x).toDouble(); val dy = (p[1] - y).toDouble()
            val d = Math.sqrt(dx * dx + dy * dy)
            if (d < bd) { bd = d; best = i }
        }
        return if (bd < tolerance) best else -1
    }

    private fun degenerate(t: Array<FloatArray>): Boolean {
        val v1x = (t[1][0] - t[0][0]).toDouble(); val v1y = (t[1][1] - t[0][1]).toDouble()
        val v2x = (t[2][0] - t[0][0]).toDouble(); val v2y = (t[2][1] - t[0][1]).toDouble()
        return abs(v1x * v2y - v1y * v2x) / 2.0 < 1e-6
    }

    private fun affine(s: Array<FloatArray>, d: Array<FloatArray>): Mat {
        val ms = MatOfPoint2f(Point(s[0][0].toDouble(), s[0][1].toDouble()), Point(s[1][0].toDouble(), s[1][1].toDouble()), Point(s[2][0].toDouble(), s[2][1].toDouble()))
        val md = MatOfPoint2f(Point(d[0][0].toDouble(), d[0][1].toDouble()), Point(d[1][0].toDouble(), d[1][1].toDouble()), Point(d[2][0].toDouble(), d[2][1].toDouble()))
        return Imgproc.getAffineTransform(ms, md)
    }

    /** Deforma la imagen (un canal) triángulo a triángulo; fuera de los triángulos queda [fill]. */
    fun warpImage(image: Mat, fill: Int = 1): Mat {
        val height = image.rows(); val width = image.cols()
        val output = Mat(height, width, image.type(), Scalar(fill.toDouble()))
        for ((idx, simplex) in simplices.withIndex()) {
            if (matrices[idx] == null) continue
            val st = triangle(src, simplex); val dt = triangle(dst, simplex)
            if (degenerate(st) || degenerate(dt)) continue
            val srcRect = boundingRectOf(st); val dstRect = boundingRectOf(dt)
            if (srcRect.width <= 0 || srcRect.height <= 0 || dstRect.width <= 0 || dstRect.height <= 0) continue
            val stc = Array(3) { floatArrayOf(st[it][0] - srcRect.x, st[it][1] - srcRect.y) }
            val dtc = Array(3) { floatArrayOf(dt[it][0] - dstRect.x, dt[it][1] - dstRect.y) }
            if (degenerate(stc) || degenerate(dtc)) continue
            val warpMat = runCatching { affine(stc, dtc) }.getOrNull() ?: continue
            // numpy recorta los índices que se salen de la imagen
            val sx0 = maxOf(srcRect.x, 0); val sy0 = maxOf(srcRect.y, 0)
            val sx1 = minOf(srcRect.x + srcRect.width, width); val sy1 = minOf(srcRect.y + srcRect.height, height)
            if (sx1 <= sx0 || sy1 <= sy0) { warpMat.release(); continue }
            val srcCropped = Mat(image, Rect(sx0, sy0, sx1 - sx0, sy1 - sy0))
            val dstCropped = Mat()
            Imgproc.warpAffine(srcCropped, dstCropped, warpMat, Size(dstRect.width.toDouble(), dstRect.height.toDouble()),
                Imgproc.INTER_LINEAR, org.opencv.core.Core.BORDER_CONSTANT, Scalar(fill.toDouble()))
            val mask = Mat.zeros(dstRect.height, dstRect.width, CvType.CV_8UC1)
            val poly = MatOfPoint(*Array(3) { Point(dtc[it][0].toInt().toDouble(), dtc[it][1].toInt().toDouble()) })
            Imgproc.fillConvexPoly(mask, poly, Scalar(255.0))
            val y1 = dstRect.y; val y2 = dstRect.y + dstRect.height
            val x1 = dstRect.x; val x2 = dstRect.x + dstRect.width
            val y1c = maxOf(0, y1); val y2c = minOf(height, y2)
            val x1c = maxOf(0, x1); val x2c = minOf(width, x2)
            if (y2c > y1c && x2c > x1c) {
                val dy1 = y1c - y1; val dx1 = x1c - x1
                val w = x2c - x1c; val h = y2c - y1c
                val maskPart = Mat(mask, Rect(dx1, dy1, w, h))
                val dstPart = Mat(dstCropped, Rect(dx1, dy1, w, h))
                val outPart = Mat(output, Rect(x1c, y1c, w, h))
                dstPart.copyTo(outPart, maskPart)
            }
            warpMat.release(); dstCropped.release(); mask.release(); poly.release()
        }
        return output
    }

    private fun boundingRectOf(t: Array<FloatArray>): Rect {
        val m = Mat(3, 1, CvType.CV_32FC2)
        m.put(0, 0, floatArrayOf(t[0][0], t[0][1], t[1][0], t[1][1], t[2][0], t[2][1]))
        val r = boundingRectFloat(m)
        m.release()
        return r
    }

    private fun boundingRectFloat(m: Mat): Rect = Imgproc.boundingRect(m)
}

object Dewarp {
    private fun isPointOnImage(x: Int, y: Int, w: Int, h: Int): Boolean {
        val margin = 10
        return !(x < margin || x > w - margin || y < margin || y > h - margin)
    }

    /** Puntos de la rejilla de partida y de destino (líneas horizontales rectas) para una imagen de pentagrama. */
    private fun spanAndOptimalPoints(staff: Staff, w: Int, h: Int): Pair<List<MutableList<IntArray>>, List<MutableList<IntArray>>> {
        val span = ArrayList<MutableList<IntArray>>()
        val optimal = ArrayList<MutableList<IntArray>>()
        var firstYOffset: Double? = null
        val intervals = 6
        if (h / intervals == 0) return Pair(span, optimal)
        var y = 2
        while (y < h - 2) {
            val linePoints = ArrayList<IntArray>()
            var x = 2
            while (x < w) {
                val yv = staff.getAt(x.toDouble())
                if (yv != null) {
                    val yOffset = yv.y[2]
                    val yDelta: Int
                    if (firstYOffset == null || firstYOffset == 0.0) { firstYOffset = yOffset; yDelta = 0 }
                    else yDelta = (yOffset - firstYOffset).toInt()
                    val px = x; val py = y + yDelta
                    if (isPointOnImage(px, py, w, h)) linePoints.add(intArrayOf(px, py))
                }
                x += 80
            }
            if (linePoints.size > 2) {
                val avg = linePoints.sumOf { it[1] }.toDouble() / linePoints.size
                span.add(linePoints)
                optimal.add(linePoints.map { intArrayOf(it[0], avg.toInt()) }.toMutableList())
            }
            y += h / intervals
        }
        return Pair(span, optimal)
    }

    /** Devuelve la imagen enderezada, o la misma imagen si no se pudo calcular. */
    fun dewarpStaffImage(image: Mat, staff: Staff): Mat {
        return try {
            val (source, destination) = spanAndOptimalPoints(staff, image.cols(), image.rows())
            val w = image.cols(); val h = image.rows()
            fun prepare(lines: List<MutableList<IntArray>>): List<List<IntArray>> {
                for (line in lines) {
                    line.add(0, intArrayOf(0, line[0][1]))
                    line.add(intArrayOf(w, line.last()[1]))
                }
                val all = ArrayList<List<IntArray>>()
                all.add(listOf(intArrayOf(0, 0), intArrayOf(w, 0)))
                all.addAll(lines)
                all.add(listOf(intArrayOf(0, h), intArrayOf(w, h)))
                return all
            }
            val s = prepare(source).flatten(); val d = prepare(destination).flatten()
            val tf = PiecewiseAffine(Array(s.size) { floatArrayOf(s[it][0].toFloat(), s[it][1].toFloat()) }, Array(d.size) { floatArrayOf(d[it][0].toFloat(), d[it][1].toFloat()) })
            tf.warpImage(image, 1)
        } catch (e: Exception) {
            image
        }
    }
}
