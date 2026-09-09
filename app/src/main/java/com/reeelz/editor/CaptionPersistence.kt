package com.reeelz.editor

import java.util.Properties

internal fun Properties.writeCaption(prefix: String, text: TextParameters) {
    setProperty(prefix + "content", text.content)
    setProperty(prefix + "size", text.size.toString())
    setProperty(prefix + "color", text.color.toString())
    setProperty(prefix + "x", text.x.toString())
    setProperty(prefix + "y", text.y.toString())
    setProperty(prefix + "alignment", text.alignment.toString())
    setProperty(prefix + "background", text.background.toString())
    setProperty(prefix + "start", text.startMs.toString())
    setProperty(prefix + "end", text.endMs.toString())
}

internal fun Properties.readCaption(prefix: String, fallback: TextParameters = TextParameters()): TextParameters {
    if (!containsKey(prefix + "content")) return fallback
    fun f(key: String) = getProperty(prefix + key).toFloat().also { require(it.isFinite()) }
    return TextParameters(getProperty(prefix + "content"), f("size"), getProperty(prefix + "color").toInt(), f("x"), f("y"),
        getProperty(prefix + "alignment").toInt(), getProperty(prefix + "background").toBoolean(),
        getProperty(prefix + "start").toLong(), getProperty(prefix + "end").toLong()).normalized()
}
