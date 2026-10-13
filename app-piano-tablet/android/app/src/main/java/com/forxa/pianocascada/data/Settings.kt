package com.forxa.pianocascada.data

import com.forxa.pianocascada.audio.P
import com.forxa.pianocascada.audio.PianoAudio
import com.forxa.pianocascada.core.CurveKind
import org.json.JSONArray
import org.json.JSONObject

/** REDUCED: una fila que se desliza · TWO_ROWS: dos filas a medida de la pieza · FULL_88: el piano entero en dos filas. */
enum class LayoutMode { REDUCED, TWO_ROWS, FULL_88 }
enum class KeyLetters { ALL, C_ONLY, NONE }
enum class ColorScheme { BY_HAND, BY_NOTE, BY_OCTAVE, MONO }

/** Un timbre de piano: ecualización, brillo, duración de las notas y sala por defecto. */
data class PianoPreset(
    val name: String, val desc: String,
    val eq: List<Float>, val brightness: Float, val decay: Float, val release: Float, val velExp: Float,
    val reverbMix: Float, val reverbSize: Float, val reverbDamp: Float,
)

object SoundPresets {
    val all = listOf(
        PianoPreset("Gran cola", "Piano de concierto, equilibrado", listOf(0f, 0f, 0f, 0f, 0f), 1.0f, 1.0f, 1.0f, 1.3f, 0.18f, 0.55f, 0.40f),
        PianoPreset("Brillante", "Más ataque y presencia, ideal para pop", listOf(1f, 0f, 0f, 2.5f, 3f), 1.45f, 0.9f, 0.9f, 1.2f, 0.16f, 0.45f, 0.30f),
        PianoPreset("Cálido", "Suave y redondo, estilo jazz", listOf(3f, 2f, 0f, -2f, -5f), 0.70f, 1.15f, 1.1f, 1.4f, 0.20f, 0.60f, 0.55f),
        PianoPreset("Vertical", "Sonido seco de piano de pared", listOf(-2f, 2f, 3f, 1f, -2f), 1.0f, 0.55f, 0.7f, 1.2f, 0.07f, 0.25f, 0.50f),
        PianoPreset("Íntimo", "Apagado, para tocar de noche", listOf(1f, 1f, -1f, -4f, -8f), 0.50f, 0.8f, 0.8f, 1.0f, 0.05f, 0.30f, 0.70f),
    )
    val rooms = listOf("Seca" to 0.0f, "Habitación" to 0.12f, "Estudio" to 0.2f, "Auditorio" to 0.32f, "Catedral" to 0.5f)
}

data class SoundSettings(
    val preset: Int = 0,
    val bass: Float = 0f, val body: Float = 0f, val bright: Float = 0f,   // -1..1 (capa sencilla)
    val eq: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),                      // dB (capa avanzada)
    val reverbMix: Float = -1f,                                           // -1 = el del preset
    val reverbSize: Float = -1f, val reverbDamp: Float = -1f,
    val master: Float = 0.8f, val tuning: Float = 440f,
    val speaker: Boolean = true, val night: Boolean = false,
)

data class KeyboardSettings(
    val curve: CurveKind = CurveKind.NORMAL, val fixedVelocity: Int = 90,
    val zoneLow: Float = 1f, val zoneMid: Float = 1f, val zoneHigh: Float = 1f,
    val backing: Float = 0.55f,                                           // volumen de la mano que no practicas
    val heightScale: Float = 1f, val blackWidth: Float = 0.62f,
    val slide: Boolean = false, val palmReject: Boolean = true,
    val calibMin: Float = 0f, val calibMax: Float = 0f,                   // tamaño de contacto (0 = autocalibrado)
    val layout: LayoutMode = LayoutMode.TWO_ROWS, val reducedSpan: Int = 15, val split: Int = 65,
)

