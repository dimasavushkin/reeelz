package com.reeelz.editor

import org.junit.Assert.*
import org.junit.Test

class TextParametersTest {
    @Test fun clampsInputToSupportedLimits() {
        val text = TextParameters("a".repeat(250), 300f, x = -3f, y = 5f).normalized()
        assertEquals(200, text.content.length)
        assertEquals(120f, text.size, 0f)
        assertEquals(-1f, text.x, 0f)
        assertEquals(1f, text.y, 0f)
    }
    @Test fun captionEdgesStayInsideCanvas() {
        for (width in listOf(24, 400, 960)) for (position in listOf(-1f, 0f, 1f)) {
            val center = textAnchor(position, width, 1080)
            assertTrue(center - width / 1080f >= -1.00001f)
            assertTrue(center + width / 1080f <= 1.00001f)
        }
    }
}
