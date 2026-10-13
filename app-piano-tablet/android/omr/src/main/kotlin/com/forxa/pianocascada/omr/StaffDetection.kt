package com.forxa.pianocascada.omr

import org.opencv.core.Mat
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/* Detección de pentagramas: traducción de homr/staff_detection.py. */

/** Un tramo de línea de pentagrama formado por fragmentos conectados. */
class StaffLineSegment(val debugId: Int, fragments: List<RotBox>) {
    val staffFragments: List<RotBox> = fragments.sortedBy { it.box.cx }
    val minX: Double = fragments.minOf { it.centerX - it.sizeW / 2 }
    val maxX: Double = fragments.maxOf { it.centerX + it.sizeW / 2 }
    val minY: Double = fragments.minOf { it.centerY - it.sizeH / 2 }
    val maxY: Double = fragments.maxOf { it.centerY + it.sizeH / 2 }

    fun merge(other: StaffLineSegment): StaffLineSegment {
        val lines = ArrayList(staffFragments)
        for (f in other.staffFragments) if (lines.none { it == f }) lines.add(f)
        return StaffLineSegment(debugId, lines)
    }

    fun getAt(x: Double): RotBox? {
        val tol = Constants.staffLineSegmentXTolerance
        for (f in staffFragments) {
            if (x >= f.centerX - f.sizeW / 2 - tol && x <= f.centerX + f.sizeW / 2 + tol) return f
        }
        return null
    }

    fun isOverlapping(other: StaffLineSegment): Boolean {
        for (a in staffFragments) for (b in other.staffFragments) if (a.isOverlapping(b)) return true
        return false
    }

    private val fragmentSet: Set<RotBox> by lazy { HashSet(staffFragments) }
    fun fragmentSet(): Set<RotBox> = fragmentSet

    override fun equals(other: Any?): Boolean = other is StaffLineSegment && fragmentSet == other.fragmentSet
    override fun hashCode(): Int = fragmentSet.hashCode()
}

class StaffAnchor(val staffLines: List<StaffLineSegment>, val symbol: RotBox) {
    val averageUnitSize: Double
    val maxY: Double = staffLines.maxOf { it.maxY }
    val minY: Double = staffLines.minOf { it.minY }
    val zoneStart: Int
    val zoneStop: Int

    init {
        val ys = staffLines.map { it.staffFragments[0].getCenterExtrapolated(symbol.centerX) }.sorted()
        val deltas = (1 until ys.size).map { abs(ys[it] - ys[it - 1]) }
        averageUnitSize = if (deltas.isEmpty()) 0.0 else deltas.sum() / deltas.size
        val maxLedger = 5
        zoneStart = (minY - maxLedger * averageUnitSize).toInt()
        zoneStop = (maxY + maxLedger * averageUnitSize).toInt()
    }
}

/** Pentagrama «en bruto»: con huecos y con tramos que empiezan y acaban distinto en cada línea. */
class RawStaff private constructor(
    val staffId: Int, val lines: List<StaffLineSegment>, val anchors: List<StaffAnchor>, contour: Contour, rbox: RRect,
) : AngledBox(rbox, contour, boxPointsInt(rbox), staffId) {
    val minX: Double = centerX - sizeW / 2
    val maxX: Double = centerX + sizeW / 2
    val minY: Double = centerY - sizeH / 2
    val maxY: Double = centerY + sizeH / 2

    fun merge(other: RawStaff): RawStaff {
        val merged = lines.mapIndexed { i, line -> other.lines[i].merge(line) }
        return create(staffId, merged, anchors + other.anchors)
    }

    companion object {
        fun create(staffId: Int, lines: List<StaffLineSegment>, anchors: List<StaffAnchor>): RawStaff {
            val parts = ArrayList<Contour>()
            for (l in lines) for (f in l.staffFragments) parts.add(f.contours)
            val c = Contour.concat(parts)
            return RawStaff(staffId, lines, anchors, c, minAreaRect(c))
        }
    }
}

