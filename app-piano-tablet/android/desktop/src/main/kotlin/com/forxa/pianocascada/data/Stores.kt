package com.forxa.pianocascada.data

import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.core.SongLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Carpeta de datos de la app en el PC (ajustes, progreso y canciones propias). */
object AppDirs {
    val base: File by lazy {
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        val dir = when {
            "win" in os -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "PianoCascada")
            "mac" in os -> File(home, "Library/Application Support/PianoCascada")
            else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "PianoCascada")
        }
        dir.mkdirs()
        dir
    }
    val userSongs: File get() = File(base, "user_songs").also { it.mkdirs() }
    val models: File get() = File(base, "omr").also { it.mkdirs() }
}

private fun File.writeAtomic(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) { writeText(text); tmp.delete() }
}

/** Ajustes persistentes (archivo JSON) con un flujo observable desde Compose. */
class SettingsStore(private val file: File = File(AppDirs.base, "ajustes.json")) {
    private val _state = MutableStateFlow(Settings.fromJson(runCatching { file.readText() }.getOrNull()))
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    fun update(block: (Settings) -> Settings) {
        val n = block(_state.value)
        _state.value = n
        runCatching { file.writeAtomic(n.toJson()) }
    }
}

data class SongProgress(
    val stars: Int = 0, val best: Float = 0f, val plays: Int = 0,
    val lastLevel: Int = 1, val favorite: Boolean = false, val lastPlayed: Long = 0L,
)

class ProgressStore(private val file: File = File(AppDirs.base, "progreso.json")) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Map<String, SongProgress>> = _state.asStateFlow()

    private fun load(): Map<String, SongProgress> {
        val t = runCatching { file.readText() }.getOrNull() ?: return emptyMap()
        return runCatching {
            val j = JSONObject(t)
            j.keys().asSequence().associateWith { id ->
                val o = j.getJSONObject(id)
                SongProgress(o.optInt("s"), o.optDouble("b", 0.0).toFloat(), o.optInt("p"), o.optInt("l", 1), o.optBoolean("f"), o.optLong("t"))
            }
        }.getOrDefault(emptyMap())
    }

    private fun save(m: Map<String, SongProgress>) {
        val j = JSONObject()
        for ((id, p) in m) j.put(id, JSONObject().apply {
            put("s", p.stars); put("b", p.best.toDouble()); put("p", p.plays); put("l", p.lastLevel); put("f", p.favorite); put("t", p.lastPlayed)
        })
        runCatching { file.writeAtomic(j.toString()) }
        _state.value = m
    }

    fun get(id: String): SongProgress = _state.value[id] ?: SongProgress()

    fun record(id: String, level: Int, accuracy: Float, stars: Int) {
        val old = get(id)
        save(_state.value + (id to old.copy(
            stars = maxOf(old.stars, stars), best = maxOf(old.best, accuracy), plays = old.plays + 1,
            lastLevel = level, lastPlayed = System.currentTimeMillis(),
        )))
    }

    fun touch(id: String, level: Int) {
        save(_state.value + (id to get(id).copy(lastLevel = level, lastPlayed = System.currentTimeMillis())))
    }

    fun toggleFavorite(id: String) {
        val old = get(id)
        save(_state.value + (id to old.copy(favorite = !old.favorite)))
    }

    fun lastPlayedId(): String? = _state.value.filter { it.value.lastPlayed > 0 }.maxByOrNull { it.value.lastPlayed }?.key
}

/** Repertorio incluido en la app + canciones propias importadas (MP3, PDF, MIDI…). */
class SongRepository {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private fun resource(name: String): String? =
        SongRepository::class.java.getResourceAsStream("/$name")?.use { String(it.readBytes(), Charsets.UTF_8) }

    fun load() {
        val bundled = resource("songs.json")?.let { SongLibrary.parse(it) }.orEmpty()
        // canciones extra del propietario de la app (opcional): si el archivo no existe, no se añade nada
        val extra = runCatching { resource("songs_extra.json")?.let { SongLibrary.parse(it) } }.getOrNull().orEmpty()
        val user = AppDirs.userSongs.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }?.mapNotNull { f ->
            runCatching { SongLibrary.parse(f.readText()) }.getOrNull()
        }?.flatten().orEmpty().mapIndexed { i, s -> s.copy(order = 1000 + i) }
        _songs.value = bundled + extra + user
    }

    fun find(id: String): Song? = _songs.value.firstOrNull { it.id == id }

    fun addUserSong(id: String, json: String) {
        File(AppDirs.userSongs, "$id.json").writeText(json)
        load()
    }

    fun deleteUserSong(id: String) {
        File(AppDirs.userSongs, "$id.json").delete()
        load()
    }
}
