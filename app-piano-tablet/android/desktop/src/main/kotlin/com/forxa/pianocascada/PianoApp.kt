package com.forxa.pianocascada

import com.forxa.pianocascada.audio.PianoAudio
import com.forxa.pianocascada.data.ProgressStore
import com.forxa.pianocascada.data.SettingsStore
import com.forxa.pianocascada.data.SongRepository
import com.forxa.pianocascada.data.applySound
import com.forxa.pianocascada.transcribe.BasicPitch
import com.forxa.pianocascada.transcribe.Transcriber

/** Contenedor de singletons de la app (sin inyección de dependencias: es una app personal y pequeña). */
class PianoApp {
    val settings = SettingsStore()
    val progress = ProgressStore()
    val songs = SongRepository().also { it.load() }
    val audio = PianoAudio()

    /** Transcriptor de audio (modelo Basic Pitch en ONNX Runtime); se crea al primer uso. */
    val transcriber: Transcriber by lazy {
        val bytes = PianoApp::class.java.getResourceAsStream("/basic_pitch.onnx")!!.use { it.readBytes() }
        Transcriber(BasicPitch(bytes))
    }

    /** Gancho de pruebas: se llama al crear el controlador de una pantalla de práctica. */
    var onPracticeController: ((com.forxa.pianocascada.piano.PracticeController) -> Unit)? = null

    init {
        if (audio.init()) applySound(audio, settings.value.sound)
    }
}
