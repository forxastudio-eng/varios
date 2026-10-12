package com.forxa.pianocascada.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.forxa.pianocascada.PianoApp
import com.forxa.pianocascada.core.Hand
import com.forxa.pianocascada.core.Notes
import com.forxa.pianocascada.core.PracticeMode
import com.forxa.pianocascada.core.SongLibrary
import com.forxa.pianocascada.core.SongNote
import com.forxa.pianocascada.core.VelocityMapper
import com.forxa.pianocascada.core.CurveKind
import com.forxa.pianocascada.data.LayoutMode
import com.forxa.pianocascada.data.Settings
import com.forxa.pianocascada.piano.HandsMode
import com.forxa.pianocascada.piano.KeyListener
import com.forxa.pianocascada.piano.Palette
import com.forxa.pianocascada.piano.PracticeController
import com.forxa.pianocascada.ui.components.G
import com.forxa.pianocascada.ui.components.Gap
import com.forxa.pianocascada.ui.components.GhostButton
import com.forxa.pianocascada.ui.components.Glyph
import com.forxa.pianocascada.ui.components.PrimaryButton
import com.forxa.pianocascada.ui.theme.PC
import kotlinx.coroutines.delay

private const val STEPS = 9

/** Tutorial de inicio guiado e interactivo (≈ 5 minutos). */
@Composable
fun OnboardingScreen(app: PianoApp, settings: Settings, onFinish: (startSongId: String?) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(step) { done = step == 0 || step == 1 || step == 8 }
    BackHandler { if (step > 0) step-- }

    Column(Modifier.fillMaxSize().background(PC.bg).padding(horizontal = 56.dp, vertical = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until STEPS) Box(Modifier.size(width = if (i == step) 28.dp else 10.dp, height = 10.dp).clip(CircleShape).background(if (i <= step) PC.text1 else PC.surface3))
            }
            GhostButton("Saltar tutorial", { onFinish(null) })
        }
        Gap(h = 24.dp)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                StepText(step)
            }
            Box(Modifier.weight(1.3f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(PC.surface1)) {
                StepArea(app, settings, step, onDone = { done = true })
            }
        }
        Gap(h = 24.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (step > 0) GhostButton("Atrás", { step-- })
            Box(Modifier.weight(1f))
            if (step < STEPS - 1) PrimaryButton(if (done) "Siguiente" else "Haz el paso para continuar", { step++ }, enabled = done, big = true)
            else {
                GhostButton("Ir al inicio", { onFinish(null) })
                PrimaryButton("Tocar el Himno de la alegría", { onFinish("himno-alegria") }, big = true)
            }
        }
    }
}

