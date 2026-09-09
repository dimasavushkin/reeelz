package com.reeelz.export

import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportRecovery(private val context: Context) {
    private val journal = context.getSharedPreferences("pending_export", Context.MODE_PRIVATE)
    fun begin(name: String) { check(journal.edit().putString("name", name).commit()) { "Не удалось записать состояние экспорта" } }
    fun finish() { journal.edit().remove("name").commit() }
    suspend fun recover() = withContext(Dispatchers.IO) {
        val name = journal.getString("name", null)
        if (name != null) {
            // Include pending entries explicitly; ordinary collection queries hide them.
            // This API also supports our minimum Android 10.
            @Suppress("DEPRECATION")
            val collection = MediaStore.setIncludePending(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            context.contentResolver.query(collection, arrayOf(MediaStore.Video.Media._ID),
                "${MediaStore.Video.Media.DISPLAY_NAME}=? AND ${MediaStore.Video.Media.IS_PENDING}=1 AND ${MediaStore.Video.Media.OWNER_PACKAGE_NAME}=?",
                arrayOf(name, context.packageName), null)?.use { cursor ->
                while (cursor.moveToNext()) context.contentResolver.delete(android.content.ContentUris.withAppendedId(collection, cursor.getLong(0)), null, null)
            }
            finish()
        }
        context.cacheDir.listFiles()?.filter { it.name.startsWith("reeelz-") && it.extension == "mp4" }?.forEach { it.delete() }
    }
}
