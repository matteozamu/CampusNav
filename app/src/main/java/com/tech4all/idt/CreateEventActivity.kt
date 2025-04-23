package com.tech4all.idt

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateEventActivity : AppCompatActivity() {
    private lateinit var eventDateEditText: EditText
    private lateinit var eventTimeEditText: EditText
    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.event_creation)

        // Find the EditText fields and the button
        val eventCreatorNameEditText = findViewById<EditText>(R.id.eventCreatorName)
        eventDateEditText = findViewById(R.id.eventDate)
        eventTimeEditText = findViewById(R.id.eventTime)
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
            val newEvent = Event(null, eventCreatorName, eventDate, eventTime, eventType)

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
        // Set up date picker dialog
        eventDateEditText.setOnClickListener {
            showDatePickerDialog()
        }

        // Set up time picker dialog
        eventTimeEditText.setOnClickListener {
            showTimePickerDialog()
        }
    }

    private fun showDatePickerDialog() {
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, monthOfYear, dayOfMonth ->
                calendar.set(year, monthOfYear, dayOfMonth)
                updateDateInView()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun updateDateInView() {
        val myFormat = "yyyy-MM-dd"
        val sdf = SimpleDateFormat(myFormat, Locale.US)
        eventDateEditText.setText(sdf.format(calendar.time))
    }

    private fun showTimePickerDialog() {
        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                updateTimeInView()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    private fun updateTimeInView() {
        val myFormat = "HH:mm"
        val sdf = SimpleDateFormat(myFormat, Locale.US)
        eventTimeEditText.setText(sdf.format(calendar.time))
    }
}