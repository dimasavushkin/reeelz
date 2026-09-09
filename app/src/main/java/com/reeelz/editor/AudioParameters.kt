package com.reeelz.editor

data class AudioParameters(
    val originalVolume: Float = 1f,
    val musicUri: String? = null,
    val musicName: String = "Музыка",
    val musicDurationMs: Long = 0,
    val musicVolume: Float = .5f,
) {
    fun normalized() = copy(
        originalVolume = originalVolume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f,
        musicVolume = musicVolume.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .5f,
        musicDurationMs = musicDurationMs.coerceAtLeast(0),
    )
}