data class DisplaySettings(
    val preset: Int = 0,
    val noteLetters: Boolean = true, val keyLetters: KeyLetters = KeyLetters.ALL,
    val preWarnMs: Int = 600, val colors: ColorScheme = ColorScheme.BY_HAND,
    val fallMs: Int = 3200, val guides: Boolean = true,
)

data class Settings(
    val sound: SoundSettings = SoundSettings(),
    val keyboard: KeyboardSettings = KeyboardSettings(),
    val display: DisplaySettings = DisplaySettings(),
    val onboardingDone: Boolean = false,
    val speed: Float = 0.7f,
    val serverUrl: String = "",
) {
    fun toJson(): String = JSONObject().apply {
        put("sound", JSONObject().apply {
            put("preset", sound.preset); put("bass", sound.bass.toDouble()); put("body", sound.body.toDouble())
            put("bright", sound.bright.toDouble()); put("eq", JSONArray(sound.eq.map { it.toDouble() }))
            put("rMix", sound.reverbMix.toDouble()); put("rSize", sound.reverbSize.toDouble()); put("rDamp", sound.reverbDamp.toDouble())
            put("master", sound.master.toDouble()); put("tuning", sound.tuning.toDouble())
            put("speaker", sound.speaker); put("night", sound.night)
        })
        put("keyboard", JSONObject().apply {
            put("curve", keyboard.curve.name); put("fixed", keyboard.fixedVelocity)
            put("zl", keyboard.zoneLow.toDouble()); put("zm", keyboard.zoneMid.toDouble()); put("zh", keyboard.zoneHigh.toDouble())
            put("backing", keyboard.backing.toDouble()); put("height", keyboard.heightScale.toDouble())
            put("black", keyboard.blackWidth.toDouble()); put("slide", keyboard.slide); put("palm", keyboard.palmReject)
            put("cmin", keyboard.calibMin.toDouble()); put("cmax", keyboard.calibMax.toDouble())
            put("layout", keyboard.layout.name); put("span", keyboard.reducedSpan); put("split", keyboard.split)
        })
        put("display", JSONObject().apply {
            put("preset", display.preset); put("noteLetters", display.noteLetters); put("keyLetters", display.keyLetters.name)
            put("preWarn", display.preWarnMs); put("colors", display.colors.name); put("fall", display.fallMs); put("guides", display.guides)
        })
        put("onboardingDone", onboardingDone); put("speed", speed.toDouble()); put("serverUrl", serverUrl)
    }.toString()

    companion object {
        private inline fun <reified E : Enum<E>> JSONObject.optEnum(k: String, def: E): E =
            runCatching { enumValueOf<E>(optString(k, def.name)) }.getOrDefault(def)

        fun fromJson(text: String?): Settings {
            if (text.isNullOrBlank()) return Settings()
            return runCatching {
                val j = JSONObject(text)
                val s = j.optJSONObject("sound") ?: JSONObject()
                val k = j.optJSONObject("keyboard") ?: JSONObject()
                val d = j.optJSONObject("display") ?: JSONObject()
                val ds = SoundSettings()
                val dk = KeyboardSettings()
                val dd = DisplaySettings()
                val eqArr = s.optJSONArray("eq")
                Settings(
                    sound = SoundSettings(
                        preset = s.optInt("preset", ds.preset).coerceIn(0, SoundPresets.all.size - 1),
                        bass = s.optDouble("bass", 0.0).toFloat(), body = s.optDouble("body", 0.0).toFloat(),
                        bright = s.optDouble("bright", 0.0).toFloat(),
                        eq = List(5) { i -> eqArr?.optDouble(i, 0.0)?.toFloat() ?: 0f },
                        reverbMix = s.optDouble("rMix", -1.0).toFloat(), reverbSize = s.optDouble("rSize", -1.0).toFloat(),
                        reverbDamp = s.optDouble("rDamp", -1.0).toFloat(),
                        master = s.optDouble("master", ds.master.toDouble()).toFloat(),
                        tuning = s.optDouble("tuning", 440.0).toFloat(),
                        speaker = s.optBoolean("speaker", ds.speaker), night = s.optBoolean("night", ds.night),
                    ),
                    keyboard = KeyboardSettings(
                        curve = k.optEnum("curve", dk.curve), fixedVelocity = k.optInt("fixed", dk.fixedVelocity),
                        zoneLow = k.optDouble("zl", 1.0).toFloat(), zoneMid = k.optDouble("zm", 1.0).toFloat(),
                        zoneHigh = k.optDouble("zh", 1.0).toFloat(),
                        backing = k.optDouble("backing", dk.backing.toDouble()).toFloat(),
                        heightScale = k.optDouble("height", 1.0).toFloat(), blackWidth = k.optDouble("black", 0.62).toFloat(),
                        slide = k.optBoolean("slide", dk.slide), palmReject = k.optBoolean("palm", dk.palmReject),
                        calibMin = k.optDouble("cmin", 0.0).toFloat(), calibMax = k.optDouble("cmax", 0.0).toFloat(),
                        layout = k.optEnum("layout", dk.layout), reducedSpan = k.optInt("span", dk.reducedSpan).coerceIn(8, 28),
                        split = k.optInt("split", dk.split).let { if (it in 48..77 && !com.forxa.pianocascada.core.Notes.isBlack(it) && !com.forxa.pianocascada.core.Notes.isBlack(it - 1)) it else dk.split },
                    ),
                    display = DisplaySettings(
                        preset = d.optInt("preset", 0), noteLetters = d.optBoolean("noteLetters", dd.noteLetters),
                        keyLetters = d.optEnum("keyLetters", dd.keyLetters), preWarnMs = d.optInt("preWarn", dd.preWarnMs),
                        colors = d.optEnum("colors", dd.colors), fallMs = d.optInt("fall", dd.fallMs),
                        guides = d.optBoolean("guides", dd.guides),
                    ),
                    onboardingDone = j.optBoolean("onboardingDone", false),
                    speed = j.optDouble("speed", 0.7).toFloat(),
                    serverUrl = j.optString("serverUrl", ""),
                )
            }.getOrDefault(Settings())
        }
    }
}