@Composable
private fun StepText(step: Int) {
    val (title, body) = when (step) {
        0 -> "Bienvenido" to "Vas a aprender a tocar el piano viendo cómo caen las notas.\n\nCada barra es una nota: cuanto más larga, más tiempo debes mantener la tecla. Cuando llega abajo, la tocas.\n\nEste tutorial dura unos 5 minutos y no necesitas saber música."
        1 -> "Coloca la tablet" to "Ponla en horizontal sobre una mesa o un atril, a la altura de tus codos.\n\nSiéntate recto, con los brazos relajados y las muñecas al nivel del teclado.\n\nPuedes tocar con los dedos de las dos manos: la pantalla detecta hasta 10 a la vez."
        2 -> "Tu primera tecla" to "Toca cualquier tecla.\n\nVerás cómo se ilumina y oirás un piano de cola de verdad. Prueba también varias a la vez."
        3 -> "El mapa del teclado" to "Las teclas negras forman grupos de 2 y de 3. Eso te sirve para orientarte.\n\nLa nota C está justo a la izquierda de cada grupo de 2 teclas negras. Las tienes marcadas con luz.\n\nToca una C."
        4 -> "Las letras de las notas" to "Las notas se llaman con letras: C D E F G A B.\n\nToca en orden C, D, E, F, G: son las 5 teclas blancas seguidas, empezando por la C. Las que debes tocar brillan."
        5 -> "Calibra tu toque" to "El piano detecta cuánto presionas por el tamaño de tu dedo en la pantalla.\n\nPrimero toca varias teclas muy suave, con la yema. Después varias fuerte, con el dedo más plano."
        6 -> "Así caen las notas" to "Mira las barras que bajan: cuando una llegue a la línea luminosa, toca su tecla.\n\nEn el modo Espera la música se detiene hasta que aciertas, así nunca te pierdes.\n\nToca C, D, E, F, G."
        7 -> "Notas largas" to "La primera barra es larga: debes mantener la tecla pulsada mientras la barra pasa por la línea.\n\nLas barras cortas son notas rápidas.\n\nMantén la C y luego toca la G."
        else -> "¡Listo!" to "Ya sabes lo básico. Tu biblioteca tiene canciones clásicas para aprender: con la melodía sola, con el bajo y con las dos manos.\n\nEmpieza con el Himno de la alegría: solo usa cinco notas."
    }
    Text("Paso ${step + 1} de $STEPS", color = PC.right, style = MaterialTheme.typography.labelMedium)
    Gap(h = 8.dp)
    Text(title, color = PC.text1, style = MaterialTheme.typography.displayLarge)
    Gap(h = 18.dp)
    Text(body, color = PC.text2, style = MaterialTheme.typography.bodyLarge)
}

private class TutorialListener(val onPress: (pitch: Int, sizeMm: Float) -> Int, val app: PianoApp) : KeyListener {
    private val mapper = VelocityMapper(CurveKind.NORMAL)
    override fun onKeyDown(pitch: Int, sizeMm: Float): Int {
        app.audio.noteOn(pitch, mapper.velocity(0.6f, pitch))
        return onPress(pitch, sizeMm)
    }
    override fun onKeyUp(pitch: Int) { app.audio.noteOff(pitch) }
    override fun onBackgroundTap() {}
}

@Composable
private fun StepArea(app: PianoApp, settings: Settings, step: Int, onDone: () -> Unit) {
    when (step) {
        0 -> {
            val himno = remember { app.songs.find("himno-alegria") }
            if (himno != null) DemoSurface(app, settings, himno, PracticeMode.LISTEN, 0.8f, 1, autoPlay = true, onDone = {}) else Hero()
        }
        1 -> Hero()
        2 -> FreePlayStep(app, settings, highlight = IntArray(0), accept = { _ -> true }, onDone = onDone)
        3 -> FreePlayStep(app, settings, highlight = IntArray(Notes.MAX) { it }.filter { it >= 48 && it <= 84 && it % 12 == 0 }.toIntArray(), accept = { p -> p % 12 == 0 }, onDone = onDone)
        4 -> {
            val seq = intArrayOf(60, 62, 64, 65, 67)
            var idx by remember { mutableIntStateOf(0) }
            Box(Modifier.fillMaxSize()) {
                val hl = if (idx < seq.size) intArrayOf(seq[idx]) else IntArray(0)
                val listener = remember { TutorialListener({ p, _ -> if (idx < seq.size && p == seq[idx]) { idx++; if (idx == seq.size) onDone(); Palette.RIGHT } else if (idx < seq.size) Palette.MISS else 0 }, app) }
                PianoHost(settings, null, listener, Modifier.fillMaxSize(), keyboardOnly = true, staticLeft = 21f, span = 15, highlight = hl, labels = com.forxa.pianocascada.data.KeyLetters.ALL)
                Text("$idx / ${seq.size}", color = PC.text1, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
            }
        }
        5 -> CalibrationStep(app, settings, onDone)
        6 -> {
            val song = remember {
                SongLibrary.simple("tut1", "Tutorial", 60, listOf(60, 62, 64, 65, 67).mapIndexed { i, p -> SongNote(i * 1f + 1f, 0.8f, p, Hand.RIGHT) })
            }
            DemoSurface(app, settings, song, PracticeMode.WAIT, 1f, 3, autoPlay = true, onDone = onDone)
        }
        7 -> {
            val song = remember { SongLibrary.simple("tut2", "Tutorial", 60, listOf(SongNote(1f, 4f, 60, Hand.RIGHT), SongNote(6f, 1f, 67, Hand.RIGHT))) }
            DemoSurface(app, settings, song, PracticeMode.WAIT, 1f, 3, autoPlay = true, onDone = onDone)
        }
        else -> Hero()
    }
}

@Composable
private fun Hero() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(G.NOTE, Modifier.size(72.dp), PC.right)
            Glyph(G.KEYS, Modifier.size(120.dp), PC.text1)
            Glyph(G.NOTE, Modifier.size(72.dp), PC.left)
        }
    }
}

