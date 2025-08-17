package com.seuapp.whatsautoresponder.util

import android.content.Context
import android.content.Intent

/**
 * Wrapper de compatibilidade para projetos antigos que usavam broadcast.
 * Hoje o app usa Prefs + LogBus, mas mantemos ACTION/EXTRA para evitar erros.
 */
object LogHelper {
    const val ACTION_LOG_UPDATED = "com.seuapp.whatsautoresponder.LOG_UPDATED"
    const val EXTRA_LINE = "line"

    fun append(ctx: Context, line: String) {
        // grava no SharedPreferences
        Prefs.appendLog(ctx, line)
        // notifica a UI em tempo real
        LogBus.emit(line)
        // broadcast opcional (compatibilidade com receivers antigos)
        ctx.sendBroadcast(Intent(ACTION_LOG_UPDATED).putExtra(EXTRA_LINE, line))
    }

    fun clear(ctx: Context) {
        Prefs.clearLog(ctx)
        LogBus.emit("Log limpo.")
        ctx.sendBroadcast(Intent(ACTION_LOG_UPDATED).putExtra(EXTRA_LINE, ""))
    }

    fun read(ctx: Context): String = Prefs.readLog(ctx)
}
