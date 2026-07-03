package com.example

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BotService : Service() {

    private val CHANNEL_ID = "BotServiceChannel"
    private var wakeLock: PowerManager.WakeLock? = null
    private var nodeJob: Job? = null
    
    private val watchdog = WatchdogManager(this)
    private var lastLogTime = 0L

    companion object {
        var isRunning = false
        var onLogCallback: ((String) -> Unit)? = null
        var onStatusChanged: ((String) -> Unit)? = null
        var onWaStatusChanged: ((String) -> Unit)? = null
        var onPairingCode: ((String) -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BotNeo:WakeLock")
        wakeLock?.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Bot NEO activo 🤖")
        startForeground(1, notification)

        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            startNodeProcess()
        }

        return START_STICKY
    }

    private fun startNodeProcess() {
        isRunning = true
        onStatusChanged?.invoke("CORRIENDO \uD83D\uDFE2") // Green circle
        
        NodeBridge.setOutputListener { output ->
            handleOutput(output)
        }

        nodeJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val indexFile = File(filesDir, "index.js").absolutePath
                val args = arrayOf("node", indexFile)
                val result = NodeBridge.startNodeWithArguments(args)
                
                withContext(Dispatchers.Main) {
                    log("Proceso Node finalizó con código: $result")
                    isRunning = false
                    onStatusChanged?.invoke("DETENIDO \uD83D\uDD34")
                    
                    if (watchdog.isEnabled) {
                        handleWatchdogRestart()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    log("Error en Node: ${e.message}")
                    isRunning = false
                    onStatusChanged?.invoke("DETENIDO \uD83D\uDD34")
                }
            }
        }
        
        // Watchdog loop
        CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                delay(30000)
                if (watchdog.isEnabled && isRunning) {
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime > 10 * 60 * 1000) {
                        // 10 minutes without logs
                        withContext(Dispatchers.Main) {
                            log("Watchdog: 10 minutos sin logs, reiniciando...")
                            handleWatchdogRestart()
                        }
                    }
                }
            }
        }
    }
    
    private fun handleWatchdogRestart() {
        if (watchdog.restartCount >= watchdog.maxRestarts) {
            log("Watchdog: Límite de reinicios alcanzado.")
            stopSelf()
            return
        }
        watchdog.incrementRestartCount()
        log("Watchdog: Reiniciando en 5s...")
        CoroutineScope(Dispatchers.Main).launch {
            delay(5000)
            startNodeProcess()
        }
    }

    private fun handleOutput(output: String) {
        lastLogTime = System.currentTimeMillis()
        val lines = output.split("\n")
        for (line in lines) {
            if (line.isBlank()) continue
            
            CoroutineScope(Dispatchers.Main).launch {
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val formattedLog = "[$time] $line"
                onLogCallback?.invoke(formattedLog)

                if (line.contains("PAIRING_CODE:")) {
                    val code = line.substringAfter("PAIRING_CODE:").trim()
                    onPairingCode?.invoke(code)
                }
                else if (line.contains("WA_STATUS:CONECTADO")) {
                    onWaStatusChanged?.invoke("CONECTADO ✅")
                }
                else if (line.contains("WA_STATUS:DESCONECTADO") || line.contains("loggedOut")) {
                    onWaStatusChanged?.invoke("DESCONECTADO ❌")
                }
                else if (line.contains("WA_STATUS:REQUIERE_CODIGO")) {
                    onWaStatusChanged?.invoke("REQUIERE CÓDIGO \uD83D\uDD11")
                }
                else if (line.contains("restartRequired") || line.contains("Stream Errored") || line.contains("Connection Failure") || line.contains("Session is not open") || line.contains("Connection closed") || line.contains("Timed Out")) {
                    onWaStatusChanged?.invoke("ERROR_DETECTADO")
                }
            }
        }
    }

    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        CoroutineScope(Dispatchers.Main).launch {
            onLogCallback?.invoke("[$time] $message")
        }
    }

    private fun createNotification(content: String): Notification {
        val stopIntent = Intent(this, BotService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bot NEO")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_delete, "Detener", stopPendingIntent)
            .setOngoing(true)
            
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Bot Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        onStatusChanged?.invoke("DETENIDO \uD83D\uDD34")
        nodeJob?.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
