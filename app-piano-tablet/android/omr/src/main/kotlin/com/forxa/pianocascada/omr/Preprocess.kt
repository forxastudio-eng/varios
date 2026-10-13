package com.forxa.pianocascada.omr

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.ceil
import kotlin.math.abs

/* Preparación de la imagen y filtrado de ruido: traducción de homr (autocrop, resize, color_adjust, noise_filtering). */

object Preprocess {
    /** Recorta el folio si la foto tiene bordes alrededor; si la partitura ocupa toda la imagen la devuelve igual. */
    fun autocrop(img: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY)
        val hist = IntArray(256)
        val n = img.rows() * img.cols()
        val buf = ByteArray(n * 3)
        img.get(0, 0, buf)
        for (i in 0 until n) hist[buf[3 * i].toInt() and 0xFF]++
        var dominant = 0
        for (i in 1 until 256) if (hist[i] > hist[dominant]) dominant = i

        val thresh = Mat()
        Imgproc.threshold(gray, thresh, (dominant - 30).toDouble(), 255.0, Imgproc.THRESH_BINARY)
        val morph = Mat()
        Imgproc.morphologyEx(thresh, morph, Imgproc.MORPH_CLOSE, Mat.ones(7, 7, CvType.CV_8U))
        val morph2 = Mat()
        Imgproc.morphologyEx(morph, morph2, Imgproc.MORPH_ERODE, Mat.ones(9, 9, CvType.CV_8U))

