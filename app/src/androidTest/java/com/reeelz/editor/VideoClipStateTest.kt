package com.reeelz.editor

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test

class VideoClipStateTest {
    private fun clip(id: String, duration: Long) = VideoClip(id, VideoSource(Uri.parse("file:///$id.mp4"), duration, 1920, 1080))

    @Test fun selectingAndEditingClipKeepsLegacyFieldsInSync() {
        val first = clip("first", 1000)
        val second = clip("second", 2000).copy(startMs = 200, endMs = 1500, crop = CropParameters(2f, rotation = 90))
        val state = EditorUiState(source = first.source, endMs = 1000, clips = listOf(first, second)).withSelectedClip(1)
        assertEquals(second.source, state.source)
        assertEquals(200L, state.startMs)
        assertEquals(second.crop, state.crop)
        val edited = state.withActiveClip(second.copy(startMs = 400))
        assertEquals(400L, edited.startMs)
        assertEquals(400L, edited.clips[1].startMs)
        assertEquals(first, edited.clips[0])
    }

    @Test fun oldSingleClipStateStillHasTimelineAdapter() {
        val source = clip("legacy", 1000).source
        val state = EditorUiState(source = source, startMs = 100, endMs = 900)
        assertEquals(1, state.timelineClips().size)
        assertEquals("legacy", state.timelineClips().single().id)
    }
}
