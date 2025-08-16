package com.seuapp.whatsautoresponder.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.RemoteInput
import com.seuapp.whatsautoresponder.util.LogHelper
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

class WANotificationListener : NotificationListenerService() {

    companion object {
        private const val PREFS = "auto_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_AT = "last_at"
        private const val KEY_LAST_HASH = "last_hash"

        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        // Nomes dos grupos admitidos (normalizados p/ minúsculo e sem acento)
        private val VALID_GROUPS = listOf(
            "gsta1 - tennis",
            "gsta2 - tennis"
        )

        // Palavras obrigatórias
        private const val MUST1 = "vagas"
        private const val MUST2 = "quem topa"

        private const val NAME = "Thiago Soares"
        private const val THROTTLE_MS = 10 * 60 * 1000L // 10min
    }

    // ===== Modelos de dia e janelas de horário =====

    private enum class DayKind { SEG, TER, QUA, QUI, SEX, SAB, DOM }

    // Pares (keyword, DayKind) — todos normalizados (sem acento e minúsculo)
    private val DAY_KEYWORDS: List<Pair<String, DayKind>> = listOf(
        "segunda" to DayKind.SEG,
        "terca" to DayKind.TER, "terça" to DayKind.TER, // "terça" é capturada após normalize
        "quarta" to DayKind.QUA,
        "quinta" to DayKind.QUI,
        "sexta"  to DayKind.SEX,
        "sabado" to DayKind.SAB, "sábado" to DayKind.SAB,
        "domingo" to DayKind.DOM
    )

    // Janela por dia:
    // - Segunda: 11:00..13:59
    // - Terça..Sexta: 18:00..21:59
    // - Domingo: 14:00..15:59
    private fun isTimeAllowedForDay(day: DayKind, hour: Int, minute: Int): Boolean {
        return when (day) {
            DayKind.SEG -> hour in 11..13 // 11:00..13:59
            DayKind.TER, DayKind.QUA, DayKind.QUI, DayKind.SEX -> hour in 18..21 // 18:00..21:59
            DayKind.DOM -> hour in 14..15 // 14:00..15:59
            DayKind.SAB -> false // sábado ignorado
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        LogHelper.write(this, "CONNECTED", "Notification listener conectado")
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun isEnabled(): Boolean = prefs().getBoolean(KEY_ENABLED, true)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName !in WHATSAPP_PACKAGES) return

            val n: Notification = sbn.notification ?: return
            val extras = n.extras

            val title = (extras?.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString()
            val text = extractMessageText(n).ifBlank {
                (extras?.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString()
            }

            val titleNorm = norm(title)
            val textNorm = norm(text)

            // Grupo permitido?
            val isValidGroup = VALID_GROUPS.any { titleNorm.contains(it) }
            if (!isValidGroup) {
                LogHelper.write(this, "IGNORED", "Grupo não permitido: '$title' | $text")
                return
            }

            // Palavras obrigatórias?
            if (!textNorm.contains(MUST1) || !textNorm.contains(MUST2)) {
                LogHelper.write(this, "IGNORED", "Sem must-words: $text")
                return
            }

            // Detecta dias mencionados
            val mentionedDays = detectDaysInText(textNorm)
            if (mentionedDays.isEmpty()) {
                LogHelper.write(this, "IGNORED", "Texto não menciona dia reconhecido: $text")
                return
            }

            // Extrai todos os horários do texto
            val times = extractTimes(textNorm) // lista de Pair(hour, minute)

            // Valida: para ALGUM dia mencionado existe ALGUM horário dentro da janela daquele dia?
            val hasAllowedSlot = mentionedDays.any { day ->
                times.any { (h, m) -> isTimeAllowedForDay(day, h, m) }
            }
            if (!hasAllowedSlot) {
                LogHelper.write(this, "IGNORED", "Sem horário permitido para os dias citados: $text")
                return
            }

            // Regras de lista
            if (text.contains(NAME, ignoreCase = true)) {
                LogHelper.write(this, "IGNORED", "Já tem $NAME no texto")
                return
            }
            val hasEmptyDash = text.lineSequence().any { it.trim() == "-" }
            if (!hasEmptyDash) {
                LogHelper.write(this, "IGNORED", "Sem '-' vazio disponível")
                return
            }

            // Throttle + dedupe
            val now = System.currentTimeMillis()
            val candidateHash = stableHash("${title.trim()}|${canonicalContent(text)}")
            val lastAt = prefs().getLong(KEY_LAST_AT, 0L)
            val lastHash = prefs().getLong(KEY_LAST_HASH, Long.MIN_VALUE)

            if (now - lastAt < THROTTLE_MS) {
                LogHelper.write(this, "SKIP_THROTTLE", "Aguardando 10min; faltam ${(THROTTLE_MS - (now - lastAt)) / 1000}s")
                return
            }
            if (candidateHash == lastHash) {
                LogHelper.write(this, "SKIP_DUPLICATE", "Conteúdo idêntico ao último respondido")
                return
            }

            // Monta resposta inserindo "- Thiago Soares" no primeiro '-' vazio
            val response = injectNameOnFirstEmptyDash(text, NAME)

            // Salva throttle/dedupe antes de enviar
            prefs().edit()
                .putLong(KEY_LAST_AT, now)
                .putLong(KEY_LAST_HASH, candidateHash)
                .apply()

            if (!isEnabled()) {
                LogHelper.write(this, "WOULD_SEND", "DESLIGADO. Resposta simulada:\n$response")
                return
            }

            // Procura ação de resposta inline
            val action = findReplyAction(n)
            if (action == null) {
                LogHelper.write(this, "NO_ACTION", "Não encontrei ação de resposta inline")
                return
            }

            val ok = sendInlineReply(action, response)
            if (ok) LogHelper.write(this, "SENT", "Mensagem enviada para '$title'")
            else LogHelper.write(this, "SEND_FAILED", "Falha ao enviar inline reply")

        } catch (t: Throwable) {
            LogHelper.write(this, "ERROR", "Exceção: ${t.message}")
        }
    }

    // ========= Helpers =========

    private fun norm(s: String): String {
        val tmp = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return tmp.lowercase(Locale.getDefault())
    }

    private fun canonicalContent(s: String): String =
        s.trim().replace("[ \\t]+".toRegex(), " ")

    private fun stableHash(s: String): Long {
        var h = 1125899906842597L
        for (c in s) h = (h * 131) xor c.code.toLong()
        return abs(h)
    }

    private fun injectNameOnFirstEmptyDash(original: String, name: String): String {
        val lines = original.lines().toMutableList()
        for (i in lines.indices) {
            if (lines[i].trim() == "-") {
                lines[i] = "- $name"
                return lines.joinToString("\n")
            }
        }
        return "$original\n- $name"
    }

    private fun extractMessageText(n: Notification): String {
        val extras = n.extras
        val lines = mutableListOf<String>()
        (extras?.getCharSequence(Notification.EXTRA_TEXT))?.let { lines += it.toString() }
        (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT))?.let { lines += it.toString() }
        return lines.filter { it.isNotBlank() }.joinToString("\n")
    }

