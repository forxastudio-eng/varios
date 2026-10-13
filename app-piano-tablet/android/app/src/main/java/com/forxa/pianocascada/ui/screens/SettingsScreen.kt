package com.forxa.pianocascada.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.forxa.pianocascada.PianoApp
import com.forxa.pianocascada.core.CurveKind
import com.forxa.pianocascada.data.ColorScheme
import com.forxa.pianocascada.data.DisplaySettings
import com.forxa.pianocascada.data.KeyLetters
import com.forxa.pianocascada.data.LayoutMode
import com.forxa.pianocascada.data.Settings
import com.forxa.pianocascada.data.SoundPresets
import com.forxa.pianocascada.piano.PracticeController
import com.forxa.pianocascada.ui.components.Chip
import com.forxa.pianocascada.ui.components.G
import com.forxa.pianocascada.ui.components.Gap
import com.forxa.pianocascada.ui.components.IconBtn
import com.forxa.pianocascada.ui.components.LabeledSlider
import com.forxa.pianocascada.ui.components.SectionTitle
import com.forxa.pianocascada.ui.components.Segmented
import com.forxa.pianocascada.ui.components.SwitchRow
import com.forxa.pianocascada.ui.theme.PC

private enum class Tab(val label: String) { SOUND("Sonido"), KEYBOARD("Teclado"), DISPLAY("Señalización") }

@Composable
fun SettingsScreen(app: PianoApp, settings: Settings, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.SOUND) }
    val st = app.settings
    // teclado de prueba al pie: sirve para oír y sentir cada cambio al instante
    val tester = remember { PracticeController(app.audio, settings, null) }
    androidx.compose.runtime.LaunchedEffect(settings) { tester.updateSettings(settings) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { tester.dispose() } }

    Column(Modifier.fillMaxSize().background(PC.bg).padding(horizontal = 48.dp, vertical = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            IconBtn(G.BACK, onBack)
            Text("Ajustes", color = PC.text1, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            for (t in Tab.values()) Chip(t.label, tab == t, { tab = t })
        }
        Gap(h = 16.dp)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (tab) {
                Tab.SOUND -> SoundTab(settings) { f -> st.update { it.copy(sound = f(it.sound)) } }
                Tab.KEYBOARD -> KeyboardTab(settings) { f -> st.update { it.copy(keyboard = f(it.keyboard)) } }
                Tab.DISPLAY -> DisplayTab(settings) { f -> st.update { it.copy(display = f(it.display)) } }
            }
            Gap(h = 12.dp)
        }
        Gap(h = 8.dp)
        Text("Prueba aquí tus cambios", color = PC.text3, style = MaterialTheme.typography.labelMedium)
        Gap(h = 6.dp)
        PianoHost(
            settings, null, tester, Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(16.dp)),
            keyboardOnly = true, staticLeft = 16f, span = 22,
        )
    }
}

@Composable
private fun PresetCard(title: String, desc: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) PC.surface3 else PC.surface2)
            .border(1.dp, if (selected) PC.right else PC.surface2, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(16.dp),
    ) {
        Text(title, color = PC.text1, style = MaterialTheme.typography.titleMedium)
        Text(desc, color = PC.text2, style = MaterialTheme.typography.bodySmall)
    }
}

