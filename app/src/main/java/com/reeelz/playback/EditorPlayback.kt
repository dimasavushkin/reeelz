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
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@UnstableApi
class EditorPlayback(context: Context, onError: (String) -> Unit) {
    val player = ExoPlayer.Builder(context).build()
    private val music = ExoPlayer.Builder(context).build()
    private var audioParameters = AudioParameters()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val playing = MutableStateFlow(false)
    val isPlaying = playing.asStateFlow()
    private var clipStart = 0L
    private var clipEnd = 1L
    private val position = MutableStateFlow(0L)
    val positionMs = position.asStateFlow()
    private val clock = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            position.value = clipStart + player.currentPosition
            syncMusic()
            clock.postDelayed(this, 100)
        }
    }
    init {
        player.repeatMode = Player.REPEAT_MODE_ONE
        music.repeatMode = Player.REPEAT_MODE_ONE
        music.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { onError("Музыка: ${error.localizedMessage}") }
        })
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
        clock.post(tick)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing.value = isPlaying; syncMusic() }
            override fun onPlayerError(error: PlaybackException) { onError(error.localizedMessage ?: "Ошибка preview") }
        })
    }
    fun load(state: EditorUiState) {
        audio(state.audio)
        clipStart = state.startMs
        clipEnd = state.endMs
        refreshHandler.removeCallbacksAndMessages(null)
        player.setVideoEffects(EditEffects.video(state))
        player.setMediaItem(EditEffects.item(state))
        player.prepare()
    }
    fun crop(state: EditorUiState) {
        audio(state.audio)
        clipStart = state.startMs
        clipEnd = state.endMs
        refreshHandler.removeCallbacksAndMessages(null)
        // Coalesce slider/input events; a 1 ms seek can reuse the old decoded frame.
        // Reprepare the stream to render the new overlay even while paused.
        refreshHandler.postDelayed({
            val position = player.currentPosition.coerceIn(0, (state.endMs - state.startMs - 1).coerceAtLeast(0))
            val shouldPlay = player.playWhenReady
            player.stop()
            player.setVideoEffects(EditEffects.video(state))
            player.setMediaItem(EditEffects.item(state), position)
            player.prepare()
            player.playWhenReady = shouldPlay
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
        val target = player.currentPosition % audioParameters.musicDurationMs
        if (kotlin.math.abs(music.currentPosition - target) > 100) music.seekTo(target)
        music.playWhenReady = player.isPlaying && audioParameters.musicVolume > 0
    }
    fun seek(absoluteMs: Long) {
        player.pause()
        val value = absoluteMs.coerceIn(clipStart, (clipEnd - 1).coerceAtLeast(clipStart))
        player.seekTo(value - clipStart)
        position.value = value
        syncMusic()
    }
    fun pause() { refreshHandler.removeCallbacksAndMessages(null); player.pause(); music.pause() }
    fun clear() { refreshHandler.removeCallbacksAndMessages(null); player.stop(); player.clearMediaItems(); music.stop(); music.clearMediaItems(); audioParameters = AudioParameters() }
    fun release() { clock.removeCallbacksAndMessages(null); refreshHandler.removeCallbacksAndMessages(null); player.release(); music.release() }
}
