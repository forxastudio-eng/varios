package com.forxa.pianocascada.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

/** «Atrás» en el PC = tecla Esc. Se apila: responde el último que se registró y está activo. */
object BackStack {
    private class Entry(val enabled: () -> Boolean, val action: () -> Unit)
    private val entries = ArrayList<Entry>()

    internal fun push(e: Any) { entries.add(e as Entry) }
    internal fun remove(e: Any) { entries.remove(e as Entry) }
    internal fun make(enabled: () -> Boolean, action: () -> Unit): Any = Entry(enabled, action)

    /** Devuelve true si alguien lo atendió. */
    fun back(): Boolean {
        val e = entries.lastOrNull { it.enabled() } ?: return false
        e.action()
        return true
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val en = rememberUpdatedState(enabled)
    val act = rememberUpdatedState(onBack)
    DisposableEffect(Unit) {
        val e = BackStack.make({ en.value }, { act.value() })
        BackStack.push(e)
        onDispose { BackStack.remove(e) }
    }
}
