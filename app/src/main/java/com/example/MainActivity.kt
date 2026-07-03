package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val PREFS_NAME = "BotPrefs"
    private lateinit var watchdog: WatchdogManager
    private lateinit var sessionManager: SessionManager
    private val URL_KEY = "index_url"

    private var uptimeTimer: kotlinx.coroutines.Job? = null
    private var botStartTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        watchdog = WatchdogManager(this)
        sessionManager = SessionManager(this)

        checkPermissions()
        setupUI()
        setupCallbacks()
        startUptimeTimer()
    }
    
    private fun startUptimeTimer() {
        uptimeTimer = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            while (true) {
                if (BotService.isRunning) {
                    if (botStartTime == 0L) botStartTime = System.currentTimeMillis()
                    val diff = (System.currentTimeMillis() - botStartTime) / 1000
                    val h = diff / 3600
                    val m = (diff % 3600) / 60
                    val s = diff % 60
                    binding.tvStatusUptime.text = String.format("%02d:%02d:%02d", h, m, s)
                } else {
                    botStartTime = 0L
                    binding.tvStatusUptime.text = "00:00:00"
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        uptimeTimer?.cancel()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun setupUI() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        binding.etMainUrl.setText(prefs.getString(URL_KEY, ""))

        binding.swWatchdog.isChecked = watchdog.isEnabled
        binding.tvWatchdogCount.text = "Reinicios: ${watchdog.restartCount} / 5"

        binding.swWatchdog.setOnCheckedChangeListener { _, isChecked ->
            watchdog.isEnabled = isChecked
        }

        binding.btnResetWatchdog.setOnClickListener {
            watchdog.resetCounter()
            binding.tvWatchdogCount.text = "Reinicios: 0 / 5"
        }

        binding.btnStart.setOnClickListener {
            val startIntent = Intent(this, BotService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
        }

        binding.btnStop.setOnClickListener {
            val stopIntent = Intent(this, BotService::class.java).apply { action = "STOP" }
            startService(stopIntent)
        }
        
        binding.btnKillAll.setOnClickListener {
            val stopIntent = Intent(this, BotService::class.java).apply { action = "STOP" }
            startService(stopIntent)
            addLog("🔴 TODOS LOS PROCESOS MATADOS")
        }
        
        binding.btnLogoutWa.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cerrar Sesión")
                .setMessage("¿Seguro que deseas borrar los archivos de sesión de WhatsApp?")
                .setPositiveButton("Sí") { _, _ ->
                    val stopIntent = Intent(this, BotService::class.java).apply { action = "STOP" }
                    startService(stopIntent)
                    sessionManager.clearAllSessions()
                    addLog("🗂 Sesión borrada por usuario")
                    updateStatusPanel()
                }
                .setNegativeButton("No", null)
                .show()
        }
        
        binding.btnCleanGhosts.setOnClickListener {
            val deleted = sessionManager.cleanGhostSessions()
            addLog("🧹 Se limpiaron $deleted sesiones fantasma.")
            updateStatusPanel()
        }
        
        binding.btnCleanRestart.setOnClickListener {
            addLog("🔄 Reinicio limpio iniciado")
            val stopIntent = Intent(this, BotService::class.java).apply { action = "STOP" }
            startService(stopIntent)
            sessionManager.cleanGhostSessions()
            binding.btnStart.postDelayed({ binding.btnStart.performClick() }, 2000)
        }
        
        binding.btnDeauthReconnect.setOnClickListener {
            addLog("🔁 Deautenticar y reconectar iniciado")
            val stopIntent = Intent(this, BotService::class.java).apply { action = "STOP" }
            startService(stopIntent)
            sessionManager.clearAllSessions()
            binding.btnStart.postDelayed({ 
                binding.btnStart.performClick()
                binding.panelAuth.visibility = View.VISIBLE
            }, 3000)
        }

        binding.btnReqCode.setOnClickListener {
            val phone = binding.etPhone.text.toString().trim()
            if (phone.isNotEmpty()) {
                addLog("📲 Código de vinculación solicitado")
                NodeBridge.sendInput("PAIRING_NUMBER:$phone")
            } else {
                Toast.makeText(this, "Ingresa un teléfono", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopyCode.setOnClickListener {
            val code = binding.tvCode.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Código WA", code)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Código copiado", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveUrl.setOnClickListener {
            val url = binding.etMainUrl.text.toString()
            prefs.edit().putString(URL_KEY, url).apply()
            Toast.makeText(this, "URL Guardada", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnUpdateIndex.setOnClickListener {
            val urlString = prefs.getString(URL_KEY, "")
            if (!urlString.isNullOrEmpty()) {
                addLog("⬇ Descargando index.js desde URL guardada...")
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val url = java.net.URL(urlString)
                        val connection = url.openConnection() as javax.net.ssl.HttpsURLConnection
                        connection.connect()
                        if (connection.responseCode != javax.net.ssl.HttpsURLConnection.HTTP_OK) {
                            throw Exception("Server returned HTTP ${connection.responseCode}")
                        }
                        val input = connection.inputStream
                        val outputFile = getFileStreamPath("index.js")
                        val output = java.io.FileOutputStream(outputFile)
                        val data = ByteArray(4096)
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            output.write(data, 0, count)
                        }
                        output.flush()
                        output.close()
                        input.close()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            addLog("✅ index.js actualizado correctamente.")
                            Toast.makeText(this@MainActivity, "Actualizado correctamente", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            addLog("❌ Error actualizando index.js: ${e.message}")
                        }
                    }
                }
            } else {
                Toast.makeText(this, "No hay URL guardada", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.btnExportLogs.setOnClickListener {
            val text = binding.tvLogs.text.toString()
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
        
        updateStatusPanel()
    }

    private fun setupCallbacks() {
        BotService.onLogCallback = { logLine ->
            runOnUiThread { addLog(logLine) }
        }
        BotService.onStatusChanged = { status ->
            runOnUiThread {
                binding.tvStatusProcess.text = status
                binding.tvWatchdogCount.text = "Restarts: ${watchdog.restartCount} / 5"
                updateStatusPanel()
            }
        }
        BotService.onWaStatusChanged = { status ->
            runOnUiThread {
                if (status != "ERROR_DETECTADO") {
                    binding.tvStatusWa.text = status
                }
                if (status.contains("CONECTADO")) {
                    binding.panelAuth.visibility = View.GONE
                } else if (status.contains("REQUIERE CÓDIGO")) {
                    binding.panelAuth.visibility = View.VISIBLE
                    showDisconnectAlert("Se requiere emparejamiento nuevamente.")
                } else if (status.contains("DESCONECTADO")) {
                    showDisconnectAlert("Sesión desconectada.")
                } else if (status == "ERROR_DETECTADO") {
                    showDisconnectAlert("Se detectó un error en la conexión.")
                }
                updateStatusPanel()
            }
        }
        BotService.onPairingCode = { code ->
            runOnUiThread {
                binding.tvCode.text = code
                binding.panelCodeResult.visibility = View.VISIBLE
            }
        }
    }

    private fun updateStatusPanel() {
        val (count, size) = sessionManager.getSessionInfo()
        val kb = size / 1024
        binding.tvStatusFiles.text = "$count files ($kb KB)"
    }

    private fun showDisconnectAlert(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("Atención")
            .setMessage(msg)
            .setPositiveButton("Reconectar") { _, _ -> binding.btnCleanRestart.performClick() }
            .setNeutralButton("Deautenticar y reconectar") { _, _ -> binding.btnDeauthReconnect.performClick() }
            .setNegativeButton("Ignorar", null)
            .show()
    }

    private fun addLog(text: String) {
        val currentLogs = binding.tvLogs.text.toString()
        val lines = currentLogs.split("\n").toMutableList()
        lines.add(text)
        if (lines.size > 500) {
            lines.removeAt(0)
        }
        binding.tvLogs.text = lines.joinToString("\n")
        binding.svLogs.post {
            binding.svLogs.fullScroll(View.FOCUS_DOWN)
        }
    }
}
