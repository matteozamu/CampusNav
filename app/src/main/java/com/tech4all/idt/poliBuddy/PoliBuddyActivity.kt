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
    private lateinit var planNewEventButton: Button;

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_polibuddy)

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val dynamicContainer = findViewById<FrameLayout>(R.id.dynamicContainer)

        planNewEventButton = findViewById(R.id.planNewEventButton)
        planNewEventButton.setOnClickListener {
            val intent = Intent(this, PlanEventActivity::class.java)
            startActivity(intent)
        }

        tabLayout.addTab(tabLayout.newTab().setText("Send an Help Request"))
        tabLayout.addTab(tabLayout.newTab().setText("Answer to an Help Request"))

        // Show the "Send an Help Request" layout initially
        inflateDynamicLayout(R.layout.layout_helped)

        // Handle tab selection
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

