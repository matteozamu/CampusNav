package com.tech4all.idt

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import java.util.Locale

class WiFiActivity : AppCompatActivity() {
    private lateinit var wifiManager: WifiManager
    private lateinit var textView: TextView
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechToggleButton: ToggleButton
    private lateinit var positionIdEditText: EditText
    private lateinit var scanButton: Button
    private lateinit var saveButton: Button
    private var isSpeechEnabled = true
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi)

        textView = findViewById(R.id.textView)
        scanButton = findViewById(R.id.scanButton)
        speechToggleButton = findViewById(R.id.speechToggleButton)
        saveButton = findViewById(R.id.saveButton)
        positionIdEditText = findViewById(R.id.positionId)

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

        speechToggleButton.setOnCheckedChangeListener { _, isChecked ->
            isSpeechEnabled = isChecked
            if (isChecked) {
                textToSpeech.speak("Speech enabled", TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                textToSpeech.stop()
            }
        }

        checkAndRequestLocationPermission()
    }

    private fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            scanWifi()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanWifi()
            } else {
                Toast.makeText(this, "Location permission is required for Wi-Fi scanning", Toast.LENGTH_LONG).show()
            }
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
        @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                displayResults()
            }
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun displayResults() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val results = wifiManager.scanResults
        val sortedResults = results.sortedByDescending { it.level }

        val sb = StringBuilder()
        for (result in sortedResults.take(5)) {
            sb.append(result.SSID)
                .append(" - BSSID: ").append(result.BSSID)
                .append(" - Signal Strength: ").append(result.level).append(" dBm\n")
        }

        textView.text = sb.toString()

        if (isSpeechEnabled) {
            textToSpeech.speak(sb.toString(), TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun saveScanResults(positionId: Int) {
        Log.d("SaveScanResults", "saveScanResults called")

        lifecycleScope.launch {
            Log.d("SaveScanResults", "Position saved with ID: $positionId")

            val results = wifiManager.scanResults
            val sortedResults = results.sortedByDescending { it.level }
            Log.d("SaveScanResults", "Wi-Fi scan results size: ${results.size}")

            for (result in sortedResults.take(5)) {
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
