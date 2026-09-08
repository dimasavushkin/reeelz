package com.reeelz.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings

@UnstableApi
object CaptionEffect {
    fun create(parameters: TextParameters): OverlayEffect {
        val text = parameters.normalized()
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = text.color
            textSize = text.size
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }
        fun layout() = StaticLayout.Builder.obtain(text.content, 0, text.content.length, paint,
            kotlin.math.ceil(Layout.getDesiredWidth(text.content, paint).toDouble()).toInt().coerceIn(1, 936))
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(true).build()
        var layout = layout()
        // Long captions wrap and shrink to fit instead of disappearing outside the canvas.
        while (layout.height > 1500 && paint.textSize > 1f) {
            paint.textSize *= 0.9f
            layout = layout()
        }
        val bitmap = Bitmap.createBitmap(layout.width + 24, layout.height + 24, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { translate(12f, 12f); layout.draw(this) }
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(textAnchor(text.x, bitmap.width, 1080), -textAnchor(text.y, bitmap.height, 1920))
            .build()
        return OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(bitmap, settings)))
    }
}
