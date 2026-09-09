package com.reeelz.editor

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.reeelz.export.ExportEngine
import com.reeelz.playback.EditorPlayback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@UnstableApi
class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(EditorUiState())
    val state = mutableState.asStateFlow()
    val playback = EditorPlayback(application) { message -> mutableState.update { it.copy(message = message) } }
    private val exporter = ExportEngine(application)
    private var exportJob: Job? = null
    private val mutableExportedVideo = MutableStateFlow<Uri?>(null)
    val exportedVideo = mutableExportedVideo.asStateFlow()
    private val mutableResultVisible = MutableStateFlow(false)
    val resultVisible = mutableResultVisible.asStateFlow()
    fun showExportResult() { if (exportedVideo.value != null) mutableResultVisible.value = true }
    fun hideExportResult() { mutableResultVisible.value = false }
    fun exportActionError(message: String) {
        hideExportResult()
        mutableState.update { it.copy(message = message) }
    }
    private val drafts = ProjectStore(application)
    private val mutableProjects = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val projects = mutableProjects.asStateFlow()
    private suspend fun refreshProjects() { mutableProjects.value = drafts.list(); mutableHasDraft.value = mutableProjects.value.isNotEmpty() }
    private val mutableHasDraft = MutableStateFlow(false)
    val hasDraft = mutableHasDraft.asStateFlow()
    private val mutableDraftBusy = MutableStateFlow(true)
    val draftBusy = mutableDraftBusy.asStateFlow()
    private val mutableSaveStatus = MutableStateFlow("")
    val saveStatus = mutableSaveStatus.asStateFlow()
    private var autoSaveJob: Job? = null
    private val history = EditHistory()
    private val mutableHistory = MutableStateFlow(false to false)
    val historyAvailability = mutableHistory.asStateFlow()
    private var typingJob: Job? = null
    private fun EditorUiState.values() = EditValues(startMs, endMs, crop, text, extraTexts, audio)
    private fun publishHistory() { mutableHistory.value = history.canUndo to history.canRedo }
    fun finishEdit() { typingJob?.cancel(); history.finish() }
    private fun resetHistory() { finishEdit(); history.clear(); publishHistory() }
    private fun record(next: EditorUiState, key: String) {
        if (key != "typing") typingJob?.cancel()
        history.record(state.value.values(), next.values(), key)
        publishHistory()
    }
    private fun navigateHistory(redo: Boolean) {
        if (draftBusy.value || state.value.loading || state.value.exporting || state.value.source == null) return
        finishEdit()
        val edit = if (redo) history.redo(state.value.values()) else history.undo(state.value.values())
        if (edit == null) return
        mutableState.update { it.copy(startMs = edit.startMs, endMs = edit.endMs, crop = edit.crop, text = edit.text, extraTexts = edit.extraTexts, audio = edit.audio, selectedText = state.value.selectedText.coerceAtMost(edit.extraTexts.size), message = null) }
        publishHistory()
        playback.load(state.value)
        scheduleSave()
    }
    fun undo() = navigateHistory(false)
    fun redo() = navigateHistory(true)

    private fun scheduleSave(wait: Long = 600) {
        if (state.value.source == null) return
        autoSaveJob?.cancel()
        val snapshot = state.value
        mutableSaveStatus.value = "Сохраняется…"
        autoSaveJob = viewModelScope.launch {
            delay(wait)
            try {
                drafts.save(snapshot)
                mutableHasDraft.value = true
                mutableSaveStatus.value = "Сохранено"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableSaveStatus.value = "Не удалось сохранить. Нажмите «Повторить сохранение»." }
        }
    }

    fun flushDraft() { if (state.value.source != null && !draftBusy.value && mutableSaveStatus.value != "Сохранено") scheduleSave(0) }

    init {
        viewModelScope.launch {
            try { com.reeelz.export.ExportRecovery(application).recover(); refreshProjects() }
            catch (e: Exception) { mutableState.update { it.copy(message = "Не удалось восстановить проекты: ${e.localizedMessage}") } }
            finally { mutableDraftBusy.value = false }
        }
    }

    fun saveDraft() {
        scheduleSave(0)
    }

    fun restoreDraft(id: String) {
        if (draftBusy.value || state.value.loading || state.value.source != null) return
        mutableDraftBusy.value = true
        viewModelScope.launch {
            try {
                mutableState.value = drafts.load(id)
                resetHistory()
                mutableSaveStatus.value = "Сохранено"
                playback.load(state.value)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(message = "Не удалось открыть черновик: ${e.localizedMessage}") } }
            finally { mutableDraftBusy.value = false }
        }
    }

    fun deleteDraft(id: String) {
        if (draftBusy.value || state.value.source != null || state.value.loading) return
        mutableDraftBusy.value = true
        viewModelScope.launch {
            try { drafts.delete(id); refreshProjects() }
            catch (e: Exception) { mutableState.update { it.copy(message = "Не удалось удалить черновик") } }
            finally { mutableDraftBusy.value = false }
        }
    }

    fun renameProject(id: String, name: String) {
        if (draftBusy.value || state.value.source != null || state.value.loading) return
        mutableDraftBusy.value = true
        viewModelScope.launch {
            try { drafts.rename(id, name); refreshProjects() }
            catch (e: Exception) { mutableState.update { it.copy(message = e.localizedMessage ?: "Ошибка переименования") } }
            finally { mutableDraftBusy.value = false }
        }
    }

    fun dismissMessage() { mutableState.update { it.copy(message = null) } }

    fun open(uri: Uri) {
        if (state.value.loading || state.value.exporting || draftBusy.value) return
        mutableState.value = EditorUiState(loading = true)
        viewModelScope.launch {
            try {
                val source = withContext(Dispatchers.IO) {
                    MediaMetadataRetriever().use { reader ->
                        reader.setDataSource(getApplication(), uri)
                        fun meta(key: Int) = reader.extractMetadata(key)?.toLongOrNull() ?: 0L
                        val duration = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        var width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
                        var height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
                        if (meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 180 != 0L) {
                            val old = width; width = height; height = old
                        }
                        require(duration > 0 && width > 0 && height > 0) { "Не удалось прочитать видео" }
                        VideoSource(uri, duration, width, height)
                    }
                }
                val initial = EditorUiState(source = source, endMs = source.durationMs)
                val created = drafts.create(initial)
                mutableHasDraft.value = true
                mutableSaveStatus.value = "Сохранено"
                mutableState.value = created
                resetHistory()
                playback.load(state.value)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.value = EditorUiState(message = e.localizedMessage ?: "Не удалось открыть видео") }
        }
    }
    fun trim(start: Long, end: Long) {
        val s = state.value
        val source = s.source ?: return
        if (s.exporting || draftBusy.value) return
        val gap = minOf(100L, source.durationMs)
        val a = start.coerceIn(0, source.durationMs - gap)
        val next = s.copy(startMs = a, endMs = end.coerceIn(a + gap, source.durationMs), message = null)
        record(next, "trim")
        mutableState.value = next
        scheduleSave()
    }
    fun applyTrim() { finishEdit(); playback.load(state.value) }
    fun crop(crop: CropParameters) {
        if (state.value.exporting || draftBusy.value) return
        val next = state.value.copy(crop = crop.normalized(), message = null)
        record(next, "crop")
        mutableState.value = next
        scheduleSave()
        playback.crop(state.value)
    }
    fun home() {
        if (state.value.exporting || state.value.loading || draftBusy.value) return
        flushDraft()
        mutableDraftBusy.value = true
        viewModelScope.launch {
            try {
            autoSaveJob?.join()
            if (mutableSaveStatus.value != "Сохранено") {
                mutableState.update { it.copy(message = "Не удалось сохранить правки. Повторите сохранение перед выходом.") }
            } else {
                playback.clear()
                refreshProjects()
                mutableState.value = EditorUiState()
            }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(message = "Не удалось открыть список проектов: ${e.localizedMessage}") } }
            finally { mutableDraftBusy.value = false }
        }
    }
    fun text(parameters: TextParameters) {
        if (state.value.exporting || state.value.source == null || draftBusy.value) return
        val next = if (state.value.selectedText == 0) state.value.copy(text = parameters.normalized()) else state.value.copy(extraTexts = state.value.extraTexts.mapIndexed { i, t -> if (i + 1 == state.value.selectedText) parameters.normalized() else t })
        val old = state.value.activeText()
        val key = when {
            old.content != next.activeText().content -> "typing"
            old.color != next.activeText().color -> "color"
            old.size != next.activeText().size -> "size"
            else -> "position"
        }
        if (key == "color") finishEdit()
        record(next, key)
        mutableState.value = next
        if (key == "typing") {
            typingJob?.cancel()
            typingJob = viewModelScope.launch { delay(700); history.finish() }
        } else {
            typingJob?.cancel()
            if (key == "color") finishEdit()
        }
        scheduleSave()
        playback.crop(state.value)
    }
    fun selectText(index: Int) { finishEdit(); mutableState.update { it.copy(selectedText = index.coerceIn(0, it.extraTexts.size)) } }
    fun addText() {
        if (draftBusy.value || state.value.exporting || state.value.extraTexts.size >= 4) return
        finishEdit()
        val next = state.value.copy(extraTexts = state.value.extraTexts + TextParameters("Новая надпись"), selectedText = state.value.extraTexts.size + 1)
        record(next, "addText"); mutableState.value = next; finishEdit(); scheduleSave(); playback.crop(next)
    }
    fun removeText() {
        if (draftBusy.value || state.value.exporting) return
        finishEdit()
        val s = state.value
        val all = s.allTexts().filterIndexed { i, _ -> i != s.selectedText }
        val next = s.copy(text = all.firstOrNull() ?: TextParameters(), extraTexts = all.drop(1), selectedText = 0)
        record(next, "removeText"); mutableState.value = next; finishEdit(); scheduleSave(); playback.crop(next)
    }
    fun audio(parameters: AudioParameters) {
        if (draftBusy.value || state.value.exporting || state.value.source == null) return
        val next = state.value.copy(audio = parameters.normalized())
        record(next, "audio"); mutableState.value = next; scheduleSave(); playback.audio(next.audio)
    }
    fun importMusic(uri: Uri) {
        val id = state.value.projectId ?: return
        if (draftBusy.value || state.value.exporting) return
        finishEdit(); playback.pause(); mutableDraftBusy.value = true
        viewModelScope.launch {
            try {
                val imported = drafts.importMusic(id, uri)
                val next = state.value.copy(audio = state.value.audio.copy(musicUri = imported.musicUri, musicName = imported.musicName, musicDurationMs = imported.musicDurationMs))
                record(next, "music"); mutableState.value = next; finishEdit(); scheduleSave(); playback.audio(next.audio)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(message = "Не удалось добавить музыку: ${e.localizedMessage}") } }
            finally { mutableDraftBusy.value = false }
        }
    }
    fun moveText(dx: Float, dy: Float) {
        val current = state.value.activeText()
        text(current.copy(x = current.x + dx, y = current.y + dy))
    }
    fun export() {
        if (state.value.source == null || state.value.exporting || draftBusy.value) return
        playback.pause()
        playback.stopForExport()
        val snapshot = state.value
        mutableState.update { it.copy(exporting = true, progress = 0, message = null) }
        exportJob = viewModelScope.launch {
            try {
                val uri = exporter.export(snapshot) { progress -> mutableState.update { it.copy(progress = progress) } }
                mutableExportedVideo.value = uri
                mutableResultVisible.value = true
            } catch (e: CancellationException) {
                mutableState.update { it.copy(message = "Экспорт отменён") }
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(message = "Ошибка экспорта: ${e.localizedMessage}") }
            } finally {
                mutableState.update { it.copy(exporting = false) }
                if (currentCoroutineContext().isActive) playback.load(state.value)
            }
        }
    }
    fun cancelExport() {
        viewModelScope.launch {
            exportJob?.cancelAndJoin()
            if (state.value.source != null) playback.load(state.value)
        }
    }
    override fun onCleared() { playback.release() }
}





