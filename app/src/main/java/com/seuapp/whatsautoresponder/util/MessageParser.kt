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
    private const val EMPTY_VACANCY = "-"  // Linha vazia (só o traço)
    private const val RESPONSE_NAME = "- Thiago Soares"
    
    // Regex to extract time like "09h00", "14h00", "9h00", etc.
    private val TIME_REGEX = Regex("""(\d{1,2})h(\d{2})""", RegexOption.IGNORE_CASE)

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

        // Split into blocks
        val blocks = splitIntoBlocks(message)
        if (blocks.isEmpty()) {
            return ParseResult(false, null, null, "Nenhum bloco encontrado")
        }

        // Find the first valid block (within schedule and has vacancy)
        for ((index, block) in blocks.withIndex()) {
            val hour = extractHour(block)
            if (hour == null) continue

            // Check if this hour is within the configured schedule
            if (!Prefs.isHourWithinSchedule(ctx, hour)) continue

            // Check if block has available vacancy
            if (!hasVacancy(block)) continue

            // Found valid block - generate response
            val responseMessage = generateResponse(message, blocks, index)
            return ParseResult(true, responseMessage, hour, "Bloco válido encontrado: ${hour}h")
        }

        return ParseResult(false, null, null, "Nenhum bloco válido no horário configurado ou sem vagas")
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
        // Verifica se existe alguma linha que seja APENAS "-" (vaga vazia)
        return lines.any { it == EMPTY_VACANCY }
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
            // Substitui apenas linhas que sejam EXATAMENTE "-" (vaga vazia)
            // Linhas como "- João Silva" são ignoradas
            if (!replaced && lines[i].trim() == EMPTY_VACANCY) {
                lines[i] = RESPONSE_NAME
                replaced = true
                break
            }
        }
        
        return lines.joinToString("\n")
    }
}