/** Traduce los ajustes de sonido a parámetros del motor. */
fun applySound(audio: PianoAudio, s: SoundSettings) {
    val pr = SoundPresets.all[s.preset.coerceIn(0, SoundPresets.all.size - 1)]
    val eq = FloatArray(5) { pr.eq[it] + s.eq.getOrElse(it) { 0f } }
    eq[0] += s.bass * 8f
    eq[1] += s.body * 6f; eq[2] += s.body * 3f
    eq[3] += s.bright * 6f; eq[4] += s.bright * 8f
    for (i in 0 until 5) audio.setParam(P.EQ0 + i, eq[i].coerceIn(-12f, 12f))
    audio.setParam(P.MASTER, s.master * (if (s.night) 0.65f else 1f))
    audio.setParam(P.VEL_EXP, if (s.night) 0.85f else pr.velExp)
    audio.setParam(P.BRIGHTNESS, pr.brightness * (if (s.night) 0.8f else 1f))
    audio.setParam(P.DECAY_SCALE, pr.decay)
    audio.setParam(P.RELEASE_SCALE, pr.release)
    audio.setParam(P.REVERB_MIX, if (s.reverbMix >= 0f) s.reverbMix else pr.reverbMix)
    audio.setParam(P.REVERB_SIZE, if (s.reverbSize >= 0f) s.reverbSize else pr.reverbSize)
    audio.setParam(P.REVERB_DAMP, if (s.reverbDamp >= 0f) s.reverbDamp else pr.reverbDamp)
    audio.setParam(P.TUNING_A4, s.tuning)
    audio.setParam(P.SPEAKER, if (s.speaker) 1f else 0f)
}
