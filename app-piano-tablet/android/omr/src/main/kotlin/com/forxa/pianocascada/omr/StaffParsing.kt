package com.forxa.pianocascada.omr

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/* Recorte, enderezado y lectura de cada pentagrama: traducción de homr/staff_parsing.py. */

class StaffRegions(systems: List<MultiStaff>) {
    private val centers = systems.flatMap { ms -> ms.staffs.map { Pair(it.minY, it.maxY) } }

    fun startOfClosestStaffAbove(y: Double): Double {
        val above = centers.filter { it.first < y }.map { it.second }
        return if (above.isEmpty()) 0.0 else above.max()
    }

    fun startOfClosestStaffBelow(y: Double): Double {
        val below = centers.filter { it.second > y }.map { it.first }
        return if (below.isEmpty()) 1e12 else below.min()
    }
}

object StaffParsing {
    private fun haveAllTheSameNumberOfStaffs(staffs: List<MultiStaff>) = staffs.all { it.staffs.size == staffs[0].staffs.size }

    private fun isCloseToImageTopOrBottom(staff: MultiStaff, imageRows: Int): Boolean {
        val tolerance = 50.0
        // (homr compara x con el alto de la imagen)
        return staff.staffs.map { min(it.minX, imageRows - it.maxX) }.min() < tolerance
    }

    fun ensureSameNumberOfStaffs(staffs: List<MultiStaff>, imageRows: Int): List<MultiStaff> {
        if (haveAllTheSameNumberOfStaffs(staffs)) return staffs
        if (staffs.size > 2) {
            if (isCloseToImageTopOrBottom(staffs[0], imageRows) && haveAllTheSameNumberOfStaffs(staffs.drop(1))) return staffs.drop(1)
            if (isCloseToImageTopOrBottom(staffs.last(), imageRows) && haveAllTheSameNumberOfStaffs(staffs.dropLast(1))) return staffs.dropLast(1)
        }
        val result = ArrayList<MultiStaff>()
        for (s in staffs) result.addAll(s.breakApart())
        return result.sortedBy { it.staffs[0].minY }
    }

    /** Tamaño (ancho, alto) con el que cabe la imagen en el lienzo del modelo sin deformarla. */
    fun canvasSize(height: Int, width: Int): IntArray {
        val ratio = Tromr.HEIGHT.toDouble() / Tromr.WIDTH
        return if (height.toDouble() / width > ratio) intArrayOf((width.toDouble() / height * Tromr.HEIGHT).toInt(), Tromr.HEIGHT)
        else intArrayOf(Tromr.WIDTH, (height.toDouble() / width * Tromr.WIDTH).toInt())
    }

    fun centerImageOnCanvas(image: Mat, size: IntArray): Mat {
        val resized = Mat()
        Imgproc.resize(image, resized, Size(size[0].toDouble(), size[1].toDouble()))
        val canvas = Mat(Tromr.HEIGHT, Tromr.WIDTH, CvType.CV_8UC1, Scalar(255.0))
        val yOffset = (Tromr.HEIGHT - resized.rows()) / 2   // Python //: la altura nunca supera el lienzo
        val h = min(resized.rows(), Tromr.HEIGHT - yOffset); val w = min(resized.cols(), Tromr.WIDTH)
        Mat(resized, Rect(0, 0, w, h)).copyTo(Mat(canvas, Rect(0, yOffset, w, h)))
        resized.release()
        return canvas
    }

