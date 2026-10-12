package com.forxa.pianocascada.transcribe

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

private class Truth(val pitch: Int, val start: Double, val end: Double, val right: Boolean)
private class Info(val bpm: Int, val key: String, val meter: String)
private class Score(val p: Double, val r: Double, val f: Double, val offF: Double, val bias: Double)

private fun readWav(f: File): Pair<FloatArray, Int> {
    val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
    var pos = 12
    var channels = 2; var rate = 44100; var data: ByteBuffer? = null
    while (pos + 8 <= b.capacity()) {
        val id = String(ByteArray(4) { b.get(pos + it) })
        val size = b.getInt(pos + 4)
        if (id == "fmt ") { channels = b.getShort(pos + 10).toInt(); rate = b.getInt(pos + 12) }
        if (id == "data") { data = b.duplicate().order(ByteOrder.LITTLE_ENDIAN).also { it.position(pos + 8); it.limit(minOf(b.capacity(), pos + 8 + size)) }; break }
        pos += 8 + size + (size and 1)
    }
    val d = data!!
    val n = d.remaining() / 2 / channels
    val out = FloatArray(n)
    for (i in 0 until n) { var s = 0f; for (c in 0 until channels) s += d.getShort() / 32768f; out[i] = s / channels }
    return out to rate
}

private fun readTruth(f: File): Pair<List<Truth>, Info> {
    val lines = f.readLines()
    val h = lines.first().removePrefix("# ").split(" ").associate { it.substringBefore("=") to it.substringAfter("=") }
    val notes = lines.drop(1).filter { it.isNotBlank() }.map { l -> val p = l.split(","); Truth(p[0].toInt(), p[1].toDouble(), p[2].toDouble(), p[3] == "R") }
    return notes to Info(h["bpm"]!!.toInt(), h["tonalidad"]!!, h["compas"]!!)
}

/** Empareja notas (misma tecla, inicio dentro de ±tol) y calcula precisión, exhaustividad y F1. */
private fun score(est: List<RawNote>, truth: List<Truth>, tol: Double = 0.05): Pair<Score, List<Pair<RawNote, Truth>>> {
    val used = BooleanArray(est.size)
    val matches = ArrayList<Pair<RawNote, Truth>>()
    var offOk = 0
    val diffs = ArrayList<Double>()
    val byPitch = est.indices.groupBy { est[it].pitch }
    for (t in truth.sortedBy { it.start }) {
        var best = -1; var bd = Double.MAX_VALUE
        for (i in byPitch[t.pitch] ?: emptyList()) {
            if (used[i]) continue
            val d = abs(est[i].start - t.start)
            if (d <= tol && d < bd) { bd = d; best = i }
        }
        if (best >= 0) {
            used[best] = true; matches.add(est[best] to t); diffs.add(est[best].start - t.start)
            if (abs(est[best].end - t.end) <= max(0.05, 0.2 * (t.end - t.start))) offOk++
        }
    }
    val tp = matches.size.toDouble()
    val p = if (est.isEmpty()) 0.0 else tp / est.size
    val r = if (truth.isEmpty()) 0.0 else tp / truth.size
    val f = if (p + r == 0.0) 0.0 else 2 * p * r / (p + r)
    val po = if (est.isEmpty()) 0.0 else offOk / est.size.toDouble()
    val ro = if (truth.isEmpty()) 0.0 else offOk / truth.size.toDouble()
    val fo = if (po + ro == 0.0) 0.0 else 2 * po * ro / (po + ro)
    val bias = if (diffs.isEmpty()) 0.0 else diffs.sorted()[diffs.size / 2]
    return Score(p, r, f, fo, bias) to matches
}

