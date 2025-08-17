package com.seuapp.whatsautoresponder.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.os.Handler
import android.os.Looper
import com.seuapp.whatsautoresponder.util.Prefs
import com.seuapp.whatsautoresponder.util.LogBus

class WANotificationListener : NotificationListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        // Apenas informação no log
        Prefs.appendLog(this, "Serviço conectado.")
        LogBus.emit("Serviço conectado.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!Prefs.isEnabled(this)) {
            // app desligado: só registra no log
            Prefs.appendLog(this, "Notificação recebida, mas app está DESLIGADO.")
            LogBus.emit("Notificação recebida, mas app está DESLIGADO.")
            return
        }

        // Exemplo mínimo para não quebrar build:
        val pkg = sbn.packageName ?: return
        val title = sbn.notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text  = sbn.notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        Prefs.appendLog(this, "[$pkg] $title: $text")

        // ==== AÇÕES DE RESPOSTA (apenas se precisar) ====
        // Troca de getActions() -> notification.actions
        val actions: Array<Notification.Action> = sbn.notification.actions ?: emptyArray()
        // Se precisar encontrar a ação de "Responder", faça seu filtro aqui.
        // Exemplo de uso seguro (não enviar nada, só logar):
        val hasReply = actions.any { (it.title?.toString() ?: "").contains("responder", ignoreCase = true) }
        if (hasReply) {
            Prefs.appendLog(this, "Ação de resposta detectada (apenas log).")
            LogBus.emit("Ação de resposta detectada (apenas log).")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Prefs.appendLog(this, "Notificação removida.")
        LogBus.emit("Notificação removida.")
    }
}
