package com.seuapp.whatsautoresponder.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class WANotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.d(TAG, "Notification listener conectado")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            // Só WhatsApp
            val pkg = sbn.packageName ?: return
            if (pkg != "com.whatsapp") return

            val n: Notification = sbn.notification
            val extras = n.extras ?: return

            val title = (extras.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString()
            val text = buildString {
                val t = extras.getCharSequence(Notification.EXTRA_TEXT)
                if (t != null) append(t.toString())
                val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                if (big != null) {
                    if (isNotEmpty()) append("\n")
                    append(big.toString())
                }
            }.trim()
            if (text.isEmpty()) return

            // Grupos alvo
            val isTargetGroup =
                title.contains("GSTA1 - Tennis", ignoreCase = true) ||
                title.contains("GSTA2 - Tennis", ignoreCase = true)
            if (!isTargetGroup) return

            // Palavras-chave
            val hasKeyword = text.contains("vagas", ignoreCase = true) ||
                    text.contains("quem topa", ignoreCase = true)
            if (!hasKeyword) return

            // Dia da semana
            val hasWeekday = listOf("segunda","terça","terca","quarta","quinta","sexta")
                .any { wd -> text.contains(wd, ignoreCase = true) }
            if (!hasWeekday) return

            // Faixa de horário 18:00–23:59 (formato 18h00, 21h30 etc.)
            val timeRegex = Regex("""\b(1[89]|2[0-3])h([0-5]\d)\b""")
            if (!timeRegex.containsMatchIn(text)) return

            // Não responder se já tiver "Thiago Soares"
            if (text.contains("Thiago Soares", ignoreCase = true)) return

            // Precisa ter linha '-' vazia para preencher
            val replyText = makeReplyText(text) ?: return

            // Envia resposta inline
            sendReply(n, replyText)

        } catch (t: Throwable) {
            Log.e(TAG, "Erro processando notificação", t)
        }
    }

    /**
     * Copia a mensagem original e substitui a **primeira** linha "-" vazia
     * APÓS o último horário >= 18h00 por "- Thiago Soares".
     * Se não achar horário, faz o replace na primeira linha "-" da mensagem.
     */
    private fun makeReplyText(original: String): String? {
        val blankHyphen = Regex("(?m)^-\\s*$")
        if (!blankHyphen.containsMatchIn(original)) return null

        val timeRegex = Regex("""\b(1[89]|2[0-3])h([0-5]\d)\b""")
        val lastTime = timeRegex.findAll(original).lastOrNull()

        if (lastTime != null) {
            val idx = lastTime.range.first
            val head = original.substring(0, idx)
            val tail = original.substring(idx)
            val newTail = blankHyphen.replaceFirst(tail, "- Thiago Soares")
            return head + newTail
        }

        // Fallback: primeira linha "-" da mensagem inteira
        return blankHyphen.replaceFirst(original, "- Thiago Soares")
    }

    /**
     * Localiza a ação de "responder" e envia o texto como resposta inline.
     * Usa apenas classes da plataforma (android.app.*) para evitar conflitos.
     */
    private fun sendReply(notification: Notification, replyText: String) {
        try {
            val action = findInlineReplyAction(notification)
            if (action == null) {
                Log.w(TAG, "Nenhuma ação de resposta inline encontrada.")
                return
            }

            val remoteInputs = action.remoteInputs
            if (remoteInputs == null || remoteInputs.isEmpty()) {
                Log.w(TAG, "Ação de resposta sem RemoteInputs.")
                return
            }

            val freeForm = remoteInputs.firstOrNull { it.allowFreeFormInput }
            if (freeForm == null) {
                Log.w(TAG, "Nenhum RemoteInput de texto livre encontrado.")
                return
            }

            val resultBundle = Bundle().apply {
                putCharSequence(freeForm.resultKey, replyText)
            }

            val fillIn = Intent()
            // Usa android.app.RemoteInput para evitar conflito com androidx.core
            RemoteInput.addResultsToIntent(remoteInputs, fillIn, resultBundle)
            action.actionIntent.send(this, 0, fillIn)

            Log.d(TAG, "Resposta inline enviada.")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao enviar resposta inline", e)
        }
    }

    /** Procura a Notification.Action que aceita RemoteInput (botão Responder). */
    private fun findInlineReplyAction(n: Notification): Notification.Action? {
        val actions = n.actions ?: return null
        for (act in actions) {
            val inputs = act.remoteInputs ?: continue
            if (inputs.any { it.allowFreeFormInput }) return act
        }
        return null
    }

    companion object {
        private const val TAG = "WA_AutoResponder"
    }
}
