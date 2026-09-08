package com.reeelz.editor

import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation

@UnstableApi
object EditEffects {
    fun video(state: EditorUiState): List<Effect> {
        val source = requireNotNull(state.source)
        val b = cropBounds(source.width, source.height, state.crop)
        return buildList {
            add(Crop(b.left, b.right, b.bottom, b.top))
            add(Presentation.createForWidthAndHeight(1080, 1920, Presentation.LAYOUT_STRETCH_TO_FIT))
            if (state.text.content.isNotBlank()) add(CaptionEffect.create(state.text))
        }
    }
    fun item(state: EditorUiState): MediaItem = MediaItem.Builder()
        .setUri(requireNotNull(state.source).uri)
        .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(state.startMs).setEndPositionMs(state.endMs).build())
        .build()
}
