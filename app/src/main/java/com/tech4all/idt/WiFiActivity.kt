package com.tech4all.idt

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.addCallback
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * WiFiActivity handles Wi-Fi scanning, voice feedback, and saving scan results to a database.
 */
class WiFiActivity : AppCompatActivity() {

    // Define UI elements
    private lateinit var wifiManager: WifiManager
    private lateinit var textView: TextView
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechToggleButton: ToggleButton
    private lateinit var positionIdEditText: EditText
    private lateinit var scanButton: Button
    private lateinit var saveButton: Button
    private var isSpeechEnabled = true // Flag to toggle speech feedback
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var matchResultsTextView: TextView

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    /**
     * onCreate initializes the activity, sets up listeners, and checks permissions.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi)

        // Set up the action bar with a back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        // Initialize UI elements
        textView = findViewById(R.id.textView)
        scanButton = findViewById(R.id.scanButton)
        speechToggleButton = findViewById(R.id.speechToggleButton)
        saveButton = findViewById(R.id.saveButton)
        positionIdEditText = findViewById(R.id.positionId)
        matchResultsTextView = findViewById(R.id.matchResultsTextView)

        // Initialize WifiManager and Location client
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.getDefault()
            }
        }

        // Set up button listeners
        scanButton.setOnClickListener {
            textView.text = ""  // Clear previous results
            scanWifi()
        }

        saveButton.setOnClickListener {
            val positionIdText = positionIdEditText.text.toString()
            if (positionIdText.isNotEmpty()) {
                try {
                    val positionId = positionIdText.toInt()
                    Log.d("PositionID", "Position ID: $positionId")
                    saveScanResults(positionId) // Save scan results
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "Invalid position ID", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please enter a position ID", Toast.LENGTH_SHORT).show()
            }
        }

        // Toggle speech feedback based on button state
        speechToggleButton.setOnCheckedChangeListener { _, isChecked ->
            isSpeechEnabled = isChecked
            if (isChecked) {
                textToSpeech.speak("Speech enabled", TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                textToSpeech.stop()
            }
        }

        // Check for location permissions
        checkAndRequestLocationPermission()
    }

    /**
     * checkAndRequestLocationPermission checks if location permission is granted,
     * if not, it requests permission from the user.
     */
    private fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            scanWifi()  // Permission granted, proceed with scanning
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * onRequestPermissionsResult handles the result of permission requests.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanWifi()  // Permission granted, proceed with scanning
            } else {
                Toast.makeText(this, "Location permission is required for Wi-Fi scanning", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * scanWifi starts a Wi-Fi scan and registers a receiver to get the scan results.
     */
    private fun scanWifi() {
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        val success = wifiManager.startScan()
        if (!success) {
            textView.text = "Scan failed"  // Handle scan failure
        } else {
            textView.text = "Scan successful"  // Clear previous results
        }
    }

    /**
     * wifiScanReceiver handles broadcasted scan results once the Wi-Fi scan is complete.
     */
    private val wifiScanReceiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                displayResults()  // Display scan results
            }
        }
    }

    /**
     * displayResults processes and displays the Wi-Fi scan results.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun displayResults() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // Sort results by signal strength (level)
        val results = wifiManager.scanResults
        val sortedResults = results.sortedByDescending { it.level }

        val sb = StringBuilder()
        val bssids = mutableListOf<String>()
        val signalStrengths = mutableListOf<Int>()

        // Display top 5 strongest networks
        for (result in sortedResults.take(5)) {
            sb.append(result.SSID)
                .append(" - BSSID: ").append(result.BSSID)
                .append(" - Signal Strength: ").append(result.level).append(" dBm\n")

            // Add BSSIDs and signal strengths to the respective lists
            bssids.add(result.BSSID)
            signalStrengths.add(result.level)
        }

        /*
        textView.text = sb.toString()  // Show results on UI
        // Optionally speak the results
        if (isSpeechEnabled) {
            textToSpeech.speak(sb.toString(), TextToSpeech.QUEUE_FLUSH, null, null)
        }
        */


        // Call queryBestMatchingPosition with the extracted BSSIDs and signal strengths
        CoroutineScope(Dispatchers.Main).launch {
            val matches = SupabaseHelper.queryBestMatchingPosition(bssids, signalStrengths)
            val resultText = if (matches.isNotEmpty()) {
                "You are near room: " + matches.joinToString(separator = "\n") { (id, name) ->
                    "• ID: $id | Number: $name"
                }
            } else {
                "No position founded."
            }

            matchResultsTextView.text = resultText
        }
    }


    /**
     * saveScanResults saves the top 5 Wi-Fi scan results to a database.
     * @param positionId The position identifier for the scan.
     */
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun saveScanResults(positionId: Int) {
        Log.d("SaveScanResults", "saveScanResults called")

        lifecycleScope.launch {
            Log.d("SaveScanResults", "Position saved with ID: $positionId")

            val results = wifiManager.scanResults
            val sortedResults = results.sortedByDescending { it.level }
            Log.d("SaveScanResults", "Wi-Fi scan results size: ${results.size}")

            // Log and save top 5 results
            for (result in sortedResults.take(5)) {
                Log.d("SaveScanResults", "BSSID: ${result.BSSID}, Signal Strength: ${result.level}")
                SupabaseHelper.insertWifiScan(
                    positionId,
                    result.SSID,
                    result.BSSID,
                    result.level
                )  // Insert data to database
            }

            Toast.makeText(applicationContext, "Data saved!", Toast.LENGTH_SHORT).show()  // Show success message
        }
    }

    /**
     * onDestroy cleans up resources when the activity is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wifiScanReceiver)  // Unregister Wi-Fi receiver
        textToSpeech.stop()  // Stop speech synthesis
        textToSpeech.shutdown()  // Release resources
    }

    /**
     * Handle the back button press in the action bar.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
