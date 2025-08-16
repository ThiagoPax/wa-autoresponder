package com.seuapp.whatsautoresponder.util

import android.content.Context
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.seuapp.whatsautoresponder.ui.MainActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogHelper {
    private const val FILE_NAME = "auto_log.txt"
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun write(context: Context, tag: String, msg: String) {
        val line = "[${sdf.format(Date())}] $tag: $msg"
        try {
            File(context.filesDir, FILE_NAME).appendText(line + "\n")
        } catch (_: Throwable) { /* ignore */ }

        // Notifica UI
        val intent = android.content.Intent(MainActivity.ACTION_LOG_UPDATED)
            .putExtra(MainActivity.EXTRA_LINE, line)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    @Synchronized
    fun readAll(context: Context): String {
        val f = File(context.filesDir, FILE_NAME)
        return if (f.exists()) f.readText() else ""
    }

    @Synchronized
    fun clear(context: Context) {
        val f = File(context.filesDir, FILE_NAME)
        if (f.exists()) f.writeText("")
    }
}
