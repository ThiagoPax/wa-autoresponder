 (cd "$(git rev-parse --show-toplevel)" && git apply --3way <<'EOF' 
diff --git a/app/src/main/java/com/seuapp/whatsautoresponder/service/WANotificationListener.kt b/app/src/main/java/com/seuapp/whatsautoresponder/service/WANotificationListener.kt
index bb44d146d82f1ce024ab230ce8c3aed4c39ec1b9..e95fc8a3b98198ea34da2b3b22533fa7ac60c5f9 100644
--- a/app/src/main/java/com/seuapp/whatsautoresponder/service/WANotificationListener.kt
+++ b/app/src/main/java/com/seuapp/whatsautoresponder/service/WANotificationListener.kt
@@ -1,114 +1,96 @@
 package com.seuapp.whatsautoresponder.service
 
 import android.app.Notification
 import android.os.Bundle
-import android.os.Handler
-import android.os.Looper
 import android.service.notification.NotificationListenerService
 import android.service.notification.StatusBarNotification
-import com.seuapp.whatsautoresponder.util.Prefs
 import com.seuapp.whatsautoresponder.util.LogBus
+import com.seuapp.whatsautoresponder.util.Prefs
 
 class WANotificationListener : NotificationListenerService() {
 
-    private val mainHandler = Handler(Looper.getMainLooper())
-
     override fun onListenerConnected() {
+        if (!Prefs.isEnabled(this)) return
         Prefs.appendLog(this, "Serviço conectado.")
         LogBus.emit("Serviço conectado.")
     }
 
     override fun onNotificationPosted(sbn: StatusBarNotification) {
-        try {
-            if (!Prefs.isEnabled(this)) {
-                Prefs.appendLog(this, "Notificação recebida, mas app está DESLIGADO.")
-                LogBus.emit("Notificação recebida, mas app está DESLIGADO.")
-                return
-            }
+        if (!Prefs.isEnabled(this)) return
 
+        try {
             val pkg = sbn.packageName ?: return
-            if (pkg != "com.whatsapp") return  // ignoramos outros apps
+            if (pkg != "com.whatsapp") return
 
-            val extras = sbn.notification.extras
+            val extras = sbn.notification?.extras
 
-            val title = extractTitle(extras)      // nome do grupo/contato (seguro)
-            val body  = extractBestText(extras)   // corpo da mensagem (seguro)
+            val title = extractTitle(extras)
+            val body = extractBestText(extras)
 
             if (title.isBlank() && body.isBlank()) return
 
-            // Log básico em PT-BR
             Prefs.appendLog(this, "[WhatsApp] $title: $body")
             LogBus.emit("[WhatsApp] $title: $body")
 
-            // ==== AÇÕES DE RESPOSTA (apenas detecção / sem enviar) ====
-            val actions: Array<Notification.Action> = sbn.notification.actions ?: emptyArray()
-            val hasReply = actions.any { (it.title?.toString() ?: "").contains("responder", ignoreCase = true) }
+            val actions = runCatching { sbn.notification?.actions ?: emptyArray() }
+                .getOrDefault(emptyArray())
+            val hasReply = actions.any {
+                runCatching { (it.title?.toString() ?: "").contains("responder", true) }
+                    .getOrDefault(false)
+            }
             if (hasReply) {
                 Prefs.appendLog(this, "Ação de resposta disponível (apenas log).")
                 LogBus.emit("Ação de resposta disponível (apenas log).")
             }
 
         } catch (t: Throwable) {
-            // Nunca deixa derrubar o app por formato diferente de notificação
             val msg = "ERRO ao ler notificação: ${t::class.java.simpleName}: ${t.message ?: "sem mensagem"}"
             Prefs.appendLog(this, msg)
             LogBus.emit(msg)
         }
     }
 
     override fun onNotificationRemoved(sbn: StatusBarNotification) {
+        if (!Prefs.isEnabled(this)) return
         Prefs.appendLog(this, "Notificação removida.")
         LogBus.emit("Notificação removida.")
     }
 
-    // ================== Utilitários de extração segura ==================
-
-    /** Lê um CharSequence do Bundle e devolve String se houver conteúdo, sem lançar exceção. */
     private fun Bundle?.getCs(key: String): String? =
         try {
             this?.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
-        } catch (_: Throwable) { null }
+        } catch (_: Throwable) {
+            null
+        }
 
-    /** Extrai o título (nome do grupo/contato) de forma resiliente. */
     private fun extractTitle(extras: Bundle?): String {
         extras.getCs(Notification.EXTRA_TITLE)?.let { return it }
         extras.getCs(Notification.EXTRA_TITLE_BIG)?.let { return it }
         return ""
     }
 
-    /**
-     * Extrai o melhor texto disponível da notificação do WhatsApp, lidando com:
-     * - CharSequence[] em EXTRA_TEXT_LINES (listas de mensagens)
-     * - EXTRA_BIG_TEXT
-     * - EXTRA_TEXT
-     * - SubText/summary como fallback
-     */
     private fun extractBestText(extras: Bundle?): String {
-        // 1) lines (lista de mensagens)
         try {
             when (val v = extras?.get(Notification.EXTRA_TEXT_LINES)) {
                 is Array<*> -> {
                     val s = v.filterIsInstance<CharSequence>()
                         .joinToString("\n") { it.toString() }
                     if (s.isNotBlank()) return s
                 }
+
                 is CharSequence -> {
                     val s = v.toString()
                     if (s.isNotBlank()) return s
                 }
             }
-        } catch (_: Throwable) { /* ignorar e tentar próximas fontes */ }
+        } catch (_: Throwable) {
+        }
 
-        // 2) bigText
         extras.getCs(Notification.EXTRA_BIG_TEXT)?.let { if (it.isNotBlank()) return it }
-
-        // 3) texto "normal"
         extras.getCs(Notification.EXTRA_TEXT)?.let { if (it.isNotBlank()) return it }
-
-        // 4) fallbacks comuns
         extras.getCs(Notification.EXTRA_SUB_TEXT)?.let { if (it.isNotBlank()) return it }
         extras.getCs("android.summaryText")?.let { if (it.isNotBlank()) return it }
 
         return ""
     }
 }
 
EOF
)
