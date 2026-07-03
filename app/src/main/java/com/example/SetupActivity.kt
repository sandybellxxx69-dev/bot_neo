package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.databinding.ActivitySetupBinding
import kotlinx.coroutines.*
import java.io.FileOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val PREFS_NAME = "BotPrefs"
    private val URL_KEY = "index_url"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(URL_KEY, "")

        if (!savedUrl.isNullOrEmpty()) {
            binding.etUrl.setText(savedUrl)
            binding.btnChangeUrl.visibility = View.VISIBLE
            binding.btnContinue.visibility = View.VISIBLE
            binding.btnDownloadAndContinue.visibility = View.GONE
        }

        binding.btnDownloadAndContinue.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                downloadFileAndContinue(url)
            } else {
                Toast.makeText(this, "URL no puede estar vacía", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnChangeUrl.setOnClickListener {
            binding.btnDownloadAndContinue.visibility = View.VISIBLE
            binding.btnChangeUrl.visibility = View.GONE
            binding.btnContinue.visibility = View.GONE
        }

        binding.btnContinue.setOnClickListener {
            goToMain()
        }
    }

    private fun downloadFileAndContinue(urlString: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnDownloadAndContinue.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpsURLConnection
                connection.connect()

                if (connection.responseCode != HttpsURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP ${connection.responseCode}")
                }

                val input = connection.inputStream
                val outputFile = getFileStreamPath("index.js")
                val output = FileOutputStream(outputFile)

                val data = ByteArray(4096)
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                withContext(Dispatchers.Main) {
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString(URL_KEY, urlString).apply()
                    goToMain()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnDownloadAndContinue.isEnabled = true
                    Toast.makeText(this@SetupActivity, "Error al descargar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
