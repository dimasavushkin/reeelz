package com.reeelz.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.reeelz.editor.EditEffects
import com.reeelz.editor.EditorUiState
import com.reeelz.editor.AudioParameters
import com.reeelz.editor.forTimelineClip
import com.reeelz.editor.outputDurationMs
import com.reeelz.editor.timelineClips
import com.reeelz.editor.timelinePosition
import com.reeelz.editor.timelineStartMs
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@UnstableApi
class EditorPlayback(context: Context, onError: (String) -> Unit, private val onClipChanged: (Int) -> Unit = {}) {
    val player = ExoPlayer.Builder(context).build()
    private val music = ExoPlayer.Builder(context).build()
    private var audioParameters = AudioParameters()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val playing = MutableStateFlow(false)
    val isPlaying = playing.asStateFlow()
    private var editorState = EditorUiState()
    private val position = MutableStateFlow(0L)
    val positionMs = position.asStateFlow()
    private val sourcePosition = MutableStateFlow(0L)
    val sourcePositionMs = sourcePosition.asStateFlow()
    private val clock = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updatePositions()
            syncMusic()
            clock.postDelayed(this, 100)
        }
    }
    init {
        player.repeatMode = Player.REPEAT_MODE_ALL
        music.repeatMode = Player.REPEAT_MODE_ONE
        music.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { onError("Музыка: ${error.localizedMessage}") }
        })
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
        clock.post(tick)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing.value = isPlaying; syncMusic() }
            override fun onPlayerError(error: PlaybackException) { onError(error.localizedMessage ?: "Ошибка preview") }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val clips = editorState.timelineClips()
                if (clips.isNotEmpty()) {
                    val index = player.currentMediaItemIndex.coerceIn(0, clips.lastIndex)
                    player.setVideoEffects(EditEffects.video(editorState.forTimelineClip(index)))
                    onClipChanged(index)
                }
                updatePositions()
            }
        })
    }
    fun load(state: EditorUiState) {
        audio(state.audio)
        editorState = state
        refreshHandler.removeCallbacksAndMessages(null)
        val clips = state.timelineClips()
        if (clips.isEmpty()) return
        val selected = state.selectedClipIndex.coerceIn(0, clips.lastIndex)
        player.setVideoEffects(EditEffects.video(state.forTimelineClip(selected)))
        player.setMediaItems(clips.indices.map { EditEffects.item(state.forTimelineClip(it)) }, selected, 0L)
        player.prepare()
        updatePositions()
    }
    fun crop(state: EditorUiState) {
        audio(state.audio)
        editorState = state
        refreshHandler.removeCallbacksAndMessages(null)
        // Coalesce slider/input events; a 1 ms seek can reuse the old decoded frame.
        // Reprepare the stream to render the new overlay even while paused.
        refreshHandler.postDelayed({
            val globalPosition = position.value
            val shouldPlay = player.playWhenReady
            loadAtTimelinePosition(state, globalPosition, shouldPlay)
        }, 100)
    }
    fun toggle() { if (player.isPlaying) player.pause() else player.play() }
    fun stopForExport() {
        pause(); player.stop(); music.stop(); music.clearMediaItems(); audioParameters = AudioParameters()
    }
    fun audio(parameters: AudioParameters) {
        player.volume = parameters.originalVolume
        music.volume = parameters.musicVolume
        if (parameters.musicUri != audioParameters.musicUri) {
            music.stop(); music.clearMediaItems()
            parameters.musicUri?.let { music.setMediaItem(MediaItem.fromUri(it)); music.prepare() }
        }
        audioParameters = parameters
        syncMusic()
    }
    private fun syncMusic() {
        if (audioParameters.musicUri == null || audioParameters.musicDurationMs <= 0) { music.pause(); return }
        val target = position.value % audioParameters.musicDurationMs
        if (kotlin.math.abs(music.currentPosition - target) > 100) music.seekTo(target)
        music.playWhenReady = player.isPlaying && audioParameters.musicVolume > 0
    }
    fun seek(absoluteMs: Long) {
        player.pause()
        val clips = editorState.timelineClips()
        if (clips.isEmpty()) return
        val index = editorState.selectedClipIndex.coerceIn(0, clips.lastIndex)
        val clip = clips[index]
        val value = absoluteMs.coerceIn(clip.startMs, (clip.endMs - 1).coerceAtLeast(clip.startMs))
        player.setVideoEffects(EditEffects.video(editorState.forTimelineClip(index)))
        player.seekTo(index, value - clip.startMs)
        updatePositions()
        syncMusic()
    }
    fun seekTimeline(absoluteMs: Long) {
        player.pause()
        val clips = editorState.timelineClips()
        if (clips.isEmpty()) return
        val target = editorState.timelinePosition(absoluteMs)
        player.setVideoEffects(EditEffects.video(editorState.forTimelineClip(target.clipIndex)))
        player.seekTo(target.clipIndex, target.localMs)
        updatePositions()
        syncMusic()
    }
    private fun loadAtTimelinePosition(state: EditorUiState, globalMs: Long, shouldPlay: Boolean) {
        editorState = state
        val clips = state.timelineClips()
        if (clips.isEmpty()) return
        val target = state.timelinePosition(globalMs)
        player.stop()
        player.setVideoEffects(EditEffects.video(state.forTimelineClip(target.clipIndex)))
        player.setMediaItems(clips.indices.map { EditEffects.item(state.forTimelineClip(it)) }, target.clipIndex, target.localMs)
        player.prepare()
        player.playWhenReady = shouldPlay
        updatePositions()
    }
    private fun updatePositions() {
        val clips = editorState.timelineClips()
        if (clips.isEmpty()) { position.value = 0; sourcePosition.value = 0; return }
        val index = player.currentMediaItemIndex.coerceIn(0, clips.lastIndex)
        val local = player.currentPosition.coerceIn(0, (clips[index].outputDurationMs - 1).coerceAtLeast(0))
        position.value = editorState.timelineStartMs(index) + local
        sourcePosition.value = clips[index].startMs + local
    }
    fun pause() { refreshHandler.removeCallbacksAndMessages(null); player.pause(); music.pause() }
    fun clear() { refreshHandler.removeCallbacksAndMessages(null); player.stop(); player.clearMediaItems(); music.stop(); music.clearMediaItems(); audioParameters = AudioParameters() }
    fun release() { clock.removeCallbacksAndMessages(null); refreshHandler.removeCallbacksAndMessages(null); player.release(); music.release() }
}
