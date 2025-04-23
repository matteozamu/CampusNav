package com.tech4all.idt

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class CreateEventActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.event_creation)

        // Find the EditText fields and the button
        val eventCreatorNameEditText = findViewById<EditText>(R.id.eventCreatorName)
        val eventDateEditText = findViewById<EditText>(R.id.eventDate)
        val eventTimeEditText = findViewById<EditText>(R.id.eventTime)
        val eventTypeEditText = findViewById<EditText>(R.id.eventType)
        val createEventButton = findViewById<Button>(R.id.createEventButton)

        // Set a click listener for the button
        createEventButton.setOnClickListener {
            // Get the data from the EditText fields
            val eventCreatorName = eventCreatorNameEditText.text.toString()
            val eventDate = eventDateEditText.text.toString()
            val eventTime = eventTimeEditText.text.toString()
            val eventType = eventTypeEditText.text.toString()

            // Validate the data (you can add more validation if needed)
            if (eventCreatorName.isEmpty() || eventDate.isEmpty() || eventTime.isEmpty() || eventType.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create an Event object
            //the correct number of params is now passed
            val newEvent = Event(null,eventCreatorName, eventDate, eventTime, eventType)

            // Save the event to Supabase
            lifecycleScope.launch {
                try {
                    SupabaseHelper.insertEvent(newEvent)
                    Toast.makeText(this@CreateEventActivity, "Event created successfully", Toast.LENGTH_SHORT).show()
                    // Optionally, you could finish() here to close this activity after saving
                    finish()
                } catch (e: Exception) {
                    Log.e("CreateEventActivity", "Error saving event to Supabase", e)
                    Toast.makeText(this@CreateEventActivity, "Error saving event", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}