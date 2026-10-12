package com.forxa.pianocascada.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forxa.pianocascada.PianoApp
import com.forxa.pianocascada.core.PracticeMode
import com.forxa.pianocascada.core.Transposer
import com.forxa.pianocascada.data.LayoutMode
import com.forxa.pianocascada.data.Settings
import com.forxa.pianocascada.data.applySound
import com.forxa.pianocascada.piano.HandsMode
import com.forxa.pianocascada.piano.PianoSurfaceView
import com.forxa.pianocascada.piano.PracticeController
import com.forxa.pianocascada.ui.components.G
import com.forxa.pianocascada.ui.components.Gap
import com.forxa.pianocascada.ui.components.Glyph
import com.forxa.pianocascada.ui.components.GhostButton
import com.forxa.pianocascada.ui.components.IconBtn
import com.forxa.pianocascada.ui.components.PrimaryButton
import com.forxa.pianocascada.ui.components.StarRow
import com.forxa.pianocascada.ui.theme.PC

@Composable
fun PracticeScreen(app: PianoApp, params: PracticeParams, settings: Settings, onExit: () -> Unit, onRetryWith: (PracticeParams) -> Unit) {
    val base = remember(params.songId) { params.songId?.let { app.songs.find(it) } }
    val plan = remember(base?.id) {
        base?.let { Transposer.bestWhiteKeyShift((it.rh + it.lh + (it.melody ?: emptyList())).map { n -> n.pitch }) }
    }
    val song = remember(params) { base?.transposed(if (params.whiteKeys) plan?.shift ?: 0 else 0) }
    val controller = remember(params) {
        PracticeController(app.audio, settings, song, params.level, params.hands, params.mode, params.speed)
    }
    var showSettings by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        controller.onFinished = { r -> song?.let { app.progress.record(it.id, controller.level, r.accuracy, r.stars) } }
        song?.let { app.progress.touch(it.id, controller.level) }
        onDispose { controller.dispose() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) controller.pause() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(settings) { controller.updateSettings(settings); applySound(app.audio, settings.sound) }
    BackHandler {
        if (showSettings) showSettings = false else { controller.pause(); onExit() }
    }

    val layout = params.layout
    Box(Modifier.fillMaxSize().background(PC.bg)) {
        PianoHost(settings, controller, controller, Modifier.fillMaxSize(), layout = layout)
        if (layout == LayoutMode.TWO_ROWS) {
            val (bandTop, bandH) = PianoSurfaceView.bandFractions(settings.keyboard.heightScale)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val top = maxHeight * bandTop
                val h = maxHeight * bandH
                Row(Modifier.offset(y = top).height(h).fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Controls(controller, song?.title ?: "", onExit = { controller.pause(); onExit() }, onSettings = { controller.pause(); showSettings = true }, big = true, withTitle = false)
                }
                // título y progreso discretos arriba
                Row(Modifier.padding(12.dp).align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                    Text(song?.title ?: "", color = PC.text2, style = MaterialTheme.typography.labelLarge)
                    Gap(w = 16.dp)
                    Text(controller.barText, color = PC.text3, style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Controls(controller, song?.title ?: "", onExit = { controller.pause(); onExit() }, onSettings = { controller.pause(); showSettings = true }, big = false, withTitle = true)
            }
        }
        if (controller.waiting && controller.mode == PracticeMode.WAIT) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 76.dp).clip(RoundedCornerShape(20.dp)).background(PC.surface2).padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text("Toca la nota que brilla", color = PC.text1, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (controller.combo >= 4) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 24.dp)) {
                Text("×${controller.combo}", color = PC.ok, style = MaterialTheme.typography.headlineMedium)
            }
        }
        val res = controller.result
        if (controller.finished && res != null && controller.mode != PracticeMode.LISTEN) {
            ResultCard(
                stars = res.stars, accuracy = res.accuracy, wrong = res.wrong, combo = res.bestCombo,
                canLevelUp = controller.level < (song?.levels?.last() ?: 1),
                onRetry = { controller.restart() },
                onLevelUp = { onRetryWith(params.copy(level = controller.level + 1)) },
                onExit = onExit, modifier = Modifier.align(Alignment.Center),
            )
        }
        if (showSettings) {
            Box(Modifier.fillMaxSize().background(PC.bg)) {
                SettingsScreen(app, settings, onBack = { showSettings = false })
            }
        }
    }
}

