package com.reeelz.editor

import org.junit.Assert.*
import org.junit.Test

class CropBoundsTest {
    @Test fun cropAlwaysFillsPortraitCanvasWithoutExposingOutsidePixels() {
        for ((w, h) in listOf(1920 to 1080, 1080 to 1920, 1000 to 1000, 720 to 2560)) {
            for (zoom in listOf(1f, 2f, 4f)) for (x in listOf(-1f, 0f, 1f)) for (y in listOf(-1f, 0f, 1f)) {
                val b = cropBounds(w, h, CropParameters(zoom, x, y))
                assertTrue(b.left >= -1.00001f && b.right <= 1.00001f)
                assertTrue(b.bottom >= -1.00001f && b.top <= 1.00001f)
                assertEquals(9f / 16f, (b.right - b.left) * w / ((b.top - b.bottom) * h), 0.00001f)
            }
        }
    }
}
