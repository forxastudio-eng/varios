package com.forxa.pianocascada.audio

/** Puente JNI con el motor de audio en C++ (libpianoengine.so). */
object NativeEngine {
    init { System.loadLibrary("pianoengine") }

    external fun nCreate(): Boolean
    external fun nDestroy()
    external fun nLoadBank(bytes: ByteArray): Boolean
    external fun nStart(): Boolean
    external fun nStop()
    external fun nNeedsRestart(): Boolean
    external fun nUpdateLatency()
    external fun nSampleRate(): Int

    external fun nNoteOn(key: Int, vel: Int)
    external fun nNoteOff(key: Int)
    external fun nPedal(down: Boolean)
    external fun nAllOff()
    external fun nSetParam(id: Int, value: Float)

    external fun nLoadSeq(keys: IntArray, vels: IntArray, hands: IntArray, starts: DoubleArray, durs: DoubleArray, n: Int)
    external fun nSeqPlay(play: Boolean)
    external fun nSeqSeek(ms: Double)
    external fun nSeqSpeed(speed: Float)
    external fun nSeqLoop(startMs: Double, endMs: Double, enabled: Boolean)
    external fun nSeqGate(ms: Double)
    external fun nHandGain(hand: Int, gain: Float)
    external fun nSeqStopNotes()

    external fun nPositionMs(): Double
    external fun nStampNs(): Long
    external fun nPlaying(): Boolean
    external fun nWaiting(): Boolean
    external fun nLatencyMs(): Float
    external fun nActiveVoices(): Int
}
