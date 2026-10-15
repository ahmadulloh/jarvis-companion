package com.jarvis.companion.domain

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.companion.R

class DomainAdapter(
    private val domains: List<JarvisDomain>,
    private var activeDomainId: String,
    private val onDomainSelected: (JarvisDomain) -> Unit
) : RecyclerView.Adapter<DomainAdapter.DomainViewHolder>() {

    fun updateActiveDomain(newId: String) {
        activeDomainId = newId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DomainViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_domain_selector, parent, false)
        return DomainViewHolder(view)
    }

    override fun onBindViewHolder(holder: DomainViewHolder, position: Int) {
        val domain = domains[position]
        holder.bind(domain, position, domain.id.equals(activeDomainId, ignoreCase = true), onDomainSelected)
    }

    override fun getItemCount(): Int = domains.size

    class DomainViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: View = itemView.findViewById(R.id.containerDomainItem)
        private val iconCircle: FrameLayout = itemView.findViewById(R.id.layoutDomainIconCircle)
        private val tvIcon: TextView = itemView.findViewById(R.id.tvDomainIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvDomainTitle)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvDomainCategory)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvDomainBadge)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDomainDescription)

        fun bind(domain: JarvisDomain, index: Int, isActive: Boolean, onSelect: (JarvisDomain) -> Unit) {
            tvIcon.text = domain.icon
            tvTitle.text = "${index + 1}. ${domain.titleUz}"
            tvCategory.text = domain.category
            tvCategory.setTextColor(domain.primaryColor)
            tvDescription.text = domain.description

            if (isActive) {
                tvBadge.text = "FAOL ✓"
                tvBadge.backgroundTintList = ColorStateList.valueOf(domain.primaryColor)
                tvBadge.setTextColor(Color.parseColor("#0F172A"))
                container.alpha = 1.0f
            } else {
                tvBadge.text = "TANLASH"
                tvBadge.backgroundTintList = null
                tvBadge.setTextColor(Color.parseColor("#80D8FF"))
                container.alpha = 0.85f
            }

            container.setOnClickListener {
                onSelect(domain)
            }
        }
    }
}