    private fun removeBlackContoursAtEdgesOfImage(gray: Mat, unitSize: Double): Mat {
        val thresh = Mat()
        Imgproc.threshold(gray, thresh, 97.0, 255.0, Imgproc.THRESH_BINARY)
        val inv = Mat()
        org.opencv.core.Core.subtract(Mat(thresh.rows(), thresh.cols(), thresh.type(), Scalar(255.0)), thresh, inv)
        val contours = contoursOf(inv)
        val threshold = Constants.blackSpotRemovalThreshold(unitSize)
        for (c in contours) {
            if (c.size == 0) continue
            var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
            for (i in 0 until c.size) { minX = min(minX, c.x(i)); maxX = max(maxX, c.x(i)); minY = min(minY, c.y(i)); maxY = max(maxY, c.y(i)) }
            val w = maxX - minX + 1; val h = maxY - minY + 1
            if (w < threshold || h < threshold) continue
            val atEdge = minX == 0 || minY == 0 || minX + w == gray.cols() || minY + h == gray.rows()
            if (!atEdge) continue
            val region = Mat(inv, Rect(minX, minY, w, h))
            val mean = org.opencv.core.Core.mean(region).`val`[0]
            if (mean < 127) continue
            Mat(gray, Rect(minX, minY, w, h)).setTo(Scalar(255.0))
        }
        thresh.release(); inv.release()
        return gray
    }

    /** Devuelve la imagen del pentagrama lista para el modelo (256×1280, un canal). */
    fun prepareStaffImage(staff: Staff, page: Mat, regions: StaffRegions): Mat {
        // región del pentagrama con margen, sin pisar a los vecinos
        val u = staff.averageUnitSize
        val xMin = staff.minX - 2 * u
        val xMax = staff.maxX + 2 * u
        val yMin = max(staff.minY - 4 * u, regions.startOfClosestStaffAbove(staff.minY))
        val yMax = min(staff.maxY + 4 * u, regions.startOfClosestStaffBelow(staff.maxY))
        val region = doubleArrayOf(xMin.toInt().toDouble(), yMin.toInt().toDouble(), xMax.toInt().toDouble(), yMax.toInt().toDouble())
        val dims = canvasSize((region[3] - region[1]).toInt(), (region[2] - region[0]).toInt())
        val scaling = dims[1] / (region[3] - region[1])

        val scaled = Mat()
        Imgproc.resize(page, scaled, Size((page.cols() * scaling).toInt().toDouble(), (page.rows() * scaling).toInt().toDouble()))
        val reg = DoubleArray(4) { Math.rint(region[it] * scaling) }
        val step1 = doubleArrayOf(reg[0] - 10, reg[1] - 50, reg[2] + 10, reg[3] + 50)
        val (cropped1, topLeft) = cropAndReturnNewTop(scaled, step1[0], step1[1], step1[2], step1[3])
        scaled.release()
        val step2 = doubleArrayOf(reg[0] - topLeft[0], reg[1] - topLeft[1], reg[2] - topLeft[0], reg[3] - topLeft[1])
        val tlx = topLeft[0] / scaling; val tly = topLeft[1] / scaling
        val transformed = staff.transformCoordinates { x, y -> Pair((x - tlx) * scaling, (y - tly) * scaling) }

        val dewarped = Dewarp.dewarpStaffImage(cropped1, transformed)
        val (cropped2, _) = cropAndReturnNewTop(dewarped, step2[0], step2[1], step2[2], step2[3])
        val cleaned = removeBlackContoursAtEdgesOfImage(cropped2.clone(), transformed.averageUnitSize)
        return centerImageOnCanvas(cleaned, dims)
    }

    private fun limitX(img: Mat, x: Double) = max(0, min(img.cols() - 1, pyRound(x).toInt()))
    private fun limitY(img: Mat, y: Double) = max(0, min(img.rows() - 1, pyRound(y).toInt()))

    private fun cropAndReturnNewTop(img: Mat, x1: Double, y1: Double, x2: Double, y2: Double): Pair<Mat, IntArray> {
        val xa = limitX(img, min(x1, x2)); val ya = limitY(img, min(y1, y2))
        val xb = limitX(img, max(x1, x2)); val yb = limitY(img, max(y1, y2))
        val w = max(0, xb - xa); val h = max(0, yb - ya)
        val sub = if (w == 0 || h == 0) Mat() else Mat(img, Rect(xa, ya, w, h)).clone()
        return Pair(sub, intArrayOf(xa, ya))
    }
}
