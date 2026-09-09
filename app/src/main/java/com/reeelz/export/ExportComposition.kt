package com.reeelz.export

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import com.reeelz.editor.*

@UnstableApi
object ExportComposition {
    fun create(state: EditorUiState): Composition {
        val audio = state.audio.normalized()
        val video = EditedMediaItem.Builder(EditEffects.item(state))
            .setRemoveAudio(audio.originalVolume == 0f)
            .setEffects(Effects(listOf(GainAudioProcessor(audio.originalVolume)), EditEffects.video(state))).build()
        val sequences = mutableListOf(EditedMediaItemSequence.Builder(video).build())
        if (audio.musicUri != null && audio.musicVolume > 0f) {
            val music = MediaItem.Builder().setUri(audio.musicUri)
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                    .setEndPositionMs(minOf(audio.musicDurationMs, state.endMs - state.startMs)).build()).build()
            val edited = EditedMediaItem.Builder(music).setRemoveVideo(true)
                .setEffects(Effects(listOf(GainAudioProcessor(audio.musicVolume)), emptyList())).build()
            sequences += EditedMediaItemSequence.Builder(edited).setIsLooping(true).build()
        }
        return Composition.Builder(sequences).build()
    }
}
