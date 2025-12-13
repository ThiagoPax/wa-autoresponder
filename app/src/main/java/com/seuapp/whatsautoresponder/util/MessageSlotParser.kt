package com.seuapp.whatsautoresponder.util

object MessageSlotParser {
    /**
     * Inserts [userName] into the first available "-" line inside the block that contains [targetTime].
     * Blocks are split by the literal delimiter "OUTRA TURMA" and only the matching block is edited.
     * Returns the updated message or null if no matching block or free slot is found.
     */
    fun insertNameIntoTimeBlock(message: String, targetTime: String, userName: String): String? {
        if (message.isBlank() || targetTime.isBlank() || userName.isBlank()) return null

        val delimiter = "OUTRA TURMA"
        val blocks = message.split(delimiter)
        val updatedBlocks = blocks.toMutableList()

        for (i in blocks.indices) {
            val block = blocks[i]
            if (!block.contains(targetTime, ignoreCase = true)) continue

            val lines = block.lines()
            val newLines = lines.toMutableList()
            val dashIndex = newLines.indexOfFirst { it.trim() == "-" }
            if (dashIndex == -1) return null

            newLines[dashIndex] = userName
            updatedBlocks[i] = newLines.joinToString("\n")
            return updatedBlocks.joinToString(delimiter)
        }

        return null
    }
}
