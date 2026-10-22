package com.example.data.database

import android.content.Context
import java.io.File

class DatabaseBackupManager(private val context: Context) {
    fun exportBackup(destFile: File): Boolean {
        return try {
            val dbFile = context.getDatabasePath("valencia_transit.db")
            if (dbFile.exists()) {
                dbFile.copyTo(destFile, overwrite = true)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun restoreBackup(srcFile: File): Boolean {
        return try {
            val dbFile = context.getDatabasePath("valencia_transit.db")
            if (srcFile.exists()) {
                srcFile.copyTo(dbFile, overwrite = true)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
