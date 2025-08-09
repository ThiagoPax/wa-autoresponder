package com.seuapp.whatsautoresponder.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.app.Notification
import android.os.Bundle
import androidx.core.app.RemoteInput // use sempre a versão androidx

class WANotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "WAListener"
        private val WA_PACKAGES = setOf(
            "com.whatsapp",          // WhatsApp pessoal
            "com.whatsapp.w4b"       // WhatsApp Business
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener conectado com sucesso")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener desconectado")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return

            val pkg = sbn.packageName ?: return
            if (!WA_PACKAGES.contains(pkg)) return

            val notification = sbn.notification ?: return
            val extras: Bundle = notification.extras ?: Bundle()

            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            Log.d(TAG, "Nova notificação do WhatsApp | de=$title | msg=$text")

            // Se futuramente você quiser responder via notificação, lembre-se:
            // - Encontre a ação "Responder" na notification.actions
            // - Use androidx.core.app.RemoteInput para preencher a resposta
            // Aqui não respondemos nada, só registramos/filtramos (evita crashes).
        } catch (t: Throwable) {
            Log.e(TAG, "Erro em onNotificationPosted", t)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Opcional: logar remoções
        try {
            if (sbn == null) return
            val pkg = sbn.packageName ?: return
            if (!WA_PACKAGES.contains(pkg)) return
            Log.d(TAG, "Notificação do WhatsApp removida")
        } catch (t: Throwable) {
            Log.e(TAG, "Erro em onNotificationRemoved", t)
        }
    }
}
