package com.forxa.pianocascada.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.forxa.pianocascada.data.KeyLetters
import com.forxa.pianocascada.data.LayoutMode
import com.forxa.pianocascada.data.Settings
import com.forxa.pianocascada.piano.FrameSource
import com.forxa.pianocascada.piano.KeyListener
import com.forxa.pianocascada.piano.PianoSurfaceView

/** Aloja la vista de piano dentro de Compose y le pasa los ajustes sin recrearla. */
@Composable
fun PianoHost(
    settings: Settings,
    source: FrameSource?,
    listener: KeyListener?,
    modifier: Modifier = Modifier,
    layout: LayoutMode = settings.keyboard.layout,
    keyboardOnly: Boolean = false,
    staticLeft: Float = -1f,
    highlight: IntArray = IntArray(0),
    span: Int = settings.keyboard.reducedSpan,
    labels: KeyLetters? = null,
    onView: ((PianoSurfaceView) -> Unit)? = null,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> PianoSurfaceView(ctx).also { onView?.invoke(it) } },
        update = { v ->
            if (v.source !== source) v.source = source
            if (v.listener !== listener) v.listener = listener
            if (v.layoutMode != layout) v.layoutMode = layout
            if (v.keyboardSettings != settings.keyboard) v.keyboardSettings = settings.keyboard
            v.display = settings.display
            if (v.reducedSpan != span) v.reducedSpan = span
            if (v.split != settings.keyboard.split) v.split = settings.keyboard.split
            if (v.keyboardOnly != keyboardOnly) v.keyboardOnly = keyboardOnly
            v.staticLeft = staticLeft
            v.highlight = highlight
            v.showLabelsOverride = labels
        },
    )
}
