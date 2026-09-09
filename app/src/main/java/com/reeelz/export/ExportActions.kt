package com.reeelz.export

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Grants access to one published video, never to the original or private draft. */
object ExportActions {
    fun intent(uri: Uri, share: Boolean): Intent {
        require(uri.scheme == "content" && uri.authority == "media")
        return Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
            if (share) {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
            } else setDataAndType(uri, "video/mp4")
            clipData = ClipData.newRawUri("Reeelz MP4", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun open(activity: Activity, uri: Uri, share: Boolean) {
        withContext(Dispatchers.IO) {
            checkNotNull(activity.contentResolver.openFileDescriptor(uri, "r")).use { }
        }
        withContext(Dispatchers.Main.immediate) {
            activity.startActivity(Intent.createChooser(intent(uri, share), if (share) "Поделиться роликом" else "Посмотреть ролик"))
        }
    }
}
