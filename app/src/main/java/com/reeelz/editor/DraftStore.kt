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
            val incoming = state.timelineClips()
            require(incoming.isNotEmpty())
            val created = mutableListOf<File>()
            try {
                val clips = incoming.map { it.copy(source = own(it.source, created)) }
                val selected = state.selectedClipIndex.coerceIn(0, clips.lastIndex)
                val active = clips[selected]
                val data = Properties().apply {
                    setProperty("version", "1")
                    setProperty("originalVolume", state.audio.originalVolume.toString())
                    setProperty("musicVolume", state.audio.musicVolume.toString())
                    setProperty("musicFile", state.audio.musicUri?.let { File(requireNotNull(Uri.parse(it).path)).name } ?: "")
                    setProperty("musicName", state.audio.musicName)
                    setProperty("musicDuration", state.audio.musicDurationMs.toString())
                    setProperty("file", File(requireNotNull(active.source.uri.path)).name)
                    setProperty("duration", active.source.durationMs.toString())
                    setProperty("width", active.source.width.toString())
                    setProperty("height", active.source.height.toString())
                    setProperty("start", active.startMs.toString())
                    setProperty("end", active.endMs.toString())
                    setProperty("zoom", active.crop.zoom.toString())
                    setProperty("x", active.crop.x.toString())
                    setProperty("y", active.crop.y.toString())
                    setProperty("rotation", active.crop.rotation.toString())
                    if (state.clips.isNotEmpty()) {
                        setProperty("clipCount", clips.size.toString())
                        setProperty("selectedClip", selected.toString())
                        clips.forEachIndexed { index, clip ->
                            val prefix = "clip$index."
                            setProperty(prefix + "id", clip.id)
                            setProperty(prefix + "file", File(requireNotNull(clip.source.uri.path)).name)
                            setProperty(prefix + "duration", clip.source.durationMs.toString())
                            setProperty(prefix + "width", clip.source.width.toString())
                            setProperty(prefix + "height", clip.source.height.toString())
                            setProperty(prefix + "start", clip.startMs.toString())
                            setProperty(prefix + "end", clip.endMs.toString())
                            setProperty(prefix + "zoom", clip.crop.zoom.toString())
                            setProperty(prefix + "x", clip.crop.x.toString())
                            setProperty(prefix + "y", clip.crop.y.toString())
                            setProperty(prefix + "rotation", clip.crop.rotation.toString())
                        }
                    }
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
                active.source
            } catch (e: Throwable) { created.forEach { it.delete() }; throw e }
        }
    }

    /** Removes clips no longer referenced after the in-memory undo history is discarded. */
    suspend fun prune(state: EditorUiState) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val keep = state.timelineClips().mapNotNull { clip ->
                clip.source.uri.path?.let(::File)?.takeIf { it.isFile && it.canonicalFile.parentFile == directory.canonicalFile }?.canonicalPath
            }.toSet()
            directory.listFiles()?.filter { it.extension == "video" && it.canonicalPath !in keep }?.forEach { it.delete() }
        }
    }

    private suspend fun own(source: VideoSource, created: MutableList<File>): VideoSource {
        val existing = if (source.uri.scheme == "file") source.uri.path?.let(::File) else null
        if (existing != null && existing.isFile && existing.canonicalFile.parentFile == directory.canonicalFile && existing.extension == "video") {
            return source.copy(uri = Uri.fromFile(existing))
        }
        StorageChecks.requireSpace(directory)
        val copy = File(directory, "${UUID.randomUUID()}.video")
        created += copy
        checkNotNull(context.contentResolver.openInputStream(source.uri)).use { input ->
            copy.outputStream().use { output ->
                val buffer = ByteArray(128 * 1024)
                var written = 0L
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    written += count
                    if (written % (4 * 1024 * 1024) < buffer.size) StorageChecks.requireSpace(directory)
                }
                output.fd.sync()
            }
        }
        check(copy.length() > 0) { "Исходное видео пустое" }
        return source.copy(uri = Uri.fromFile(copy))
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
            val legacySource = VideoSource(Uri.fromFile(file), duration, width, height)
            val legacyCrop = CropParameters(float("zoom"), float("x"), float("y"), p.getProperty("rotation", "0").toInt()).normalized()
            val clipCount = p.getProperty("clipCount", "0").toInt().coerceIn(0, 10)
            val clips = if (clipCount == 0) emptyList() else List(clipCount) { index ->
                val prefix = "clip$index."
                val clipName = requireNotNull(p.getProperty(prefix + "file"))
                require(clipName.matches(Regex("[a-f0-9-]+\\.video")))
                val clipFile = File(directory, clipName)
                check(clipFile.isFile && clipFile.length() > 0) { "Видео клипа недоступно" }
                val clipDuration = p.getProperty(prefix + "duration").toLong()
                val clipStart = p.getProperty(prefix + "start").toLong()
                val clipEnd = p.getProperty(prefix + "end").toLong()
                require(clipDuration > 0 && clipStart >= 0 && clipEnd > clipStart && clipEnd <= clipDuration)
                VideoClip(requireNotNull(p.getProperty(prefix + "id")),
                    VideoSource(Uri.fromFile(clipFile), clipDuration, p.getProperty(prefix + "width").toInt(), p.getProperty(prefix + "height").toInt()),
                    clipStart, clipEnd, CropParameters(p.getProperty(prefix + "zoom").toFloat(), p.getProperty(prefix + "x").toFloat(),
                        p.getProperty(prefix + "y").toFloat(), p.getProperty(prefix + "rotation", "0").toInt()).normalized())
            }
            val selected = if (clips.isEmpty()) 0 else p.getProperty("selectedClip", "0").toInt().coerceIn(0, clips.lastIndex)
            val active = clips.getOrNull(selected)
            EditorUiState(
                audio = AudioParameters(
                    p.getProperty("originalVolume", "1").toFloat(),
                    p.getProperty("musicFile", "").takeIf { it.isNotEmpty() }?.let { name -> require(name.matches(Regex("[a-f0-9-]+\\.audio"))); Uri.fromFile(File(directory, name)).toString() },
                    p.getProperty("musicName", "Музыка"), p.getProperty("musicDuration", "0").toLong(),
                    p.getProperty("musicVolume", "0.5").toFloat()).normalized(),
                source = active?.source ?: legacySource,
                startMs = active?.startMs ?: start, endMs = active?.endMs ?: end,
                crop = active?.crop ?: legacyCrop, clips = clips, selectedClipIndex = selected,
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


