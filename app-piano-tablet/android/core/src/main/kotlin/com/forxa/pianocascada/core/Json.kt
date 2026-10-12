package com.forxa.pianocascada.core

/**
 * Lector de JSON mínimo (sin dependencias, funciona igual en Android y en el PC).
 * Devuelve Map<String, Any?>, List<Any?>, String, Double, Boolean o null.
 */
object Json {
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (p.pos != text.length) throw IllegalArgumentException("JSON: texto sobrante en ${p.pos}")
        return v
    }

    private class Parser(val s: String) {
        var pos = 0

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipWs()
            if (pos >= s.length) throw IllegalArgumentException("JSON: fin inesperado")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("JSON: carácter '$c' en $pos")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) throw IllegalArgumentException("JSON: se esperaba $word en $pos")
            pos += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            pos++
            skipWs()
            if (s[pos] == '}') { pos++; return m }
            while (true) {
                skipWs()
                val k = str()
                skipWs()
                if (s[pos] != ':') throw IllegalArgumentException("JSON: falta ':' en $pos")
                pos++
                m[k] = value()
                skipWs()
                when (s[pos++]) {
                    ',' -> continue
                    '}' -> return m
                    else -> throw IllegalArgumentException("JSON: se esperaba ',' o '}' en ${pos - 1}")
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            pos++
            skipWs()
            if (s[pos] == ']') { pos++; return l }
            while (true) {
                l.add(value())
                skipWs()
                when (s[pos++]) {
                    ',' -> continue
                    ']' -> return l
                    else -> throw IllegalArgumentException("JSON: se esperaba ',' o ']' en ${pos - 1}")
                }
            }
        }

        private fun str(): String {
            if (s[pos] != '"') throw IllegalArgumentException("JSON: se esperaba '\"' en $pos")
            pos++
            val sb = StringBuilder()
            while (true) {
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> { sb.append(s.substring(pos, pos + 4).toInt(16).toChar()); pos += 4 }
                            else -> throw IllegalArgumentException("JSON: escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val st = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            return s.substring(st, pos).toDouble()
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun Any?.asMap(): Map<String, Any?> = this as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Any?.asList(): List<Any?> = this as List<Any?>