object StaffDetection {
    fun breakWideFragments(fragments: List<RotBox>, limit: Int = 100): List<RotBox> {
        val result = ArrayList<RotBox>()
        for (fragment in fragments) {
            var remaining = fragment
            while (remaining.sizeW > limit) {
                val c = remaining.contours
                var minX = Int.MAX_VALUE
                for (i in 0 until c.size) minX = minOf(minX, c.x(i))
                val left = ArrayList<IntArray>(); val right = ArrayList<IntArray>()
                for (i in 0 until c.size) {
                    if (c.x(i) < minX + limit) left.add(intArrayOf(c.x(i), c.y(i))) else right.add(intArrayOf(c.x(i), c.y(i)))
                }
                val ls = left.sortedBy { it[0] }.toMutableList()
                val rs = right.sortedBy { it[0] }.toMutableList()
                if (ls.isEmpty() || rs.isEmpty()) break
                ls.add(rs[0])
                rs.add(ls.last())
                result.add(createRotatedBoundingBox(toContour(ls), remaining.debugId))
                remaining = createRotatedBoundingBox(toContour(rs), remaining.debugId)
            }
            result.add(remaining)
        }
        return result
    }

    private fun toContour(pts: List<IntArray>): Contour {
        val a = IntArray(pts.size * 2)
        for ((i, p) in pts.withIndex()) { a[2 * i] = p[0]; a[2 * i + 1] = p[1] }
        return Contour(a)
    }

    /** Agrupa fragmentos de línea de izquierda a derecha extrapolando los huecos. */
    fun connectStaffLines(staffLines: List<RotBox>, unitSize: Double): List<StaffLineSegment> {
        // Python: sorted(..., reverse=True) y pop() desde el final = de izquierda a derecha
        val stack = ArrayList(staffLines.sortedByDescending { it.bottomLeftX })
        val result = ArrayList<ArrayList<RotBox>>()
        var active = ArrayList<ArrayList<RotBox>>()
        var lastCleanupAtX = 0.0
        while (stack.isNotEmpty()) {
            val current = stack.removeAt(stack.size - 1)
            val x = current.bottomLeftX
            if (x - lastCleanupAtX > Constants.maxLineGapSize(unitSize)) {
                active = ArrayList(active.filter { x - it.last().bottomRightX < Constants.maxLineGapSize(unitSize) })
                lastCleanupAtX = x
            }
            val isShort = current.box.w < Constants.isShortLine(unitSize)
            if (isShort) continue
            var connected = false
            for (line in active) {
                if (line.last().isOverlappingExtrapolated(current, unitSize)) { line.add(current); connected = true }
            }
            if (!connected) {
                val nl = arrayListOf(current)
                result.add(nl); active.add(nl)
            }
        }
        val top = result.sortedBy { it[0].box.cy }
        return top.mapIndexed { i, l -> StaffLineSegment(i, l) }
    }

    private fun areLinesCrossing(lines: List<StaffLineSegment>): Boolean {
        for (i in lines.indices) for (j in i + 1 until lines.size) if (lines[i].isOverlapping(lines[j])) return true
        return false
    }

    private fun areLinesParallel(lines: List<StaffLineSegment>, unitSize: Double): Boolean {
        val allFragments = ArrayList<RotBox>()
        for (l in lines) allFragments.addAll(l.staffFragments)
        if (allFragments.isEmpty()) return false
        val avg = allFragments.sumOf { it.angle } / allFragments.size
        for (f in allFragments) {
            if (abs(f.angle - avg) > Constants.maxAngleForLinesToBeParallel && f.sizeW > Constants.isShortConnectedLine(unitSize)) return false
        }
        return true
    }

    private fun beginsOrEndsOnOneStaffLine(line: RotBox, staffLines: List<StaffLineSegment>, unitSize: Double): Boolean {
        for (sl in staffLines) {
            val fragment = sl.getAt(line.centerX) ?: continue
            val staffY = fragment.getCenterExtrapolated(line.centerX)
            if (abs(staffY - line.centerY) < unitSize) return true
        }
        return false
    }

