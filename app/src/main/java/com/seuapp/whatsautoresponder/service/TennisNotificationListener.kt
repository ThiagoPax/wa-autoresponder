package com.seuapp.whatsautoresponder.service

import android.app.Notification
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.seuapp.whatsautoresponder.util.LogBus
import com.seuapp.whatsautoresponder.util.MessageParser
import com.seuapp.whatsautoresponder.util.Prefs
import com.seuapp.whatsautoresponder.util.ReplyHelper

class TennisNotificationListener : NotificationListenerService() {

    companion object {
        private val MONITORED_GROUPS = listOf(
            "GSTA1 - Tennis 🎾🔵",
            "GSTA2 - Tennis 🎾🔵"
        )
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val REPLY_DELAY_MS = 10_000L  // 10 segundos
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        log("Serviço conectado")
        log("Monitorando: ${MONITORED_GROUPS.joinToString(", ")}")
    }

    override fun onListenerDisconnected() {
        log("Serviço desconectado")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Prefs.isEnabled(this)) return
        if (sbn.packageName != WHATSAPP_PACKAGE) return

        try {
            processNotification(sbn)
        } catch (e: Exception) {
            log("ERRO: ${e.message}")
        }
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras ?: return
        val title = extractTitle(extras)
        val body = extractBody(extras)

        if (title.isBlank() || body.isBlank()) return

        // Check if it's from a monitored group
        if (!isMonitoredGroup(title)) return

        log("[$title] Mensagem recebida")

        // Check if already replied today
        if (Prefs.hasRepliedToday(this)) {
            log("Já respondeu hoje, ignorando")
            return
        }

        // Parse message and check for valid block
        val result = MessageParser.parseAndGenerateResponse(this, body)

        if (!result.canRespond) {
            log("${result.reason}")
            return
        }

        log("Bloco válido: ${result.blockHour}h - Aguardando 10s para responder...")

        // Try to send reply
        val actions = sbn.notification?.actions
        if (actions.isNullOrEmpty()) {
            log("Sem ação de resposta disponível")
            return
        }

        // Marca que vai responder (evita duplicatas durante o delay)
        Prefs.markRepliedToday(this)

        // Aguarda 10 segundos antes de responder
        val responseMessage = result.responseMessage!!
        val context = this
        
        handler.postDelayed({
            val success = ReplyHelper.sendReply(context, actions, responseMessage)

            if (success) {
                log("✓ Resposta enviada com sucesso!")
                log("Próxima resposta: amanhã")
            } else {
                // Se falhou, limpa a flag para permitir nova tentativa
                Prefs.clearReplyFlag(context)
                log("✗ Falha ao enviar resposta")
            }
        }, REPLY_DELAY_MS)
    }

    private fun isMonitoredGroup(title: String): Boolean {
        return MONITORED_GROUPS.any { title.contains(it, ignoreCase = true) }
    }

    private fun extractTitle(extras: Bundle): String {
        return extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: ""
    }

    private fun extractBody(extras: Bundle): String {
        // Try text lines first (for grouped notifications)
        extras.get(Notification.EXTRA_TEXT_LINES)?.let { lines ->
            if (lines is Array<*>) {
                val text = lines.filterIsInstance<CharSequence>().joinToString("\n")
                if (text.isNotBlank()) return text
            }
        }

        // Try big text
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let {
            if (it.isNotBlank()) return it
        }

        // Try regular text
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let {
            if (it.isNotBlank()) return it
        }

        return ""
    }

    private fun log(message: String) {
        Prefs.appendLog(this, message)
        LogBus.emit(message)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not needed for this implementation
    }
}
