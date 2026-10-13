package com.forxa.pianocascada.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.forxa.pianocascada.AppRoot
import com.forxa.pianocascada.PianoApp
import com.forxa.pianocascada.piano.MidiRouter
import com.forxa.pianocascada.ui.components.BackStack
import com.forxa.pianocascada.ui.theme.PianoTheme
import java.awt.Dimension

fun main() = application {
    val app = remember { PianoApp() }
    val state = rememberWindowState(size = DpSize(1366.dp, 860.dp), position = WindowPosition(Alignment.Center))
    LaunchedEffect(Unit) { app.audio.start(); MidiRouter.start() }
    Window(
        onCloseRequest = { app.audio.release(); exitApplication() },
        title = "Piano Cascada",
        state = state,
        onPreviewKeyEvent = { e ->
            when {
                e.type == KeyEventType.KeyDown && e.key == Key.Escape -> BackStack.back()
                e.type == KeyEventType.KeyDown && e.key == Key.F11 -> {
                    state.placement = if (state.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
                    true
                }
                else -> false
            }
        },
    ) {
        window.minimumSize = Dimension(1000, 640)
        PianoTheme { AppRoot(app) }
    }
}