@Composable
private fun ResultCard(stars: Int, accuracy: Float, wrong: Int, combo: Int, canLevelUp: Boolean, onRetry: () -> Unit, onLevelUp: () -> Unit, onExit: () -> Unit, modifier: Modifier) {
    Box(modifier.width(520.dp).clip(RoundedCornerShape(28.dp)).background(PC.surface1).padding(36.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                when (stars) { 3 -> "¡Perfecto!"; 2 -> "¡Muy bien!"; 1 -> "¡Buen trabajo!"; else -> "Sigue practicando" },
                color = PC.text1, style = MaterialTheme.typography.headlineLarge,
            )
            Gap(h = 14.dp)
            StarRow(stars, 40.dp)
            Gap(h = 18.dp)
            Text("${(accuracy * 100).toInt()} % de notas limpias", color = PC.text1, style = MaterialTheme.typography.titleLarge)
            Text("Mejor racha: $combo · Teclas equivocadas: $wrong", color = PC.text2, style = MaterialTheme.typography.bodyMedium)
            Text(
                when {
                    accuracy >= 0.95f -> "Ya la dominas. Prueba con el siguiente nivel."
                    accuracy >= 0.7f -> "Casi está. Repítela una vez más a esta velocidad."
                    else -> "Baja la velocidad y toca primero solo una mano."
                }, color = PC.text3, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp),
            )
            Gap(h = 24.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton("Volver", onExit)
                GhostButton("Repetir", onRetry)
                if (canLevelUp) PrimaryButton("Siguiente nivel", onLevelUp)
            }
        }
    }
}

// ---------------------------------------------------------------------- controles
@Composable
private fun Pill(label: String, onClick: () -> Unit, big: Boolean, active: Boolean = false) {
    Box(
        Modifier.height(if (big) 56.dp else 40.dp).clip(RoundedCornerShape(if (big) 16.dp else 12.dp))
            .background(if (active) PC.surface3 else PC.surface2).clickable(onClick = onClick).padding(horizontal = if (big) 20.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = PC.text1, style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge) }
}

