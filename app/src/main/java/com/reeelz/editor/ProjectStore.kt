package com.reeelz.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Properties
import java.util.UUID
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.ensureActive

data class ProjectSummary(val id: String, val name: String, val modified: Long, val cover: String?)

/** Each project owns its source and metadata. The old single draft is exposed as 'legacy'. */
class ProjectStore(private val context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val mutex = Mutex()
    private fun folder(id: String): File {
        require(id == "legacy" || id.matches(Regex("[a-f0-9-]{36}")))
        return if (id == "legacy") File(context.filesDir, "draft") else File(root, id)
    }
    private fun store(id: String) = DraftStore(context, folder(id))
    private fun title(id: String): String = runCatching {
        Properties().apply { AtomicFile(File(folder(id), "title.properties")).openRead().use { load(it) } }.getProperty("name")
    }.getOrNull() ?: if (id == "legacy") "Сохранённый ролик" else "Новый ролик"

    suspend fun list(): List<ProjectSummary> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val ids = root.listFiles()?.filter { it.isDirectory && it.name.matches(Regex("[a-f0-9-]{36}")) }?.map { it.name }.orEmpty() + "legacy"
            ids.filter { store(it).exists() }.map { id ->
                val dir = folder(id)
                ProjectSummary(id, title(id), maxOf(File(dir, "draft.properties").lastModified(), File(dir, "title.properties").lastModified()),
                    File(dir, "cover.jpg").takeIf { it.exists() }?.absolutePath)
            }.sortedByDescending { it.modified }
        }
    }
    suspend fun create(initial: EditorUiState): EditorUiState = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = UUID.randomUUID().toString()
            try {
                val owned = store(id).save(initial)
                writeTitle(id, initial.projectName)
                // A missing/unsupported thumbnail must not prevent editing the video.
                runCatching {
                    MediaMetadataRetriever().use { reader ->
                        reader.setDataSource(context, owned.uri)
                        reader.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 160)?.let { frame ->
                            try { File(folder(id), "cover.jpg").outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
                            finally { frame.recycle() }
                        }
                    }
                }
                initial.copy(projectId = id, source = owned)
            } catch (error: Throwable) { folder(id).deleteRecursively(); throw error }
        }
    }
    suspend fun save(state: EditorUiState) = withContext(Dispatchers.IO) {
        mutex.withLock { store(requireNotNull(state.projectId)).save(state) }
    }
    suspend fun importMusic(id: String, uri: Uri): AudioParameters = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(store(id).exists())
            val file = File(folder(id), "${UUID.randomUUID()}.audio")
            try {
                checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    StorageChecks.requireSpace(folder(id))
                    file.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var written = 0L
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            written += count
                            if (written % (4 * 1024 * 1024) < buffer.size) StorageChecks.requireSpace(folder(id))
                        }
                        output.fd.sync()
                    }
                }
                val duration = MediaMetadataRetriever().use { reader ->
                    reader.setDataSource(file.absolutePath)
                    require(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") { "В файле нет аудио" }
                    reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                }
                require(duration > 0) { "Не удалось прочитать длительность музыки" }
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "Музыка"
                AudioParameters(musicUri = Uri.fromFile(file).toString(), musicName = name, musicDurationMs = duration)
            } catch (e: Throwable) { file.delete(); throw e }
        }
    }
    suspend fun load(id: String): EditorUiState = withContext(Dispatchers.IO) {
        mutex.withLock { store(id).load().copy(projectId = id, projectName = title(id)) }
    }
    suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        mutex.withLock { require(store(id).exists()); writeTitle(id, name) }
    }
    private fun writeTitle(id: String, name: String) {
        val clean = name.trim().take(60)
        require(clean.isNotBlank()) { "Введите название проекта" }
        val file = AtomicFile(File(folder(id), "title.properties"))
        val out = file.startWrite()
        try {
            Properties().apply { setProperty("name", clean) }.store(out, null)
            file.finishWrite(out)
        } catch (error: Throwable) { file.failWrite(out); throw error }
    }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock { check(folder(id).deleteRecursively()) { "Не удалось удалить проект" } }
    }
}
