package com.forxa.pianocascada.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.forxa.pianocascada.PianoApp
import com.forxa.pianocascada.core.PracticeMode
import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.data.LayoutMode
import com.forxa.pianocascada.data.Settings
import com.forxa.pianocascada.importer.ImportController
import com.forxa.pianocascada.importer.ImportState
import com.forxa.pianocascada.importer.OmrClient
import com.forxa.pianocascada.piano.HandsMode
import com.forxa.pianocascada.piano.PracticeController
import com.forxa.pianocascada.ui.components.Card
import com.forxa.pianocascada.ui.components.Chip
import com.forxa.pianocascada.ui.components.G
import com.forxa.pianocascada.ui.components.Gap
import com.forxa.pianocascada.ui.components.GhostButton
import com.forxa.pianocascada.ui.components.Glyph
import com.forxa.pianocascada.ui.components.IconBtn
import com.forxa.pianocascada.ui.components.LabeledSlider
import com.forxa.pianocascada.ui.components.PrimaryButton
import com.forxa.pianocascada.ui.components.SectionTitle
import com.forxa.pianocascada.ui.theme.PC

@Composable
fun ImportScreen(app: PianoApp, settings: Settings, onBack: () -> Unit, onImported: (String) -> Unit) {
    val ctl = remember { ImportController(app) }
    DisposableEffect(Unit) { onDispose { if (ctl.state is ImportState.Working) ctl.cancel() } }
    val state = ctl.state
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) ctl.importAudio(uri) }
    val scorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ctl.importScore(uri, settings.serverUrl)
    }
    val midiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) ctl.importMidi(uri) }

    Column(Modifier.fillMaxSize().background(PC.bg).padding(horizontal = 48.dp, vertical = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            IconBtn(G.BACK, { if (state is ImportState.Working) ctl.cancel(); if (state is ImportState.Review) ctl.reset() else onBack() })
            Text("Añadir canción", color = PC.text1, style = MaterialTheme.typography.headlineLarge)
        }
        Gap(h = 24.dp)
        when (state) {
            is ImportState.Idle -> IdleView(app, settings, onAudio = { audioPicker.launch(arrayOf("audio/*")) }, onScore = { scorePicker.launch(arrayOf("application/pdf", "image/*")) }, onMidi = { midiPicker.launch(arrayOf("*/*")) })
            is ImportState.Working -> WorkingView(state, onCancel = { ctl.cancel() })
            is ImportState.Review -> ReviewView(app, settings, ctl, state, onSaved = onImported)
            is ImportState.Failed -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No se pudo importar", color = PC.miss, style = MaterialTheme.typography.headlineMedium)
                Gap(h = 10.dp)
                Text(state.message, color = PC.text2, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 120.dp))
                Gap(h = 24.dp)
                GhostButton("Volver a intentarlo", { ctl.reset() })
            }
        }
    }
}

@Composable
private fun IdleView(app: PianoApp, settings: Settings, onAudio: () -> Unit, onScore: () -> Unit, onMidi: () -> Unit) {
    var url by remember { mutableStateOf(settings.serverUrl) }
    var test by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(1.3f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            OptionCard(G.WAVE, "Audio: MP3, M4A, WAV…", "Convierte una grabación en notas que caen. Funciona mejor con piano solo, y tarda unos segundos por cada minuto de música.", "Elegir un audio", onAudio, PC.right)
            OptionCard(G.FILE, "Partitura en PDF o foto", "Lee la partitura con reconocimiento óptico musical. Necesita el servidor en casa (a la derecha).", "Elegir PDF o foto", onScore, PC.left)
            OptionCard(G.NOTE, "Archivo MIDI", "Importación exacta de un .mid, con las dos manos si el archivo las trae separadas. Ideal para obras como el «Claro de luna».", "Elegir un MIDI", onMidi, PC.ok)
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            SectionTitle("Servidor en casa (para PDF y fotos)")
            Text("Un pequeño programa en tu ordenador (carpeta «servidor» del proyecto) que lee las partituras. La tablet y el ordenador deben estar en la misma red Wi‑Fi.", color = PC.text2, style = MaterialTheme.typography.bodyMedium)
            Gap(h = 12.dp)
            Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(PC.surface2).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (url.isEmpty()) Text("http://192.168.1.20:8765", color = PC.text3, style = MaterialTheme.typography.bodyMedium)
                BasicTextField(
                    value = url, onValueChange = { url = it; app.settings.update { s -> s.copy(serverUrl = it.trim()) } }, singleLine = true,
                    textStyle = TextStyle(color = PC.text1, fontSize = MaterialTheme.typography.bodyMedium.fontSize), cursorBrush = SolidColor(PC.text1), modifier = Modifier.fillMaxWidth(),
                )
            }
            Gap(h = 10.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip("Probar conexión", false, {
                    test = "Probando…"
                    Thread { val ok = runCatching { OmrClient(url).health() }.getOrDefault(false); test = if (ok) "✓ Servidor encontrado" else "✗ No responde" }.start()
                })
                Text(test, color = if (test.startsWith("✓")) PC.ok else PC.text2, style = MaterialTheme.typography.bodyMedium)
            }
            @Suppress("UNUSED_VARIABLE") val unused = scope
        }
    }
}

