package com.example.ytclient.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ytclient.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Downloads a chosen resolution to Downloads/YTMaterial. Video-only tracks are downloaded together
 * with the best audio track and muxed into one MP4. Lives at app level so it survives the player closing.
 */
object VideoDownloader {
    /** null = idle, 0..1 = running. Read from Compose. */
    var progress by mutableStateOf<Float?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .dns(Net.dns)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    private const val CHUNK = 9_000_000L

    /** [video] null = audio only. [mux] = the video track has no audio and needs [audioUrl] merged in. */
    fun start(
        context: Context,
        title: String,
        video: StreamTrack?,
        mux: Boolean,
        audioUrl: String?,
        audioExt: String
    ) {
        if (progress != null) return
        val app = context.applicationContext
        progress = 0f
        toast(app, app.getString(R.string.download_starting))
        scope.launch {
            val result = runCatching { doDownload(app, title, video, mux, audioUrl, audioExt) }
            progress = null
            val msg = result.fold(
                onSuccess = { app.getString(R.string.download_saved, it) },
                onFailure = { app.getString(R.string.download_error, it.message ?: "") }
            )
            toast(app, msg)
        }
    }

    private fun toast(context: Context, msg: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
    }

    private fun doDownload(
        context: Context,
        title: String,
        video: StreamTrack?,
        mux: Boolean,
        audioUrl: String?,
        audioExt: String
    ): String {
        val name = Repository.sanitizeFilename(title).ifBlank { "video" }
        val cache = File(context.cacheDir, "dl").apply { mkdirs() }
        val temps = mutableListOf<File>()
        try {
            if (video == null) {
                val url = audioUrl ?: throw IOException("No audio stream")
                val f = File(cache, "a_${System.nanoTime()}.$audioExt").also { temps.add(it) }
                fetch(url, f) { progress = it }
                return save(context, f, "$name.$audioExt", if (audioExt == "m4a") "audio/mp4" else "audio/webm")
            }
            if (!mux) {
                val f = File(cache, "v_${System.nanoTime()}.${video.ext}").also { temps.add(it) }
                fetch(video.url, f) { progress = it }
                val mime = if (video.ext == "webm") "video/webm" else "video/mp4"
                return save(context, f, "$name ${video.height}p.${video.ext}", mime)
            }
            val audio = audioUrl ?: throw IOException("No audio stream")
            val vf = File(cache, "v_${System.nanoTime()}.mp4").also { temps.add(it) }
            val af = File(cache, "a_${System.nanoTime()}.m4a").also { temps.add(it) }
            val out = File(cache, "o_${System.nanoTime()}.mp4").also { temps.add(it) }
            fetch(video.url, vf) { progress = it * 0.8f }
            fetch(audio, af) { progress = 0.8f + it * 0.1f }
            progress = 0.92f
            muxFiles(vf, af, out)
            progress = 1f
            return save(context, out, "$name ${video.height}p.mp4", "video/mp4")
        } finally {
            temps.forEach { runCatching { it.delete() } }
        }
    }

    /** Ranged download in ~9 MB pieces (googlevideo throttles single huge requests). */
    private fun fetch(url: String, out: File, onFrac: (Float) -> Unit) {
        var start = 0L
        var total = -1L
        out.outputStream().use { output ->
            val buf = ByteArray(64 * 1024)
            while (total < 0 || start < total) {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", UA)
                    .header("Range", "bytes=$start-${start + CHUNK - 1}")
                    .build()
                var wholeFile = false
                client.newCall(req).execute().use { r ->
                    if (r.code != 200 && r.code != 206) throw IOException("HTTP ${r.code}")
                    val body = r.body ?: throw IOException("Empty response")
                    val range = r.header("Content-Range")
                    if (r.code == 206 && range != null) {
                        total = range.substringAfter('/').toLongOrNull() ?: -1L
                    } else {
                        wholeFile = true
                        total = body.contentLength()
                    }
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            start += n
                            if (total > 0) onFrac((start.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                if (wholeFile) break
                if (total < 0) break
            }
        }
    }

    private fun muxFiles(video: File, audio: File, out: File) {
        val vEx = MediaExtractor()
        val aEx = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            vEx.setDataSource(video.absolutePath)
            aEx.setDataSource(audio.absolutePath)
            val vi = (0 until vEx.trackCount).firstOrNull {
                vEx.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: throw IOException("No video track")
            val ai = (0 until aEx.trackCount).firstOrNull {
                aEx.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("No audio track")
            vEx.selectTrack(vi)
            aEx.selectTrack(ai)
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val vt = m.addTrack(vEx.getTrackFormat(vi))
            val at = m.addTrack(aEx.getTrackFormat(ai))
            m.start()
            copyTrack(vEx, m, vt)
            copyTrack(aEx, m, at)
            m.stop()
        } finally {
            runCatching { muxer?.release() }
            vEx.release()
            aEx.release()
        }
    }

    private fun copyTrack(ex: MediaExtractor, muxer: MediaMuxer, track: Int) {
        val buf = ByteBuffer.allocate(8 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        while (true) {
            buf.clear()
            val size = ex.readSampleData(buf, 0)
            if (size < 0) break
            val flags = if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else 0
            info.set(0, size, ex.sampleTime, flags)
            muxer.writeSampleData(track, buf, info)
            ex.advance()
        }
    }

    private fun save(context: Context, file: File, display: String, mime: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, display)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/YTMaterial")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Cannot create file")
            val stream = resolver.openOutputStream(uri) ?: throw IOException("Cannot write file")
            stream.use { o -> file.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "Downloads/YTMaterial/$display"
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "YTMaterial")
        dir.mkdirs()
        val dest = File(dir, display)
        file.copyTo(dest, overwrite = true)
        return dest.absolutePath
    }
}
