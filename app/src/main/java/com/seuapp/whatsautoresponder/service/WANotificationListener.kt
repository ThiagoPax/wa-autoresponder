package com.seuapp.whatsautoresponder.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.seuapp.whatsautoresponder.util.LogBus
import com.seuapp.whatsautoresponder.util.Prefs

class WANotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        if (!Prefs.isEnabled(this)) return
        Prefs.appendLog(this, "Serviço conectado.")
        LogBus.emit("Serviço conectado.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Prefs.isEnabled(this)) return

        try {
            val pkg = sbn.packageName ?: return
            if (pkg != "com.whatsapp") return

            val extras = sbn.notification?.extras
            val title = extractTitle(extras)
            val body = extractBestText(extras)

            if (title.isBlank() && body.isBlank()) return

            Prefs.appendLog(this, "[WhatsApp] $title: $body")
            LogBus.emit("[WhatsApp] $title: $body")

            val actions = runCatching { sbn.notification?.actions ?: emptyArray() }
                .getOrDefault(emptyArray())

            val hasReply = actions.any {
                runCatching { (it.title?.toString() ?: "").contains("responder", ignoreCase = true) }
                    .getOrDefault(false)
            }

            if (hasReply) {
                Prefs.appendLog(this, "Ação de resposta disponível (apenas log).")
                LogBus.emit("Ação de resposta disponível (apenas log).")
            }

        } catch (t: Throwable) {
            val msg = "ERRO ao ler notificação: ${t::class.java.simpleName}: ${t.message ?: "sem mensagem"}"
            Prefs.appendLog(this, msg)
            LogBus.emit(msg)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!Prefs.isEnabled(this)) return
        Prefs.appendLog(this, "Notificação removida.")
        LogBus.emit("Notificação removida.")
    }

    private fun Bundle?.getCs(key: String): String? =
        try {
            this?.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }

    private fun extractTitle(extras: Bundle?): String {
        extras.getCs(Notification.EXTRA_TITLE)?.let { return it }
        extras.getCs(Notification.EXTRA_TITLE_BIG)?.let { return it }
        return ""
    }

    private fun extractBestText(extras: Bundle?): String {
        try {
            when (val v = extras?.get(Notification.EXTRA_TEXT_LINES)) {
                is Array<*> -> {
                    val s = v.filterIsInstance<CharSequence>().joinToString("\n") { it.toString() }
                    if (s.isNotBlank()) return s
                }
                is CharSequence -> {
                    val s = v.toString()
                    if (s.isNotBlank()) return s
                }
            }
        } catch (_: Throwable) {
            // ignora e tenta próximas fontes
        }

        extras.getCs(Notification.EXTRA_BIG_TEXT)?.let { if (it.isNotBlank()) return it }
        extras.getCs(Notification.EXTRA_TEXT)?.let { if (it.isNotBlank()) return it }
        extras.getCs(Notification.EXTRA_SUB_TEXT)?.let { if (it.isNotBlank()) return it }
        extras.getCs("android.summaryText")?.let { if (it.isNotBlank()) return it }

        return ""
    }
}