@Composable
private fun OptionCard(icon: G, title: String, desc: String, button: String, onClick: () -> Unit, accent: androidx.compose.ui.graphics.Color) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(icon, Modifier.size(44.dp), accent)
            Column(Modifier.weight(1f)) {
                Text(title, color = PC.text1, style = MaterialTheme.typography.titleLarge)
                Text(desc, color = PC.text2, style = MaterialTheme.typography.bodyMedium)
            }
            PrimaryButton(button, onClick)
        }
    }
}

@Composable
private fun WorkingView(s: ImportState.Working, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(s.title, color = PC.text2, style = MaterialTheme.typography.titleMedium)
        Gap(h = 8.dp)
        Text(s.message, color = PC.text1, style = MaterialTheme.typography.headlineMedium)
        Gap(h = 24.dp)
        Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(PC.surface3)) {
            Box(Modifier.fillMaxWidth(s.progress.coerceIn(0.02f, 1f)).fillMaxHeight().background(PC.right))
        }
        Gap(h = 10.dp)
        Text("${(s.progress * 100).toInt()} %", color = PC.text2, style = MaterialTheme.typography.bodyMedium)
        Gap(h = 28.dp)
        GhostButton("Cancelar", onCancel)
    }
}

@Composable
private fun ReviewView(app: PianoApp, settings: Settings, ctl: ImportController, r: ImportState.Review, onSaved: (String) -> Unit) {
    var title by remember(r.song.id) { mutableStateOf(r.title) }
    var q by remember(r.song.id) { mutableStateOf(r.quality) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(Modifier.weight(1.6f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(PC.surface1)) {
            SongPreview(app, settings, r.song)
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("Título")
            Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(PC.surface2).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(value = title, onValueChange = { title = it }, singleLine = true, textStyle = TextStyle(color = PC.text1, fontSize = MaterialTheme.typography.bodyLarge.fontSize), cursorBrush = SolidColor(PC.text1), modifier = Modifier.fillMaxWidth())
            }
            Gap(h = 8.dp)
            Text(r.detail, color = PC.text2, style = MaterialTheme.typography.bodyMedium)
            if (r.analyzed != null) {
                Gap(h = 14.dp)
                LabeledSlider(
                    "Sensibilidad", q, 0.25f..0.65f, { q = it }, { if (it < 0.34f) "Más notas" else if (it > 0.5f) "Solo las seguras" else "Equilibrada" },
                    hint = "Si ves notas de más, sube hacia «solo las seguras»; si faltan notas, bájala.",
                    onFinished = { ctl.rebuild(r, q) },
                )
            }
            if (r.warnings.isNotEmpty()) {
                Gap(h = 10.dp)
                SectionTitle("Avisos")
                for (w in r.warnings.take(6)) Text("• $w", color = PC.left, style = MaterialTheme.typography.bodySmall)
            }
            Gap(h = 12.dp)
            Text("Las manos y los compases son una propuesta automática. Empieza por el nivel 1 y baja la velocidad si algo no suena como esperabas.", color = PC.text3, style = MaterialTheme.typography.bodySmall)
            Gap(h = 16.dp)
            PrimaryButton("Guardar en mi biblioteca", { onSaved(ctl.save(r.copy(song = r.song.copy(title = title.ifBlank { r.title })))) }, Modifier.fillMaxWidth(), big = true)
            Gap(h = 8.dp)
            GhostButton("Descartar", { ctl.reset() }, Modifier.fillMaxWidth())
        }
    }
}

/** Vista previa de una canción con su cascada de notas; suena sola al pulsar «Escuchar». */
@Composable
fun SongPreview(app: PianoApp, settings: Settings, song: Song) {
    val controller = remember(song) { PracticeController(app.audio, settings, song, song.levels.last(), HandsMode.BOTH, PracticeMode.LISTEN, 1f) }
    DisposableEffect(controller) { onDispose { controller.dispose() } }
    Box(Modifier.fillMaxSize()) {
        PianoHost(settings, controller, null, Modifier.fillMaxSize(), layout = LayoutMode.REDUCED, span = 22)
        Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(if (controller.playing) G.PAUSE else G.PLAY, { controller.togglePlay() }, size = 48.dp, active = controller.playing)
            IconBtn(G.RESTART, { controller.restart() }, size = 48.dp)
            Text(if (controller.playing) "Escuchando…" else "Escuchar la transcripción", color = PC.text1, style = MaterialTheme.typography.labelLarge)
        }
    }
}
