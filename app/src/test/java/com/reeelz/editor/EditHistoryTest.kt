package com.reeelz.editor

import org.junit.Assert.*
import org.junit.Test

class EditHistoryTest {
    private val initial = EditValues(0, 10000, CropParameters(), TextParameters())
    @Test fun continuousGestureIsOneStepAndRedoRestoresFinalValue() {
        val history = EditHistory()
        val middle = initial.copy(crop = CropParameters(2f))
        val end = initial.copy(crop = CropParameters(3f))
        history.record(initial, middle, "crop")
        history.record(middle, end, "crop")
        history.finish()
        assertEquals(initial, history.undo(end))
        assertFalse(history.canUndo)
        assertEquals(end, history.redo(initial))
    }
    @Test fun newEditAfterUndoDiscardsRedoButNoOpDoesNot() {
        val history = EditHistory()
        val edited = initial.copy(text = TextParameters("Hello"))
        history.record(initial, edited, "text")
        history.undo(edited)
        history.record(initial, initial, "text")
        assertTrue(history.canRedo)
        history.record(initial, initial.copy(startMs = 1000), "trim")
        assertFalse(history.canRedo)
    }
    @Test fun separateGesturesRemainSeparateAndHistoryIsBounded() {
        val history = EditHistory(2)
        var current = initial
        repeat(3) {
            val next = current.copy(startMs = (it + 1) * 100L)
            history.record(current, next, "trim")
            history.finish()
            current = next
        }
        current = requireNotNull(history.undo(current))
        assertEquals(200L, current.startMs)
        current = requireNotNull(history.undo(current))
        assertEquals(100L, current.startMs)
        assertNull(history.undo(current))
        history.clear()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }
}
