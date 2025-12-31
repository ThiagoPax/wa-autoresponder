package com.seuapp.whatsautoresponder.util

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput

/**
 * Helper class to send replies through WhatsApp notification actions.
 */
object ReplyHelper {

    /**
     * Sends a reply message using the notification's reply action.
     * Returns true if the reply was sent successfully.
     */
    fun sendReply(
        context: Context,
        actions: Array<Notification.Action>?,
        replyMessage: String
    ): Boolean {
        if (actions.isNullOrEmpty()) return false

        // Find the reply action
        val replyAction = findReplyAction(actions) ?: return false
        
        // Get RemoteInput from the action
        val remoteInputs = replyAction.remoteInputs
        if (remoteInputs.isNullOrEmpty()) return false

        return try {
            // Build the reply intent
            val intent = Intent()
            val bundle = Bundle()

            for (remoteInput in remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyMessage)
            }

            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

            // Send the reply
            replyAction.actionIntent.send(context, 0, intent)
            true
        } catch (e: Exception) {
            Prefs.appendLog(context, "Erro ao enviar resposta: ${e.message}")
            false
        }
    }

    private fun findReplyAction(actions: Array<Notification.Action>): Notification.Action? {
        // First try to find by remoteInputs (direct reply action)
        for (action in actions) {
            if (!action.remoteInputs.isNullOrEmpty()) {
                return action
            }
        }

        // Fallback: find by title containing "responder" or "reply"
        for (action in actions) {
            val title = action.title?.toString()?.lowercase() ?: continue
            if (title.contains("responder") || title.contains("reply") || title.contains("respond")) {
                if (!action.remoteInputs.isNullOrEmpty()) {
                    return action
                }
            }
        }

        return null
    }
}
