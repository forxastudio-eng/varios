package com.forxa.pianocascada.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.forxa.pianocascada.data.KeyLetters
import com.forxa.pianocascada.data.LayoutMode
import com.forxa.pianocascada.data.Settings
import com.forxa.pianocascada.piano.FrameSource
import com.forxa.pianocascada.piano.KeyListener
import com.forxa.pianocascada.piano.PianoState
import com.forxa.pianocascada.piano.PianoView

/** Aloja la vista de piano dentro de Compose y le pasa los ajustes. */
@Composable
fun PianoHost(
    settings: Settings,
    source: FrameSource?,
    listener: KeyListener?,
    modifier: Modifier = Modifier,
    layout: LayoutMode = settings.keyboard.layout,
    keyboardOnly: Boolean = false,
    staticLeft: Float = -1f,
    fitRange: IntRange? = null,
    highlight: IntArray = IntArray(0),
    span: Int = settings.keyboard.reducedSpan,
    labels: KeyLetters? = null,
    onSpace: ((Boolean) -> Unit)? = null,
    onEnter: (() -> Unit)? = null,
    onState: ((PianoState) -> Unit)? = null,
) {
    val v = remember { PianoState().also { onState?.invoke(it) } }
    if (v.source !== source) v.source = source
    if (v.listener !== listener) v.listener = listener
    v.layoutMode = layout
    v.keyboardSettings = settings.keyboard
    v.display = settings.display
    v.reducedSpan = span
    v.split = settings.keyboard.split
    v.keyboardOnly = keyboardOnly
    v.staticLeft = staticLeft
    v.fitMin = fitRange?.first ?: -1
    v.fitMax = fitRange?.last ?: -1
    v.highlight = highlight
    v.showLabelsOverride = labels
    PianoView(v, modifier, onSpace, onEnter)
}
