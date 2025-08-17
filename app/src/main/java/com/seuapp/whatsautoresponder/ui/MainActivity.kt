package com.seuapp.whatsautoresponder.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.seuapp.whatsautoresponder.R
import com.seuapp.whatsautoresponder.util.LogBus
import com.seuapp.whatsautoresponder.util.Prefs

class MainActivity : AppCompatActivity() {

    private fun idOf(name: String): Int =
        resources.getIdentifier(name, "id", packageName)

    private lateinit var tvLog: TextView
    private var swEnabled: Switch? = null
    private var btnOpenSettings: Button? = null
    private var btnRefresh: Button? = null
    private var btnClear: Button? = null

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val line = intent.getStringExtra(LogBus.EXTRA_LINE) ?: return
            appendLine(line)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            setContentView(R.layout.activity_main)

            // tenta achar cada view por nome; se faltar, não trava
            tvLog = findViewById(idOf("tvLog")) ?: TextView(this).also { setContentView(it) }

            swEnabled = findViewById(idOf("swEnabled"))
            btnOpenSettings = findViewById(idOf("btnOpenSettings"))
            btnRefresh = findViewById(idOf("btnRefresh"))
            btnClear = findViewById(idOf("btnClear"))

            // estado inicial
            swEnabled?.isChecked = Prefs.isEnabled(this)
            tvLog.text = Prefs.readLog(this)

            // listeners seguros
            swEnabled?.setOnCheckedChangeListener { _, checked ->
                Prefs.setEnabled(this, checked)
                appendLine(if (checked) "Auto-responder LIGADO pela interface"
                           else "Auto-responder DESLIGADO pela interface")
            }

            btnOpenSettings?.setOnClickListener {
                runCatching {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }.onFailure {
                    Toast.makeText(this, "Não foi possível abrir as configurações.", Toast.LENGTH_LONG).show()
                }
            }

            btnRefresh?.setOnClickListener {
                tvLog.text = Prefs.readLog(this)
            }

            btnClear?.setOnClickListener {
                Prefs.clearLog(this)
                tvLog.text = ""
                Toast.makeText(this, "Log limpo.", Toast.LENGTH_SHORT).show()
            }

            // Mostra o último crash (se houver) para diagnóstico
            readCrashFile().takeIf { it.isNotBlank() }?.let {
                appendLine("---- ÚLTIMO CRASH ----")
                appendLine(it)
                appendLine("----------------------")
                runCatching { deleteFile("last_crash.txt") }
            }

        }.onFailure { e ->
            // Se o layout/IDs não baterem, mostra fallback simples (não trava)
            val tv = TextView(this).apply {
                text = "Falha ao carregar a interface: ${e.message}\n" +
                       "Use o botão de permissões de Notificação no próximo build."
                setPadding(24, 24, 24, 24)
            }
            setContentView(tv)
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(logReceiver, IntentFilter(LogBus.ACTION_LOG_UPDATED))
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(logReceiver) }
    }

    private fun appendLine(line: String) {
        tvLog.append(if (tvLog.text.isNullOrEmpty()) line else "\n$line")
    }

    private fun readCrashFile(): String = runCatching {
        openFileInput("last_crash.txt").bufferedReader().use { it.readText() }
    }.getOrElse { "" }
}