// ------------------------------------------------------------------ Sonido (ecualizador de sonido)
@Composable
private fun SoundTab(s: Settings, edit: ((com.forxa.pianocascada.data.SoundSettings) -> com.forxa.pianocascada.data.SoundSettings) -> Unit) {
    val snd = s.sound
    var advanced by remember { mutableStateOf(false) }
    SectionTitle("Piano")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SoundPresets.all.forEachIndexed { i, p ->
            PresetCard(p.name, p.desc, snd.preset == i, { edit { it.copy(preset = i, reverbMix = -1f, reverbSize = -1f, reverbDamp = -1f) } }, Modifier.weight(1f))
        }
    }
    Gap(h = 22.dp)
    SectionTitle("Ecualizador sencillo")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f)) {
            LabeledSlider("Graves", snd.bass, -1f..1f, { v -> edit { it.copy(bass = v) } }, { "%+.0f".format(it * 100) + " %" })
            LabeledSlider("Cuerpo", snd.body, -1f..1f, { v -> edit { it.copy(body = v) } }, { "%+.0f".format(it * 100) + " %" })
            LabeledSlider("Brillo", snd.bright, -1f..1f, { v -> edit { it.copy(bright = v) } }, { "%+.0f".format(it * 100) + " %" })
        }
        Column(Modifier.weight(1f)) {
            LabeledSlider("Volumen", snd.master, 0f..1f, { v -> edit { it.copy(master = v) } }, { "%.0f".format(it * 100) + " %" })
            SwitchRow("Modo noche", snd.night, { v -> edit { it.copy(night = v) } }, "Suaviza el volumen y el brillo para tocar bajito")
            SwitchRow("Perfil de altavoces de la tablet", snd.speaker, { v -> edit { it.copy(speaker = v) } }, "Quita graves inútiles y realza la presencia; desactívalo con auriculares")
        }
    }
    Gap(h = 14.dp)
    SectionTitle("Sala")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        SoundPresets.rooms.forEach { (name, mix) ->
            val current = if (snd.reverbMix >= 0f) snd.reverbMix else SoundPresets.all[snd.preset].reverbMix
            Chip(name, kotlin.math.abs(current - mix) < 0.03f, { edit { it.copy(reverbMix = mix) } })
        }
    }
    Gap(h = 18.dp)
    Chip(if (advanced) "Ocultar ecualizador avanzado" else "Mostrar ecualizador avanzado", advanced, { advanced = !advanced })
    if (advanced) {
        Gap(h = 14.dp)
        SectionTitle("Ecualizador de 5 bandas")
        val names = listOf("60 Hz", "250 Hz", "1 kHz", "4 kHz", "12 kHz")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            for (i in 0 until 5) {
                Column(Modifier.weight(1f)) {
                    LabeledSlider(names[i], snd.eq[i], -12f..12f, { v -> edit { it.copy(eq = it.eq.toMutableList().also { l -> l[i] = v }) } }, { "%+.1f dB".format(it) })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            val cur = SoundPresets.all[snd.preset]
            Column(Modifier.weight(1f)) {
                LabeledSlider("Tamaño de la sala", if (snd.reverbSize >= 0f) snd.reverbSize else cur.reverbSize, 0f..1f, { v -> edit { it.copy(reverbSize = v) } }, { "%.0f".format(it * 100) + " %" })
                LabeledSlider("Absorción de la sala", if (snd.reverbDamp >= 0f) snd.reverbDamp else cur.reverbDamp, 0f..1f, { v -> edit { it.copy(reverbDamp = v) } }, { "%.0f".format(it * 100) + " %" })
            }
            Column(Modifier.weight(1f)) {
                LabeledSlider("Afinación (La4)", snd.tuning, 415f..466f, { v -> edit { it.copy(tuning = Math.round(v).toFloat()) } }, { "%.0f Hz".format(it) })
            }
        }
        Chip("Restablecer ecualizador", false, { edit { it.copy(bass = 0f, body = 0f, bright = 0f, eq = listOf(0f, 0f, 0f, 0f, 0f), reverbMix = -1f, reverbSize = -1f, reverbDamp = -1f, tuning = 440f) } })
    }
}

// ------------------------------------------------------------------ Teclado (ecualizador de teclado)
@Composable
private fun KeyboardTab(s: Settings, edit: ((com.forxa.pianocascada.data.KeyboardSettings) -> com.forxa.pianocascada.data.KeyboardSettings) -> Unit) {
    val k = s.keyboard
    SectionTitle("Disposición")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f)) {
            Segmented(listOf(LayoutMode.TWO_ROWS to "2 filas a medida", LayoutMode.REDUCED to "1 fila deslizable", LayoutMode.FULL_88 to "Piano 88"), k.layout, { v -> edit { it.copy(layout = v) } })
            Text(
                when (k.layout) {
                    LayoutMode.TWO_ROWS -> "Recomendado. Solo las teclas que usa la canción, en dos filas (graves abajo, agudos arriba): salen del tamaño de un piano de verdad."
                    LayoutMode.REDUCED -> "Una sola fila de teclas grandes. Se desplaza sola hacia las notas que vienen, y tú puedes arrastrar la cascada, el mapa de arriba o usar los botones laterales."
                    LayoutMode.FULL_88 -> "Las 88 teclas partidas en dos filas iguales (corte entre E4 y F4). Las teclas son pequeñas (≈ 1,2 cm)."
                },
                color = PC.text3, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            if (k.layout == LayoutMode.REDUCED) {
                Segmented(listOf(15 to "2 octavas", 22 to "3 octavas", 29 to "4 octavas"), k.reducedSpan, { v -> edit { it.copy(reducedSpan = v) } })
            } else if (k.layout == LayoutMode.FULL_88) {
                Segmented(listOf(60 to "Corte en C4", 65 to "Corte en F4", 67 to "Corte en G4"), k.split, { v -> edit { it.copy(split = v) } })
            }
        }
    }
    Gap(h = 22.dp)
    SectionTitle("Respuesta al toque")
    Segmented(listOf(CurveKind.SOFT to "Suave", CurveKind.NORMAL to "Normal", CurveKind.HARD to "Dura", CurveKind.FIXED to "Fija"), k.curve, { v -> edit { it.copy(curve = v) } })
    Text("Mide el tamaño de tu dedo en la pantalla: más superficie = nota más fuerte. «Fija» suena siempre igual.", color = PC.text3, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
    if (k.curve == CurveKind.FIXED) LabeledSlider("Velocidad fija", k.fixedVelocity.toFloat(), 20f..127f, { v -> edit { it.copy(fixedVelocity = Math.round(v)) } }, { "%.0f".format(it) })
    Gap(h = 14.dp)
    SectionTitle("Sensibilidad por zonas del teclado")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f)) { LabeledSlider("Graves", k.zoneLow, 0.5f..1.5f, { v -> edit { it.copy(zoneLow = v) } }, { "%.0f".format(it * 100) + " %" }) }
        Column(Modifier.weight(1f)) { LabeledSlider("Centro", k.zoneMid, 0.5f..1.5f, { v -> edit { it.copy(zoneMid = v) } }, { "%.0f".format(it * 100) + " %" }) }
        Column(Modifier.weight(1f)) { LabeledSlider("Agudos", k.zoneHigh, 0.5f..1.5f, { v -> edit { it.copy(zoneHigh = v) } }, { "%.0f".format(it * 100) + " %" }) }
    }
    Gap(h = 14.dp)
    SectionTitle("Forma y comportamiento")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f)) {
            LabeledSlider("Altura del teclado", k.heightScale, 0.8f..1.25f, { v -> edit { it.copy(heightScale = v) } }, { "%.0f".format(it * 100) + " %" })
            LabeledSlider("Ancho de las teclas negras", k.blackWidth, 0.5f..0.78f, { v -> edit { it.copy(blackWidth = v) } }, { "%.0f".format(it * 100) + " %" })
            LabeledSlider("Volumen de la mano que no practicas", k.backing, 0f..1f, { v -> edit { it.copy(backing = v) } }, { "%.0f".format(it * 100) + " %" })
        }
        Column(Modifier.weight(1f)) {
            SwitchRow("Deslizar entre teclas", k.slide, { v -> edit { it.copy(slide = v) } }, "Permite hacer glissandos; desactivado evita notas accidentales")
            SwitchRow("Rechazo de palma", k.palmReject, { v -> edit { it.copy(palmReject = v) } }, "Ignora los apoyos grandes de la muñeca")
        }
    }
}

