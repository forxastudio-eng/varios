package com.forxa.pianocascada.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.data.SongProgress
import com.forxa.pianocascada.ui.components.Card
import com.forxa.pianocascada.ui.components.G
import com.forxa.pianocascada.ui.components.Gap
import com.forxa.pianocascada.ui.components.Glyph
import com.forxa.pianocascada.ui.components.GhostButton
import com.forxa.pianocascada.ui.components.IconBtn
import com.forxa.pianocascada.ui.components.PrimaryButton
import com.forxa.pianocascada.ui.components.StarRow
import com.forxa.pianocascada.ui.theme.PC
import java.util.Calendar

@Composable
fun HomeScreen(
    songs: List<Song>, progress: Map<String, SongProgress>,
    onContinue: (Song) -> Unit, onLibrary: () -> Unit, onFree: () -> Unit,
    onSettings: () -> Unit, onTutorial: () -> Unit, onImport: () -> Unit,
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when { hour < 6 -> "Buenas noches"; hour < 13 -> "Buenos días"; hour < 21 -> "Buenas tardes"; else -> "Buenas noches" }
    val lastId = progress.filter { it.value.lastPlayed > 0 }.maxByOrNull { it.value.lastPlayed }?.key
    val cont = songs.firstOrNull { it.id == lastId } ?: songs.firstOrNull { it.id == "para-elisa" } ?: songs.firstOrNull()
    val played = lastId != null

    Column(Modifier.fillMaxSize().background(PC.bg).padding(horizontal = 56.dp, vertical = 40.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting, color = PC.text2, style = MaterialTheme.typography.titleMedium)
                Text("Piano Cascada", color = PC.text1, style = MaterialTheme.typography.displayLarge)
            }
            IconBtn(G.GEAR, onSettings, size = 56.dp)
        }
        Gap(h = 36.dp)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // CONTINUAR
            Card(Modifier.weight(1.5f).fillMaxHeight(), onClick = { cont?.let(onContinue) }) {
                Box(Modifier.fillMaxSize()) {
                    if (cont != null) Thumb(cont, Modifier.fillMaxSize().padding(bottom = 140.dp), alpha = 0.55f)
                    Column(Modifier.align(Alignment.BottomStart).padding(32.dp)) {
                        Text(if (played) "CONTINUAR" else "EMPIEZA AQUÍ", color = PC.right, style = MaterialTheme.typography.labelMedium)
                        Gap(h = 6.dp)
                        Text(cont?.title ?: "—", color = PC.text1, style = MaterialTheme.typography.headlineLarge, maxLines = 2)
                        Text(cont?.composer ?: "", color = PC.text2, style = MaterialTheme.typography.bodyLarge)
                        Gap(h = 16.dp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            PrimaryButton(if (played) "Seguir practicando" else "Tocar ahora", { cont?.let(onContinue) })
                            val p = cont?.let { progress[it.id] }
                            if (p != null && p.stars > 0) StarRow(p.stars, 22.dp)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Card(Modifier.weight(1f).fillMaxWidth(), onClick = onLibrary) {
                    Column(Modifier.padding(28.dp)) {
                        Glyph(G.NOTE, Modifier.size(34.dp), PC.right)
                        Gap(h = 14.dp)
                        Text("Mi biblioteca", color = PC.text1, style = MaterialTheme.typography.headlineMedium)
                        Text("${songs.size} canciones para aprender", color = PC.text2, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Card(Modifier.weight(1f).fillMaxWidth(), onClick = onFree) {
                    Column(Modifier.padding(28.dp)) {
                        Glyph(G.KEYS, Modifier.size(34.dp), PC.left)
                        Gap(h = 14.dp)
                        Text("Piano libre", color = PC.text1, style = MaterialTheme.typography.headlineMedium)
                        Text("Toca sin partitura", color = PC.text2, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Gap(h = 24.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GhostButton("Añadir canción (MP3, PDF, MIDI)", onImport)
            GhostButton("Repetir el tutorial", onTutorial)
        }
        Gap(h = 14.dp)
        val midi = com.forxa.pianocascada.piano.MidiRouter.devices
        Text(
            (if (midi.isNotEmpty()) "Piano MIDI conectado: ${midi.joinToString()} · " else "") +
                "Teclado del ordenador: A S D F G H J K L = teclas blancas, W E T Y U O P = negras · Z / X = octava · Espacio = pedal · Intro = pausa · R = reiniciar · Esc = volver · F11 = pantalla completa",
            color = PC.text3, style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Miniatura de una canción: sus notas dibujadas como una pequeña cascada. */
@Composable
fun Thumb(song: Song, modifier: Modifier = Modifier, alpha: Float = 0.9f) {
    Canvas(modifier) {
        val notes = song.notes(3)
        if (notes.isEmpty()) return@Canvas
        val mn = notes.minOf { it.pitch }
        val mx = notes.maxOf { it.pitch }.coerceAtLeast(mn + 12)
        val total = song.totalBeats.coerceAtLeast(1f)
        val w = size.width
        val h = size.height
        val pad = 10f
        for (n in notes) {
            val x = pad + (n.pitch - mn).toFloat() / (mx - mn) * (w - 2 * pad)
            val top = h - (n.end / total) * (h - pad) - pad / 2f
            val bar = ((n.dur / total) * (h - pad)).coerceAtLeast(3f)
            val c = if (n.hand == com.forxa.pianocascada.core.Hand.RIGHT) PC.right else PC.left
            drawRoundRect(c.copy(alpha = alpha), Offset(x - 3f, top), Size(6f, bar), CornerRadius(3f, 3f))
        }
    }
}