    fun findStaffAnchors(staffLines: List<RotBox>, anchorSymbols: List<RotBox>, areClefs: Boolean = false): List<StaffAnchor> {
        val result = ArrayList<StaffAnchor>()
        for (center in anchorSymbols) {
            val adjacent = if (areClefs) listOf(
                center.moveToXHorizontalBy(-10), center, center.moveToXHorizontalBy(10),
                center.moveToXHorizontalBy(30), center.moveToXHorizontalBy(60), center.moveToXHorizontalBy(80),
            ) else listOf(
                center.moveToXHorizontalBy(-10), center.moveToXHorizontalBy(-5), center,
                center.moveToXHorizontalBy(5), center.moveToXHorizontalBy(10),
            )
            for (symbol in adjacent) {
                val estimatedUnitSize = pyRound(symbol.sizeH / (Constants.numberOfLinesOnAStaff - 1)).toInt()
                val thickened = symbol.makeBoxTaller(estimatedUnitSize)
                val overlapping = staffLines.filter { it.isIntersecting(thickened) }
                var connected = connectStaffLines(overlapping, estimatedUnitSize.toDouble())
                if (connected.size > Constants.numberOfLinesOnAStaff) {
                    connected = connected.filter { (it.maxX - it.minX) > Constants.isShortConnectedLine(estimatedUnitSize.toDouble()) }
                }
                if (connected.size != Constants.numberOfLinesOnAStaff) continue
                if (!areLinesParallel(connected, estimatedUnitSize.toDouble())) continue
                if (areLinesCrossing(connected)) continue
                if (!areClefs && !beginsOrEndsOnOneStaffLine(symbol, connected, estimatedUnitSize.toDouble())) continue
                result.add(StaffAnchor(connected, symbol))
            }
        }
        return result
    }

    // ------------------------------------------------------------------ pentagramas en bruto a partir de las anclas
    private fun getStaffForAnchor(anchor: StaffAnchor, staffs: List<RawStaff>): RawStaff? {
        for (staff in staffs) {
            for ((i, anchorLine) in anchor.staffLines.withIndex()) {
                val req = anchorLine.fragmentSet()
                if (staff.lines[i].fragmentSet().containsAll(req)) return staff
            }
        }
        return null
    }

    fun findRawStaffsByConnectingLineFragments(anchors: List<StaffAnchor>, staffFragments: List<RotBox>): List<RawStaff> {
        val staffs = ArrayList<RawStaff>()
        var staffId = 0
        for (anchor in anchors) {
            val existing = getStaffForAnchor(anchor, staffs)
            val fragments = staffFragments.filter { it.centerY >= anchor.zoneStart && it.centerY <= anchor.zoneStop }
            val connected = connectStaffLines(fragments, anchor.averageUnitSize)
            val staffLines = ArrayList<StaffLineSegment>()
            for (anchorLine in anchor.staffLines) {
                val req = anchorLine.fragmentSet()
                val matching = connected.filter { it.fragmentSet().containsAll(req) }
                if (matching.size == 1) staffLines.addAll(matching) else staffLines.add(anchorLine)
            }
            if (existing != null) {
                staffs.remove(existing)
                staffs.add(existing.merge(RawStaff.create(staffId, staffLines, listOf(anchor))))
            } else {
                staffs.add(RawStaff.create(staffId, staffLines, listOf(anchor)))
            }
            staffId++
        }
        return staffs
    }

    fun removeDuplicateStaffs(staffs: List<RawStaff>): List<RawStaff> {
        var result = ArrayList<RawStaff>()
        for (staff in staffs) {
            val overlapping = result.filter { staff.isOverlapping(it) }
            if (overlapping.isEmpty()) { result.add(staff); continue }
            if (overlapping.size >= 2) continue
            if (overlapping[0].anchors.size < staff.anchors.size) {
                result = ArrayList(result.filter { it !== overlapping[0] })
                result.add(staff)
            }
        }
        return result
    }

    // ------------------------------------------------------------------ remuestreo
    private fun resampleStaffSegment(anchor: StaffAnchor, staff: RawStaff, axis: List<Int>): List<StaffPoint> {
        val out = ArrayList<StaffPoint>()
        val x0 = anchor.symbol.centerX
        val lineFragments = anchor.staffLines.map { it.staffFragments[0] }
        val centers = lineFragments.map { it.getCenterExtrapolated(x0) }
        var previous = StaffPoint(x0, centers, lineFragments.sumOf { it.angle } / lineFragments.size)
        for (x in axis) {
            val lines = staff.lines.map { it.getAt(x.toDouble()) }
            val axisCenter = arrayOfNulls<Double>(lines.size)
            for ((i, l) in lines.withIndex()) axisCenter[i] = l?.getCenterExtrapolated(x.toDouble())
            val centerValues = axisCenter.filterNotNull()
            if (centerValues.isEmpty()) continue
            // np.diff de los valores presentes; los índices se aplican a axisCenter (igual que homr)
            for (i in 0 until centerValues.size - 1) {
                val delta = centerValues[i + 1] - centerValues[i]
                if (delta < 0.5 * anchor.averageUnitSize) { axisCenter[i] = null; if (i + 1 < axisCenter.size) axisCenter[i + 1] = null }
            }
            for ((i, prevY) in previous.y.withIndex()) {
                val cv = axisCenter.getOrNull(i)
                if (cv != null && abs(cv - prevY) > 0.5 * anchor.averageUnitSize) axisCenter[i] = null
            }
            var prevCenter = -1
            val order = (axisCenter.indices).toList() + (axisCenter.indices).toList().reversed()
            for (i in order) {
                if (axisCenter[i] != null) {
                    prevCenter = i
                } else if (prevCenter >= 0) {
                    val cv = axisCenter[prevCenter]
                    if (cv != null) axisCenter[i] = cv + anchor.averageUnitSize * (i - prevCenter)
                }
            }
            if (axisCenter.any { it == null }) continue
            val present = lines.filterNotNull()
            val angle = present.sumOf { it.angle } / present.size
            previous = StaffPoint(x.toDouble(), axisCenter.map { it!! }, angle)
            out.add(previous)
        }
        return out
    }

