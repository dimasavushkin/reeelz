package com.reeelz.editor

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Properties
import java.util.UUID

/** One committed draft; source copies and metadata are private to the app. */
class DraftStore(private val context: Context, private val directory: File = File(context.filesDir, "draft")) {
    init { directory.mkdirs() }
    private val metadata = AtomicFile(File(directory, "draft.properties"))
    private val mutex = Mutex()

    suspend fun exists(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { metadata.baseFile.exists() || File(directory, "draft.properties.bak").exists() }
    }

    suspend fun save(state: EditorUiState) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val source = requireNotNull(state.source)
            val existing = if (source.uri.scheme == "file") File(requireNotNull(source.uri.path)) else null
            val reuse = existing != null && existing.isFile && existing.canonicalFile.parentFile == directory.canonicalFile && existing.extension == "video"
            val copy = if (reuse) requireNotNull(existing) else File(directory, "${UUID.randomUUID()}.video")
            try {
                if (!reuse) {
                StorageChecks.requireSpace(directory)
                checkNotNull(context.contentResolver.openInputStream(source.uri)).use { input ->
                    copy.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var written = 0L
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            written += count
                            if (written % (4 * 1024 * 1024) < buffer.size) StorageChecks.requireSpace(directory)
                        }
                        output.fd.sync()
                    }
                }
                }
                check(copy.length() > 0) { "Исходное видео пустое" }
                val data = Properties().apply {
                    setProperty("version", "1")
                    setProperty("originalVolume", state.audio.originalVolume.toString())
                    setProperty("musicVolume", state.audio.musicVolume.toString())
                    setProperty("musicFile", state.audio.musicUri?.let { File(requireNotNull(Uri.parse(it).path)).name } ?: "")
                    setProperty("musicName", state.audio.musicName)
                    setProperty("musicDuration", state.audio.musicDurationMs.toString())
                    setProperty("file", copy.name)
                    setProperty("duration", source.durationMs.toString())
                    setProperty("width", source.width.toString())
                    setProperty("height", source.height.toString())
                    setProperty("start", state.startMs.toString())
                    setProperty("end", state.endMs.toString())
                    setProperty("zoom", state.crop.zoom.toString())
                    setProperty("x", state.crop.x.toString())
                    setProperty("y", state.crop.y.toString())
                    setProperty("rotation", state.crop.rotation.toString())
                    setProperty("text", state.text.content)
                    setProperty("textSize", state.text.size.toString())
                    setProperty("textColor", state.text.color.toString())
                    setProperty("textX", state.text.x.toString())
                    setProperty("textY", state.text.y.toString())
                    writeCaption("caption0.", state.text)
                    setProperty("extraCount", state.extraTexts.size.toString())
                    state.extraTexts.forEachIndexed { i, text -> writeCaption("extra$i.", text) }
                }
                ensureActive()
                val stream = metadata.startWrite()
                try {
                    data.store(stream, "Reeelz draft v1")
                    metadata.finishWrite(stream)
                } catch (e: Throwable) { metadata.failWrite(stream); throw e }
            } catch (e: Throwable) { if (!reuse) copy.delete(); throw e }
            // Keep the currently opened source too: ExoPlayer may still be reading it.
            directory.listFiles()?.filter { it.extension == "video" && it != copy && Uri.fromFile(it) != source.uri }
                ?.forEach { it.delete() }
            source.copy(uri = Uri.fromFile(copy))
        }
    }

    suspend fun load(): EditorUiState = withContext(Dispatchers.IO) {
        mutex.withLock {
            val p = Properties().apply { metadata.openRead().use { load(it) } }
            require(p.getProperty("version") == "1") { "Неподдерживаемая версия черновика" }
            val name = p.getProperty("file") ?: error("Нет видео в черновике")
            require(name.matches(Regex("[a-f0-9-]+\\.video")))
            val file = File(directory, name)
            check(file.isFile && file.length() > 0) { "Видео черновика недоступно" }
            fun long(key: String) = p.getProperty(key).toLong()
            fun float(key: String) = p.getProperty(key).toFloat().also { require(it.isFinite()) }
            val duration = long("duration")
            val width = long("width").toInt()
            val height = long("height").toInt()
            val start = long("start")
            val end = long("end")
            require(duration > 0 && width > 0 && height > 0 && start >= 0 && end > start && end <= duration)
            EditorUiState(
                audio = AudioParameters(
                    p.getProperty("originalVolume", "1").toFloat(),
                    p.getProperty("musicFile", "").takeIf { it.isNotEmpty() }?.let { name -> require(name.matches(Regex("[a-f0-9-]+\\.audio"))); Uri.fromFile(File(directory, name)).toString() },
                    p.getProperty("musicName", "Музыка"), p.getProperty("musicDuration", "0").toLong(),
                    p.getProperty("musicVolume", "0.5").toFloat()).normalized(),
                source = VideoSource(Uri.fromFile(file), duration, width, height),
                startMs = start, endMs = end,
                crop = CropParameters(float("zoom"), float("x"), float("y"), p.getProperty("rotation", "0").toInt()).normalized(),
                text = p.readCaption("caption0.", TextParameters(p.getProperty("text", ""), float("textSize"), long("textColor").toInt(), float("textX"), float("textY")).normalized()),
                extraTexts = List(p.getProperty("extraCount", "0").toInt().coerceIn(0, 4)) { p.readCaption("extra$it.") },
            )
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        mutex.withLock {
            metadata.delete()
            directory.listFiles()?.filter { it.extension == "video" }?.forEach { it.delete() }
        }
    }
}


