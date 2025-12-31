package com.seuapp.whatsautoresponder.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.widget.doAfterTextChanged
import com.seuapp.whatsautoresponder.R
import com.seuapp.whatsautoresponder.util.Prefs

class MainActivity : ComponentActivity() {

    private data class DayConfig(val key: String, val labelRes: Int)

    private val dayConfigs = listOf(
        DayConfig("mon", R.string.mon),
        DayConfig("tue", R.string.tue),
        DayConfig("wed", R.string.wed),
        DayConfig("thu", R.string.thu),
        DayConfig("fri", R.string.fri),
        DayConfig("sat", R.string.sat),
        DayConfig("sun", R.string.sun)
    )

    private lateinit var btnToggle: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var scheduleContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        tvStatus = findViewById(R.id.tvStatus)
        scheduleContainer = findViewById(R.id.scheduleContainer)

        setupToggleButton()
        setupPermissionButton()
        setupSchedule()
        setupLogButton()
        updateToggleState()
    }

    override fun onResume() {
        super.onResume()
        updateToggleState()
    }

    private fun setupToggleButton() {
        btnToggle.setOnClickListener {
            val newState = !Prefs.isEnabled(this)
            Prefs.setEnabled(this, newState)
            updateToggleState()

            val msg = if (newState) "Monitoramento ativado" else "Monitoramento desativado"
            Prefs.appendLog(this, msg)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateToggleState() {
        val isEnabled = Prefs.isEnabled(this)
        btnToggle.setImageResource(
            if (isEnabled) R.drawable.tennis_ball_on else R.drawable.tennis_ball_off
        )
        tvStatus.text = getString(
            if (isEnabled) R.string.monitoring_active else R.string.monitoring_inactive
        )
        tvStatus.setTextColor(
            getColor(if (isEnabled) R.color.tennis_green else R.color.gray_dark)
        )
    }

    private fun setupPermissionButton() {
        findViewById<android.widget.Button>(R.id.btnPermission).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Não foi possível abrir configurações", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSchedule() {
        val inflater = LayoutInflater.from(this)
        scheduleContainer.removeAllViews()

        dayConfigs.forEach { day ->
            val row = inflater.inflate(R.layout.item_day_schedule, scheduleContainer, false)

            val cbEnabled = row.findViewById<CheckBox>(R.id.cbDayEnabled)
            val tvDayName = row.findViewById<TextView>(R.id.tvDayName)
            val etStart = row.findViewById<EditText>(R.id.etStart)
            val etEnd = row.findViewById<EditText>(R.id.etEnd)

            tvDayName.text = getString(day.labelRes)
            cbEnabled.isChecked = Prefs.isDayEnabled(this, day.key)
            etStart.setText(Prefs.getDayStart(this, day.key))
            etEnd.setText(Prefs.getDayEnd(this, day.key))

            // Enable/disable inputs based on checkbox
            val updateInputState = { enabled: Boolean ->
                etStart.isEnabled = enabled
                etEnd.isEnabled = enabled
                etStart.alpha = if (enabled) 1f else 0.5f
                etEnd.alpha = if (enabled) 1f else 0.5f
            }
            updateInputState(cbEnabled.isChecked)

            cbEnabled.setOnCheckedChangeListener { _, checked ->
                Prefs.setDayEnabled(this, day.key, checked)
                updateInputState(checked)
            }

            etStart.doAfterTextChanged { text ->
                Prefs.setDayStart(this, day.key, formatTimeInput(text?.toString()))
            }

            etEnd.doAfterTextChanged { text ->
                Prefs.setDayEnd(this, day.key, formatTimeInput(text?.toString()))
            }

            scheduleContainer.addView(row)
        }
    }

    private fun formatTimeInput(input: String?): String {
        val clean = input?.filter { it.isDigit() || it == ':' } ?: ""
        return clean.take(5)
    }

    private fun setupLogButton() {
        findViewById<android.widget.Button>(R.id.btnViewLog).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }
}
