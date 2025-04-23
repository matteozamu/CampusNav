package com.tech4all.idt

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import java.util.Locale
import android.util.Log
import android.widget.EditText

class MainActivity : AppCompatActivity() {
    private lateinit var wifiManager: WifiManager
    private lateinit var textView: TextView
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechToggleButton: ToggleButton
    private var isSpeechEnabled = true
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var positionIdEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.textView)
        val scanButton: Button = findViewById(R.id.scanButton)
        speechToggleButton = findViewById(R.id.speechToggleButton)
        val saveButton: Button = findViewById(R.id.saveButton)
        val newEventButton : Button = findViewById(R.id.newEventButton) //find the new button

        positionIdEditText = findViewById<EditText>(R.id.positionId)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.getDefault()
            }
        }

        scanButton.setOnClickListener {
            textView.text = ""
            scanWifi()
        }

        saveButton.setOnClickListener {
            val positionIdText = positionIdEditText.text.toString()
            if (positionIdText.isNotEmpty()) {
                try {
                    val positionId = positionIdText.toInt()
                    Log.d("PositionID", "Position ID: $positionId")

                    saveScanResults(positionId)
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "Invalid position ID", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter a position ID", Toast.LENGTH_SHORT).show()
            }
        }

        // ToggleButton listener to enable/disable speech immediately
        speechToggleButton.setOnCheckedChangeListener { _, isChecked ->
            isSpeechEnabled = isChecked
            if (isChecked) {
                textToSpeech.speak("Speech enabled", TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                textToSpeech.stop()
            }
        }

        requestLocationPermission()

        // New Event Button Click Listener
        newEventButton.setOnClickListener {
            val intent = Intent(this, CreateEventActivity::class.java)
            startActivity(intent)
        }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                scanWifi()
            } else {
                textView.text = "Permission denied"
            }
        }

    private fun scanWifi() {
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        val success = wifiManager.startScan()
        if (!success) {
            textView.text = "Scan failed"
        }
    }

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                displayResults()
            }
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    private fun displayResults() {
        val results: List<ScanResult> = wifiManager.scanResults

        // Sort the results by signal strength (RSSI) in descending order
        val sortedResults = results.sortedByDescending { it.level }

        val sb = StringBuilder()
        for (result in sortedResults.take(5)) {
            // Get the RSSI (signal strength)
            val signalStrength = result.level // Signal strength in dBm
            sb.append(result.SSID)
                .append(" - BSSID: ").append(result.BSSID)
                .append(" - Signal Strength: ").append(signalStrength).append(" dBm\n")
        }

        textView.text = sb.toString()

        if (isSpeechEnabled) {
            // Speak immediately if speech is enabled
            textToSpeech.speak(sb.toString(), TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun saveScanResults(positionId: Int) {
        // Log start of the function
        Log.d("SaveScanResults", "saveScanResults called")

        lifecycleScope.launch {

            // Log position insertion result
            Log.d("SaveScanResults", "Position saved with ID: $positionId")

            // Get Wi-Fi scan results
            val results: List<ScanResult> = wifiManager.scanResults
            val sortedResults = results.sortedByDescending { it.level }
            Log.d("SaveScanResults", "Wi-Fi scan results size: ${results.size}")

            for (result in sortedResults.take(5)) {
                // Log each Wi-Fi scan result
                Log.d("SaveScanResults", "BSSID: ${result.BSSID}, Signal Strength: ${result.level}")
                SupabaseHelper.insertWifiScan(positionId, result.SSID, result.BSSID, result.level)
            }

            Toast.makeText(applicationContext, "Data saved!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wifiScanReceiver)
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}