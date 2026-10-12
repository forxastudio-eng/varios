package com.forxa.pianocascada.data

import android.content.Context
import com.forxa.pianocascada.core.Song
import com.forxa.pianocascada.core.SongLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/** Ajustes persistentes (SharedPreferences) con un flujo observable desde Compose. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(Settings.fromJson(prefs.getString("json", null)))
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    fun update(block: (Settings) -> Settings) {
        val n = block(_state.value)
        _state.value = n
        prefs.edit().putString("json", n.toJson()).apply()
    }
}

data class SongProgress(
    val stars: Int = 0, val best: Float = 0f, val plays: Int = 0,
    val lastLevel: Int = 1, val favorite: Boolean = false, val lastPlayed: Long = 0L,
)

class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("progreso", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Map<String, SongProgress>> = _state.asStateFlow()

    private fun load(): Map<String, SongProgress> {
        val t = prefs.getString("json", null) ?: return emptyMap()
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
        prefs.edit().putString("json", j.toString()).apply()
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
class SongRepository(private val context: Context) {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private val userDir get() = File(context.filesDir, "user_songs").also { it.mkdirs() }

    fun load() {
        val bundled = context.assets.open("songs.json").bufferedReader().use { SongLibrary.parse(it.readText()) }
        val user = userDir.listFiles { f -> f.extension == "json" }?.mapNotNull { f ->
            runCatching { SongLibrary.parse(f.readText()) }.getOrNull()
        }?.flatten().orEmpty().mapIndexed { i, s -> s.copy(order = 1000 + i) }
        _songs.value = bundled + user
    }

    fun find(id: String): Song? = _songs.value.firstOrNull { it.id == id }

    fun addUserSong(id: String, json: String) {
        File(userDir, "$id.json").writeText(json)
        load()
    }

    fun deleteUserSong(id: String) {
        File(userDir, "$id.json").delete()
        load()
    }
}
