package com.seuapp.whatsautoresponder

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.seuapp.whatsautoresponder.service.WANotificationListener

class MainActivity : ComponentActivity() {

    private lateinit var btnPermissao: MaterialButton
    private lateinit var btnToggle: MaterialButton
    private lateinit var btnAtualizar: MaterialButton
    private lateinit var btnLimpar: MaterialButton
    private lateinit var txtLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        btnPermissao = findViewById(R.id.btnPermissao)
        btnToggle = findViewById(R.id.btnToggle)
        btnAtualizar = findViewById(R.id.btnAtualizarLog)
        btnLimpar = findViewById(R.id.btnLimparLog)
        txtLog = findViewById(R.id.txtLog)

        btnPermissao.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Estado inicial do toggle
        refreshToggleUI(WANotificationListener.isEnabled(this))

        btnToggle.setOnClickListener {
            val newState = !WANotificationListener.isEnabled(this)
            WANotificationListener.setEnabled(this, newState)
            appendUiLog(
                if (newState) "[TOGGLE] Auto-responder LIGADO pela interface"
                else "[TOGGLE] Auto-responder DESLIGADO pela interface"
            )
            refreshToggleUI(newState)
        }

        btnAtualizar.setOnClickListener { showFilteredLog() }
        btnLimpar.setOnClickListener {
            WANotificationListener.clearLog(this)
            txtLog.text = ""
        }

        showFilteredLog()
    }

    private fun refreshToggleUI(isOn: Boolean) {
        btnToggle.text = if (isOn) "ON" else "OFF"
        btnToggle.backgroundTintList = ContextCompat.getColorStateList(
            this, if (isOn) R.color.toggleOn else R.color.toggleOff
        )
    }

    private fun showFilteredLog() {
        val raw = WANotificationListener.readLog(this)
        // apenas linhas relacionadas aos grupos monitorados
        val filtered = raw.lines().filter {
            it.contains("GSTA1 - Tennis") || it.contains("GSTA2 - Tennis") || it.startsWith("[TOGGLE]") || it.startsWith("[CONECTADO]")
        }.joinToString("\n")
        txtLog.text = filtered
        txtLog.post {
            findViewById<android.widget.ScrollView>(R.id.logScroll).fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private fun appendUiLog(line: String) {
        WANotificationListener.appendLog(this, line)
        showFilteredLog()
    }
}
