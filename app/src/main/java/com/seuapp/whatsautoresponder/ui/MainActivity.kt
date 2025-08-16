package com.seuapp.whatsautoresponder.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.seuapp.whatsautoresponder.R
import com.seuapp.whatsautoresponder.util.LogHelper

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "auto_prefs"
        private const val KEY_ENABLED = "enabled"
        const val ACTION_LOG_UPDATED = "LOG_UPDATED"
        const val EXTRA_LINE = "LINE"
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private lateinit var tvLog: TextView
    private lateinit var swEnabled: Switch
    private lateinit var btnOpenSettings: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnClear: Button

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_LOG_UPDATED) {
                val line = intent.getStringExtra(EXTRA_LINE) ?: return
                tvLog.append("\n$line")
                scrollLogToBottom()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)
        swEnabled = findViewById(R.id.swEnabled)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnClear = findViewById(R.id.btnClear)

        tvLog.movementMethod = ScrollingMovementMethod()

        // Inicializa UI
        val enabled = prefs().getBoolean(KEY_ENABLED, true)
        swEnabled.isChecked = enabled

        swEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs().edit().putBoolean(KEY_ENABLED, isChecked).apply()
            val status = if (isChecked) "LIGADO" else "DESLIGADO"
            LogHelper.write(this, "TOGGLE", "Auto-responder $status pela interface")
            // Mostra imediatamente no log
            refreshLog()
        }

        btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnRefresh.setOnClickListener { refreshLog() }
        btnClear.setOnClickListener {
            LogHelper.clear(this)
            refreshLog()
        }

        refreshLog()
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(logReceiver, IntentFilter(ACTION_LOG_UPDATED))
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
    }

    private fun refreshLog() {
        tvLog.text = LogHelper.readAll(this).ifBlank { "Sem eventos ainda." }
        scrollLogToBottom()
    }

    private fun scrollLogToBottom() {
        tvLog.post {
            val layout = tvLog.layout ?: return@post
            val scrollAmount = layout.getLineTop(tvLog.lineCount) - tvLog.height
            if (scrollAmount > 0) tvLog.scrollTo(0, scrollAmount) else tvLog.scrollTo(0, 0)
        }
    }
}
