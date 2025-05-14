package com.tech4all.idt.wifiLocalization

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
import android.widget.TextView
import android.widget.Toast
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

import android.location.LocationManager
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import com.tech4all.idt.R
import com.tech4all.idt.SupabaseHelper


/**
 * WiFiActivity handles Wi-Fi scanning, voice feedback, and saving scan results to a database.
 */
class WiFiActivityMain : AppCompatActivity() {

    // Define UI elements
    private lateinit var wifiManager: WifiManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var matchResultsTextView: TextView
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var scanButton: Button
    private lateinit var newPositionButton: Button
    private lateinit var goButton: ImageButton

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private var lastScanTime = 0L
    private var isReceiverRegistered = false
    private lateinit var loadingProgressBar: ProgressBar


    /**
     * onCreate initializes the activity, sets up listeners, and checks permissions.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        if (!isReceiverRegistered) {
            registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            isReceiverRegistered = true
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi)

        scanButton = findViewById(R.id.scanButton)
        scanButton.setOnClickListener {
            scanWifi()
        }

        goButton = findViewById(R.id.confirmButton)
        goButton.setOnClickListener {
            val intent = Intent(this, NavigationActivity::class.java)
            startActivity(intent)
        }

        newPositionButton = findViewById(R.id.newPositionButton)
        newPositionButton.setOnClickListener {
            val intent = Intent(this, NewPositionActivity::class.java)
            startActivity(intent)
        }

        // Set up the action bar with a back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true);
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        // Initialize UI elements
        matchResultsTextView = findViewById(R.id.matchResultsTextView)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)


        // Initialize WifiManager and Location client
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.getDefault()
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
        loadingProgressBar.visibility = View.VISIBLE
        val now = SystemClock.elapsedRealtime()

        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "Please enable Wi-Fi", Toast.LENGTH_SHORT).show()
            loadingProgressBar.visibility = View.GONE
            return
        }

        if (!isLocationServiceEnabled()) {
            Toast.makeText(this, "Please enable Location (GPS)", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
            loadingProgressBar.visibility = View.GONE
            return
        }

        if (now - lastScanTime < 30000) {
            Toast.makeText(this, "Please wait before scanning again", Toast.LENGTH_SHORT).show()
            loadingProgressBar.visibility = View.GONE
            return
        }

        val success = wifiManager.startScan()
        lastScanTime = now

        if (!success) {
            Log.e("WiFiScan", "startScan() failed")
            loadingProgressBar.visibility = View.GONE
        }
    }


    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
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

        // Call queryBestMatchingPosition with the extracted BSSIDs and signal strengths
        CoroutineScope(Dispatchers.Main).launch {
            loadingProgressBar.visibility = View.VISIBLE // show loader
            try {
                Log.d("queryBestMatchingPosition called", bssids.toString())
                val matches = SupabaseHelper.queryBestMatchingPosition(bssids, signalStrengths)
                val resultText = if (matches.isNotEmpty()) {
                    "You are near room: " + matches.joinToString(separator = "\n") { (id, name) ->
                        "$id | $name"
                    }
                } else {
                    "No position founded."
                }

                matchResultsTextView.text = resultText

                val spokenText = if (matches.isNotEmpty()) {
                    "You are near room " + matches.joinToString(separator = ", ") { it.second }
                } else {
                    "No position found"
                }
                textToSpeech.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, null)

            } catch (e: Exception) {
                Log.e("WiFiActivity", "Error during query: ${e.message}", e)
                matchResultsTextView.text = "Error retrieving position."
            } finally {
                loadingProgressBar.visibility = View.GONE // hide loader
            }
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
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(wifiScanReceiver)
                isReceiverRegistered = false
            } catch (e: IllegalArgumentException) {
                Log.w("WiFiScan", "Receiver already unregistered")
            }
        }
        textToSpeech.stop()
        textToSpeech.shutdown()
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