        val contours = contoursOf(morph2, Imgproc.RETR_EXTERNAL)
        gray.release(); thresh.release(); morph.release(); morph2.release()
        var areaThresh = 0.0
        var big: Contour? = null
        for (c in contours) {
            val a = contourArea(c)
            if (a > areaThresh) { areaThresh = a; big = c }
        }
        if (big == null) return img
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
        for (i in 0 until big.size) {
            minX = minOf(minX, big.x(i)); maxX = maxOf(maxX, big.x(i))
            minY = minOf(minY, big.y(i)); maxY = maxOf(maxY, big.y(i))
        }
        val x = minX; val y = minY; val w = maxX - minX + 1; val h = maxY - minY + 1
        val pageW = img.cols(); val pageH = img.rows()
        val isFullPageView = x < pageW * 0.25 || y < pageH * 0.25
        if (isFullPageView) return img
        return Mat(img, org.opencv.core.Rect(x, y, w, h)).clone()
    }

    private fun contourArea(c: Contour): Double {
        val n = c.size
        if (n < 3) return 0.0
        var s = 0.0
        var px = c.x(n - 1).toDouble(); var py = c.y(n - 1).toDouble()
        for (i in 0 until n) {
            val cx = c.x(i).toDouble(); val cy = c.y(i).toDouble()
            s += px * cy - cx * py
            px = cx; py = cy
        }
        return abs(s) / 2.0
    }

    /** Ancho fijo de 1920 px manteniendo la proporción (interpolación bicúbica de PIL, igual que homr). */
    fun resizeImage(img: Mat): Mat {
        val targetW = 1920
        val w = img.cols(); val h = img.rows()
        if (w == targetW) return img
        val ratio = targetW.toDouble() / w
        val th = pyRound(h * ratio).toInt()   // Python round(): al par más cercano
        return pilResizeBicubic(img, targetW, th)
    }

    fun applyClahe(bgr: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
        val clahe = Imgproc.createCLAHE(1.0, Size(8.0, 8.0))
        val out = Mat()
        clahe.apply(gray, out)
        gray.release()
        return out
    }

    // ------------------------------------------------------------------ PIL.Image.resize (BICUBIC)
    private const val PRECISION_BITS = 32 - 8 - 2

    private fun bicubic(xIn: Double): Double {
        val a = -0.5
        var x = xIn
        if (x < 0.0) x = -x
        if (x < 1.0) return ((a + 2.0) * x - (a + 3.0)) * x * x + 1
        if (x < 2.0) return (((x - 5) * x + 8) * x - 4) * a
        return 0.0
    }

    private class Coeffs(val bounds: IntArray, val kk: IntArray, val ksize: Int)

    private fun precomputeCoeffs(inSize: Int, outSize: Int): Coeffs {
        val scale = inSize.toDouble() / outSize
        val filterscale = if (scale < 1.0) 1.0 else scale
        val support = 2.0 * filterscale
        val ksize = ceil(support).toInt() * 2 + 1
        val kk = IntArray(outSize * ksize)
        val bounds = IntArray(outSize * 2)
        val k = DoubleArray(ksize)
        val ss = 1.0 / filterscale
        for (xx in 0 until outSize) {
            val center = (xx + 0.5) * scale
            var xmin = (center - support + 0.5).toInt()
            if (xmin < 0) xmin = 0
            var xmax = (center + support + 0.5).toInt()
            if (xmax > inSize) xmax = inSize
            xmax -= xmin
            var ww = 0.0
            for (x in 0 until xmax) {
                val w = bicubic((x + xmin - center + 0.5) * ss)
                k[x] = w
                ww += w
            }
            for (x in 0 until xmax) if (ww != 0.0) k[x] = k[x] / ww
            for (x in xmax until ksize) k[x] = 0.0
            for (x in 0 until ksize) {
                kk[xx * ksize + x] = if (k[x] < 0) (-0.5 + k[x] * (1 shl PRECISION_BITS)).toInt() else (0.5 + k[x] * (1 shl PRECISION_BITS)).toInt()
            }
            bounds[2 * xx] = xmin
            bounds[2 * xx + 1] = xmax
        }
        return Coeffs(bounds, kk, ksize)
    }

    private fun clip8(v: Int): Int {
        val r = v shr PRECISION_BITS
        return if (r < 0) 0 else if (r > 255) 255 else r
    }

    fun pilResizeBicubic(src: Mat, outW: Int, outH: Int): Mat {
        val ch = src.channels()
        val inW = src.cols(); val inH = src.rows()
        val data = ByteArray(inW * inH * ch)
        src.get(0, 0, data)
        // pasada horizontal
        var cur = data
        var curW = inW
        if (outW != inW) {
            val c = precomputeCoeffs(inW, outW)
            val tmp = ByteArray(outW * inH * ch)
            for (y in 0 until inH) {
                for (x in 0 until outW) {
                    val xmin = c.bounds[2 * x]; val xmax = c.bounds[2 * x + 1]
                    for (cc in 0 until ch) {
                        var ss = 1 shl (PRECISION_BITS - 1)
                        for (i in 0 until xmax) ss += (cur[(y * inW + xmin + i) * ch + cc].toInt() and 0xFF) * c.kk[x * c.ksize + i]
                        tmp[(y * outW + x) * ch + cc] = clip8(ss).toByte()
                    }
                }
            }
            cur = tmp; curW = outW
        }
        if (outH != inH) {
            val c = precomputeCoeffs(inH, outH)
            val tmp = ByteArray(curW * outH * ch)
            for (y in 0 until outH) {
                val ymin = c.bounds[2 * y]; val ymax = c.bounds[2 * y + 1]
                for (x in 0 until curW) {
                    for (cc in 0 until ch) {
                        var ss = 1 shl (PRECISION_BITS - 1)
                        for (i in 0 until ymax) ss += (cur[((ymin + i) * curW + x) * ch + cc].toInt() and 0xFF) * c.kk[y * c.ksize + i]
                        tmp[(y * curW + x) * ch + cc] = clip8(ss).toByte()
                    }
                }
            }
            cur = tmp
        }
        val out = Mat(outH, outW, src.type())
        out.put(0, 0, cur)
        return out
    }

    // ------------------------------------------------------------------ filtrado de ruido (noise_filtering.py)
    private fun estimateNoise(gray: Mat, y1: Int, y2: Int, x1: Int, x2: Int, fullH: Int, fullW: Int): Int {
        val h = y2 - y1; val w = x2 - x1
        if (h <= 0 || w <= 0) return 0
        // filter2D con el núcleo [[1,-2,1],[-2,4,-2],[1,-2,1]] y borde REFLECT_101 sobre la tesela
        val tile = Mat(gray, org.opencv.core.Rect(x1, y1, w, h)).clone()
        val dst = Mat()
        val k = Mat(3, 3, CvType.CV_64F)
        k.put(0, 0, 1.0, -2.0, 1.0, -2.0, 4.0, -2.0, 1.0, -2.0, 1.0)
        Imgproc.filter2D(tile, dst, CvType.CV_64F, k)
        val abs = Mat()
        Core.absdiff(dst, org.opencv.core.Scalar(0.0), abs)
        val sum = Core.sumElems(abs).`val`[0]
        tile.release(); dst.release(); abs.release(); k.release()
        val sigma = sum / (h * w)
        return sigma.toLong().toInt() and 0xFF   // asignación de coma flotante a uint8, como numpy
    }

    /** Devuelve la máscara (255 = conservar) o null si no hay que filtrar. */
    fun createNoiseMask(staffMask255: Mat): Mat? {
        val imgH = staffMask255.rows(); val imgW = staffMask255.cols()
        val m = imgH / 20; val n = imgW / 20
        if (m == 0 || n == 0) return null
        val gridH = ceil(imgH.toDouble() / m).toInt(); val gridW = ceil(imgW.toDouble() / n).toInt()
        val grid = Array(gridH) { IntArray(gridW) }
        var i = 0; var y1 = 0
        while (y1 < imgH) {
            var j = 0; var x1 = 0
            while (x1 < imgW) {
                grid[i][j] = estimateNoise(staffMask255, y1, minOf(y1 + m, imgH), x1, minOf(x1 + n, imgW), imgH, imgW)
                j++; x1 += n
            }
            i++; y1 += m
        }
        val mask = Mat.zeros(imgH, imgW, CvType.CV_8UC1)
        var filtered = 0; var total = 0
        i = 0; y1 = 0
        while (y1 < imgH) {
            var j = 0; var x1 = 0
            while (x1 < imgW) {
                val noise = grid[i][j]
                var anyAbove = false
                if (i > 0 && grid[i - 1][j] > Constants.imageNoiseLimit) anyAbove = true
                if (j > 0 && grid[i][j - 1] > Constants.imageNoiseLimit) anyAbove = true
                if (i < gridH - 1 && grid[i + 1][j] > Constants.imageNoiseLimit) anyAbove = true
                if (j < gridW - 1 && grid[i][j + 1] > Constants.imageNoiseLimit) anyAbove = true
                if (noise > Constants.imageNoiseLimit && anyAbove) {
                    filtered++
                } else {
                    val y2 = minOf(y1 + m, imgH); val x2 = minOf(x1 + n, imgW)
                    Mat(mask, org.opencv.core.Rect(x1, y1, x2 - x1, y2 - y1)).setTo(org.opencv.core.Scalar(255.0))
                }
                total++
                j++; x1 += n
            }
            i++; y1 += m
        }
        if (filtered.toDouble() / total > 0.5) { mask.release(); return null }
        if (filtered > 0) return mask
        mask.release()
        return null
    }

    fun makeLinesStronger(img: Mat, kw: Int, kh: Int): Mat {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(kw.toDouble(), kh.toDouble()))
        val dil = Mat()
        Imgproc.dilate(img, dil, kernel)
        val out = Mat()
        Imgproc.threshold(dil, out, 0.1, 1.0, Imgproc.THRESH_BINARY)
        dil.release()
        return out
    }
}
