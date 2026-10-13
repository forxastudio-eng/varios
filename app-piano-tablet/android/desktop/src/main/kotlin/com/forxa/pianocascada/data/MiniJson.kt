package com.forxa.pianocascada.data

import com.forxa.pianocascada.core.Json

/** Pequeño sustituto de org.json (que solo existe en Android) con lo que usan los ajustes y el progreso. */
class JSONArray(private val items: MutableList<Any?> = ArrayList()) {
    constructor(src: Collection<*>) : this(ArrayList<Any?>(src))
    fun optDouble(i: Int, def: Double): Double = (items.getOrNull(i) as? Number)?.toDouble() ?: def
    override fun toString(): String = MiniJson.write(items)
    internal fun raw() = items
}

class JSONObject(private val map: MutableMap<String, Any?> = LinkedHashMap()) {
    constructor(text: String) : this(((Json.parse(text) as? Map<*, *>) ?: error("no es un objeto JSON")).entries.associate { it.key as String to it.value }.toMutableMap())

    fun put(k: String, v: Any?): JSONObject { map[k] = if (v is JSONObject) v.map else if (v is JSONArray) v.raw() else v; return this }
    fun keys(): Iterator<String> = map.keys.toList().iterator()
    fun optInt(k: String, def: Int = 0): Int = (map[k] as? Number)?.toInt() ?: def
    fun optLong(k: String, def: Long = 0L): Long = (map[k] as? Number)?.toLong() ?: def
    fun optDouble(k: String, def: Double = 0.0): Double = (map[k] as? Number)?.toDouble() ?: def
    fun optBoolean(k: String, def: Boolean = false): Boolean = (map[k] as? Boolean) ?: def
    fun optString(k: String, def: String = ""): String = (map[k] as? String) ?: def
    @Suppress("UNCHECKED_CAST")
    fun optJSONObject(k: String): JSONObject? = (map[k] as? Map<String, Any?>)?.let { JSONObject(it.toMutableMap()) }
    @Suppress("UNCHECKED_CAST")
    fun optJSONArray(k: String): JSONArray? = (map[k] as? List<Any?>)?.let { JSONArray(it.toMutableList()) }
    fun getJSONObject(k: String): JSONObject = optJSONObject(k) ?: error("falta $k")
    override fun toString(): String = MiniJson.write(map)
}

internal object MiniJson {
    fun write(v: Any?): String = StringBuilder().also { write(it, v) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> str(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v.toString())
            is Number -> { val d = v.toDouble(); sb.append(if (d == Math.rint(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()) }
            is Map<*, *> -> { sb.append('{'); var first = true; for ((k, x) in v) { if (!first) sb.append(','); first = false; str(sb, k.toString()); sb.append(':'); write(sb, x) }; sb.append('}') }
            is List<*> -> { sb.append('['); v.forEachIndexed { i, x -> if (i > 0) sb.append(','); write(sb, x) }; sb.append(']') }
            else -> str(sb, v.toString())
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\""); '\\' -> sb.append("\\\\"); '\n' -> sb.append("\\n"); '\r' -> sb.append("\\r"); '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
        sb.append('"')
    }
}
