package com.forxa.pianocascada.omr

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

/* Pentagramas: traducción de homr/model.py (solo la parte que usa el lector). */

fun medianOf(values: List<Double>): Double {
    val s = values.sorted()
    val n = s.size
    return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
}

/** Python round(): al par más cercano. */
fun pyRound(x: Double): Long = Math.rint(x).toLong()

class StaffPoint(val x: Double, val y: List<Double>, val angle: Double) {
    val averageUnitSize: Double

    init {
        if (y.size % Constants.numberOfLinesOnAStaff != 0) throw IllegalStateException("A staff must consist of 5, 10, ... lines")
        var sum = 0.0
        for (i in 1 until y.size) sum += y[i] - y[i - 1]
        averageUnitSize = if (y.size > 1) sum / (y.size - 1) else Double.NaN
    }

    fun merge(other: StaffPoint): StaffPoint {
        if (abs(x - other.x) > 1e-3) throw IllegalArgumentException("Can't merge points at different positions")
        val ys = (y + other.y).sorted()
        return StaffPoint(x, ys, (angle + other.angle) / 2)
    }

    fun transformCoordinates(t: (Double, Double) -> Pair<Double, Double>): StaffPoint {
        val xy = y.map { t(x, it) }
        val averageX = xy.sumOf { it.first } / xy.size
        return StaffPoint(averageX, xy.map { it.second }, angle)
    }

    fun toAxisBox(): AxisBox = AxisBox(x.toInt(), y.first().toInt(), x.toInt(), y.last().toInt(), Contour(IntArray(0)), -2)
}

class Staff(val grid: List<StaffPoint>) {
    val minX: Double = grid.first().x
    val maxX: Double = grid.last().x
    val minY: Double = grid.minOf { p -> p.y.min() }
    val maxY: Double = grid.maxOf { p -> p.y.max() }
    val averageUnitSize: Double = medianOf(grid.map { it.averageUnitSize })
    var isGrandstaff = false

    fun merge(other: Staff): Staff {
        val a = HashMap<Long, StaffPoint>(); for (p in grid) a[pyRound(p.x)] = p
        val b = HashMap<Long, StaffPoint>(); for (p in other.grid) b[pyRound(p.x)] = p
        val xs = a.keys.filter { it in b }.sorted()
        val result = Staff(xs.map { a[it]!!.merge(b[it]!!) })
        result.isGrandstaff = true
        return result
    }

    fun getAt(x: Double): StaffPoint? {
        var best = grid[0]
        var bd = abs(best.x - x)
        for (i in 1 until grid.size) {
            val d = abs(grid[i].x - x)
            if (d < bd) { bd = d; best = grid[i] }
        }
        return if (bd > Constants.staffPositionTolerance) null else best
    }

    fun yDistanceTo(px: Double, py: Double): Double {
        val sp = getAt(px) ?: return 1e10
        return sp.y.minOf { abs(it - py) }
    }

    fun transformCoordinates(t: (Double, Double) -> Pair<Double, Double>): Staff {
        val copy = Staff(grid.map { it.transformCoordinates(t) })
        copy.isGrandstaff = isGrandstaff
        return copy
    }
}

/** Un sistema: un pentagrama solo, un piano (llave) o varias voces. */
class MultiStaff(staffs: List<Staff>, val connections: List<RotBox>) {
    val staffs: List<Staff> = staffs.sortedBy { it.minY }

    fun merge(other: MultiStaff): MultiStaff {
        val us = ArrayList<Staff>()
        for (s in staffs + other.staffs) if (us.none { it === s }) us.add(s)
        val uc = ArrayList<RotBox>()
        for (c in connections + other.connections) if (uc.none { it == c }) uc.add(c)
        return MultiStaff(us, uc)
    }

    private fun scoreBrace(symbol: RotBox, upper: Staff, lower: Staff): Double {
        val unitSize = medianOf(listOf(upper.averageUnitSize, lower.averageUnitSize))
        val xDistanceThreshold = Constants.grandstaffXDistanceThresholdFactor * unitSize
        val yOverlapThreshold = Constants.grandstaffYOverlapThresholdFactor * symbol.sizeH
        val symbolMinX = symbol.centerX
        val staffMinX = min(upper.minX, lower.minX)
        val xDistance = abs(staffMinX - symbolMinX)
        val symbolMinY = symbol.centerY - symbol.sizeH / 2
        val symbolMaxY = symbol.centerY + symbol.sizeH / 2
        val yOverlap = min(symbolMaxY, lower.maxY) - max(symbolMinY, upper.minY)
        return if (xDistance < xDistanceThreshold && yOverlap > yOverlapThreshold && yOverlap > xDistance) yOverlap - xDistance else 0.0
    }

    private class Pair2(val a: Int, val b: Int, val score: Double)

    private fun selectGrandstaffs(braceDot: List<RotBox>): List<Pair2> {
        val pairScores = ArrayList<Pair2>()
        for (i in 0 until staffs.size - 1) {
            var best = 0.0
            var first = true
            for (symbol in braceDot) {
                val sc = scoreBrace(symbol, staffs[i], staffs[i + 1])
                if (first || sc > best) { best = sc; first = false }
            }
            if (best > 0) pairScores.add(Pair2(i, i + 1, best))
        }
        val result = ArrayList<Pair2>()
        val used = HashSet<Int>()
        for (p in pairScores.sortedByDescending { it.score }) {
            if (p.a in used || p.b in used) continue
            result.add(p)
            used.add(p.a); used.add(p.b)
        }
        return result
    }

    private fun mergeSelectedPairs(pairs: List<Pair2>): List<Staff> {
        val result = ArrayList<Staff>()
        var i = 0
        while (i < staffs.size) {
            if (pairs.any { it.a == i || it.b == i }) {
                result.add(staffs[i].merge(staffs[i + 1]))
                i += 2
                continue
            }
            result.add(staffs[i])
            i += 1
        }
        return result
    }

    fun createGrandstaffs(braceDot: List<RotBox>): MultiStaff {
        if (staffs.size < 2) return this
        val pairs = selectGrandstaffs(braceDot)
        if (pairs.isEmpty()) return this
        return MultiStaff(mergeSelectedPairs(pairs), connections)
    }

    fun breakApart(): List<MultiStaff> = staffs.map { MultiStaff(listOf(it), emptyList()) }
}
