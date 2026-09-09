package com.reeelz.editor

import org.junit.Assert.*
import org.junit.Test

class AudioParametersTest {
    @Test fun malformedVolumesCannotReachThePlayer() {
        val result = AudioParameters(originalVolume = Float.NaN, musicVolume = Float.POSITIVE_INFINITY, musicDurationMs = -1).normalized()
        assertEquals(1f, result.originalVolume)
        assertEquals(.5f, result.musicVolume)
        assertEquals(0L, result.musicDurationMs)
        assertEquals(0f, AudioParameters(originalVolume = -10f).normalized().originalVolume)
    }
}
