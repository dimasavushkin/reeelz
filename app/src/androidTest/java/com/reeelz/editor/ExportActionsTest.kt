package com.reeelz.editor

import android.content.Intent
import android.net.Uri
import com.reeelz.export.ExportActions
import org.junit.Assert.*
import org.junit.Test

class ExportActionsTest {
    private val output = Uri.parse("content://media/external/video/media/123")

    @Test fun shareContainsOnlyPublishedVideoWithReadPermission() {
        val intent = ExportActions.intent(output, true)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("video/mp4", intent.type)
        assertEquals(output, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(output, intent.clipData!!.getItemAt(0).uri)
        assertEquals(1, intent.clipData!!.itemCount)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
    }
    @Test fun viewTargetsTheExportedMp4() {
        val intent = ExportActions.intent(output, false)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(output, intent.data)
        assertEquals("video/mp4", intent.type)
    }
    @Test(expected = IllegalArgumentException::class)
    fun privateDraftCannotBeShared() {
        ExportActions.intent(Uri.parse("file:///data/data/com.reeelz/files/draft/video"), true)
    }
}
