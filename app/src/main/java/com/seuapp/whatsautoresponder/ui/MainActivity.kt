package com.seuapp.whatsautoresponder.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.seuapp.whatsautoresponder.R
import com.seuapp.whatsautoresponder.util.LogBus
import com.seuapp.whatsautoresponder.util.Prefs

class MainActivity : AppCompatActivity() {

    private data class DayConfig(val key: String, val label: String)

    private val dayConfigs = listOf(
        DayConfig("mon", "Segunda-feira"),
        DayConfig("tue", "Terça-feira"),
        DayConfig("wed", "Quarta-feira"),
        DayConfig("thu", "Quinta-feira"),
        DayConfig("fri", "Sexta-feira"),
        DayConfig("sat", "Sábado"),
        DayConfig("sun", "Domingo")
    )

    private lateinit var tvLog: TextView
    private lateinit var swEnabled: Switch
    private lateinit var btnOpenSettings: Button
    private lateinit var btnClear: Button
    private lateinit var scheduleContainer: LinearLayout

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val line = intent.getStringExtra(LogBus.EXTRA_LINE) ?: return
            appendLine(line)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)
        swEnabled = findViewById(R.id.swEnabled)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        btnClear = findViewById(R.id.btnClear)
        scheduleContainer = findViewById(R.id.scheduleContainer)

        setupSwitch()
        setupButtons()
        setupSchedule()

        tvLog.text = Prefs.readLog(this)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(LogBus.ACTION_LOG_UPDATED)

        // Android 13+ exige declarar se o receiver é exported ou não (senão crash ao abrir).
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(logReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(logReceiver) }
    }

    private fun setupSwitch() {
        swEnabled.isChecked = Prefs.isEnabled(this)
        swEnabled.setOnCheckedChangeListener { _, checked ->
            Prefs.setEnabled(this, checked)
            val line = if (checked) "App habilitado manualmente." else "App desabilitado manualmente."
            Prefs.appendLog(this, line)
            LogBus.emit(line)
            tvLog.text = Prefs.readLog(this)
        }
    }

    private fun setupButtons() {
        btnOpenSettings.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }.onFailure {
                Toast.makeText(this, "Não foi possível abrir as configurações.", Toast.LENGTH_LONG).show()
            }
        }

        btnClear.setOnClickListener {
            Prefs.clearLog(this)
            tvLog.text = ""
            Toast.makeText(this, "Log limpo.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSchedule() {
        val inflater = LayoutInflater.from(this)
        scheduleContainer.removeAllViews()

        dayConfigs.forEach { day ->
            val row = inflater.inflate(R.layout.item_day_schedule, scheduleContainer, false)

            val tvDayName = row.findViewById<TextView>(R.id.tvDayName)
            val swDayEnabled = row.findViewById<Switch>(R.id.swDayEnabled)
            val etStart = row.findViewById<EditText>(R.id.etStart)
            val etEnd = row.findViewById<EditText>(R.id.etEnd)

            tvDayName.text = day.label
            swDayEnabled.isChecked = Prefs.isDayEnabled(this, day.key)
            etStart.setText(Prefs.getDayStart(this, day.key))
            etEnd.setText(Prefs.getDayEnd(this, day.key))

            swDayEnabled.setOnCheckedChangeListener { _, checked ->
                Prefs.setDayEnabled(this, day.key, checked)
            }

            etStart.doAfterTextChanged {
                Prefs.setDayStart(this, day.key, it?.toString()?.trim().orEmpty())
            }

            etEnd.doAfterTextChanged {
                Prefs.setDayEnd(this, day.key, it?.toString()?.trim().orEmpty())
            }

            scheduleContainer.addView(row)
        }
    }

    private fun appendLine(line: String) {
        val current = tvLog.text?.toString().orEmpty()
        tvLog.text = if (current.isBlank()) line else "$current\n$line"
    }
}
