package com.reeelz.editor

data class TextParameters(
    val content: String = "",
    val size: Float = 64f,
    val color: Int = -1,
    val x: Float = 0f,
    val y: Float = 0.5f,
) {
    fun normalized() = copy(content = content.take(200), size = size.coerceIn(32f, 120f),
        x = x.coerceIn(-1f, 1f), y = y.coerceIn(-1f, 1f))
}

// Position spans the available space, keeping the entire caption inside the canvas.
fun textAnchor(position: Float, overlaySize: Int, canvasSize: Int): Float =
    position.coerceIn(-1f, 1f) * (1f - overlaySize.toFloat() / canvasSize).coerceAtLeast(0f)
