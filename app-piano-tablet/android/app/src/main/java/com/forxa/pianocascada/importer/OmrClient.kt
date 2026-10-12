package com.forxa.pianocascada.importer

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente del «servidor en casa» que lee partituras (PDF o foto) con reconocimiento óptico musical (Audiveris).
 * Protocolo: GET /health · POST /omr (multipart «file») → {job} · GET /jobs/{id} → {status, progress, message, result, warnings}.
 */
class OmrClient(baseUrl: String) {
    private val base = baseUrl.trim().trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }

    class Result(val json: String, val warnings: List<String>)

    fun health(): Boolean = runCatching {
        val c = open("$base/health", 5000)
        c.responseCode == 200 && JSONObject(c.inputStream.bufferedReader().readText()).optBoolean("ok", false)
    }.getOrDefault(false)

    private fun open(url: String, timeout: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = timeout; readTimeout = timeout }

    fun convert(
        context: Context, uri: Uri, fileName: String, mime: String,
        onProgress: (Float, String) -> Unit, cancelled: () -> Boolean,
    ): Result {
        onProgress(0.02f, "Enviando la partitura al servidor…")
        val boundary = "----pc${System.nanoTime()}"
        val c = open("$base/omr", 60_000)
        c.doOutput = true
        c.requestMethod = "POST"
        c.setChunkedStreamingMode(64 * 1024)
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        DataOutputStream(c.outputStream).use { o ->
            o.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${fileName.replace("\"", "")}\"\r\nContent-Type: $mime\r\n\r\n")
            context.contentResolver.openInputStream(uri)!!.use { it.copyTo(o, 64 * 1024) }
            o.writeBytes("\r\n--$boundary--\r\n")
        }
        if (c.responseCode !in 200..299) throw IllegalStateException("El servidor respondió ${c.responseCode}: ${c.errorStream?.bufferedReader()?.readText()?.take(200)}")
        val job = JSONObject(c.inputStream.bufferedReader().readText()).getString("job")
        val start = System.currentTimeMillis()
        while (true) {
            if (cancelled()) throw InterruptedException("cancelado")
            Thread.sleep(1200)
            val j = JSONObject(open("$base/jobs/$job", 15_000).inputStream.bufferedReader().readText())
            val p = j.optDouble("progress", 0.0).toFloat()
            when (j.optString("status")) {
                "done" -> {
                    val w = j.optJSONArray("warnings")
                    return Result(j.getJSONObject("result").toString(), List(w?.length() ?: 0) { w!!.getString(it) })
                }
                "error" -> throw IllegalStateException(j.optString("error", "El servidor no pudo leer la partitura"))
                else -> onProgress(0.05f + 0.9f * p, j.optString("message", "Leyendo la partitura…"))
            }
            if (System.currentTimeMillis() - start > 15 * 60_000L) throw IllegalStateException("El servidor tardó demasiado")
        }
    }
}
