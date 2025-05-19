package com.tech4all.idt.poliBuddy

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.*
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

class PoliBuddyActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var dynamicContainer: FrameLayout

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_polibuddy)

        tabLayout = findViewById(R.id.tabLayout)
        dynamicContainer = findViewById(R.id.dynamicContainer)

        tabLayout.addTab(tabLayout.newTab().setText("Help Request"))
        tabLayout.addTab(tabLayout.newTab().setText("Events"))

        // Show the "Send an Help Request" layout initially
        inflateDynamicLayout(R.layout.layout_help_request_tab)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> inflateDynamicLayout(R.layout.layout_help_request_tab)
                    1 -> inflateDynamicLayout(R.layout.layout_events_tab)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Action bar back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun inflateDynamicLayout(layoutId: Int) {
        dynamicContainer.removeAllViews()
        val view = LayoutInflater.from(this).inflate(layoutId, dynamicContainer, false)
        dynamicContainer.addView(view)

        when (layoutId) {
            R.layout.layout_help_request_tab -> {
                val helpButton = view.findViewById<LinearLayout>(R.id.help_button)
                helpButton.setOnClickListener {
                    // Gestisci richiesta di aiuto
                    Toast.makeText(this, "Help request sent", Toast.LENGTH_SHORT).show()
                }
            }

            R.layout.layout_events_tab -> {
                val createNewEventButton = view.findViewById<Button>(R.id.createNewEventButton)
                val refreshButton = view.findViewById<ImageButton>(R.id.refreshButton)
                val recyclerView = view.findViewById<RecyclerView>(R.id.eventRecyclerView)

                createNewEventButton.setOnClickListener {
                    val intent = Intent(this, CreateEventActivity::class.java)
                    startActivity(intent)
                }

                refreshButton.setOnClickListener {
                    refreshEvents(recyclerView)
                }

                recyclerView.layoutManager = LinearLayoutManager(this)
                refreshEvents(recyclerView)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun refreshEvents(recyclerView: RecyclerView) {
        lifecycleScope.launch {
            val events = SupabaseHelper.getUpcomingEvents()
            val adapter = EventAdapter(events, this@PoliBuddyActivity)
            recyclerView.adapter = adapter
        }
    }

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
