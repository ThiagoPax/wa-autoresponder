package com.seuapp.whatsautoresponder

import android.app.Application
import com.seuapp.whatsautoresponder.util.Prefs
import java.io.PrintWriter
import java.io.StringWriter

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            val sw = StringWriter().also { ex.printStackTrace(PrintWriter(it)) }
            val text = buildString {
                appendLine("CRASH em ${System.currentTimeMillis()} no thread ${thread.name}")
                appendLine("${ex::class.java.name}: ${ex.message}")
                append(sw.toString())
            }
            // grava para leitura no próximo boot do app
            runCatching {
                openFileOutput("last_crash.txt", MODE_PRIVATE).use { it.write(text.toByteArray()) }
            }
            // também joga no log interno
            Prefs.appendLog(applicationContext, text)
            // deixa o sistema tratar o crash normalmente
        }
    }
}
