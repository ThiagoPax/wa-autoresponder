package com.seuapp.whatsautoresponder.util

import android.content.Context
import android.content.Intent

/**
 * Barramento simples para avisar a UI que chegou uma nova linha de log.
 * Fornece as constantes ACTION_LOG_UPDATED e EXTRA_LINE usadas na MainActivity.
 *
 * Funciona com LocalBroadcastManager (se a lib estiver no classpath) e,
 * em último caso, cai para um broadcast normal restrito ao próprio pacote.
 */
object LogBus {
    const val ACTION_LOG_UPDATED = "com.seuapp.whatsautoresponder.ACTION_LOG_UPDATED"
    const val EXTRA_LINE = "EXTRA_LINE"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun emit(line: String) {
        val ctx = appContext ?: return
        val intent = Intent(ACTION_LOG_UPDATED).putExtra(EXTRA_LINE, line)

        // Tenta LocalBroadcastManager (sem depender do import em compile-time)
        try {
            val lbmCls = Class.forName("androidx.localbroadcastmanager.content.LocalBroadcastManager")
            val getInstance = lbmCls.getMethod("getInstance", Context::class.java)
            val lbm = getInstance.invoke(null, ctx)
            val sendBroadcast = lbmCls.getMethod("sendBroadcast", Intent::class.java)
            sendBroadcast.invoke(lbm, intent)
        } catch (_: Throwable) {
            // Fallback: broadcast normal, restrito ao próprio app
            ctx.sendBroadcast(intent.setPackage(ctx.packageName))
        }
    }
}
