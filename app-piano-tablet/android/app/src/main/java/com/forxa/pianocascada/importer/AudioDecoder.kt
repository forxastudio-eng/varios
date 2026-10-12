package com.forxa.pianocascada.importer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** Decodifica MP3, M4A/AAC, WAV, OGG, FLAC… a PCM mono en coma flotante con los códecs del propio Android. */
object AudioDecoder {
    class Decoded(val pcm: FloatArray, val sampleRate: Int) {
        val seconds: Double get() = pcm.size.toDouble() / sampleRate
    }

    class TooLong(val seconds: Int) : Exception("El audio dura más de ${seconds / 60} minutos")

    fun decode(context: Context, uri: Uri, maxSeconds: Int = 20 * 60, onProgress: (Float) -> Unit = {}, cancelled: () -> Boolean = { false }): Decoded {
        val ex = MediaExtractor()
        ex.setDataSource(context, uri, null)
        var track = -1
        for (i in 0 until ex.trackCount) {
            if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
        }
        require(track >= 0) { "El archivo no contiene audio" }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val mime = fmt.getString(MediaFormat.KEY_MIME)!!
        var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        if (durationUs > maxSeconds * 1_000_000L) throw TooLong(maxSeconds)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var out = FloatArray(maxOf(1, (if (durationUs > 0) durationUs / 1_000_000.0 * sr else sr * 60.0).toInt()))
        var n = 0
        var encoding = android.media.AudioFormat.ENCODING_PCM_16BIT
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (cancelled()) throw InterruptedException("cancelado")
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o >= 0 -> {
                        if (info.size > 0) {
                            val buf = codec.getOutputBuffer(o)!!
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            buf.order(ByteOrder.nativeOrder())
                            val frames: Int
                            if (encoding == android.media.AudioFormat.ENCODING_PCM_FLOAT) {
                                val fb = buf.asFloatBuffer()
                                frames = fb.remaining() / ch
                                if (n + frames > out.size) out = out.copyOf(maxOf(out.size * 2, n + frames))
                                for (f in 0 until frames) { var s = 0f; for (c in 0 until ch) s += fb.get(); out[n++] = s / ch }
                            } else {
                                val sb = buf.asShortBuffer()
                                frames = sb.remaining() / ch
                                if (n + frames > out.size) out = out.copyOf(maxOf(out.size * 2, n + frames))
                                for (f in 0 until frames) { var s = 0; for (c in 0 until ch) s += sb.get(); out[n++] = s / (ch * 32768f) }
                            }
                            if (durationUs > 0) onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                            if (n > maxSeconds * sr) throw TooLong(maxSeconds)
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING) else android.media.AudioFormat.ENCODING_PCM_16BIT
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            ex.release()
        }
        return Decoded(out.copyOf(n), sr)
    }
}
