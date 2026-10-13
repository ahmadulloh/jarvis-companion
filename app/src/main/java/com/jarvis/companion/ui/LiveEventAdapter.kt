package com.jarvis.companion.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.jarvis.companion.R

class LiveEventAdapter(
    private val items: MutableList<LiveEvent> = mutableListOf()
) : RecyclerView.Adapter<LiveEventAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardContainer)
        val tvTitle: TextView = view.findViewById(R.id.tvEventTitle)
        val tvTime: TextView = view.findViewById(R.id.tvEventTime)
        val tvContent: TextView = view.findViewById(R.id.tvEventContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_live_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTime.text = item.time
        holder.tvContent.text = item.content

        when (item.type) {
            EventType.USER -> {
                holder.tvTitle.text = "[Siz] 👤"
                holder.tvTitle.setTextColor(Color.parseColor("#00E5FF"))
                holder.card.setStrokeColor(Color.parseColor("#00838F"))
                holder.card.setCardBackgroundColor(Color.parseColor("#102A43"))
            }
            EventType.JARVIS -> {
                holder.tvTitle.text = "[JARVIS] 🤖"
                holder.tvTitle.setTextColor(Color.parseColor("#00E676"))
                holder.card.setStrokeColor(Color.parseColor("#00E5FF"))
                holder.card.setCardBackgroundColor(Color.parseColor("#131B2A"))
            }
            EventType.ACTION -> {
                holder.tvTitle.text = "⚡ [AMAL / PLAGIN] " + item.title
                holder.tvTitle.setTextColor(Color.parseColor("#FFB300"))
                holder.card.setStrokeColor(Color.parseColor("#FFB300"))
                holder.card.setCardBackgroundColor(Color.parseColor("#2A2410"))
            }
            EventType.SYSTEM -> {
                holder.tvTitle.text = "ℹ️ [TIZIM]"
                holder.tvTitle.setTextColor(Color.parseColor("#80DEEA"))
                holder.card.setStrokeColor(Color.parseColor("#00838F"))
                holder.card.setCardBackgroundColor(Color.parseColor("#131B2A"))
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun addEvent(event: LiveEvent) {
        items.add(event)
        notifyItemInserted(items.size - 1)
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }
}
