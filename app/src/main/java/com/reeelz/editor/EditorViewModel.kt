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

    fun dismissMessage() { mutableState.update { it.copy(message = null) } }

    fun open(uri: Uri) {
        if (state.value.loading || state.value.exporting) return
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
                mutableState.value = EditorUiState(source = source, endMs = source.durationMs)
                playback.load(state.value)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.value = EditorUiState(message = e.localizedMessage ?: "Не удалось открыть видео") }
        }
    }
    fun trim(start: Long, end: Long) {
        val s = state.value
        val source = s.source ?: return
        if (s.exporting) return
        val gap = minOf(100L, source.durationMs)
        val a = start.coerceIn(0, source.durationMs - gap)
        mutableState.update { it.copy(startMs = a, endMs = end.coerceIn(a + gap, source.durationMs), message = null) }
    }
    fun applyTrim() { playback.load(state.value) }
    fun crop(crop: CropParameters) {
        if (state.value.exporting) return
        mutableState.update { it.copy(crop = crop, message = null) }
        playback.crop(state.value)
    }
    fun home() {
        if (state.value.exporting || state.value.loading) return
        playback.clear()
        mutableState.value = EditorUiState()
    }
    fun text(parameters: TextParameters) {
        if (state.value.exporting || state.value.source == null) return
        mutableState.update { it.copy(text = parameters.normalized()) }
        playback.crop(state.value)
    }
    fun moveText(dx: Float, dy: Float) {
        val current = state.value.text
        text(current.copy(x = current.x + dx, y = current.y + dy))
    }
    fun export() {
        if (state.value.source == null || state.value.exporting) return
        playback.pause()
        playback.player.stop() // Release the preview decoder before Transformer acquires one.
        val snapshot = state.value
        mutableState.update { it.copy(exporting = true, progress = 0, message = null) }
        exportJob = viewModelScope.launch {
            try {
                exporter.export(snapshot) { progress -> mutableState.update { it.copy(progress = progress) } }
                mutableState.update { it.copy(message = "Сохранено в галерею · Movies/Reeelz") }
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
