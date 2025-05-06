package com.tech4all.idt.wifi

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.tech4all.idt.R

class WiFiMainActivity : AppCompatActivity() {
    private lateinit var scanWifiButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_main)

        // Set up the action bar with a back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true);
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        scanWifiButton = findViewById(R.id.scanWifiButton)

        // Gemma Button Click Listener
        scanWifiButton.setOnClickListener {
            val intent = Intent(this, WiFiScanActivity::class.java)
            startActivity(intent)
        }
    }

    // Insert here all the methods to show the position retrieved with the query on the supabase

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