// ------------------------------------------------------------------ Señalización
@Composable
private fun DisplayTab(s: Settings, edit: ((DisplaySettings) -> DisplaySettings) -> Unit) {
    val d = s.display
    SectionTitle("Presets para ir progresando")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PresetCard("Guiado", "Letras en notas y teclas, luz de aviso. Primeras semanas.", d.preset == 0, {
            edit { it.copy(preset = 0, noteLetters = true, keyLetters = KeyLetters.ALL, preWarnMs = 600, guides = true) }
        }, Modifier.weight(1f))
        PresetCard("Confianza", "Letras solo en las notas y en las C. Ya conoces el teclado.", d.preset == 1, {
            edit { it.copy(preset = 1, noteLetters = true, keyLetters = KeyLetters.C_ONLY, preWarnMs = 300, guides = true) }
        }, Modifier.weight(1f))
        PresetCard("Memoria", "Sin letras ni aviso: comprueba que te la sabes.", d.preset == 2, {
            edit { it.copy(preset = 2, noteLetters = false, keyLetters = KeyLetters.NONE, preWarnMs = 0, guides = false) }
        }, Modifier.weight(1f))
    }
    Gap(h = 22.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f)) {
            SectionTitle("Letras (C D E F G A B)")
            SwitchRow("Letra dentro de cada nota que cae", d.noteLetters, { v -> edit { it.copy(noteLetters = v) } })
            Text("Letras en las teclas", color = PC.text1, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            Segmented(listOf(KeyLetters.ALL to "Todas", KeyLetters.C_ONLY to "Solo las C", KeyLetters.NONE to "Ninguna"), d.keyLetters, { v -> edit { it.copy(keyLetters = v) } })
            Gap(h = 18.dp)
            LabeledSlider("Luz de aviso antes de la nota", d.preWarnMs.toFloat(), 0f..1000f, { v -> edit { it.copy(preWarnMs = Math.round(v / 50f) * 50) } }, { if (it < 25f) "Sin aviso" else "%.0f ms".format(it) })
        }
        Column(Modifier.weight(1f)) {
            SectionTitle("Colores")
            Segmented(listOf(ColorScheme.BY_HAND to "Por mano", ColorScheme.BY_NOTE to "Por nota", ColorScheme.BY_OCTAVE to "Por octava", ColorScheme.MONO to "Un color"), d.colors, { v -> edit { it.copy(colors = v) } })
            Text("Por mano: derecha azul hielo, izquierda ámbar.", color = PC.text3, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            Gap(h = 18.dp)
            LabeledSlider("Velocidad de caída (tiempo visible)", d.fallMs.toFloat(), 1500f..6000f, { v -> edit { it.copy(fallMs = Math.round(v / 100f) * 100) } }, { "%.1f s".format(it / 1000f) }, hint = "Más segundos = las notas bajan más despacio y se ven con más antelación")
            SwitchRow("Guías verticales", d.guides, { v -> edit { it.copy(guides = v) } }, "Líneas tenues de cada tecla en la cascada")
        }
    }
}
