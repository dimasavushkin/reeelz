package com.reeelz.editor

import android.os.StatFs
import java.io.File

object StorageChecks {
    private const val RESERVE = 16L * 1024 * 1024
    fun requireSpace(directory: File, bytes: Long = 0) {
        check(StatFs(directory.absolutePath).availableBytes > bytes.coerceAtLeast(0) + RESERVE) {
            "Недостаточно свободного места. Освободите память и повторите."
        }
    }
}
