package com.tech4all.idt.poliBuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tech4all.idt.Event
import com.tech4all.idt.R

class EventAdapter(private val events: List<Event>) :
    RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val creatorName: TextView = itemView.findViewById(R.id.eventCreatorItem)
        val date: TextView = itemView.findViewById(R.id.eventDateItem)
        val time: TextView = itemView.findViewById(R.id.eventTimeItem)
        val type: TextView = itemView.findViewById(R.id.eventTypeItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.creatorName.text = event.creatorName
        holder.date.text = event.date
        holder.time.text = event.time
        holder.type.text = event.type
    }

    override fun getItemCount(): Int = events.size
}
