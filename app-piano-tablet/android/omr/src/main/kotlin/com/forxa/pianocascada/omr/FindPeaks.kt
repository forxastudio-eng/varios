package com.forxa.pianocascada.omr

import kotlin.math.abs

/** Picos de una señal 1D sin scipy (homr/find_peaks.py). */
object FindPeaks {
    fun findPeaks(x: DoubleArray, height: Double? = null, distance: Double? = null, prominence: Double? = null): IntArray {
        if (x.size < 3) return IntArray(0)
        val peaksList = ArrayList<Int>()
        var i = 1
        while (i < x.size - 1) {
            if (x[i] > x[i - 1]) {
                var j = i
                while (j < x.size - 1 && x[j] == x[j + 1]) j++
                if (j < x.size - 1 && x[j] > x[j + 1]) {
                    peaksList.add((i + j) / 2)
                    i = j + 1
                } else {
                    i = j + 1
                }
            } else if (x[i] == x[i - 1]) {
                var j = i
                while (j < x.size - 1 && x[j] == x[j + 1]) j++
                if (j < x.size - 1 && x[j] > x[j + 1]) peaksList.add((i + j) / 2)
                i = j + 1
            } else {
                i++
            }
        }
        var peaks = peaksList.toIntArray()
        if (peaks.isEmpty()) return IntArray(0)
        if (height != null) peaks = peaks.filter { x[it] >= height }.toIntArray()
        if (peaks.isEmpty()) return IntArray(0)
        if (prominence != null) {
            val valid = ArrayList<Int>()
            for (peak in peaks) {
                var leftMin = x[peak]
                for (k in peak - 1 downTo 0) {
                    if (x[k] > x[peak]) break
                    leftMin = minOf(leftMin, x[k])
                }
                var rightMin = x[peak]
                for (k in peak + 1 until x.size) {
                    if (x[k] > x[peak]) break
                    rightMin = minOf(rightMin, x[k])
                }
                if (x[peak] - maxOf(leftMin, rightMin) >= prominence) valid.add(peak)
            }
            peaks = valid.toIntArray()
        }
        if (peaks.isEmpty()) return IntArray(0)
        if (distance != null && peaks.size > 1) {
            // np.argsort ascendente y luego [::-1]: a igual altura va primero el de índice mayor
            val order = peaks.indices.sortedWith(compareBy<Int> { x[peaks[it]] }.thenBy { it }).reversed()
            val keep = ArrayList<Int>()
            for (oi in order) {
                val peak = peaks[oi]
                if (keep.isEmpty() || keep.all { abs(it - peak) >= distance }) keep.add(peak)
            }
            peaks = keep.sorted().toIntArray()
        }
        return peaks
    }
}
