package com.seuapp.whatsautoresponder.util

import android.content.Context

/**
 * Parses tennis class vacancy messages and generates responses.
 * 
 * Message format:
 * Vagas para SEXTA (02/01) 
 * Unidade 1 - Rua São Benedito, 1813
 * Temos 4 vagas às 09h00 na rápida, quem topa? 
 * -
 * -
 * -
 * -
 * OUTRA TURMA
 * Temos 4 vagas às 14h00 na rápida, quem topa? 
 * -
 * -
 * -
 * -
 */
object MessageParser {

    private const val BLOCK_DELIMITER = "OUTRA TURMA"
    private const val RESPONSE_NAME = "- Thiago Soares"
    
    // Marcadores de vaga vazia (tanto "-" quanto "•")
    private val EMPTY_VACANCY_MARKERS = listOf("-", "•", "*", "·")
    
    // Regex to extract time like "09h00", "14h00", "9h00", etc.
    private val TIME_REGEX = Regex("""(\d{1,2})h(\d{2})""", RegexOption.IGNORE_CASE)
    
    // Regex to extract day of week from message like "SEXTA (02/01)" or "SEGUNDA (05/01)"
    private val DAY_REGEX = Regex("""(SEGUNDA|TERÇA|TERCA|QUARTA|QUINTA|SEXTA|SÁBADO|SABADO|DOMINGO)""", RegexOption.IGNORE_CASE)
    
    // Map Portuguese day names to day keys
    private fun dayNameToKey(dayName: String): String? {
        return when (dayName.uppercase().replace("Ç", "C").replace("Á", "A")) {
            "SEGUNDA" -> "mon"
            "TERCA", "TERÇA" -> "tue"
            "QUARTA" -> "wed"
            "QUINTA" -> "thu"
            "SEXTA" -> "fri"
            "SABADO", "SÁBADO" -> "sat"
            "DOMINGO" -> "sun"
            else -> null
        }
    }

    data class ParseResult(
        val canRespond: Boolean,
        val responseMessage: String?,
        val blockHour: Int?,
        val reason: String
    )

    /**
     * Parses the message and generates a response if applicable.
     * Returns null if the message doesn't match the expected format or no valid block found.
     */
    fun parseAndGenerateResponse(ctx: Context, message: String): ParseResult {
        // Check if message looks like a vacancy message
        if (!isVacancyMessage(message)) {
            return ParseResult(false, null, null, "Não é mensagem de vagas")
        }
        
        // Extract the day of the class from the message (e.g., "SEXTA" from "Vagas para SEXTA (02/01)")
        val classDayKey = extractDayKey(message)
        if (classDayKey == null) {
            Prefs.appendLog(ctx, "Dia da aula não encontrado na mensagem")
            return ParseResult(false, null, null, "Dia da aula não identificado")
        }
        
        Prefs.appendLog(ctx, "Dia da aula identificado: $classDayKey")

        // Split into blocks
        val blocks = splitIntoBlocks(message)
        if (blocks.isEmpty()) {
            return ParseResult(false, null, null, "Nenhum bloco encontrado")
        }
        
        Prefs.appendLog(ctx, "Blocos encontrados: ${blocks.size}")

        // Find the first valid block (within schedule and has vacancy)
        for ((index, block) in blocks.withIndex()) {
            val hour = extractHour(block)
            if (hour == null) {
                Prefs.appendLog(ctx, "Bloco $index: sem horário detectado")
                continue
            }

            // Check if this hour is within the configured schedule for the CLASS DAY (not current day)
            if (!Prefs.isHourWithinScheduleForDay(ctx, classDayKey, hour)) {
                Prefs.appendLog(ctx, "Bloco $index: ${hour}h fora do horário configurado para $classDayKey")
                continue
            }

            // Check if block has available vacancy
            if (!hasVacancy(block)) {
                Prefs.appendLog(ctx, "Bloco $index: ${hour}h sem vagas disponíveis")
                continue
            }

            // Found valid block - generate response
            Prefs.appendLog(ctx, "Bloco $index: ${hour}h VÁLIDO - gerando resposta")
            val responseMessage = generateResponse(message, blocks, index)
            return ParseResult(true, responseMessage, hour, "Bloco válido encontrado: ${hour}h")
        }

        return ParseResult(false, null, null, "Nenhum bloco válido no horário configurado ou sem vagas")
    }
    
    private fun extractDayKey(message: String): String? {
        val match = DAY_REGEX.find(message) ?: return null
        return dayNameToKey(match.value)
    }

    private fun isVacancyMessage(message: String): Boolean {
        val lower = message.lowercase()
        return (lower.contains("vagas") || lower.contains("vaga")) &&
               TIME_REGEX.containsMatchIn(message)
    }

    private fun splitIntoBlocks(message: String): List<String> {
        val parts = message.split(BLOCK_DELIMITER, ignoreCase = true)
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun extractHour(block: String): Int? {
        val match = TIME_REGEX.find(block) ?: return null
        return try {
            match.groupValues[1].toInt()
        } catch (e: Exception) {
            null
        }
    }

    private fun hasVacancy(block: String): Boolean {
        val lines = block.split("\n").map { it.trim() }
        // Verifica se existe alguma linha que seja APENAS um marcador de vaga vazia
        return lines.any { line -> EMPTY_VACANCY_MARKERS.any { marker -> line == marker } }
    }

    private fun generateResponse(originalMessage: String, blocks: List<String>, targetBlockIndex: Int): String {
        val result = StringBuilder()
        
        for ((index, block) in blocks.withIndex()) {
            if (index > 0) {
                result.append("\n$BLOCK_DELIMITER\n")
            }
            
            if (index == targetBlockIndex) {
                // Replace first "-" with response
                result.append(replaceFirstVacancy(block))
            } else {
                result.append(block)
            }
        }
        
        return result.toString()
    }

    private fun replaceFirstVacancy(block: String): String {
        val lines = block.split("\n").toMutableList()
        var replaced = false
        
        for (i in lines.indices) {
            val trimmedLine = lines[i].trim()
            // Substitui apenas linhas que sejam EXATAMENTE um marcador de vaga vazia
            // Linhas como "• João Silva" ou "- Maria" são ignoradas
            if (!replaced && EMPTY_VACANCY_MARKERS.any { marker -> trimmedLine == marker }) {
                lines[i] = RESPONSE_NAME
                replaced = true
                break
            }
        }
        
        return lines.joinToString("\n")
    }
}
