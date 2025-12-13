 package com.seuapp.whatsautoresponder.ui
 
 import android.content.BroadcastReceiver
 import android.content.Context
 import android.content.Intent
 import android.content.IntentFilter
 import android.os.Bundle
 import android.provider.Settings
+import android.view.LayoutInflater
 import android.widget.Button
+import android.widget.EditText
+import android.widget.LinearLayout
 import android.widget.Switch
 import android.widget.TextView
 import android.widget.Toast
 import androidx.appcompat.app.AppCompatActivity
+import androidx.core.widget.doAfterTextChanged
 import com.seuapp.whatsautoresponder.R
 import com.seuapp.whatsautoresponder.util.LogBus
 import com.seuapp.whatsautoresponder.util.Prefs
 
 class MainActivity : AppCompatActivity() {
 
-    private fun idOf(name: String): Int =
-        resources.getIdentifier(name, "id", packageName)
+    private data class DayConfig(val key: String, val label: String)
+
+    private val dayConfigs = listOf(
+        DayConfig("mon", "Segunda-feira"),
+        DayConfig("tue", "Terça-feira"),
+        DayConfig("wed", "Quarta-feira"),
+        DayConfig("thu", "Quinta-feira"),
+        DayConfig("fri", "Sexta-feira"),
+        DayConfig("sat", "Sábado"),
+        DayConfig("sun", "Domingo"),
+    )
 
     private lateinit var tvLog: TextView
-    private var swEnabled: Switch? = null
-    private var btnOpenSettings: Button? = null
-    private var btnRefresh: Button? = null
-    private var btnClear: Button? = null
+    private lateinit var swEnabled: Switch
+    private lateinit var btnOpenSettings: Button
+    private lateinit var btnClear: Button
+    private lateinit var scheduleContainer: LinearLayout
 
     private val logReceiver = object : BroadcastReceiver() {
         override fun onReceive(context: Context, intent: Intent) {
             val line = intent.getStringExtra(LogBus.EXTRA_LINE) ?: return
             appendLine(line)
         }
     }
 
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
+        setContentView(R.layout.activity_main)
 
-        runCatching {
-            setContentView(R.layout.activity_main)
+        tvLog = findViewById(R.id.tvLog)
+        swEnabled = findViewById(R.id.swEnabled)
+        btnOpenSettings = findViewById(R.id.btnOpenSettings)
+        btnClear = findViewById(R.id.btnClear)
+        scheduleContainer = findViewById(R.id.scheduleContainer)
 
-            // tenta achar cada view por nome; se faltar, não trava
-            tvLog = findViewById(idOf("tvLog")) ?: TextView(this).also { setContentView(it) }
+        setupSwitch()
+        setupButtons()
+        setupSchedule()
 
-            swEnabled = findViewById(idOf("swEnabled"))
-            btnOpenSettings = findViewById(idOf("btnOpenSettings"))
-            btnRefresh = findViewById(idOf("btnRefresh"))
-            btnClear = findViewById(idOf("btnClear"))
+        tvLog.text = Prefs.readLog(this)
+    }
 
-            // estado inicial
-            swEnabled?.isChecked = Prefs.isEnabled(this)
-            tvLog.text = Prefs.readLog(this)
+    override fun onResume() {
+        super.onResume()
+        registerReceiver(logReceiver, IntentFilter(LogBus.ACTION_LOG_UPDATED))
+    }
 
-            // listeners seguros
-            swEnabled?.setOnCheckedChangeListener { _, checked ->
-                Prefs.setEnabled(this, checked)
-                appendLine(if (checked) "Auto-responder LIGADO pela interface"
-                           else "Auto-responder DESLIGADO pela interface")
-            }
+    override fun onPause() {
+        super.onPause()
+        runCatching { unregisterReceiver(logReceiver) }
+    }
 
-            btnOpenSettings?.setOnClickListener {
-                runCatching {
-                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
-                }.onFailure {
-                    Toast.makeText(this, "Não foi possível abrir as configurações.", Toast.LENGTH_LONG).show()
-                }
+    private fun setupSwitch() {
+        swEnabled.isChecked = Prefs.isEnabled(this)
+        swEnabled.setOnCheckedChangeListener { _, checked ->
+            Prefs.setEnabled(this, checked)
+            val line = if (checked) {
+                "App habilitado manualmente."
+            } else {
+                "App desabilitado manualmente."
             }
+            Prefs.appendLog(this, line)
+            LogBus.emit(line)
+            tvLog.text = Prefs.readLog(this)
+        }
+    }
 
-            btnRefresh?.setOnClickListener {
-                tvLog.text = Prefs.readLog(this)
+    private fun setupButtons() {
+        btnOpenSettings.setOnClickListener {
+            runCatching {
+                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
+            }.onFailure {
+                Toast.makeText(this, "Não foi possível abrir as configurações.", Toast.LENGTH_LONG).show()
             }
+        }
 
-            btnClear?.setOnClickListener {
-                Prefs.clearLog(this)
-                tvLog.text = ""
-                Toast.makeText(this, "Log limpo.", Toast.LENGTH_SHORT).show()
-            }
+        btnClear.setOnClickListener {
+            Prefs.clearLog(this)
+            tvLog.text = ""
+            Toast.makeText(this, "Log limpo.", Toast.LENGTH_SHORT).show()
+        }
+    }
+
+    private fun setupSchedule() {
+        val inflater = LayoutInflater.from(this)
+        scheduleContainer.removeAllViews()
+
+        dayConfigs.forEach { day ->
+            val row = inflater.inflate(R.layout.item_day_schedule, scheduleContainer, false)
+            val tvDayName = row.findViewById<TextView>(R.id.tvDayName)
+            val swDayEnabled = row.findViewById<Switch>(R.id.swDayEnabled)
+            val etStart = row.findViewById<EditText>(R.id.etStart)
+            val etEnd = row.findViewById<EditText>(R.id.etEnd)
+
+            tvDayName.text = day.label
+            swDayEnabled.isChecked = Prefs.isDayEnabled(this, day.key)
+            etStart.setText(Prefs.getDayStart(this, day.key))
+            etEnd.setText(Prefs.getDayEnd(this, day.key))
 
-            // Mostra o último crash (se houver) para diagnóstico
-            readCrashFile().takeIf { it.isNotBlank() }?.let {
-                appendLine("---- ÚLTIMO CRASH ----")
-                appendLine(it)
-                appendLine("----------------------")
-                runCatching { deleteFile("last_crash.txt") }
+            swDayEnabled.setOnCheckedChangeListener { _, checked ->
+                Prefs.setDayEnabled(this, day.key, checked)
             }
 
-        }.onFailure { e ->
-            // Se o layout/IDs não baterem, mostra fallback simples (não trava)
-            val tv = TextView(this).apply {
-                text = "Falha ao carregar a interface: ${e.message}\n" +
-                       "Use o botão de permissões de Notificação no próximo build."
-                setPadding(24, 24, 24, 24)
+            etStart.doAfterTextChanged {
+                Prefs.setDayStart(this, day.key, it?.toString()?.trim().orEmpty())
             }
-            setContentView(tv)
-        }
-    }
 
-    override fun onResume() {
-        super.onResume()
-        registerReceiver(logReceiver, IntentFilter(LogBus.ACTION_LOG_UPDATED))
-    }
+            etEnd.doAfterTextChanged {
+                Prefs.setDayEnd(this, day.key, it?.toString()?.trim().orEmpty())
+            }
 
-    override fun onPause() {
-        super.onPause()
-        runCatching { unregisterReceiver(logReceiver) }
+            scheduleContainer.addView(row)
+        }
     }
 
     private fun appendLine(line: String) {
-        tvLog.append(if (tvLog.text.isNullOrEmpty()) line else "\n$line")
+        val current = tvLog.text?.toString().orEmpty()
+        tvLog.text = if (current.isBlank()) line else "$current\n$line"
     }
-
-    private fun readCrashFile(): String = runCatching {
-        openFileInput("last_crash.txt").bufferedReader().use { it.readText() }
-    }.getOrElse { "" }
 }