    // Retorna os dias mencionados (pode ter mais de um)
    private fun detectDaysInText(textNorm: String): List<DayKind> {
        val found = mutableSetOf<DayKind>()
        for ((kw, day) in DAY_KEYWORDS) {
            // usar kw já normalizada (lista contém variações)
            if (textNorm.contains(kw)) found += day
        }
        return found.toList()
    }

    // Extrai todos horários no formato HH:MM ou HHhMM (aceita 0..23)
    private fun extractTimes(textNorm: String): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        val regex = "(\\b\\d{1,2})[:h](\\d{2})".toRegex()
        for (m in regex.findAll(textNorm)) {
            val h = m.groupValues[1].toIntOrNull() ?: continue
            val min = m.groupValues[2].toIntOrNull() ?: continue
            if (h in 0..23 && min in 0..59) out += h to min
        }
        return out
    }

    // ======= Inline reply =======

    data class ReplyAction(
        val pendingIntent: PendingIntent,
        val remoteInputsKeys: List<String>
    )

    private fun findReplyAction(n: Notification): ReplyAction? {
        val actions = n.actions ?: return null
        for (a in actions) {
            val ris = a.remoteInputs
            if (ris != null && ris.isNotEmpty() && a.actionIntent != null) {
                val keys = ris.mapNotNull { it.resultKey }
                if (keys.isNotEmpty()) {
                    return ReplyAction(a.actionIntent, keys)
                }
            }
        }
        return null
    }

    private fun sendInlineReply(action: ReplyAction, message: String): Boolean {
        return try {
            val intent = Intent()
            val results = android.os.Bundle()

            val riArray = action.remoteInputsKeys.map { key ->
                RemoteInput.Builder(key).build()
            }.toTypedArray()

            for (ri in riArray) {
                results.putCharSequence(ri.resultKey, message)
            }
            RemoteInput.addResultsToIntent(riArray, intent, results)

            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            action.pendingIntent.send(this, 0, intent, null, null, null, flags)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
