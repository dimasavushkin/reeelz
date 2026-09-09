package com.reeelz.editor

data class CaptionBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

fun captionBounds(text: TextParameters, width: Int, height: Int): CaptionBounds {
    val x = (1f + textAnchor(text.x, width, 1080)) / 2f
    val y = (1f + textAnchor(text.y, height, 1920)) / 2f
    return CaptionBounds(x - width / 2160f, y - height / 3840f, x + width / 2160f, y + height / 3840f)
}

fun captionDragDelta(canvasFraction: Float, overlaySize: Int, canvasSize: Int): Float {
    val space = 1f - overlaySize.toFloat() / canvasSize
    return if (space <= 0f) 0f else 2f * canvasFraction / space
}
