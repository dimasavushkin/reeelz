package com.reeelz.editor

import android.net.Uri

data class CropParameters(
    val zoom: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
    val rotation: Int = 0,
) {
    fun normalized() = copy(
        zoom = zoom.takeIf { it.isFinite() }?.coerceIn(1f, 4f) ?: 1f,
        x = x.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f,
        y = y.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f,
        rotation = ((rotation % 360) + 360) % 360 / 90 * 90,
    )
}
data class VideoSource(val uri: Uri, val durationMs: Long, val width: Int, val height: Int)
data class VideoClip(
    val id: String,
    val source: VideoSource,
    val startMs: Long = 0,
    val endMs: Long = source.durationMs,
    val crop: CropParameters = CropParameters(),
)
data class EditorUiState(
    val projectId: String? = null,
    val projectName: String = "Новый ролик",
    val source: VideoSource? = null,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val crop: CropParameters = CropParameters(),
    val clips: List<VideoClip> = emptyList(),
    val selectedClipIndex: Int = 0,
    val text: TextParameters = TextParameters(),
    val extraTexts: List<TextParameters> = emptyList(),
    val selectedText: Int = 0,
    val audio: AudioParameters = AudioParameters(),
    val loading: Boolean = false,
    val exporting: Boolean = false,
    val progress: Int = 0,
    val message: String? = null,
)

fun EditorUiState.allTexts() = listOf(text) + extraTexts
fun EditorUiState.activeText() = allTexts().getOrElse(selectedText) { text }

fun EditorUiState.timelineClips(): List<VideoClip> = if (clips.isNotEmpty()) clips else {
    source?.let { listOf(VideoClip("legacy", it, startMs, endMs, crop)) }.orEmpty()
}

fun EditorUiState.withSelectedClip(index: Int): EditorUiState {
    if (clips.isEmpty()) return this
    val selected = index.coerceIn(0, clips.lastIndex)
    val clip = clips[selected]
    return copy(selectedClipIndex = selected, source = clip.source, startMs = clip.startMs, endMs = clip.endMs, crop = clip.crop)
}

fun EditorUiState.withActiveClip(updated: VideoClip): EditorUiState {
    if (clips.isEmpty()) return copy(source = updated.source, startMs = updated.startMs, endMs = updated.endMs, crop = updated.crop)
    val selected = selectedClipIndex.coerceIn(0, clips.lastIndex)
    return copy(clips = clips.mapIndexed { index, clip -> if (index == selected) updated else clip },
        source = updated.source, startMs = updated.startMs, endMs = updated.endMs, crop = updated.crop)
}

data class CropBounds(val left: Float, val right: Float, val bottom: Float, val top: Float)

fun cropBounds(width: Int, height: Int, crop: CropParameters): CropBounds {
    val normalized = crop.normalized()
    val rotated = normalized.rotation % 180 != 0
    val aspect = (if (rotated) height else width).toFloat() / (if (rotated) width else height)
    val target = 9f / 16f
    val halfWidth = minOf(1f, target / aspect) / normalized.zoom
    val halfHeight = minOf(1f, aspect / target) / normalized.zoom
    val cx = normalized.x * (1f - halfWidth)
    val cy = -normalized.y * (1f - halfHeight)
    return CropBounds(cx - halfWidth, cx + halfWidth, cy - halfHeight, cy + halfHeight)
}
