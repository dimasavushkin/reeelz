package com.reeelz.editor

data class EditValues(val startMs: Long, val endMs: Long, val crop: CropParameters, val text: TextParameters,
                      val extraTexts: List<TextParameters> = emptyList(), val audio: AudioParameters = AudioParameters(),
                      val clips: List<VideoClip> = emptyList(), val selectedClipIndex: Int = 0)

/** In-memory history of parameters only; video and export state are never copied. */
class EditHistory(private val limit: Int = 50) {
    private val past = ArrayDeque<EditValues>()
    private val future = ArrayDeque<EditValues>()
    private var group: String? = null
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()

    fun record(before: EditValues, after: EditValues, key: String) {
        if (before == after) return
        if (group != key) {
            past.addLast(before)
            if (past.size > limit) past.removeFirst()
        }
        group = key
        future.clear()
    }
    fun finish() { group = null }
    fun undo(current: EditValues): EditValues? {
        finish()
        if (!canUndo) return null
        future.addLast(current)
        return past.removeLast()
    }
    fun redo(current: EditValues): EditValues? {
        finish()
        if (!canRedo) return null
        past.addLast(current)
        return future.removeLast()
    }
    fun clear() { past.clear(); future.clear(); finish() }
}
