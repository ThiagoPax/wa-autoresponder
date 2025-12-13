package com.seuapp.whatsautoresponder.util

object MessageSlotParser {

    private fun normTime(raw: String): String? {
        val m = Regex("""(\d{1,2})\s*(?:h|:)\s*(\d{2})""", RegexOption.IGNORE_CASE).find(raw) ?: return null
        val hh = m.groupValues[1].padStart(2, '0')
        val mm = m.groupValues[2]
        return "$hh:$mm"
    }

    /**
     * Insere "- userName" no primeiro slot "-" vazio do bloco que tiver a linha "Temos ... às {targetTime}".
     * targetTime aceita "17:00" ou "17h00" (normaliza para HH:MM).
     */
    fun insertNameIntoTimeBlock(message: String, targetTime: String, userName: String): String? {
        if (message.isBlank() || targetTime.isBlank() || userName.isBlank()) return null

        val wanted = normTime(targetTime) ?: return null

        // Mantém o "OUTRA TURMA" como separador visual ao reconstruir
        val delimiter = "\n\nOUTRA TURMA\n\n"
        val blocks = message.replace("\r\n", "\n").split(Regex("""(?m)^\s*OUTRA TURMA\s*$"""))
        val updated = blocks.toMutableList()

        for (i in blocks.indices) {
            val block = blocks[i]
            val lines = block.lines().toMutableList()

            // Procura a linha do "Temos ... às HHhMM"
            val idxTemos = lines.indexOfFirst { it.contains("Temos", ignoreCase = true) }
            if (idxTemos == -1) continue

            val timeInTemosLine = normTime(lines[idxTemos]) ?: continue
            if (timeInTemosLine != wanted) continue

            // Preenche o primeiro "-" vazio
            val dashIndex = lines.indexOfFirst { it.trim() == "-" }
            if (dashIndex == -1) return null

            lines[dashIndex] = "- $userName"
            updated[i] = lines.joinToString("\n")

            return updated.joinToString(delimiter)
        }

        return null
    }
}
