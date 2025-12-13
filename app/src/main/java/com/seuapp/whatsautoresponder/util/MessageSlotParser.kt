package com.seuapp.whatsautoresponder.util

object MessageSlotParser {

    /**
     * Insere "- userName" no primeiro slot vazio ("-" ou "•") dentro do bloco que contém targetTime.
     *
     * Blocos são separados por uma linha "OUTRA TURMA" (sozinha na linha).
     * Retorna a mensagem atualizada, ou null se não houver bloco correspondente ou slot livre.
     */
    fun insertNameIntoTimeBlock(message: String, targetTime: String, userName: String): String? {
        if (message.isBlank() || targetTime.isBlank() || userName.isBlank()) return null

        val dividerRegex = Regex("(?m)^\\s*OUTRA TURMA\\s*$")
        val blocks = dividerRegex.split(message)
        if (blocks.isEmpty()) return null

        // Recria usando separador padronizado
        val joiner = "\n\nOUTRA TURMA\n\n"
        val updatedBlocks = blocks.toMutableList()

        val slotRegex = Regex("^\\s*[-•]\\s*$")

        for (i in blocks.indices) {
            val block = blocks[i]
            if (!block.contains(targetTime, ignoreCase = true)) continue

            val lines = block.lines().toMutableList()
            val idx = lines.indexOfFirst { slotRegex.matches(it) }
            if (idx == -1) return null

            lines[idx] = "- ${userName.trim()}"
            updatedBlocks[i] = lines.joinToString("\n")
            return updatedBlocks.joinToString(joiner)
        }

        return null
    }
}