    private fun rangeList(start: Int, stop: Int, step: Int): List<Int> {
        val l = ArrayList<Int>()
        var v = start
        while (v < stop) { l.add(v); v += step }
        return l
    }

    private fun roundToDensity(x: Double, density: Int): Int = (pyRound(x / density) * density).toInt()

    fun resampleStaff(staff: RawStaff): Staff {
        val anchors = staff.anchors.sortedBy { it.symbol.centerX }
        val density = 10
        val start = floor(staff.minX / density) * density
        val stop = (floor(staff.maxX / density) + 1) * density
        val grid = ArrayList<StaffPoint>()
        var x = start
        for ((i, anchor) in anchors.withIndex()) {
            val toLeft = rangeList(roundToDensity(x, density), roundToDensity(anchor.symbol.centerX, density), density)
            val toRightStart: Int; val toRightStop: Int
            if (i < anchors.size - 1) {
                toRightStart = anchor.symbol.centerX.toInt()
                toRightStop = ((anchor.symbol.centerX + anchors[i + 1].symbol.centerX) / 2).toInt()
            } else {
                toRightStart = roundToDensity(anchor.symbol.centerX, density)
                toRightStop = roundToDensity(stop, density)
            }
            val toRight = rangeList(toRightStart, toRightStop, density)
            // el siguiente tramo parte del final del rango de la derecha (range.stop)
            x = toRightStop.toDouble()
            grid.addAll(resampleStaffSegment(anchor, staff, toLeft.reversed()).reversed())
            grid.addAll(resampleStaffSegment(anchor, staff, toRight))
        }
        return Staff(grid)
    }

    fun filterEdgeOfVision(staffs: List<Staff>, imageH: Int, imageW: Int): List<Staff> {
        if (staffs.isEmpty()) return staffs
        val usual = staffs.sumOf { it.maxX - it.minX } / staffs.size
        val result = ArrayList<Staff>()
        for (s in staffs) {
            if (s.maxY >= imageH || s.minY < 0) continue
            val w = s.maxX - s.minX
            val shorter = w < usual / 2
            val beyondLeft = s.minX < 0.01 * imageW
            val beyondRight = s.maxX > 0.99 * imageW
            if ((beyondLeft || beyondRight) && shorter) continue
            result.add(s)
        }
        return result
    }

    fun filterUnusualAnchors(anchors: List<StaffAnchor>): List<StaffAnchor> {
        if (anchors.isEmpty()) return anchors
        val sizes = anchors.map { it.averageUnitSize }
        val mean = sizes.sum() / sizes.size
        val std = sqrt(sizes.sumOf { (it - mean) * (it - mean) } / sizes.size)
        return anchors.filter { abs(it.averageUnitSize - mean) <= 3 * std }
    }

    /** Rangos de columnas alrededor de las claves, ya unidos si se solapan. */
    private fun initZone(clefAnchors: List<StaffAnchor>, imageW: Int): List<IntRange2> {
        val marginRight = 10
        val ranges = clefAnchors.map {
            IntRange2(maxOf(it.symbol.bottomLeftX.toInt(), 0), minOf((it.symbol.topRightX + marginRight).toInt(), imageW))
        }.sortedBy { it.start }
        val result = ArrayList<IntRange2>()
        for ((i, r) in ranges.withIndex()) {
            if (i == 0) result.add(r)
            else {
                if (r.start < result.last().stop) result[result.size - 1] = IntRange2(result.last().start, r.stop) else result.add(r)
            }
        }
        return result
    }

    class IntRange2(val start: Int, val stop: Int)

