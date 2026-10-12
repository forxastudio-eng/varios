package com.forxa.pianocascada

import android.app.Application
import com.forxa.pianocascada.audio.PianoAudio
import com.forxa.pianocascada.data.ProgressStore
import com.forxa.pianocascada.data.SettingsStore
import com.forxa.pianocascada.data.SongRepository
import com.forxa.pianocascada.data.applySound
import com.forxa.pianocascada.transcribe.BasicPitch
import com.forxa.pianocascada.transcribe.Transcriber

/** Contenedor de singletons de la app (sin inyección de dependencias: es una app personal y pequeña). */
class PianoApp : Application() {
    lateinit var settings: SettingsStore
    lateinit var progress: ProgressStore
    lateinit var songs: SongRepository
    lateinit var audio: PianoAudio

    /** Transcriptor de audio (modelo Basic Pitch en ONNX Runtime); se crea al primer uso. */
    val transcriber: Transcriber by lazy { Transcriber(BasicPitch(assets.open("basic_pitch.onnx").readBytes())) }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        progress = ProgressStore(this)
        songs = SongRepository(this)
        songs.load()
        audio = PianoAudio(this)
        if (audio.init()) applySound(audio, settings.value.sound)
    }
}
