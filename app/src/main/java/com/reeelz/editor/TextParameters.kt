package com.reeelz.editor

data class TextParameters(
    val content: String = "",
    val size: Float = 64f,
    val color: Int = -1,
    val x: Float = 0f,
    val y: Float = 0.5f,
    val alignment: Int = 1,
    val background: Boolean = false,
    val startMs: Long = 0,
    val endMs: Long = Long.MAX_VALUE,
) {
    fun normalized() = copy(content = content.take(200), size = size.coerceIn(32f, 120f),
        x = x.coerceIn(-1f, 1f), y = y.coerceIn(-1f, 1f), alignment = alignment.coerceIn(0, 2),
        startMs = startMs.coerceIn(0, Long.MAX_VALUE - 1), endMs = endMs.coerceAtLeast(startMs.coerceIn(0, Long.MAX_VALUE - 1) + 1))
}

fun TextParameters.visibleAt(timeMs: Long) = content.isNotBlank() && timeMs >= startMs && timeMs < endMs

// Position spans the available space, keeping the entire caption inside the canvas.
fun textAnchor(position: Float, overlaySize: Int, canvasSize: Int): Float =
    position.coerceIn(-1f, 1f) * (1f - overlaySize.toFloat() / canvasSize).coerceAtLeast(0f)
