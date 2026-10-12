package com.forxa.pianocascada.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.core.SongSource
import com.forxa.pianocascada.data.SongProgress
import com.forxa.pianocascada.ui.components.Card
import com.forxa.pianocascada.ui.components.Chip
import com.forxa.pianocascada.ui.components.DifficultyDots
import com.forxa.pianocascada.ui.components.G
import com.forxa.pianocascada.ui.components.Gap
import com.forxa.pianocascada.ui.components.Glyph
import com.forxa.pianocascada.ui.components.IconBtn
import com.forxa.pianocascada.ui.components.PrimaryButton
import com.forxa.pianocascada.ui.components.StarRow
import com.forxa.pianocascada.ui.theme.PC

private enum class Filter(val label: String) {
    ALL("Todas"), EASY("Fáciles"), PROGRESS("En progreso"), FAV("Favoritas"),
    BAROQUE("Barroco"), CLASSIC("Clasicismo"), ROMANTIC("Romanticismo"), MINE("Mías"),
}

fun formatDuration(sec: Float): String {
    val s = sec.toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun LibraryScreen(
    songs: List<Song>, progress: Map<String, SongProgress>,
    onBack: () -> Unit, onOpen: (Song) -> Unit, onFavorite: (Song) -> Unit, onImport: () -> Unit,
) {
    var filter by remember { mutableStateOf(Filter.ALL) }
    var query by remember { mutableStateOf("") }
    val shown = songs.filter { s ->
        val p = progress[s.id]
        val okFilter = when (filter) {
            Filter.ALL -> true
            Filter.EASY -> s.difficulty <= 2
            Filter.PROGRESS -> (p?.plays ?: 0) > 0 && (p?.stars ?: 0) < 3
            Filter.FAV -> p?.favorite == true
            Filter.BAROQUE -> s.era.contains("Barroco", true)
            Filter.CLASSIC -> s.era.contains("Clasicismo", true)
            Filter.ROMANTIC -> s.era.contains("Romanticismo", true)
            Filter.MINE -> s.isUserSong
        }
        okFilter && (query.isBlank() || s.title.contains(query, true) || s.composer.contains(query, true))
    }
    Column(Modifier.fillMaxSize().background(PC.bg).padding(horizontal = 48.dp, vertical = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            IconBtn(G.BACK, onBack)
            Text("Mi biblioteca", color = PC.text1, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            Box(
                Modifier.width300().height(48.dp).clip(RoundedCornerShape(14.dp)).background(PC.surface2).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Glyph(G.SEARCH, Modifier.size(20.dp), PC.text3)
                    Box {
                        if (query.isEmpty()) Text("Buscar título o compositor", color = PC.text3, style = MaterialTheme.typography.bodyMedium)
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(color = PC.text1, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                            cursorBrush = SolidColor(PC.text1), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            PrimaryButton("Añadir canción", onImport)
        }
        Gap(h = 18.dp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (f in Filter.values()) Chip(f.label, filter == f, { filter = f })
        }
        Gap(h = 20.dp)
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (filter == Filter.MINE) "Aún no has añadido canciones propias." else "No hay canciones con este filtro.", color = PC.text2, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { s -> SongCard(s, progress[s.id], { onOpen(s) }, { onFavorite(s) }) }
            }
        }
    }
}

private fun Modifier.width300(): Modifier = this.then(Modifier.size(width = 300.dp, height = 48.dp))

@Composable
fun SongCard(song: Song, p: SongProgress?, onClick: () -> Unit, onFavorite: () -> Unit) {
    Card(Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Box(Modifier.fillMaxWidth().height(110.dp).background(PC.surface2)) {
                Thumb(song, Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(40.dp).clickable(onClick = onFavorite), contentAlignment = Alignment.Center) {
                    Glyph(G.HEART, Modifier.size(22.dp), if (p?.favorite == true) PC.miss else PC.text3, filled = p?.favorite == true)
                }
            }
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(song.title, color = PC.text1, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                Text(song.composer, color = PC.text2, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Gap(h = 12.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DifficultyDots(song.difficulty)
                    Text(formatDuration(song.durationSec), color = PC.text3, style = MaterialTheme.typography.bodySmall)
                    Box(Modifier.weight(1f))
                    StarRow(p?.stars ?: 0, 15.dp)
                }
                Gap(h = 8.dp)
                val badge = when (song.source) {
                    SongSource.SCORE -> "Partitura exacta"
                    SongSource.TRANSCRIPTION -> "Transcripción"
                    SongSource.AUDIO -> "Desde audio"
                    SongSource.PDF -> "Desde PDF"
                    SongSource.IMPORTED -> "Importada"
                }
                Text(badge, color = if (song.source == SongSource.SCORE) PC.ok else PC.text3, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
