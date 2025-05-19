package com.tech4all.idt.wifiLocalization

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
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
import kotlinx.coroutines.launch
import java.util.Locale

import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import androidx.compose.ui.semantics.text
import androidx.glance.visibility
import com.tech4all.idt.R
import com.tech4all.idt.SupabaseHelper


/**
 * WiFiActivity handles Wi-Fi scanning, voice feedback, and saving scan results to a database.
 */
class NewPositionActivity : AppCompatActivity() {

    // Define UI elements
    private lateinit var wifiManager: WifiManager
    private lateinit var textView: TextView
    private lateinit var speechToggleButton: ToggleButton
    private lateinit var positionIdEditText: EditText
    private lateinit var scanButton: Button
    private lateinit var saveButton: Button
    private var isSpeechEnabled = true // Flag to toggle speech feedback
    private lateinit var fusedLocationClient: FusedLocationProviderClient

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
        setContentView(R.layout.activity_new_position)

        // Set up the action bar with a back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true);
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        // Initialize UI elements
        textView = findViewById(R.id.textView)
        scanButton = findViewById(R.id.scanButton)
        speechToggleButton = findViewById(R.id.speechToggleButton)
        saveButton = findViewById(R.id.saveButton)
        positionIdEditText = findViewById(R.id.positionId)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)


        // Initialize WifiManager and Location client
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Set up button listeners
        scanButton.setOnClickListener {
            textView.text = "Indoor positioning system"  // Clear previous results
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

        if (now - lastScanTime < 10000) {
            Toast.makeText(this, "Please wait before scanning again", Toast.LENGTH_SHORT).show()
            loadingProgressBar.visibility = View.GONE
            return
        }

        textView.text = "Scan in progress..." // Show progress text in the TextView

        val success = wifiManager.startScan()
        lastScanTime = now

        if (!success) {
            textView.text = "Scan failed"
            Log.e("WiFiScan", "startScan() returned false. This can be due to various reasons including throttling, permissions, or device state.")

            // Log additional Wi-Fi state information for debugging
            Log.e("WiFiScan", "Wi-Fi enabled: ${wifiManager.isWifiEnabled}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.e("WiFiScan", "Location permission granted: ${checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED}")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                var locationMode = -1 // Default if error occurs
                try {
                    locationMode = Settings.Secure.getInt(contentResolver, Settings.Secure.LOCATION_MODE)
                } catch (e: Settings.SettingNotFoundException) {
                    // Logged above
                }
                Log.e("WiFiScan", "Location Mode (0=OFF, 3=HIGH_ACCURACY): $locationMode")
            }

            loadingProgressBar.visibility = View.GONE
        } else {
            Log.i("WiFiScan", "startScan() successful. Waiting for results in BroadcastReceiver.")
            // Wait for results in the BroadcastReceiver
            // textView will be updated there after scan completes
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
                loadingProgressBar.visibility = View.GONE
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

        textView.text = sb.toString()
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
