package com.seuapp.whatsautoresponder.service

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class WANotificationListener : NotificationListenerService() {

    private val TAG = "WA_Autoresponder"

    // Nomes de grupos válidos (case-insensitive, espaços nas pontas ignorados)
    private val targetGroups = setOf(
        "gsta1 - tennis",
        "gsta2 - tennis"
    )

    // Palavras-chave obrigatórias
    private val requiredKeywords = listOf("vagas", "quem topa")

    // Dias aceitos (com e sem acento para robustez)
    private val days = listOf("segunda", "terça", "terca", "quarta", "quinta", "sexta")

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener conectado")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // Só WhatsApp
        if (sbn.packageName != "com.whatsapp") return

        val extras = sbn.notification.extras ?: return

        // Para grupos, normalmente o nome do grupo vem em conversationTitle.
        // Em outras variações, pode vir em android.title.
        val conversationTitle = (extras.getString("android.conversationTitle")
            ?: extras.getString("android.title")
            ?: "").trim()

        if (!isFromTargetGroup(conversationTitle)) return

        val messageText = extractMessageText(extras)?.trim() ?: return
        if (messageText.isEmpty()) return

        // Regras de decisão
        if (!hasRequiredKeywords(messageText)) return
        if (!hasValidDay(messageText)) return
        if (!hasEveningTime(messageText)) return
        if (alreadyHasThiago(messageText)) return
        if (!hasEmptyDashLine(messageText)) return

        val replyText = buildReply(messageText) ?: return

        // Enviar resposta inline usando a Action de resposta da notificação
        sendInlineReply(sbn, replyText)
    }

    private fun isFromTargetGroup(title: String): Boolean {
        val norm = title.trim().lowercase()
        return norm in targetGroups
    }

    private fun extractMessageText(extras: Bundle): String? {
        // Tenta bigText primeiro (mensagens maiores), depois text
        val big = extras.getCharSequence("android.bigText")?.toString()
        if (!big.isNullOrBlank()) return big

        val text = extras.getCharSequence("android.text")?.toString()
        if (!text.isNullOrBlank()) return text

        // Alguns estilos empilham linhas
        val lines = extras.getCharSequenceArray("android.textLines")
            ?.joinToString("\n") { it.toString() }
        if (!lines.isNullOrBlank()) return lines

        return null
    }

    private fun hasRequiredKeywords(text: String): Boolean {
        val lower = text.lowercase()
        return requiredKeywords.all { lower.contains(it) }
    }

    private fun hasValidDay(text: String): Boolean {
        val lower = text.lowercase()
        return days.any { lower.contains(it) }
    }

    private fun alreadyHasThiago(text: String): Boolean {
        return text.contains("thiago soares", ignoreCase = true)
    }

    private fun hasEmptyDashLine(text: String): Boolean {
        // Procura uma linha que seja apenas "-" (com ou sem espaços)
        return text.lines().any { it.trim() == "-" }
    }

    private fun hasEveningTime(text: String): Boolean {
        // Aceita "18h00", "18:00", "19h30", "22:15" etc.
        val lower = text.lowercase()
        val regex = Regex("""\b([01]?\d|2[0-3])\s*[:hH]\s*([0-5]\d)\b""")
        val matches = regex.findAll(lower).toList()
