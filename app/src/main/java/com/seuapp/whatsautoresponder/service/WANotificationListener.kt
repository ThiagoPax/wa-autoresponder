package com.seuapp.whatsautoresponder.service

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class WANotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Listener conectado e ativo.")
        // Aqui você pode sinalizar para a UI (via SharedPrefs/Broadcast) que o serviço está ativo
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener desconectado pelo sistema. Solicitando rebind...")
        // Pede para o sistema reconectar quando possível (Android 7.0+)
        try {
            requestRebind(ComponentName(this, WANotificationListener::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao solicitar rebind: ${e.message}", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Disparado quando uma notificação é publicada/atualizada
        val pkg = sbn.packageName ?: return

        // Exemplo: monitora WhatsApp (com.whatsapp ou WhatsApp Business com.whatsapp.w4b)
        if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
            val extras = sbn.notification.extras
            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""

            Log.d(TAG, "WhatsApp -> de: $title | msg: $text")

            // TODO: coloque aqui a sua lógica de detecção de palavras-chave
            // e eventual resposta automática (se desejar implementar reply).
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Notificação removida
        Log.d(TAG, "Notificação removida de: ${sbn.packageName}")
    }

    companion object {
        private const val TAG = "WAListener"
    }
}
