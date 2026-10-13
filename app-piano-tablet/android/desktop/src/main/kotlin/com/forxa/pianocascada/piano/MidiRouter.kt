package com.forxa.pianocascada.piano

import androidx.compose.runtime.mutableStateListOf
import javax.sound.midi.MidiDevice
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.ShortMessage
import javax.swing.SwingUtilities

/**
 * Escucha todos los pianos/teclados MIDI conectados por USB (también los que se enchufan con la app abierta)
 * y reenvía las notas a la pantalla de piano activa.
 */
object MidiRouter {
    @Volatile var target: PianoState? = null
    val devices = mutableStateListOf<String>()
    private val open = HashMap<String, MidiDevice>()
    private var started = false

    private class Rx(val name: String) : Receiver {
        override fun send(m: MidiMessage, timeStamp: Long) {
            val sm = m as? ShortMessage ?: return
            val t = target ?: return
            val data1 = sm.data1; val data2 = sm.data2
            when (sm.command) {
                ShortMessage.NOTE_ON -> if (data2 > 0) SwingUtilities.invokeLater { t.externalDown(data1, data2) } else SwingUtilities.invokeLater { t.externalUp(data1) }
                ShortMessage.NOTE_OFF -> SwingUtilities.invokeLater { t.externalUp(data1) }
                ShortMessage.CONTROL_CHANGE -> if (data1 == 64) SwingUtilities.invokeLater { t.externalPedal(data2 >= 64) }
            }
        }
        override fun close() {}
    }

    @Synchronized fun start() {
        if (started) return
        started = true
        Thread({
            while (true) {
                runCatching { scan() }
                Thread.sleep(3000)
            }
        }, "midi-scan").also { it.isDaemon = true; it.start() }
    }

    private fun scan() {
        val infos = MidiSystem.getMidiDeviceInfo()
        val present = HashSet<String>()
        for (info in infos) {
            val key = "${info.name}|${info.vendor}|${info.description}"
            val dev = runCatching { MidiSystem.getMidiDevice(info) }.getOrNull() ?: continue
            if (dev is javax.sound.midi.Sequencer || dev is javax.sound.midi.Synthesizer) continue
            if (dev.maxTransmitters == 0) continue
            present.add(key)
            if (key in open) continue
            runCatching {
                dev.open()
                dev.transmitter.receiver = Rx(info.name)
                open[key] = dev
                SwingUtilities.invokeLater { devices.add(info.name) }
            }
        }
        for (k in open.keys.toList()) if (k !in present) {
            val name = k.substringBefore('|')
            runCatching { open.remove(k)?.close() }
            SwingUtilities.invokeLater { devices.remove(name) }
        }
    }
}
