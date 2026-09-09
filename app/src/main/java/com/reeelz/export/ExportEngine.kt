package com.reeelz.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import com.reeelz.editor.EditorUiState
import com.reeelz.editor.StorageChecks
import com.reeelz.editor.timelineClips
import com.reeelz.editor.totalDurationMs
import java.util.UUID
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
class ExportEngine(private val context: Context) {
    suspend fun export(state: EditorUiState, progress: (Int) -> Unit): Uri {
        require(state.timelineClips().isNotEmpty() && state.totalDurationMs() > 0)
        withContext(Dispatchers.IO) { StorageChecks.requireSpace(context.cacheDir) }
        val file = File.createTempFile("reeelz-", ".mp4", context.cacheDir)
        file.delete() // Transformer requires a fresh output path.
        try {
            withContext(Dispatchers.Main.immediate) {
                coroutineScope {
                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
                        .build()
                    val poll = launch {
                        val holder = ProgressHolder()
                        while (isActive) {
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progress(holder.progress)
                            delay(300)
                        }
                    }
                    try {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            transformer.addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    if (continuation.isActive) continuation.resume(Unit)
                                }
                                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                    if (continuation.isActive) continuation.resumeWithException(exportException)
                                }
                            })
                            transformer.start(ExportComposition.create(state), file.absolutePath)
                        }
                    } finally {
                        poll.cancel()
                        // Cancellation can originate on any thread; Media3 must stop on Main.
                        withContext(NonCancellable + Dispatchers.Main.immediate) { transformer.cancel() }
                    }
                }
            }
            return withContext(Dispatchers.IO) {
                StorageChecks.requireSpace(context.filesDir, file.length())
                MediaMetadataRetriever().use { reader ->
                    reader.setDataSource(file.absolutePath)
                    val width = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    val height = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    val rotation = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    val portraitWidth = if (rotation % 180 == 0) width else height
                    val portraitHeight = if (rotation % 180 == 0) height else width
                    check(portraitWidth == 1080 && portraitHeight == 1920) { "Кодек не создал видео 1080×1920" }
                }
                val resolver = context.contentResolver
                val name = "Reeelz_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.mp4"
                val recovery = ExportRecovery(context)
                recovery.begin(name)
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Reeelz")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = checkNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
                try {
                    checkNotNull(resolver.openOutputStream(uri)).use { output ->
                        file.inputStream().use { input ->
                            val buffer = ByteArray(128 * 1024)
                            while (true) {
                                ensureActive()
                                val size = input.read(buffer)
                                if (size < 0) break
                                output.write(buffer, 0, size)
                            }
                        }
                    }
                    ensureActive()
                    check(resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) == 1)
                    recovery.finish()
                    uri
                } catch (error: Throwable) {
                    resolver.delete(uri, null, null)
                    recovery.finish()
                    throw error
                }
            }
        } finally { file.delete() }
    }
}
