package com.forxa.pianocascada.omr

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Máscaras finales de la segmentación (tras el filtrado de ruido y las líneas reforzadas). */
class Predictions(val staff: Mat, val symbols: Mat, val stemsRest: Mat, val notehead: Mat, val clefsKeys: Mat)

class NoteheadWithStem(val notehead: EllipseBox, val stem: RotBox?)

/** Detecta los pentagramas y los agrupa en sistemas (homr/main.py: detect_staffs_in_image y brace_dot_detection.py). */
object Systems {
    fun detect(p: Predictions): List<MultiStaff> {
        // símbolos: elipses de cabezas, fragmentos de línea, claves, plicas y barras
        val noteheads = createBoundingEllipses(p.notehead, minSize = Pair(4, 4))
        var staffFragments = createRotatedBoundingBoxes(p.staff, skipMerging = true, minSize = Pair(5, 1), maxSize = Pair(10000, 100))
        val clefsKeys = createRotatedBoundingBoxes(p.clefsKeys, minSize = Pair(20, 40), maxSize = Pair(1000, 1000))
        val stemsRest = createRotatedBoundingBoxes(p.stemsRest)
        val barLineImg = Mat()
        Imgproc.dilate(p.stemsRest, barLineImg, Mat.ones(5, 3, CvType.CV_8U))
        val barLines = createRotatedBoundingBoxes(barLineImg, skipMerging = true, minSize = Pair(1, 5))
        barLineImg.release()

        staffFragments = StaffDetection.breakWideFragments(staffFragments)
        val withStems = combineNoteheadsWithStems(noteheads, stemsRest)
        if (withStems.isEmpty()) throw OmrException("No se han encontrado cabezas de nota en la página")

        val averageNoteHeadHeight = medianOf(withStems.map { it.notehead.sizeH })
        val allNoteheads = withStems.map { it.notehead }
        val allStems = withStems.mapNotNull { it.stem }
        val barLinesOrRests = barLines.filter { !it.isOverlappingWithAny(allNoteheads) && !it.isOverlappingWithAny(allStems) }
        val barLineBoxes = detectBarLines(barLinesOrRests, averageNoteHeadHeight)

        val staffs = StaffDetection.detectStaff(p.staff, staffFragments, clefsKeys, barLineBoxes)
        if (staffs.isEmpty()) throw OmrException("No se han encontrado pentagramas en la página")

        val braceDotImg = prepareBraceDotImage(p.symbols, p.staff)
        val braceDot = createRotatedBoundingBoxes(braceDotImg, skipMerging = true, maxSize = Pair(100, -1))
        braceDotImg.release()
        return findBracesBracketsAndGrandStaffLines(staffs, braceDot)
    }

    fun combineNoteheadsWithStems(noteheads: List<EllipseBox>, stems: List<RotBox>): List<NoteheadWithStem> {
        val result = ArrayList<NoteheadWithStem>()
        for (nh in noteheads.sortedBy { it.box.cy }) {
            val thick = nh.makeBoxThicker(15)
            var found: RotBox? = null
            for (stem in stems) if (stem.isOverlapping(thick)) { found = stem; break }
            result.add(NoteheadWithStem(nh, found))
        }
        return result
    }

    fun detectBarLines(barLines: List<RotBox>, unitSize: Double): List<RotBox> =
        barLines.filter { it.sizeH >= Constants.barLineMinHeight(unitSize) && it.sizeW <= Constants.barLineMaxWidth(unitSize) }

    fun prepareBraceDotImage(symbols: Mat, staff: Mat): Mat {
        val sub = Mat()
        Core.subtract(symbols, staff, sub)
        val out = Mat()
        Imgproc.erode(sub, out, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(1.0, 5.0)))
        val res = Mat()
        Imgproc.dilate(out, res, Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 35.0)))
        sub.release(); out.release()
        return res
    }

    private fun filterForTallElements(braceDot: List<RotBox>, staffs: List<Staff>): List<RotBox> {
        val rough = staffs[0].averageUnitSize
        val larger = braceDot.filter { it.sizeH > Constants.minHeightForBraceRough(rough) && it.sizeW < Constants.maxWidthForBraceRough(rough) }
        val result = ArrayList<RotBox>()
        for (symbol in larger) {
            val closest = staffs.minByOrNull { it.yDistanceTo(symbol.centerX, symbol.centerY) }!!
            if (symbol.sizeH > Constants.minHeightForBrace(closest.averageUnitSize)) result.add(symbol)
        }
        return result
    }

    private fun connectionsAtLines(staff1: Staff, staff2: Staff, braceDot: List<RotBox>): List<RotBox> {
        val result = ArrayList<RotBox>()
        for (symbol in braceDot) {
            val thicker = symbol.makeBoxThicker(Constants.toleranceForTouchingClefs(staff1.averageUnitSize))
            val p1 = staff1.getAt(symbol.centerX) ?: continue
            val p2 = staff2.getAt(symbol.centerX) ?: continue
            if (thicker.isOverlapping(p1.toAxisBox()) && thicker.isOverlapping(p2.toAxisBox())) result.add(symbol)
        }
        return result
    }

    private fun mergeMultiStaffIfTheyShareAStaff(staffs: List<MultiStaff>): List<MultiStaff> {
        val result = ArrayList<MultiStaff>()
        for (staff in staffs) {
            var anyMerged = false
            for (existing in result) {
                if (staff.staffs.any { s -> existing.staffs.any { it === s } }) {
                    result.remove(existing)
                    result.add(existing.merge(staff))
                    anyMerged = true
                    break
                }
            }
            if (!anyMerged) result.add(staff)
        }
        return result
    }

    fun findBracesBracketsAndGrandStaffLines(staffsIn: List<Staff>, braceDotIn: List<RotBox>): List<MultiStaff> {
        val braceDot = filterForTallElements(braceDotIn, staffsIn)
        val result = ArrayList<MultiStaff>()
        for ((i, staff) in staffsIn.withIndex()) {
            val neighbors = ArrayList<Staff>()
            if (i > 0) neighbors.add(staffsIn[i - 1])
            if (i < staffsIn.size - 1) neighbors.add(staffsIn[i + 1])
            var anyConnected = false
            for (n in neighbors) {
                val connections = connectionsAtLines(staff, n, braceDot)
                if (connections.size >= Constants.minimumConnectionsToFormCombinedStaff) {
                    result.add(MultiStaff(listOf(staff, n), connections))
                    anyConnected = true
                }
            }
            if (!anyConnected) result.add(MultiStaff(listOf(staff), emptyList()))
        }
        val merged = mergeMultiStaffIfTheyShareAStaff(result)
        return merged.map { it.createGrandstaffs(braceDot) }
    }
}

class OmrException(message: String) : Exception(message)
