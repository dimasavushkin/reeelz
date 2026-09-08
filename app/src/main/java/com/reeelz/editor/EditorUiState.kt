package com.reeelz.editor

import android.net.Uri

data class CropParameters(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f)
data class VideoSource(val uri: Uri, val durationMs: Long, val width: Int, val height: Int)
data class EditorUiState(
    val source: VideoSource? = null,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val crop: CropParameters = CropParameters(),
    val text: TextParameters = TextParameters(),
    val loading: Boolean = false,
    val exporting: Boolean = false,
    val progress: Int = 0,
    val message: String? = null,
)

data class CropBounds(val left: Float, val right: Float, val bottom: Float, val top: Float)

fun cropBounds(width: Int, height: Int, crop: CropParameters): CropBounds {
    val aspect = width.toFloat() / height
    val target = 9f / 16f
    val halfWidth = minOf(1f, target / aspect) / crop.zoom.coerceIn(1f, 4f)
    val halfHeight = minOf(1f, aspect / target) / crop.zoom.coerceIn(1f, 4f)
    val cx = crop.x.coerceIn(-1f, 1f) * (1f - halfWidth)
    val cy = -crop.y.coerceIn(-1f, 1f) * (1f - halfHeight)
    return CropBounds(cx - halfWidth, cx + halfWidth, cy - halfHeight, cy + halfHeight)
}
