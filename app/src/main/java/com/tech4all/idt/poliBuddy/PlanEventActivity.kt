package com.tech4all.idt.poliBuddy

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.tech4all.idt.R
import com.tech4all.idt.SupabaseHelper
import kotlinx.coroutines.launch
import android.view.LayoutInflater


class PlanEventActivity : AppCompatActivity () {
    private lateinit var createNewEventButton: Button;
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EventAdapter
    private lateinit var refreshButton: ImageButton
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plan_event)

        // Set up the action bar with a back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true);
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        refreshButton = findViewById(R.id.refreshButton)
        refreshButton.setOnClickListener {
            refreshEvents()
        }

        createNewEventButton = findViewById(R.id.createNewEventButton)
        createNewEventButton.setOnClickListener {
            val intent = Intent(this, CreateEventActivity::class.java)
            startActivity(intent)
        }

        recyclerView = findViewById(R.id.eventRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        refreshEvents();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun refreshEvents() {
        lifecycleScope.launch {
            val events = SupabaseHelper.getUpcomingEvents()
            adapter = EventAdapter(events, this@PlanEventActivity)
            recyclerView.adapter = adapter
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

