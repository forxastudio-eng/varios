package com.forxa.pianocascada.piano

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.forxa.pianocascada.audio.PianoAudio
import com.forxa.pianocascada.core.AdaptiveStrength
import com.forxa.pianocascada.core.Feedback
import com.forxa.pianocascada.core.Hand
import com.forxa.pianocascada.core.KeyboardPlanner
import com.forxa.pianocascada.core.NoteResult
import com.forxa.pianocascada.core.Notes
import com.forxa.pianocascada.core.PracticeMode
import com.forxa.pianocascada.core.PracticeSession
import com.forxa.pianocascada.core.Sequencer
import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.core.SongNote
import com.forxa.pianocascada.core.VelocityMapper
import com.forxa.pianocascada.data.ColorScheme
import com.forxa.pianocascada.data.Settings
import kotlin.math.abs
import kotlin.math.max

enum class HandsMode { BOTH, RIGHT, LEFT }

data class PracticeResult(val accuracy: Float, val stars: Int, val wrong: Int, val bestCombo: Int, val perfect: Int, val total: Int)

/**
 * Une el motor de audio, la lógica de práctica y la vista. Se actualiza una vez por fotograma desde la vista.
 * Con [song] = null funciona como piano libre.
 */
class PracticeController(
    private val audio: PianoAudio,
    private var settings: Settings,
    val song: Song?,
    level: Int = 3,
    hands: HandsMode = HandsMode.BOTH,
    mode: PracticeMode = PracticeMode.WAIT,
    speed: Float = 0.7f,
    startBar: Int = 0,
) : FrameSource, KeyListener {

    // ---- estado visible desde Compose
    var playing by mutableStateOf(false); private set
    var waiting by mutableStateOf(false); private set
    var finished by mutableStateOf(false); private set
    var progress by mutableStateOf(0f); private set
    var barText by mutableStateOf(""); private set
    var loopOn by mutableStateOf(false); private set
    var speed by mutableStateOf(speed); private set
    var mode by mutableStateOf(mode); private set
    var hands by mutableStateOf(hands); private set
    var level by mutableStateOf(level); private set
    var combo by mutableStateOf(0); private set
    var accuracy by mutableStateOf(0f); private set
    var result by mutableStateOf<PracticeResult?>(null); private set
    var pedal by mutableStateOf(false); private set

    // ---- FrameSource
    override var posMs: Double = 0.0
        private set
    override var songNotes: Array<DispNote> = emptyArray()
        private set
    override var barTimesMs: DoubleArray = DoubleArray(0)
        private set
    override val beatMs: Double = song?.beatToMs(song.pulse) ?: 500.0
    override var pendingPitches: IntArray = IntArray(0)
        private set
    override var followLeft: Float = 23f
        private set

    var viewHook: PianoSurfaceView? = null
    var onFinished: ((PracticeResult) -> Unit)? = null

    private var notesAll: List<SongNote> = emptyList()
    private var session: PracticeSession = PracticeSession(emptyList(), 500.0, PracticeMode.LISTEN)
    private var sessionFromMs = 0.0
    private var sessionToMs = Double.MAX_VALUE
    private val adaptive = settings.keyboard.let { if (it.calibMax > it.calibMin + 1f) AdaptiveStrength(it.calibMin, it.calibMax) else AdaptiveStrength() }
    private var mapper = buildMapper()
    private var lastGate = -1.0
    private var lastPos = 0.0
    private var lastUiNs = 0L
    private var loopStartMs = 0.0
    private var loopEndMs = 0.0
    private var lastBar = -1
    private var releasedFinish = false

    init {
        if (song != null) {
            barTimesMs = DoubleArray(song.barStarts.size) { song.beatToMs(song.barStarts[it]) }
            reconfigure(startBar)
        } else {
            posMs = 0.0
        }
    }

    private fun buildMapper(): VelocityMapper {
        val k = settings.keyboard
        return VelocityMapper(k.curve, k.fixedVelocity, k.zoneLow, k.zoneMid, k.zoneHigh)
    }

    fun updateSettings(s: Settings) {
        settings = s
        mapper = buildMapper()
        applyHandGains()
    }

    private fun userHands(): Set<Hand> = when {
        mode == PracticeMode.LISTEN -> emptySet()
        hands == HandsMode.RIGHT -> setOf(Hand.RIGHT)
        hands == HandsMode.LEFT -> setOf(Hand.LEFT)
        else -> setOf(Hand.RIGHT, Hand.LEFT)
    }

    private fun applyHandGains() {
        val u = userHands()
        val backing = settings.keyboard.backing
        audio.handGain(0, if (Hand.RIGHT in u) 0f else if (mode == PracticeMode.LISTEN) 1f else backing.coerceAtLeast(0f))
        audio.handGain(1, if (Hand.LEFT in u) 0f else if (mode == PracticeMode.LISTEN) 1f else backing.coerceAtLeast(0f))
        // si el usuario toca una sola mano, la otra suena a volumen de acompañamiento; si toca ambas, no hay acompañamiento
        if (mode != PracticeMode.LISTEN && u.size == 2) { audio.handGain(0, 0f); audio.handGain(1, 0f) }
    }

    /** (Re)carga la canción en el motor con la configuración actual y empieza a esperar en [startBar]. */
    fun reconfigure(startBar: Int = currentBar()) {
        val s = song ?: return
        notesAll = s.notes(level)
        audio.play(false)
        audio.allOff()
        audio.load(Sequencer.build(s, level))
        audio.setSpeed(speed)
        audio.loop(0.0, 0.0, false)
        loopOn = false
        applyHandGains()
        val ms = Sequencer.barStartMs(s, startBar)
        audio.seek(ms)
        posMs = ms
        buildSession(ms, Double.MAX_VALUE)
        finished = false; result = null; releasedFinish = false
        playing = false
        lastGate = -1.0
        // ventana inicial del teclado reducido
        followLeft = initialLeft()
        updateUi(force = true)
    }

    private fun initialLeft(): Float {
        val s = song ?: return 23f
        val span = settings.keyboard.reducedSpan
        val all = notesAll
        if (all.isEmpty()) return KeyboardPlanner.reducedLeft(60, 72, span)
        val mn = all.minOf { it.pitch }
        val mx = all.maxOf { it.pitch }
        val whiteSpan = Notes.whiteIndex(Notes.whiteAtOrAbove(mx)) - Notes.whiteIndex(Notes.whiteAtOrBelow(mn)) + 1
        if (whiteSpan <= span) return KeyboardPlanner.reducedLeft(mn, mx, span)
        val firstPitches = all.take(12).map { it.pitch }
        return KeyboardPlanner.reducedLeft(firstPitches.min(), firstPitches.max(), span).also { @Suppress("UNUSED_EXPRESSION") s }
    }

    private fun buildSession(fromMs: Double, toMs: Double) {
        val s = song ?: return
        sessionFromMs = fromMs; sessionToMs = toMs
        val u = userHands()
        val msPerBeat = 60000.0 / s.bpm
        val userNotes = notesAll.filter { it.hand in u && s.beatToMs(it.start) >= fromMs - 1.0 && s.beatToMs(it.start) < toMs }
        val tol = 120.0 * speed
        session = PracticeSession(userNotes, msPerBeat, mode, tolMs = max(60.0, tol), earlyAcceptMs = 450.0 * max(0.5f, speed))
        rebuildDisp()
        lastGate = -1.0
    }

    private fun rebuildDisp() {
        val s = song ?: return
        val idx = HashMap<Long, Int>()
        for (n in session.notes) idx[key(n.pitch, n.startMs)] = n.index
        val u = userHands()
        songNotes = notesAll.map { n ->
            val st = s.beatToMs(n.start)
            val sess = idx[key(n.pitch, st)] ?: -1
            DispNote(st, s.beatToMs(n.dur), n.pitch, n.hand, sess, auto = n.hand !in u)
        }.toTypedArray()
    }

    private fun key(pitch: Int, ms: Double): Long = (pitch.toLong() shl 40) xor (ms * 10.0).toLong()

    // ------------------------------------------------------------------ transporte
    fun play() {
        if (song == null) return
        if (finished) restart()
        audio.play(true)
        playing = true
    }

    fun pause() {
        audio.play(false)
        playing = false
    }

    fun togglePlay() { if (playing) pause() else play() }

    fun restart() {
        val s = song ?: return
        audio.play(false)
        audio.allOff()
        audio.seek(0.0)
        posMs = 0.0
        buildSession(0.0, Double.MAX_VALUE)
        finished = false; result = null; releasedFinish = false
        loopOn = false
        audio.loop(0.0, 0.0, false)
        playing = false
        updateUi(force = true)
        @Suppress("UNUSED_EXPRESSION") s
    }

    fun seekToFraction(f: Float) {
        val s = song ?: return
        val ms = (s.totalMs * f.coerceIn(0f, 1f))
        // al compás más cercano hacia atrás
        val bar = s.barIndexAtBeat((ms * s.bpm / 60000.0).toFloat())
        seekBar(bar)
    }

    fun seekBar(bar: Int) {
        val s = song ?: return
        val ms = Sequencer.barStartMs(s, bar)
        audio.seek(ms)
        posMs = ms
        buildSession(ms, if (loopOn) loopEndMs else Double.MAX_VALUE)
        finished = false; result = null; releasedFinish = false
        updateUi(force = true)
    }

    fun setSpeed(v: Float) {
        speed = v
        audio.setSpeed(v)
        // la tolerancia en tiempo de canción depende de la velocidad
        buildSession(posMs.coerceAtLeast(0.0), if (loopOn) loopEndMs else Double.MAX_VALUE)
    }

    fun setMode(m: PracticeMode) { mode = m; reconfigure(currentBar()) }
    fun setHands(h: HandsMode) { hands = h; reconfigure(currentBar()) }
    fun setLevel(l: Int) { level = l; reconfigure(currentBar()) }

    fun currentBar(): Int {
        val s = song ?: return 0
        return s.barIndexAtBeat((posMs * s.bpm / 60000.0).toFloat())
    }

    /** Repite el tramo de 4 compases donde estás (activa/desactiva). */
    fun toggleLoop() {
        val s = song ?: return
        if (loopOn) {
            loopOn = false
            audio.loop(0.0, 0.0, false)
            buildSession(posMs, Double.MAX_VALUE)
        } else {
            val bar = currentBar()
            loopStartMs = Sequencer.barStartMs(s, bar)
            loopEndMs = Sequencer.barEndMs(s, bar, 4)
            loopOn = true
            audio.loop(loopStartMs, loopEndMs, true)
            audio.seek(loopStartMs)
            posMs = loopStartMs
            buildSession(loopStartMs, loopEndMs)
        }
        updateUi(force = true)
    }

    fun setPedal(down: Boolean) {
        pedal = down
        audio.pedal(down)
    }

    // ------------------------------------------------------------------ cada fotograma
    override fun onFrame(nowNs: Long) {
        val s = song ?: return
        var p = audio.heardPositionMs()
        if (mode == PracticeMode.WAIT && playing) p = minOf(p, session.gateMs)
        if (loopOn && p < lastPos - 300.0) {
            // el bucle ha vuelto al principio: nuevo intento del tramo
            buildSession(loopStartMs, loopEndMs)
        }
        lastPos = p
        posMs = p
        session.update(p)
        if (mode == PracticeMode.WAIT) {
            val g = session.gateMs
            if (g != lastGate) { audio.gate(g); lastGate = g }
            val pend = session.pendingPitches()
            if (pend.size != pendingPitches.size || pend.indices.any { pend[it] != pendingPitches[it] }) pendingPitches = pend.toIntArray()
        } else if (pendingPitches.isNotEmpty()) pendingPitches = IntArray(0)

        // seguimiento del teclado reducido
        val ups = ArrayList<Int>(8)
        for (n in songNotes) {
            if (n.startMs > p + 2500.0) break
            if (n.startMs + n.durMs < p) continue
            ups.add(n.pitch)
        }
        if (ups.isNotEmpty()) followLeft = KeyboardPlanner.followLeft(ups, followLeft, settings.keyboard.reducedSpan)

        // fin de la canción
        if (!finished && !loopOn) {
            val done = (mode != PracticeMode.LISTEN && session.isFinished() && session.total > 0 && p > lastNoteEnd() - 1.0) ||
                p >= s.totalMs + 200.0
            if (done && playing) finish()
        }
        if (nowNs - lastUiNs > 90_000_000L) updateUi(false)
        lastUiNs = if (nowNs - lastUiNs > 90_000_000L) nowNs else lastUiNs
    }

    private fun lastNoteEnd(): Double = songNotes.maxOfOrNull { if (it.sessionIdx >= 0) it.startMs else 0.0 } ?: 0.0

    private fun finish() {
        playing = false
        finished = true
        audio.play(false)
        val r = PracticeResult(session.accuracy(), session.stars(), session.wrongPresses, session.bestCombo, session.perfect, session.total)
        result = r
        if (!releasedFinish) { releasedFinish = true; if (mode != PracticeMode.LISTEN) onFinished?.invoke(r) }
    }

    private fun updateUi(force: Boolean) {
        val s = song ?: return
        val f = (posMs / max(1.0, s.totalMs)).toFloat().coerceIn(0f, 1f)
        if (force || abs(f - progress) > 0.002f) progress = f
        val b = currentBar()
        if (force || b != lastBar) {
            lastBar = b
            barText = "Compás ${s.barLabel(b)} de ${s.barLabel(s.bars - 1)}"
        }
        val w = audio.isWaiting && mode == PracticeMode.WAIT
        if (w != waiting) waiting = w
        if (session.combo != combo) combo = session.combo
        val a = if (mode == PracticeMode.LISTEN) 0f else session.accuracy()
        if (abs(a - accuracy) > 0.005f) accuracy = a
    }

    override fun resultOf(sessionIdx: Int): Int {
        val r = session.result[sessionIdx.coerceIn(0, max(0, session.result.size - 1))]
        return when (r) {
            NoteResult.PENDING -> 0
            NoteResult.MISSED -> 3
            NoteResult.HIT -> {
                if (session.isHolding(sessionIdx)) 1
                else if (session.heldRatio[sessionIdx] in 0f..0.8f) 4 else 2
            }
        }
    }

    // ------------------------------------------------------------------ entradas
    override fun onKeyDown(pitch: Int, sizeMm: Float): Int {
        val strength = adaptive.observe(sizeMm)
        val vel = mapper.velocity(strength, pitch)
        audio.noteOn(pitch, vel)
        if (song == null || mode == PracticeMode.LISTEN) return 0
        val ev = session.onKeyDown(pitch, posMs)
        return when (ev.feedback) {
            Feedback.CORRECT -> {
                if (mode == PracticeMode.WAIT) {
                    val g = session.gateMs
                    if (g != lastGate) { audio.gate(g); lastGate = g }
                }
                val hand = session.notes[ev.noteIndex].hand
                Palette.note(pitch, hand, settings.display.colors)
            }
            Feedback.WRONG -> Palette.MISS
            else -> 0
        }
    }

    override fun onKeyUp(pitch: Int) {
        audio.noteOff(pitch)
        if (song != null) session.onKeyUp(pitch, posMs)
    }

    override fun onBackgroundTap() { backgroundTap?.invoke() }
    var backgroundTap: (() -> Unit)? = null

    fun dispose() {
        if (song == null) {            // piano libre / pruebas: no tocar el estado del secuenciador
            audio.allOff()
            audio.pedal(false)
            return
        }
        audio.play(false)
        audio.allOff()
        audio.pedal(false)
        audio.loop(0.0, 0.0, false)
        audio.gate(1e18)
    }
}
