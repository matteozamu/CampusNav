package com.tech4all.idt.poliBuddy

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tech4all.idt.Event
import com.tech4all.idt.R
import com.tech4all.idt.SupabaseHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


/**
 * CreateEventActivity is an activity where users can create a new event by filling in
 * details such as event creator's name, event date, event time, and event type.
 * This activity also includes date and time pickers for easier input.
 */
class CreateEventActivity : AppCompatActivity() {

    // Declaring UI components and variables
    private lateinit var eventDateEditText: EditText
    private lateinit var eventTimeEditText: EditText
    private val calendar = Calendar.getInstance() // Calendar instance to manage date and time selections

    /**
     * onCreate is the entry point for this activity. It sets up the layout, initializes
     * the UI components, and handles user interactions such as setting click listeners for buttons.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_creation) // Setting the content view to the event creation layout

        // Set up the action bar with a back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true);
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }



        // Find the EditText fields and the button by their ID from the layout
        val eventCreatorNameEditText = findViewById<EditText>(R.id.eventCreatorName)
        eventDateEditText = findViewById(R.id.eventDate)
        eventTimeEditText = findViewById(R.id.eventTime)
        val eventTypeEditText = findViewById<EditText>(R.id.eventType)
        val createEventButton = findViewById<Button>(R.id.createEventButton)

        // Set a click listener for the 'Create Event' button
        createEventButton.setOnClickListener {
            // Retrieve the values from the EditText fields
            val eventCreatorName = eventCreatorNameEditText.text.toString()
            val eventDate = eventDateEditText.text.toString()
            val eventTime = eventTimeEditText.text.toString()
            val eventType = eventTypeEditText.text.toString()

            // Validate that all fields are filled in
            if (eventCreatorName.isEmpty() || eventDate.isEmpty() || eventTime.isEmpty() || eventType.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener // Exit if validation fails
            }

            // Create an Event object with the data from the form fields
            val newEvent = Event(null, eventCreatorName, eventDate, eventTime, eventType)

            // Save the event to Supabase asynchronously using a coroutine
            lifecycleScope.launch {
                try {
                    // Call the Supabase helper to insert the event into the database
                    SupabaseHelper.insertEvent(newEvent)
                    Toast.makeText(this@CreateEventActivity, "Event created successfully", Toast.LENGTH_SHORT).show()
                    finish() // Optionally finish the activity after event is saved
                } catch (e: Exception) {
                    // Log and show error if event creation fails
                    Log.e("CreateEventActivity", "Error saving event to Supabase", e)
                    Toast.makeText(this@CreateEventActivity, "Error saving event", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Set up a date picker dialog when the user clicks on the event date EditText
        eventDateEditText.setOnClickListener {
            showDatePickerDialog() // Show the date picker dialog
        }

        // Set up a time picker dialog when the user clicks on the event time EditText
        eventTimeEditText.setOnClickListener {
            showTimePickerDialog() // Show the time picker dialog
        }
    }

    /**
     * Show the date picker dialog for selecting the event date.
     */
    private fun showDatePickerDialog() {
        // Create a DatePickerDialog instance and show it
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, monthOfYear, dayOfMonth ->
                calendar.set(year, monthOfYear, dayOfMonth) // Set the selected date to the calendar
                updateDateInView() // Update the date in the EditText field
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show() // Display the dialog
    }

    /**
     * Update the date field in the view with the selected date from the calendar.
     */
    private fun updateDateInView() {
        val myFormat = "yyyy-MM-dd" // Define the date format
        val sdf = SimpleDateFormat(myFormat, Locale.US) // Initialize SimpleDateFormat
        eventDateEditText.setText(sdf.format(calendar.time)) // Set the formatted date to the EditText field
    }

    /**
     * Show the time picker dialog for selecting the event time.
     */
    private fun showTimePickerDialog() {
        // Create a TimePickerDialog instance and show it
        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay) // Set the selected hour
                calendar.set(Calendar.MINUTE, minute) // Set the selected minute
                updateTimeInView() // Update the time in the EditText field
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true // Use 24-hour format
        )
        timePickerDialog.show() // Display the dialog
    }

    /**
     * Update the time field in the view with the selected time from the calendar.
     */
    private fun updateTimeInView() {
        val myFormat = "HH:mm" // Define the time format (24-hour)
        val sdf = SimpleDateFormat(myFormat, Locale.US) // Initialize SimpleDateFormat
        eventTimeEditText.setText(sdf.format(calendar.time)) // Set the formatted time to the EditText field
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
