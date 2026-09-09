package com.reeelz.editor

import org.junit.Assert.*
import org.junit.Test

class CaptionGeometryTest {
    @Test fun dragFollowsFingerAcrossDifferentCaptionSizes() {
        for (width in listOf(100, 500, 960)) {
            val before = captionBounds(TextParameters(), width, 150)
            val after = captionBounds(TextParameters(x = captionDragDelta(.02f, width, 1080)), width, 150)
            assertEquals(.02f, after.left - before.left, .00001f)
        }
    }
    @Test fun boundsMatchCanvasEdgesAtExtremePositions() {
        val bounds = captionBounds(TextParameters(x = -1f, y = 1f), 500, 200)
        assertEquals(0f, bounds.left, .00001f)
        assertEquals(1f, bounds.bottom, .00001f)
        assertEquals(0f, captionDragDelta(.2f, 1080, 1080), 0f)
    }
}
