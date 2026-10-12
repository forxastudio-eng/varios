package com.forxa.pianocascada.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forxa.pianocascada.core.PracticeMode
import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.core.Transposer
import com.forxa.pianocascada.data.LayoutMode
import com.forxa.pianocascada.data.Settings
import com.forxa.pianocascada.data.SongProgress
import com.forxa.pianocascada.piano.HandsMode
import com.forxa.pianocascada.ui.components.Card
import com.forxa.pianocascada.ui.components.Chip
import com.forxa.pianocascada.ui.components.DifficultyDots
import com.forxa.pianocascada.ui.components.G
import com.forxa.pianocascada.ui.components.Gap
import com.forxa.pianocascada.ui.components.Glyph
import com.forxa.pianocascada.ui.components.IconBtn
import com.forxa.pianocascada.ui.components.PrimaryButton
import com.forxa.pianocascada.ui.components.SectionTitle
import com.forxa.pianocascada.ui.components.Segmented
import com.forxa.pianocascada.ui.components.StarRow
import com.forxa.pianocascada.ui.components.SwitchRow
import com.forxa.pianocascada.ui.theme.PC

data class PracticeParams(
    val songId: String?, val level: Int, val hands: HandsMode, val mode: PracticeMode,
    val speed: Float, val whiteKeys: Boolean, val layout: LayoutMode,
)

@Composable
fun SongDetailScreen(
    song: Song, progress: SongProgress?, settings: Settings,
    onBack: () -> Unit, onFavorite: () -> Unit, onStart: (PracticeParams) -> Unit, onDelete: (() -> Unit)? = null,
) {
    var level by remember(song.id) { mutableStateOf((progress?.lastLevel ?: 1).coerceIn(1, song.levels.last())) }
    var hands by remember(song.id) { mutableStateOf(HandsMode.BOTH) }
    var mode by remember(song.id) { mutableStateOf(PracticeMode.WAIT) }
    var speed by remember(song.id) { mutableStateOf(settings.speed) }
    var layout by remember(song.id) { mutableStateOf(settings.keyboard.layout) }
    val plan = remember(song.id) { Transposer.bestWhiteKeyShift((song.rh + song.lh + (song.melody ?: emptyList())).map { it.pitch }) }
    var white by remember(song.id) { mutableStateOf(plan.allWhite && plan.shift != 0) }
    val effHands = if (level == 1) HandsMode.RIGHT else hands

    Row(Modifier.fillMaxSize().background(PC.bg).padding(horizontal = 48.dp, vertical = 32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        // ---------------- información de la obra
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBtn(G.BACK, onBack)
                Box(Modifier.weight(1f))
                IconBtn(G.HEART, onFavorite, tint = if (progress?.favorite == true) PC.miss else PC.text1, active = progress?.favorite == true)
                if (onDelete != null) { Gap(w = 12.dp); IconBtn(G.CLOSE, onDelete) }
            }
            Gap(h = 20.dp)
            Card(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(28.dp)) {
                    Text(song.title, color = PC.text1, style = MaterialTheme.typography.headlineLarge)
                    Text(song.composer, color = PC.text2, style = MaterialTheme.typography.titleMedium)
                    if (song.work.isNotBlank()) Text("${song.work}${if (song.era.isNotBlank()) " · ${song.era}" else ""}", color = PC.text3, style = MaterialTheme.typography.bodyMedium)
                    Gap(h = 14.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        DifficultyDots(song.difficulty)
                        Text(formatDuration(song.durationSec), color = PC.text2, style = MaterialTheme.typography.bodyMedium)
                        Text("${song.bars} compases · ${song.meter}", color = PC.text2, style = MaterialTheme.typography.bodyMedium)
                        StarRow(progress?.stars ?: 0, 18.dp)
                    }
                    Gap(h = 14.dp)
                    Thumb(song, Modifier.fillMaxWidth().height(120.dp))
                    Gap(h = 18.dp)
                    if (song.tip.isNotBlank()) {
                        SectionTitle("Para empezar")
                        Text(song.tip, color = PC.text1, style = MaterialTheme.typography.bodyLarge)
                        Gap(h = 14.dp)
                    }
                    SectionTitle("Tonalidad")
                    val orig = Transposer.displayKey(song.originalKey)
                    val now = if (white && plan.shift != 0) Transposer.displayKey(Transposer.transposeKeyName(song.key, plan.shift)) else Transposer.displayKey(song.key)
                    Text(
                        if (white && plan.shift != 0) "Original: $orig. Transportada a $now para usar solo teclas blancas." else "$now (la que suena en esta versión)",
                        color = PC.text1, style = MaterialTheme.typography.bodyMedium,
                    )
                    if (song.fun_.isNotBlank()) {
                        Gap(h = 14.dp)
                        SectionTitle("Curiosidad")
                        Text(song.fun_, color = PC.text2, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        // ---------------- opciones
        Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SectionTitle("Nivel")
                Segmented(
                    song.levels.map { it to "$it · ${song.levelName(it)}" }, level, { level = it },
                )
                Text(
                    "${song.notes(level).size} notas" + when (level) { 1 -> " · solo la melodía con la mano derecha"; 2 -> " · melodía y una nota de bajo por compás"; else -> " · las dos manos" },
                    color = PC.text3, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
                )
                Gap(h = 22.dp)
                if (level > 1) {
                    SectionTitle("Qué mano tocas")
                    Segmented(listOf(HandsMode.BOTH to "Las dos", HandsMode.RIGHT to "Derecha", HandsMode.LEFT to "Izquierda"), hands, { hands = it })
                    Text("La mano que no tocas suena sola a volumen suave.", color = PC.text3, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Gap(h = 22.dp)
                }
                SectionTitle("Modo")
                Segmented(listOf(PracticeMode.WAIT to "Espera", PracticeMode.RHYTHM to "Ritmo", PracticeMode.LISTEN to "Escuchar"), mode, { mode = it })
                Text(
                    when (mode) {
                        PracticeMode.WAIT -> "La música se detiene hasta que tocas la nota correcta. El mejor modo para aprender."
                        PracticeMode.RHYTHM -> "La música sigue y tú la acompañas. Puntúa si aciertas a tiempo."
                        PracticeMode.LISTEN -> "La app toca sola y tú ves cómo se mueven las luces."
                    }, color = PC.text3, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
                )
                Gap(h = 22.dp)
                SectionTitle("Velocidad")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (v in listOf(0.5f, 0.7f, 0.85f, 1f)) Chip("${(v * 100).toInt()} %", speed == v, { speed = v })
                }
                Gap(h = 22.dp)
                SectionTitle("Teclado")
                Segmented(listOf(LayoutMode.REDUCED to "Reducido", LayoutMode.TWO_ROWS to "88 teclas en 2 filas"), layout, { layout = it })
                if (plan.shift != 0 && plan.helps) {
                    Gap(h = 10.dp)
                    SwitchRow(
                        "Solo teclas blancas", white, { white = it },
                        hint = if (plan.allWhite) "Transporta la canción para evitar las teclas negras" else "Reduce las teclas negras de ${plan.blackBefore} a ${plan.blackAfter} notas",
                    )
                }
            }
            Gap(h = 16.dp)
            PrimaryButton(
                "Empezar", { onStart(PracticeParams(song.id, level, effHands, mode, speed, white && plan.shift != 0, layout)) },
                Modifier.fillMaxWidth(), big = true,
            )
        }
    }
}
