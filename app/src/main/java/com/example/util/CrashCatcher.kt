package com.example.util

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

object CrashCatcher {
    private const val FILE_NAME = "crash_report.txt"
    private const val TAG = "CrashCatcher"

    fun init(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTraceString = sw.toString()
                
                val report = """
                    Dispositivo: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
                    Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
                    Fecha/Hora: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
                    Hilo: ${thread.name}
                    
                    Error:
                    $stackTraceString
                """.trimIndent()
                
                val file = File(appContext.cacheDir, FILE_NAME)
                file.writeText(report)
                Log.e(TAG, "Crash report written successfully to ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash report", e)
            } finally {
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable)
                } else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
        }
    }

    fun getCrashReport(context: Context): String? {
        return try {
            val file = File(context.cacheDir, FILE_NAME)
            if (file.exists()) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read crash report", e)
            null
        }
    }

    fun clearCrashReport(context: Context) {
        try {
            val file = File(context.cacheDir, FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear crash report", e)
        }
    }
}