private fun cached(model: BasicPitch, f: File, cacheDir: File): ModelOutput {
    val c = File(cacheDir, f.name + ".out")
    if (c.exists()) {
        val b = ByteBuffer.wrap(c.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val n = b.getInt()
        val note = FloatArray(n * ModelOutput.BINS); val on = FloatArray(n * ModelOutput.BINS)
        b.asFloatBuffer().get(note); b.position(b.position() + note.size * 4); b.asFloatBuffer().get(on)
        return ModelOutput(n, note, on)
    }
    val (audio, rate) = readWav(f)
    val out = model.run(Resampler.resample(audio, rate, BasicPitch.SAMPLE_RATE))
    val b = ByteBuffer.allocate(4 + out.note.size * 8).order(ByteOrder.LITTLE_ENDIAN)
    b.putInt(out.frames); b.asFloatBuffer().put(out.note); b.position(b.position() + out.note.size * 4); b.asFloatBuffer().put(out.onset)
    cacheDir.mkdirs(); c.writeBytes(b.array())
    return out
}

/** Clasifica los falsos positivos y las notas perdidas para saber qué corregir. */
private fun diagnose(est: List<RawNote>, truth: List<Truth>, tol: Double = 0.05, fp: MutableMap<String, Int>, fn: MutableMap<String, Int>) {
    val (_, matches) = score(est, truth, tol)
    val matchedEst = matches.map { it.first }.toSet()
    val matchedTruth = matches.map { it.second }.toSet()
    fun overlaps(a0: Double, a1: Double, b0: Double, b1: Double) = minOf(a1, b1) - maxOf(a0, b0) > 0.03
    for (e in est) {
        if (e in matchedEst) continue
        val key = when {
            truth.any { it.pitch == e.pitch && overlaps(e.start, e.end, it.start, it.end) } -> {
                val t = truth.first { it.pitch == e.pitch && overlaps(e.start, e.end, it.start, it.end) }
                if (est.any { it !== e && it in matchedEst && it.pitch == e.pitch && overlaps(e.start, e.end, it.start, it.end) }) "fragmento/duplicado de una nota buena"
                else if (abs(e.start - t.start) > tol) "nota real con ataque desplazado (>50 ms)" else "nota real sin emparejar"
            }
            truth.any { abs(it.pitch - e.pitch) == 12 && overlaps(e.start, e.end, it.start, it.end) } -> "octava de una nota real"
            truth.any { (abs(it.pitch - e.pitch) == 19 || abs(it.pitch - e.pitch) == 24 || abs(it.pitch - e.pitch) == 7) && overlaps(e.start, e.end, it.start, it.end) } -> "quinta/doble octava de una nota real"
            truth.any { abs(it.pitch - e.pitch) <= 2 && overlaps(e.start, e.end, it.start, it.end) } -> "vecina (±1–2 semitonos)"
            truth.any { overlaps(e.start, e.end, it.start, it.end) } -> "otra altura mientras suena algo"
            else -> "sin ninguna nota real cerca (ruido/cola)"
        }
        fp[key] = (fp[key] ?: 0) + 1
    }
    for (t in truth) {
        if (t in matchedTruth) continue
        val same = est.filter { it.pitch == t.pitch && overlaps(it.start, it.end, t.start, t.end) }
        val key = when {
            same.isEmpty() -> if (est.any { abs(it.pitch - t.pitch) == 12 && overlaps(it.start, it.end, t.start, t.end) }) "perdida: solo se detectó su octava" else "perdida: no detectada"
            same.any { it.start < t.start - tol && it.end > t.end - 0.05 } -> "perdida: fundida con la nota anterior"
            else -> "perdida: ataque desplazado (>50 ms)"
        }
        fn[key] = (fn[key] ?: 0) + 1
    }
}

private val relative = mapOf("C" to "Am", "G" to "Em", "D" to "Bm", "A" to "F#m", "E" to "C#m", "B" to "G#m", "F" to "Dm", "Bb" to "Gm", "Eb" to "Cm", "Ab" to "Fm", "Db" to "Bbm")

fun main() {
    val dir = File(System.getenv("EVAL_DIR") ?: error("falta EVAL_DIR"))
    val model = BasicPitch(File(System.getenv("EVAL_MODEL") ?: error("falta EVAL_MODEL")).readBytes())
    val tags = (System.getenv("EVAL_TAGS") ?: "_seco,,_mp3").split(",", ignoreCase = false).map { it }
    val only = System.getenv("EVAL_ONLY")?.split(",")
    val ids = dir.listFiles { f -> f.name.endsWith(".csv") }!!.map { it.name.removeSuffix(".csv") }.sorted().filter { only == null || it in only }
    val cache = File(dir, ".cache")

    NoteQualityWeights.install()
    System.getenv("HANDCFG")?.split(",")?.map { it.toDouble() }?.let { Analysis.handSpanW = it[0]; Analysis.handContW = it[1]; Analysis.handPriorW = it[2]; Analysis.handHeldW = it[3] }
    class Variant(val name: String, val extract: ExtractParams, val clean: CleanConfig?, val quality: Float = 0f)
    val base = ExtractParams()
    val none = CleanConfig(mergeGapSec = 0.0, ghostRatio = 0f, minAmp = 0f, maxPolyphony = 0)
    val b5 = base.copy(minNoteLen = 5)
    val sweep = if (System.getenv("EVAL_SWEEP") == null) null else listOf(
        Variant("B  sin limpieza (min 5)", b5, null),
        Variant("fusión sola (hueco .09)", b5, none.copy(mergeGapSec = 0.09)),
        Variant("fusión sola (hueco .03)", b5, none.copy(mergeGapSec = 0.03)),
        Variant("fantasmas 1.0", b5, none.copy(ghostRatio = 1.0f)),
        Variant("fantasmas 1.3", b5, none.copy(ghostRatio = 1.3f)),
        Variant("fantasmas 1.6", b5, none.copy(ghostRatio = 1.6f)),
        Variant("fantasmas 1.0 sin cond. onset", b5, none.copy(ghostRatio = 1.0f, ghostKeepOnset = 9f)),
        Variant("fantasmas 0.7 sin cond. onset", b5, none.copy(ghostRatio = 0.7f, ghostKeepOnset = 9f)),
        Variant("débiles+cortas .33/.2", b5, none.copy(minAmp = 0.33f)),
        Variant("débiles+cortas .40/.2", b5, none.copy(minAmp = 0.40f)),
        Variant("polifonía 8", b5, none.copy(maxPolyphony = 8)),
    )
    val variants = sweep ?: listOf(
        Variant("A  referencia (BP por defecto)", base, null),
        Variant("B  notas cortas (min 5 fotog.)", base.copy(minNoteLen = 5), null),
        Variant("H1 B + filtro entrenado 0.35", base.copy(minNoteLen = 5), null, 0.35f),
        Variant("H2 B + filtro entrenado 0.40", base.copy(minNoteLen = 5), null, 0.40f),
        Variant("H3 B + filtro entrenado 0.45", base.copy(minNoteLen = 5), null, 0.45f),
        Variant("H4 filtro 0.40, onset .45", base.copy(minNoteLen = 5, onsetThresh = 0.45f), null, 0.40f),
        Variant("H5 filtro 0.40, frame .27", base.copy(minNoteLen = 5, frameThresh = 0.27f), null, 0.40f),
        Variant("H6 filtro 0.40, min 4 fotog.", base.copy(minNoteLen = 4), null, 0.40f),
        Variant("H7 filtro 0.40, min 7 fotog.", base.copy(minNoteLen = 7), null, 0.40f),
    ).let { l -> l }
    @Suppress("UNUSED_VARIABLE") val unusedLegacy = listOf(
        Variant("A  referencia (BP por defecto)", base, null),
        Variant("B  notas cortas (min 5 fotog.)", base.copy(minNoteLen = 5), null),
        Variant("C  B + limpieza", base.copy(minNoteLen = 5), CleanConfig()),
        Variant("D  C con onset 0.4", base.copy(minNoteLen = 5, onsetThresh = 0.4f), CleanConfig()),
        Variant("E  C con onset 0.6", base.copy(minNoteLen = 5, onsetThresh = 0.6f), CleanConfig()),
        Variant("F  C con frame 0.25", base.copy(minNoteLen = 5, frameThresh = 0.25f), CleanConfig()),
        Variant("G  C con frame 0.35", base.copy(minNoteLen = 5, frameThresh = 0.35f), CleanConfig()),
    )
    for (tag in tags) {
        println("\n================ audio${if (tag.isEmpty()) " (reverb)" else tag} ================")
        val acc = Array(variants.size) { DoubleArray(5) }
        var cnt = 0
        for (id in ids) {
            val wav = File(dir, "$id$tag.wav")
            if (!wav.exists()) continue
            val (truth, info) = readTruth(File(dir, "$id.csv"))
            val out = cached(model, wav, cache)
            cnt++
            val sb = StringBuilder(String.format("%-24s", id))
            for ((vi, v) in variants.withIndex()) {
                val raw = NoteExtractor.extract(out, v.extract)
                var notes = if (v.clean != null) Analysis.clean(raw, out, v.clean) else raw
                if (v.quality > 0f) notes = NoteQuality.filter(notes, out, v.quality)
                val (s, _) = score(notes, truth)
                acc[vi][0] += s.p; acc[vi][1] += s.r; acc[vi][2] += s.f; acc[vi][3] += s.offF; acc[vi][4] += s.bias
                if (vi == 3) sb.append(String.format(" C: P%.2f R%.2f F%.2f off%.2f bias%+.0fms", s.p, s.r, s.f, s.offF, s.bias * 1000))
            }
            println(sb)
        }
        println(String.format("\n%-34s %6s %6s %6s %8s %9s", "variante (media de $cnt canciones)", "P", "R", "F1", "F1+fin", "sesgo"))
        for ((vi, v) in variants.withIndex()) println(String.format("%-34s %6.3f %6.3f %6.3f %8.3f %7.1fms", v.name, acc[vi][0] / cnt, acc[vi][1] / cnt, acc[vi][2] / cnt, acc[vi][3] / cnt, acc[vi][4] / cnt * 1000))

        if (System.getenv("EVAL_FEATS") != null) {
            val f = File(System.getenv("EVAL_FEATS") + (if (tag.isEmpty()) "_reverb" else tag) + ".csv")
            val sb = StringBuilder("song,label," + (0 until NoteQuality.N_FEATURES).joinToString(",") { "f$it" } + "\n")
            for (id in ids) {
                val wav = File(dir, "$id$tag.wav"); if (!wav.exists()) continue
                val (truth, _) = readTruth(File(dir, "$id.csv"))
                val out = cached(model, wav, cache)
                val notes = NoteExtractor.extract(out, ExtractParams(minNoteLen = 5))
                val (_, matches) = score(notes, truth)
                val ok = matches.map { it.first }.toSet()
                val feats = NoteQuality.features(notes, out)
                for ((i, n) in notes.withIndex()) sb.append("$id,${if (n in ok) 1 else 0},").append(feats[i].joinToString(",")).append("\n")
            }
            f.writeText(sb.toString())
        }
        if (System.getenv("EVAL_DUMP") != null) {
            // características de cada nota estimada que tiene una «compañera» una octava (o 19/24 semitonos) por debajo
            val f = File(System.getenv("EVAL_DUMP")); val sb = StringBuilder("song,label,gap,pitch,amp,onset,dur,pAmp,pOnset,pDur,dStart,dEnd,nPartners,pPitch\n")
            for (id in ids) {
                val wav = File(dir, "$id$tag.wav"); if (!wav.exists()) continue
                val (truth, _) = readTruth(File(dir, "$id.csv"))
                val out = cached(model, wav, cache)
                val notes = NoteExtractor.extract(out, ExtractParams(minNoteLen = 5))
                val (_, matches) = score(notes, truth)
                val ok = matches.map { it.first }.toSet()
                for (n in notes) for (g in intArrayOf(12, 19, 24)) {
                    val parts = notes.filter { it.pitch == n.pitch - g && minOf(it.end, n.end) - maxOf(it.start, n.start) > 0.5 * n.dur && abs(it.start - n.start) < 0.1 }
                    if (parts.isEmpty()) continue
                    val m = parts.maxByOrNull { it.amp }!!
                    sb.append("$id,${if (n in ok) 1 else 0},$g,${n.pitch},${n.amp},${n.onsetStrength},${n.dur},${m.amp},${m.onsetStrength},${m.dur},${n.start - m.start},${n.end - m.end},${parts.size},${m.pitch}\n")
                }
            }
            f.writeText(sb.toString())
        }
        if (System.getenv("EVAL_DIAG") != null) {
            for (vi in (if (sweep != null) listOf(0, 5) else listOf(1, 2))) {
                val fp = HashMap<String, Int>(); val fn = HashMap<String, Int>()
                var tot = 0; var totT = 0
                for (id in ids) {
                    val wav = File(dir, "$id$tag.wav"); if (!wav.exists()) continue
                    val (truth, _) = readTruth(File(dir, "$id.csv"))
                    val out = cached(model, wav, cache)
                    val v = variants[vi]
                    val raw = NoteExtractor.extract(out, v.extract)
                    val notes = if (v.clean != null) Analysis.clean(raw, out, v.clean) else raw
                    diagnose(notes, truth, 0.05, fp, fn); tot += notes.size; totT += truth.size
                }
                println("\n--- diagnóstico variante ${variants[vi].name}: ${tot} notas estimadas, ${totT} reales")
                println("falsos positivos:"); fp.entries.sortedByDescending { it.value }.forEach { println(String.format("   %5d  %s", it.value, it.key)) }
                println("notas reales perdidas:"); fn.entries.sortedByDescending { it.value }.forEach { println(String.format("   %5d  %s", it.value, it.key)) }
            }
        }
        // ritmo, tonalidad y manos con la variante C
        var tempoOk = 0; var tempoOct = 0; var keyOk = 0; var keyRel = 0; var handAcc = 0.0; var beatPhase = 0.0; var rhythmOk = 0.0; var n2 = 0
        for (id in ids) {
            val wav = File(dir, "$id$tag.wav"); if (!wav.exists()) continue
            val (truth, info) = readTruth(File(dir, "$id.csv"))
            val out = cached(model, wav, cache)
            val clean = Analysis.clean(NoteQuality.filter(NoteExtractor.extract(out, ExtractParams(minNoteLen = 5, frameThresh = 0.27f)), out, 0.40f), out, CleanConfig())
            val grid = Analysis.beatGrid(Analysis.onsetEnvelope(out), clean)
            val rel = abs(grid.bpm - info.bpm) / info.bpm
            if (rel < 0.04) tempoOk++
            if (listOf(1.0, 2.0, 0.5, 1.5, 2.0 / 3.0).any { abs(grid.bpm - info.bpm * it) / (info.bpm * it) < 0.04 }) tempoOct++
            val beats = Analysis.toBeats(clean, grid, true)
            val key = Analysis.detectKey(clean.map { BeatNote(it.start, it.dur, it.pitch, it.amp) })
            if (key == info.key) keyOk++
            if (key == info.key || relative[info.key] == key || relative[key] == info.key) keyRel++
            val right = Analysis.splitHands(clean.map { BeatNote(it.start, it.dur, it.pitch, it.amp) })
            val (_, m) = score(clean, truth)
            val idxOf = clean.withIndex().associate { it.value to it.index }
            val ha = m.count { (e, t) -> right[idxOf[e]!!] == t.right } / max(1.0, m.size.toDouble())
            handAcc += ha
            if (System.getenv("EVAL_PER_SONG") != null) println(String.format("   %-24s manos %.0f%%  tempo est %.1f (real %d)  %s", id, ha * 100, grid.bpm, info.bpm, if (grid.beatsPerBar.toString() + "/4" == info.meter) "compás ok" else "compás ${grid.beatsPerBar}/4 (real ${info.meter})"))
            val spb = 60.0 / info.bpm
            val ph = ((grid.firstBeatSec / grid.beatSec) % 1.0 + 1.0) % 1.0
            // la fase verdadera es 0 (el primer pulso de la canción está en t = 0)
            val truePh = 0.0
            beatPhase += min1(abs(ph - truePh))
            // ritmo: posición en pulsos de las notas emparejadas, módulo 1 pulso, frente a la verdad (si el tempo es correcto)
            if (rel < 0.04) {
                val mb = m.map { (e, t) ->
                    val b = beats.minByOrNull { abs(it.pitch - e.pitch) * 1000 + abs((it.start * grid.beatSec + grid.firstBeatSec) - e.start) }!!
                    val d = (b.start - t.start / spb) % 1.0
                    min1(abs(d))
                }
                rhythmOk += mb.count { it < 0.06 } / max(1.0, mb.size.toDouble())
            }
            n2++
        }
        println(String.format("\nritmo/tonalidad/manos (variante C): tempo ±4%% %d/%d (con octava %d/%d), tonalidad exacta %d/%d (o relativa %d/%d)", tempoOk, n2, tempoOct, n2, keyOk, n2, keyRel, n2))
        println(String.format("manos acertadas %.1f%% · fase del pulso dentro de 0,1 pulso: error medio %.3f pulsos · notas en su pulso correcto %.1f%% (solo si el tempo es correcto)", handAcc / n2 * 100, beatPhase / n2, rhythmOk / max(1, tempoOk) * 100))
    }
}

private fun min1(x: Double): Double { val m = ((x % 1.0) + 1.0) % 1.0; return minOf(m, 1.0 - m) }
