package com.reeelz.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

object TimelineFrames {
    suspend fun load(context: Context, source: VideoSource): List<Bitmap?> = withContext(Dispatchers.IO) {
        MediaMetadataRetriever().use { reader ->
            reader.setDataSource(context, source.uri)
            List(8) { index ->
                ensureActive()
                runCatching { reader.getScaledFrameAtTime((source.durationMs - 1) * index / 7 * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 96, 96) }.getOrNull()
            }
        }
    }
}
