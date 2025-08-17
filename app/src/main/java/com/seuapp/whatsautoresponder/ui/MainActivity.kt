package com.seuapp.whatsautoresponder.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.seuapp.whatsautoresponder.databinding.ActivityMainBinding
import com.seuapp.whatsautoresponder.util.Prefs
import com.seuapp.whatsautoresponder.util.LogBus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Estado inicial
        val enabled = Prefs.isEnabled(this)
        renderEnabled(enabled)
        binding.tvLog.text = Prefs.readLog(this)

        // Botão ON/OFF grande
        binding.btnPower.setOnClickListener {
            val newVal = Prefs.toggleEnabled(this)
            renderEnabled(newVal)
            Prefs.appendLog(this, if (newVal) "Aplicativo LIGADO." else "Aplicativo DESLIGADO.")
        }

        // Abrir configurações do serviço de notificações
        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Atualizar log (puxa do SharedPreferences)
        binding.btnRefresh.setOnClickListener {
            binding.tvLog.text = Prefs.readLog(this)
        }

        // Limpar log
        binding.btnClear.setOnClickListener {
            Prefs.clearLog(this)
            binding.tvLog.text = ""
        }

        // Assinar eventos de log em tempo real (LogBus)
        lifecycleScope.launch {
            LogBus.events.collectLatest { line ->
                // acrescenta à tela sem regravar tudo
                val cur = binding.tvLog.text?.toString().orEmpty()
                binding.tvLog.text = if (cur.isEmpty()) line else "$cur\n$line"
            }
        }
    }

    private fun renderEnabled(enabled: Boolean) {
        // Visual do botão
        binding.btnPower.text = if (enabled) "ON" else "OFF"
        binding.btnPower.isSelected = enabled

        // Rótulo de status
        binding.tvStatus.text = if (enabled) "Ativo" else "Inativo"
    }
}