@Composable
private fun RowScope.Controls(c: PracticeController, title: String, onExit: () -> Unit, onSettings: () -> Unit, big: Boolean, withTitle: Boolean) {
    val s = if (big) 56.dp else 40.dp
    val gap = if (big) 14.dp else 8.dp
    IconBtn(G.BACK, onExit, size = s)
    Gap(w = gap)
    if (c.song != null) {
        if (withTitle) {
            Column(Modifier.width(210.dp)) {
                Text(title, color = PC.text1, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text("Nivel ${c.level} · ${c.song.levelName(c.level)}", color = PC.text3, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Gap(w = gap)
        }
        IconBtn(if (c.playing) G.PAUSE else G.PLAY, { c.togglePlay() }, size = if (big) 64.dp else 44.dp, active = c.playing)
        Gap(w = gap)
        IconBtn(G.RESTART, { c.restart() }, size = s)
        Gap(w = gap)
        IconBtn(G.LOOP, { c.toggleLoop() }, size = s, active = c.loopOn)
        Gap(w = gap)
        ProgressBar(c.progress, c.song.bars, { c.seekToFraction(it) }, Modifier.weight(1f).height(if (big) 40.dp else 32.dp))
        Gap(w = gap)
        if (withTitle) { Text(c.barText, color = PC.text2, style = MaterialTheme.typography.labelMedium); Gap(w = gap) }
        val speeds = listOf(0.5f, 0.7f, 0.85f, 1f)
        Pill("${(c.speed * 100).toInt()} %", { c.changeSpeed(speeds[(speeds.indexOfFirst { it >= c.speed - 0.01f }.coerceAtLeast(0) + 1) % speeds.size]) }, big)
        Gap(w = gap)
        Pill(
            when (c.mode) { PracticeMode.WAIT -> "Espera"; PracticeMode.RHYTHM -> "Ritmo"; PracticeMode.LISTEN -> "Escuchar" },
            { c.changeMode(when (c.mode) { PracticeMode.WAIT -> PracticeMode.RHYTHM; PracticeMode.RHYTHM -> PracticeMode.LISTEN; PracticeMode.LISTEN -> PracticeMode.WAIT }) }, big,
        )
        Gap(w = gap)
        if (c.level > 1) {
            Pill(
                when (c.hands) { HandsMode.BOTH -> "Ambas"; HandsMode.RIGHT -> "Der."; HandsMode.LEFT -> "Izq." },
                { c.changeHands(when (c.hands) { HandsMode.BOTH -> HandsMode.RIGHT; HandsMode.RIGHT -> HandsMode.LEFT; HandsMode.LEFT -> HandsMode.BOTH }) }, big,
            )
            Gap(w = gap)
        }
        Pill("Nv ${c.level}", { c.changeLevel(if (c.level >= c.song.levels.last()) 1 else c.level + 1) }, big)
        Gap(w = gap)
    } else {
        Box(Modifier.weight(1f))
    }
    PedalButton(c, big)
    Gap(w = gap)
    IconBtn(G.GEAR, onSettings, size = s)
}

@Composable
fun PedalButton(c: PracticeController, big: Boolean) {
    val h = if (big) 56.dp else 40.dp
    Box(
        Modifier.height(h).width(if (big) 120.dp else 86.dp).clip(RoundedCornerShape(14.dp))
            .background(if (c.pedal) PC.surface3 else PC.surface2)
            .pointerInput(c) {
                detectTapGestures(onPress = {
                    c.holdPedal(true)
                    try { awaitRelease() } finally { c.holdPedal(false) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Glyph(G.PEDAL, Modifier.size(if (big) 26.dp else 20.dp), if (c.pedal) PC.left else PC.text1)
            Text("Pedal", color = if (c.pedal) PC.left else PC.text1, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, bars: Int, onSeek: (Float) -> Unit, modifier: Modifier) {
    Box(modifier.pointerInput(Unit) { detectTapGestures { off -> onSeek(off.x / size.width) } }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            drawRoundRect(PC.surface3, Offset(0f, 0f), Size(size.width, size.height), CornerRadius(size.height / 2f))
            val w = size.width * progress.coerceIn(0f, 1f)
            drawRoundRect(PC.right, Offset(0f, 0f), Size(w, size.height), CornerRadius(size.height / 2f))
            drawCircle(PC.text1, size.height * 0.9f, Offset(w, size.height / 2f))
        }
    }
}

// ---------------------------------------------------------------------- piano libre
@Composable
fun FreePianoScreen(app: PianoApp, settings: Settings, onExit: () -> Unit) {
    val controller = remember { PracticeController(app.audio, settings, null) }
    var showSettings by remember { mutableStateOf(false) }
    DisposableEffect(controller) { onDispose { controller.dispose() } }
    LaunchedEffect(settings) { controller.updateSettings(settings); applySound(app.audio, settings.sound) }
    BackHandler { if (showSettings) showSettings = false else onExit() }
    Box(Modifier.fillMaxSize().background(PC.bg)) {
        PianoHost(settings, controller, controller, Modifier.fillMaxSize())
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Controls(controller, "Piano libre", onExit = onExit, onSettings = { showSettings = true }, big = false, withTitle = false)
        }
        if (showSettings) Box(Modifier.fillMaxSize().background(PC.bg)) { SettingsScreen(app, settings, onBack = { showSettings = false }) }
    }
}
