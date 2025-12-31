package com.seuapp.whatsautoresponder.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.seuapp.whatsautoresponder.R
import com.seuapp.whatsautoresponder.util.LogBus
import com.seuapp.whatsautoresponder.util.Prefs

class LogActivity : ComponentActivity() {

    private lateinit var tvLog: TextView

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshLog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        tvLog = findViewById(R.id.tvLog)

        setupBackButton()
        setupClearButton()
        refreshLog()
    }

    override fun onResume() {
        super.onResume()
        registerLogReceiver()
        refreshLog()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(logReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }

    private fun registerLogReceiver() {
        val filter = IntentFilter(LogBus.ACTION_LOG_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(logReceiver, filter)
        }
    }

    private fun setupBackButton() {
        findViewById<android.widget.ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun setupClearButton() {
        findViewById<android.widget.Button>(R.id.btnClearLog).setOnClickListener {
            Prefs.clearLog(this)
            Prefs.clearReplyFlag(this)
            refreshLog()
            Toast.makeText(this, "Log limpo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshLog() {
        val log = Prefs.readLog(this)
        tvLog.text = log.ifEmpty { getString(R.string.no_logs) }
    }
}
