package com.seuapp.whatsautoresponder.util

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * Salva o último stacktrace em files/crash_last.txt e joga no log do app.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private lateinit var appCtx: Context

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            appCtx.openFileOutput("crash_last.txt", Context.MODE_PRIVATE)
                .use { it.write(sw.toString().toByteArray()) }
            // também manda uma linha no log do app
            Prefs.appendLog(appCtx, "[${Date()}] ERRO FATAL: ${e::class.java.simpleName}: ${e.message}")
        } catch (_: Throwable) { /* ignore */ }
        // mantém comportamento padrão (mostra o diálogo do sistema)
        defaultHandler?.uncaughtException(t, e)
    }

    fun readLast(ctx: Context): String? = try {
        ctx.openFileInput("crash_last.txt").bufferedReader().use { it.readText() }
    } catch (_: Throwable) { null }

    fun clear(ctx: Context) {
        try { ctx.deleteFile("crash_last.txt") } catch (_: Throwable) { }
    }
}
