package com.forxa.pianocascada.importer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.forxa.pianocascada.PianoApp
import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.core.SongJson
import com.forxa.pianocascada.core.SongLibrary
import com.forxa.pianocascada.core.SongSource
import com.forxa.pianocascada.transcribe.AnalyzedAudio
import com.forxa.pianocascada.transcribe.MidiImporter
import com.forxa.pianocascada.transcribe.TranscribeOptions
import com.forxa.pianocascada.transcribe.TranscriptionResult
import java.io.File
import java.util.concurrent.Executors

sealed interface ImportState {
    data object Idle : ImportState
    data class Working(val progress: Float, val message: String, val title: String) : ImportState
    /** [analyzed] solo existe para audio: permite reajustar la sensibilidad al instante. */
    data class Review(
        val title: String, val song: Song, val analyzed: AnalyzedAudio?, val quality: Float,
        val warnings: List<String>, val detail: String,
    ) : ImportState
    data class Failed(val message: String) : ImportState
}

/** Orquesta las importaciones (audio, MIDI, partitura) en un hilo aparte y expone el estado a la interfaz. */
class ImportController(private val app: PianoApp) {
    var state by mutableStateOf<ImportState>(ImportState.Idle)
        private set
    private val exec = Executors.newSingleThreadExecutor()
    @Volatile private var cancelFlag = false

    fun displayName(file: File): String =
        file.name.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim().ifEmpty { "Mi canción" }

    private fun slug(title: String) = "mi-" + title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24) + "-" + (System.currentTimeMillis() / 1000)

    private fun fail(e: Throwable) {
        if (e is InterruptedException) { state = ImportState.Idle; return }
        val msg = when (e) {
            is AudioDecoder.TooLong -> "El audio es demasiado largo (máximo ${e.seconds / 60} minutos). Prueba con un fragmento."
            is com.forxa.pianocascada.omr.OmrException -> e.message ?: "No se pudo leer la partitura"
            is OutOfMemoryError -> "No hay memoria suficiente para este archivo. Prueba con uno más corto."
            else -> e.message ?: "No se pudo importar el archivo"
        }
        state = ImportState.Failed(msg)
    }

    // ------------------------------------------------------------------ audio
    fun importAudio(file: File) {
        val title = displayName(file)
        cancelFlag = false
        state = ImportState.Working(0f, "Leyendo el archivo…", title)
        exec.execute {
            try {
                val dec = AudioDecoder.decode(file, onProgress = { state = ImportState.Working(0.01f + 0.07f * it, "Leyendo el archivo…", title) }, cancelled = { cancelFlag })
                val tr = app.transcriber
                val analyzed = tr.analyze(dec.pcm, dec.sampleRate) { p, m ->
                    if (cancelFlag) throw InterruptedException("cancelado")
                    state = ImportState.Working(0.08f + 0.84f * p, m, title)
                }
                state = ImportState.Working(0.95f, "Preparando tu versión fácil…", title)
                val opts = TranscribeOptions()
                val r = tr.build(analyzed, slug(title), title, SongSource.AUDIO, opts)
                state = review(title, r, analyzed, opts.quality)
            } catch (e: Throwable) { fail(e) }
        }
    }

    private class NoNotes(msg: String) : Exception(msg)

    private fun review(title: String, r: TranscriptionResult, a: AnalyzedAudio, q: Float): ImportState.Review {
        val s = r.song.copy(title = title)
        if (s.notes(3).isEmpty()) throw NoNotes("No se han detectado notas. Prueba con una grabación con más volumen o de piano solo, o baja la sensibilidad.")
        val detail = "${r.rawNotes.size} candidatas → ${r.finalNotes.size} notas · tempo ${s.bpm} ppm · ${s.meter} · ${com.forxa.pianocascada.core.Transposer.displayKey(s.key)}"
        return ImportState.Review(title, s, a, q, emptyList(), detail)
    }

    /** Reajusta la sensibilidad sin volver a ejecutar el modelo (≈ 1 s). */
    fun rebuild(current: ImportState.Review, quality: Float) {
        val a = current.analyzed ?: return
        exec.execute {
            try {
                val opts = TranscribeOptions(quality = quality)
                val r = app.transcriber.build(a, current.song.id, current.title, SongSource.AUDIO, opts)
                state = review(current.title, r, a, quality)
            } catch (e: Throwable) { fail(e) }
        }
    }

    // ------------------------------------------------------------------ MIDI
    fun importMidi(file: File) {
        val title = displayName(file)
        state = ImportState.Working(0.3f, "Leyendo el MIDI…", title)
        exec.execute {
            try {
                val bytes = file.readBytes()
                val song = MidiImporter.parse(bytes, slug(title), title)
                if (song.notes(3).isEmpty()) throw NoNotes("El archivo MIDI no contiene notas.")
                state = ImportState.Review(title, song, null, 0f, emptyList(), "${song.notes(3).size} notas · tempo ${song.bpm} ppm · ${song.meter} · ${com.forxa.pianocascada.core.Transposer.displayKey(song.key)}")
            } catch (e: Throwable) { fail(e) }
        }
    }

    // ------------------------------------------------------------------ canción ya preparada (.json de Piano Cascada), sin internet
    fun importSongFile(file: File) {
        val title = displayName(file)
        state = ImportState.Working(0.3f, "Leyendo la canción…", title)
        exec.execute {
            try {
                val text = file.readText(Charsets.UTF_8)
                val parsed = runCatching { SongLibrary.parse(text).firstOrNull() }.getOrNull()
                    ?: throw NoNotes("Este archivo no es una canción de Piano Cascada.")
                val name = parsed.title.ifBlank { title }
                val song = parsed.copy(id = slug(name), title = name, source = SongSource.PDF)
                if (song.notes(3).isEmpty()) throw NoNotes("La canción no contiene notas.")
                state = ImportState.Review(name, song, null, 0f, emptyList(), "${song.notes(3).size} notas · ${song.bars} compases · ${song.meter} · ${com.forxa.pianocascada.core.Transposer.displayKey(song.key)}")
            } catch (e: Throwable) { fail(e) }
        }
    }

    // ------------------------------------------------------------------ partitura (PDF / foto), dentro de la tablet y sin internet
    fun importScore(file: File) {
        val title = displayName(file)
        cancelFlag = false
        state = ImportState.Working(0f, "Preparando el lector de partituras…", title)
        exec.execute {
            try {
                val out = ScoreReader().read(file, slug(title), title,
                    onProgress = { p, m -> state = ImportState.Working(p, m, title) }, cancelled = { cancelFlag })
                val song = out.song
                if (song.notes(3).isEmpty()) throw NoNotes("No se ha encontrado música en la partitura. Comprueba que la imagen es nítida y está recta.")
                state = ImportState.Review(title, song, null, 0f, out.warnings, "${song.notes(3).size} notas · ${song.bars} compases · ${song.meter} · ${com.forxa.pianocascada.core.Transposer.displayKey(song.key)}")
            } catch (e: Throwable) { fail(e) }
        }
    }

    // ------------------------------------------------------------------ guardar / descartar
    fun save(r: ImportState.Review): String {
        app.songs.addUserSong(r.song.id, SongJson.write(r.song))
        state = ImportState.Idle
        return r.song.id
    }

    fun cancel() { cancelFlag = true; state = ImportState.Idle }
    fun reset() { state = ImportState.Idle }
}