@Composable
private fun FreePlayStep(app: PianoApp, settings: Settings, highlight: IntArray, accept: (Int) -> Boolean, onDone: () -> Unit) {
    val listener = remember { TutorialListener({ p, _ -> if (accept(p)) { onDone(); Palette.RIGHT } else 0 }, app) }
    PianoHost(settings, null, listener, Modifier.fillMaxSize(), keyboardOnly = true, staticLeft = 21f, span = 15, highlight = highlight)
}

@Composable
private fun CalibrationStep(app: PianoApp, settings: Settings, onDone: () -> Unit) {
    val soft = remember { mutableListOf<Float>() }
    val hard = remember { mutableListOf<Float>() }
    var phase by remember { mutableIntStateOf(0) }       // 0 suave, 1 fuerte, 2 hecho
    var count by remember { mutableIntStateOf(0) }
    val listener = remember {
        TutorialListener({ _, mm ->
            if (phase == 0) { soft.add(mm); count = soft.size; if (soft.size >= 4) { phase = 1; count = 0 } }
            else if (phase == 1) {
                hard.add(mm); count = hard.size
                if (hard.size >= 4) {
                    phase = 2
                    val lo = soft.average().toFloat(); var hi = hard.average().toFloat()
                    if (hi < lo + 1f) hi = lo + 1.5f
                    app.settings.update { it.copy(keyboard = it.keyboard.copy(calibMin = lo * 0.9f, calibMax = hi * 1.05f)) }
                    onDone()
                }
            }
            if (phase == 2) Palette.OK else Palette.WARM
        }, app)
    }
    Box(Modifier.fillMaxSize()) {
        PianoHost(settings, null, listener, Modifier.fillMaxSize(), keyboardOnly = true, staticLeft = 21f, span = 15)
        Text(
            when (phase) { 0 -> "Toca MUY SUAVE: $count / 4"; 1 -> "Ahora FUERTE: $count / 4"; else -> "¡Calibrado!" },
            color = if (phase == 2) PC.ok else PC.text1, style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
        )
    }
}

/** Mini práctica real (cascada + teclado) para los pasos del tutorial. */
@Composable
private fun DemoSurface(app: PianoApp, settings: Settings, song: com.forxa.pianocascada.core.Song, mode: PracticeMode, speed: Float, level: Int, autoPlay: Boolean, onDone: () -> Unit) {
    val controller = remember(song.id) { PracticeController(app.audio, settings, song, level, HandsMode.RIGHT, mode, speed) }
    DisposableEffect(controller) { onDispose { controller.dispose() } }
    LaunchedEffect(controller) {
        if (autoPlay) { delay(500); controller.play() }
    }
    LaunchedEffect(controller.finished) {
        if (controller.finished) {
            onDone()
            if (mode == PracticeMode.LISTEN) { delay(1800); controller.play() }
        }
    }
    PianoHost(settings, controller, controller, Modifier.fillMaxSize(), layout = LayoutMode.REDUCED, span = 15)
}
