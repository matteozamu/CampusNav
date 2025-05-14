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


class PoliBuddyActivity : AppCompatActivity () {
    private lateinit var createNewEventButton: Button;
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EventAdapter
    private lateinit var refreshButton: ImageButton
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_polibuddy)

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val dynamicContainer = findViewById<FrameLayout>(R.id.dynamicContainer)


        // Aggiungi i due tab: "Helped" e "Helper"
        tabLayout.addTab(tabLayout.newTab().setText("Helped"))
        tabLayout.addTab(tabLayout.newTab().setText("Helper"))

        // Mostra il layout iniziale (Helped)
        inflateDynamicLayout(R.layout.layout_helped)

        // Listener per cambiare layout quando si seleziona un tab
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> inflateDynamicLayout(R.layout.layout_helped)
                    1 -> inflateDynamicLayout(R.layout.layout_helper)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })



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
            adapter = EventAdapter(events, this@PoliBuddyActivity)
            recyclerView.adapter = adapter
        }
    }

    private fun inflateDynamicLayout(layoutId: Int) {
        val container = findViewById<FrameLayout>(R.id.dynamicContainer)
        container.removeAllViews()
        val view = LayoutInflater.from(this).inflate(layoutId, container, false)
        container.addView(view)
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

