package com.seuapp.whatsautoresponder.service

import android.app.Notification
import android.app.PendingIntent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.seuapp.whatsautoresponder.ui.LogBus
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import android.os.Handler
import android.os.Looper
import kotlin.math.max

class WANotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "WA-Listener"

        // Grupos que o app monitora
        private val ALLOWED_GROUPS = setOf(
            "GSTA1 - Tennis",
            "GSTA2 - Tennis"
        )

        // Palavras obrigatórias (procura versão normalizada sem acento)
        private val MUST_WORDS = listOf("vaga", "vagas", "quem topa")

        // Nome a inserir
        private const val MEU_NOME = "Thiago Soares"

        // Anti-spam (10 minutos)
        private const val THROTTLE_MS = 10 * 60 * 1000L
    }

    private val lastReplyByThread: MutableMap<String, Long> = ConcurrentHashMap()
    private val lastMsgHashByThread: MutableMap<String, Int> = ConcurrentHashMap()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        super.onListenerConnected()
        log("[${now()}] CONECTADO: listener de notificações ativo")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val n = sbn.notification ?: return
            // Apenas WhatsApp (package pode variar entre "com.whatsapp" e "com.whatsapp.w4b")
            if (sbn.packageName?.startsWith("com.whatsapp") != true) return

            val extras = n.extras ?: return
            val title = (extras.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString()
            val textCS = extractText(extras) ?: return
            val text = textCS.toString()

            // Filtrar apenas grupos permitidos (título é o nome do chat/grupo)
            if (title.isBlank() || title !in ALLOWED_GROUPS) {
                log("[${now()}] IGNORADO: Grupo não permitido: '$title'")
                return
            }

            // Normalização da mensagem para buscas
            val normalized = normalize(text)

            // Palavras obrigatórias
            val hasMust = MUST_WORDS.all { normalized.contains(it) }
            if (!hasMust) {
                log("[${now()}] IGNORADO: Sem palavras obrigatórias | '$title'")
                return
            }

            // Detectar dia (segunda..domingo) a partir do texto
            val dia = detectDia(normalized)
            if (dia == null) {
                log("[${now()}] IGNORADO: Não foi possível detectar o dia | '$title'")
                return
            }

            // Selecionar o bloco da mensagem que tenha horário permitido
            val result = pickValidBlockAndBuildReply(original = text, dia = dia)
            if (result == null) {
                log("[${now()}] IGNORADO: Nenhum bloco com horário permitido | '$title'")
                return
            }

            val (replyText, delayMs, threadKey) = result

            // Anti-spam por conversa (10 min) e deduplicação por conteúdo
            val now = System.currentTimeMillis()
            val last = lastReplyByThread[threadKey] ?: 0L
            if (now - last < THROTTLE_MS) {
                val rem = (THROTTLE_MS - (now - last)) / 1000
                log("[${time()}] IGNORADO: Aguardando janela de 10min (faltam ${rem}s) | '$title'")
                return
            }
            val hash = replyText.hashCode()
            if (lastMsgHashByThread[threadKey] == hash) {
                log("[${time()}] IGNORADO: Mesma resposta já enviada recentemente | '$title'")
                return
            }

            // Encontrar ação de resposta
            val replyAction = findReplyAction(n)
            if (replyAction == null) {
                log("[${time()}] ERRO: Não encontrei ação de resposta inline | '$title'")
                return
            }

            // Agendar envio com atraso calculado
            log("[${time()}] PREPARADO: Envia em ${delayMs / 1000}s para '$title'")
            mainHandler.postDelayed({
                try {
                    sendInlineReply(replyAction, replyText)
                    lastReplyByThread[threadKey] = System.currentTimeMillis()
                    lastMsgHashByThread[threadKey] = hash
                    log("[${now()}] ENVIADA: Mensagem para '$title'")
                } catch (e: Exception) {
                    log("[${now()}] ERRO AO ENVIAR: ${e.message}")
                    Log.e(TAG, "Falha ao enviar reply", e)
                }
            }, delayMs)

        } catch (e: Exception) {
            log("[${now()}] EXCEÇÃO: ${e.message}")
            Log.e(TAG, "onNotificationPosted error", e)
        }
    }

    // ---------- Núcleo da lógica corrigida ----------

    /**
     * Percorre o texto e identifica blocos do tipo:
     * "Temos X vaga(s) às HHhMM ...\n- ...\n- ...\n"
     * Escolhe apenas UM bloco cujo horário esteja permitido para o 'dia'.
     * Retorna o texto da resposta com "- Thiago Soares" inserido no primeiro '-' vazio
     * DESSE bloco e o atraso (7/14/21/28s) conforme a quantidade de vagas vazias.
     */
    private fun pickValidBlockAndBuildReply(original: String, dia: DiaDaSemana): Triple<String, Long, String>? {
        val lines = original.lines().toMutableList()
        val replyLines = lines.toMutableList()

        // Vamos varrer linhas; quando achar um horário, avalia o bloco logo abaixo
        val timeRegex = Regex("""\b(\d{1,2})(?:h|:)?(\d{2})\b""", RegexOption.IGNORE_CASE)

        var idx = 0
        while (idx < lines.size) {
            val line = normalize(lines[idx])
            val tm = timeRegex.find(line)
            if (tm != null) {
                val h = tm.groupValues[1].toInt()
                val mm = tm.groupValues[2].toInt()

                if (isHourAllowed(dia, h, mm)) {
                    // Coletar bloco de participantes (linhas seguintes até encontrar marcador de nova turma/novo horário/linha vazia dupla)
                    var j = idx + 1
                    var firstDashLineIndex: Int? = null
                    var emptySlots = 0
                    var containsMyName = false

                    while (j < lines.size) {
                        val l = lines[j].trim()

                        // Parada: próximo horário ou cabeçalho "Vagas" / "OUTRA TURMA"
                        val isNewHeader = normalize(l).contains("vagas") || normalize(l).contains("outra turma")
                        val isNewTime = timeRegex.containsMatchIn(l)
                        if (isNewHeader || isNewTime) break

                        // Participante
                        if (l.equals("-") || l.equals("–") || l.equals("—")) {
                            if (firstDashLineIndex == null) firstDashLineIndex = j
                            emptySlots++
                        } else if (normalize(l).contains(normalize(MEU_NOME))) {
                            containsMyName = true
                        }

                        // Se linha totalmente em branco e já vimos algo, podemos encerrar bloco
                        if (l.isBlank() && (firstDashLineIndex != null || containsMyName)) break

                        j++
                    }

                    if (containsMyName) {
                        // Já tem seu nome nesse bloco; ignore e continue procurando outro bloco permitido
                        idx = j
                        continue
                    }

                    // Só responde se houver pelo menos 1 '-' vazio no bloco
                    if (firstDashLineIndex != null && emptySlots > 0) {
                        // Inserir "- Thiago Soares" no primeiro '-'
                        replyLines[firstDashLineIndex] = "- $MEU_NOME"

                        // Atraso conforme número de vagas vazias (cap em 4)
                        val capped = max(1, emptySlots.coerceAtMost(4))
                        val delay = capped * 7_000L

                        val replyText = replyLines.joinToString("\n")
                        val threadKey = dia.name // chave simples por dia; opcionalmente use o título do grupo + data
                        return Triple(replyText, delay, threadKey)
                    }
                }
            }
            idx++
        }
        return null
    }

    // ---------- Regras de horário por dia ----------
    enum class DiaDaSemana { SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA, SABADO, DOMINGO }

    private fun detectDia(normalized: String): DiaDaSemana? {
        return when {
            normalized.contains("segunda") -> DiaDaSemana.SEGUNDA
            normalized.contains("terça") || normalized.contains("terca") -> DiaDaSemana.TERCA
            normalized.contains("quarta") -> DiaDaSemana.QUARTA
            normalized.contains("quinta") -> DiaDaSemana.QUINTA
            normalized.contains("sexta") -> DiaDaSemana.SEXTA
            normalized.contains("sabado") || normalized.contains("sábado") -> DiaDaSemana.SABADO
            normalized.contains("domingo") -> DiaDaSemana.DOMINGO
            else -> null
        }
    }

    /**
     * Janelas pedidas por você:
     * - Segunda: 11:00 até 13:59
     * - Terça a Sexta: 18:00 até 21:59
     * - Domingo: 14:00 até 15:59
     * - (Sábado não permitido)
     */
    private fun isHourAllowed(dia: DiaDaSemana, h: Int, m: Int): Boolean {
        val minutes = h * 60 + m
        fun inRange(startH: Int, startM: Int, endH: Int, endM: Int): Boolean {
            val s = startH * 60 + startM
            val e = endH * 60 + endM
            return minutes in s..e
        }
        return when (dia) {
            DiaDaSemana.SEGUNDA -> inRange(11, 0, 13, 59)
            DiaDaSemana.TERCA, DiaDaSemana.QUARTA, DiaDaSemana.QUINTA, DiaDaSemana.SEXTA ->
                inRange(18, 0, 21, 59)
            DiaDaSemana.DOMINGO -> inRange(14, 0, 15, 59)
            DiaDaSemana.SABADO -> false
        }
    }

    // ---------- Envio de resposta inline ----------
    data class ReplyAction(
        val actionIntent: PendingIntent,
        val remoteInputs: Array<RemoteInput>
    )

    private fun findReplyAction(notification: Notification): ReplyAction? {
        val actions = NotificationCompat.getActions(notification) ?: return null
        for (a in actions) {
            val hasRemoteInput = a?.remoteInputs?.isNotEmpty() == true
            val isReply = a?.title?.toString()?.lowercase(Locale.ROOT)?.contains("responder") == true ||
                    a?.semanticAction == NotificationCompat.Action.SEMANTIC_ACTION_REPLY
            if (hasRemoteInput && isReply) {
                val ri = a.remoteInputs!!.map { it }.toTypedArray()
                return ReplyAction(a.actionIntent, ri)
            }
        }
        return null
    }

    private fun sendInlineReply(replyAction: ReplyAction, message: String) {
        val input = Bundle()
        for (ri in replyAction.remoteInputs) {
            // Preenche o mesmo texto em todos os RemoteInputs
            input.putCharSequence(ri.resultKey, message)
        }
        val intent = android.content.Intent()
        RemoteInput.addResultsToIntent(replyAction.remoteInputs, intent, input)
        replyAction.actionIntent.send(this, 0, intent)
    }

    // ---------- Utilidades ----------
    private fun extractText(extras: Bundle): CharSequence? {
        // Alguns aparelhos entregam as mensagens como "android.text"
        // ou em MessagingStyle
        val lines = mutableListOf<CharSequence>()

        fun addIfNonEmpty(cs: CharSequence?) {
            if (!cs.isNullOrBlank()) lines += cs
        }

        addIfNonEmpty(extras.getCharSequence(Notification.EXTRA_TEXT))
        addIfNonEmpty(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        (extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) ?: emptyArray()).forEach { addIfNonEmpty(it) }

        if (lines.isNotEmpty()) {
            return TextUtils.concat(*lines.toTypedArray())
        }

        val ms: Any? = extras.get(Notification.EXTRA_MESSAGES)
        if (ms is Array<*>) {
            val sb = StringBuilder()
            for (m in ms) {
                if (m is Bundle) sb.append(m.getCharSequence("text") ?: "")
            }
            if (sb.isNotEmpty()) return sb.toString()
        }
        return null
    }

    private fun normalize(s: String): String {
        val tmp = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return tmp.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    }

    private fun now(): String = java.time.LocalDateTime.now().toString().replace('T', ' ')
    private fun time(): String = java.time.LocalTime.now().toString().substring(0,8)

    private fun log(msg: String) {
        Log.i(TAG, msg)
        LogBus.post(msg) // envia para a UI (MainActivity) exibir no log
    }
}
