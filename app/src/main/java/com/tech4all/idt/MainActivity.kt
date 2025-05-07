package com.tech4all.idt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.tech4all.idt.gemma3.GemmaActivity
import com.tech4all.idt.poliBuddy.CreateEventActivity
import com.tech4all.idt.poliBuddy.PoliBuddyActivity
import com.tech4all.idt.yolov8tflite.CameraActivity


class MainActivity : AppCompatActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var startCameraButton: Button // Nuovo bottone per avviare CameraActivity
    private lateinit var gemmaButton: Button
    private lateinit var newEventButton: Button
    private lateinit var wifiButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        newEventButton = findViewById(R.id.newEventButton) //find the new button

        // New Event Button Click Listener
        newEventButton.setOnClickListener {
            val intent = Intent(this, PoliBuddyActivity::class.java)
            startActivity(intent)
        }

        // Initialize startCameraButton here by finding it by id
        startCameraButton = findViewById(R.id.startCameraButton)

        // Now you can safely use startCameraButton
        startCameraButton.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        gemmaButton = findViewById(R.id.gemmaButton)

        // Gemma Button Click Listener
        gemmaButton.setOnClickListener {
            val intent = Intent(this, GemmaActivity::class.java)
            startActivity(intent)
        }

        wifiButton = findViewById(R.id.wifiButton)

        // Gemma Button Click Listener
        wifiButton.setOnClickListener {
            val intent = Intent(this, WiFiActivity::class.java)
            startActivity(intent)
        }
    }

}