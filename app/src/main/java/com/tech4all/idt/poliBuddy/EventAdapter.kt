package com.tech4all.idt.poliBuddy

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.tech4all.idt.Event
import com.tech4all.idt.R
import com.tech4all.idt.SupabaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EventAdapter(
    private val events: List<Event>,
    private val context: Context
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val creatorName: TextView = itemView.findViewById(R.id.eventCreatorItem)
        val date: TextView = itemView.findViewById(R.id.eventDateItem)
        val time: TextView = itemView.findViewById(R.id.eventTimeItem)
        val type: TextView = itemView.findViewById(R.id.eventTypeItem)
        val joinButton: Button = itemView.findViewById(R.id.joinEventButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]

        holder.creatorName.text = event.creatorName
        holder.date.text = event.date
        holder.time.text = event.time
        holder.type.text = event.type

        holder.joinButton.setOnClickListener {
            showUsernameDialog(context) { username ->
                val eventId = event.id ?: return@showUsernameDialog

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        SupabaseHelper.insertJoin(eventId, username)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Joined event as $username", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to join event", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun showUsernameDialog(context: Context, onUsernameEntered: (String) -> Unit) {
        val input = EditText(context)
        input.hint = "Enter your username"

        AlertDialog.Builder(context)
            .setTitle("Join Event")
            .setView(input)
            .setPositiveButton("Join") { _, _ ->
                val username = input.text.toString().trim()
                if (username.isNotEmpty()) {
                    onUsernameEntered(username)
                } else {
                    Toast.makeText(context, "Username cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun getItemCount(): Int = events.size
}
