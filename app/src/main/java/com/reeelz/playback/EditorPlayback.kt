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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@UnstableApi
class EditorPlayback(context: Context, onError: (String) -> Unit) {
    val player = ExoPlayer.Builder(context).build()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val playing = MutableStateFlow(false)
    val isPlaying = playing.asStateFlow()
    init {
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing.value = isPlaying }
            override fun onPlayerError(error: PlaybackException) { onError(error.localizedMessage ?: "Ошибка preview") }
        })
    }
    fun load(state: EditorUiState) {
        refreshHandler.removeCallbacksAndMessages(null)
        player.setVideoEffects(EditEffects.video(state))
        player.setMediaItem(EditEffects.item(state))
        player.prepare()
    }
    fun crop(state: EditorUiState) {
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
    fun pause() { refreshHandler.removeCallbacksAndMessages(null); player.pause() }
    fun clear() { refreshHandler.removeCallbacksAndMessages(null); player.stop(); player.clearMediaItems() }
    fun release() { refreshHandler.removeCallbacksAndMessages(null); player.release() }
}
