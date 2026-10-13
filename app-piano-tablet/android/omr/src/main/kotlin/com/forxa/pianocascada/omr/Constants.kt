package com.forxa.pianocascada.omr

/** Constantes de homr/constants.py */
object Constants {
    const val numberOfLinesOnAStaff = 5
    const val maxNumberOfLedgerLines = 4

    fun toleranceForStaffLineDetection(unitSize: Double) = unitSize / 3
    fun maxLineGapSize(unitSize: Double) = 5 * unitSize
    fun isShortLine(unitSize: Double) = unitSize / 5
    fun isShortConnectedLine(unitSize: Double) = 2 * unitSize
    fun minHeightForBraceRough(unitSize: Double) = 2 * unitSize
    fun maxWidthForBraceRough(unitSize: Double) = 3 * unitSize
    fun minHeightForBrace(unitSize: Double) = 4 * unitSize
    fun toleranceForTouchingClefs(unitSize: Double): Int = Math.rint(unitSize * 2).toInt()
    @Suppress("UNUSED_PARAMETER") fun toleranceForStaffAtAnyPoint(unitSize: Double) = 0
    fun barLineMaxWidth(unitSize: Double) = 2 * unitSize
    fun barLineMinHeight(unitSize: Double) = 3 * unitSize
    fun blackSpotRemovalThreshold(unitSize: Double) = 2 * unitSize

    const val staffLineSegmentXTolerance = 10
    const val minimumConnectionsToFormCombinedStaff = 1
    const val imageNoiseLimit = 50
    const val staffPositionTolerance = 50
    const val maxAngleForLinesToBeParallel = 10

    const val grandstaffXDistanceThresholdFactor = 5
    const val grandstaffYOverlapThresholdFactor = 0.5
}