    /** Grupos de cinco líneas horizontales en una franja vertical de la máscara de pentagrama. */
    fun findHorizontalLines(staffMask: Mat, zone: IntRange2, unitSize: Double): List<List<Int>> {
        val h = staffMask.rows(); val w = staffMask.cols()
        val cols = zone.stop - zone.start
        if (cols <= 0) return emptyList()
        val data = ByteArray(h * w)
        staffMask.get(0, 0, data)
        val count = DoubleArray(h + 2)
        for (y in 0 until h) {
            var c = 0
            for (x in zone.start until zone.stop) if (data[y * w + x].toInt() != 0) c++
            count[y + 1] = c.toDouble()
        }
        val n = count.size
        val mean = count.sum() / n
        var varSum = 0.0
        for (v in count) varSum += (v - mean) * (v - mean)
        val std = sqrt(varSum / n)
        val norm = DoubleArray(n) { (count[it] - mean) / std }
        val centers = FindPeaks.findPeaks(norm, height = 0.0, distance = unitSize, prominence = 1.0).map { it - 1 }
        if (centers.size < 2) return emptyList()
        val groups = linePeakGroups(centers)
        val grouped = LinkedHashMap<Int, MutableList<Int>>()
        for ((i, c) in centers.withIndex()) grouped.getOrPut(groups[i]) { ArrayList() }.add(c)
        return grouped.values.filter { it.size == Constants.numberOfLinesOnAStaff }.map { it.sorted() }
    }

    private fun linePeakGroups(peaks: List<Int>, maxGapRatio: Double = 1.5): List<Int> {
        val gaps = (1 until peaks.size).map { (peaks[it] - peaks[it - 1]).toDouble() }.sorted()
        val count = maxOf(5, pyRound(peaks.size * 0.2).toInt())
        val take = gaps.take(count)
        val approxUnit = take.sum() / take.size
        val maxGap = approxUnit * maxGapRatio
        val ext = ArrayList<Double>()
        ext.add(peaks[0] - maxGap - 1)
        for (p in peaks) ext.add(p.toDouble())
        val groups = ArrayList<Int>()
        var group = -1
        for (i in 1 until ext.size) {
            if (ext[i] - ext[i - 1] > maxGap) group++
            groups.add(group)
        }
        return groups
    }

    fun predictOtherAnchorsFromClefs(clefAnchors: List<StaffAnchor>, staffMask: Mat): List<RotBox> {
        if (clefAnchors.isEmpty()) return emptyList()
        val avg = clefAnchors.sumOf { it.averageUnitSize } / clefAnchors.size
        val anchorSymbols = clefAnchors.map { it.symbol }
        val zones = initZone(clefAnchors, staffMask.cols())
        val result = ArrayList<RotBox>()
        for (zone in zones) {
            for (group in findHorizontalLines(staffMask, zone, avg)) {
                val minY = group.min(); val maxY = group.max()
                val centerY = (minY + maxY) / 2.0
                val centerX = zone.start + (zone.stop - zone.start) / 2.0
                val box = RRect(centerX.toInt().toDouble(), centerY.toInt().toDouble(), (zone.stop - zone.start).toDouble(), (maxY - minY).toDouble(), 0.0)
                result.add(RotBox(box, Contour(IntArray(0)), 0))
            }
        }
        return result.filter { !it.isOverlappingWithAny(anchorSymbols) }
    }

    fun detectStaff(staffMask: Mat, staffFragments: List<RotBox>, clefsKeys: List<RotBox>, likelyBarOrRestLines: List<RotBox>): List<Staff> {
        val anchors = ArrayList(findStaffAnchors(staffFragments, clefsKeys, areClefs = true))
        val possibleOtherClefs = predictOtherAnchorsFromClefs(anchors, staffMask)
        anchors.addAll(findStaffAnchors(staffFragments, possibleOtherClefs, areClefs = true))
        anchors.addAll(findStaffAnchors(staffFragments, likelyBarOrRestLines, areClefs = false))
        val filtered = filterUnusualAnchors(anchors)
        val rawWithDuplicates = findRawStaffsByConnectingLineFragments(filtered, staffFragments)
        val raw = removeDuplicateStaffs(rawWithDuplicates)
        var staffs = raw.mapNotNull { r -> runCatching { resampleStaff(r) }.getOrNull() }
        staffs = filterEdgeOfVision(staffs, staffMask.rows(), staffMask.cols())
        return staffs.sortedBy { it.minY }
    }
}
