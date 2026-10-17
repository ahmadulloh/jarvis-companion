package com.jarvis.companion.domain

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.companion.R

class DomainGridAdapter(
    private var domains: List<JarvisDomain>,
    private var selectedDomainId: String,
    private val onDomainSelected: (JarvisDomain) -> Unit
) : RecyclerView.Adapter<DomainGridAdapter.ViewHolder>() {

    fun updateData(newDomains: List<JarvisDomain>, newSelectedId: String) {
        this.domains = newDomains
        this.selectedDomainId = newSelectedId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_domain_grid_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val domain = domains[position]
        holder.bind(domain, domain.id.equals(selectedDomainId, ignoreCase = true))
    }

    override fun getItemCount(): Int = domains.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView.findViewById(R.id.cardDomainContainer)
        private val tvIcon: TextView = itemView.findViewById(R.id.tvDomainCardIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvDomainCardTitle)

        fun bind(domain: JarvisDomain, isSelected: Boolean) {
            tvIcon.text = domain.icon
            tvTitle.text = formatDomainGridTitle(domain)

            val density = itemView.resources.displayMetrics.density

            if (isSelected) {
                val activeBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 18f * density
                    setColor(Color.parseColor("#F0FDF4")) // Soft green tint
                    setStroke((2f * density).toInt(), domain.primaryColor)
                }
                container.background = activeBg
                tvTitle.setTextColor(domain.primaryColor)
            } else {
                val normalBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 18f * density
                    setColor(Color.parseColor("#F8FAFC"))
                    setStroke((1.2f * density).toInt(), Color.parseColor("#E2E8F0"))
                }
                container.background = normalBg
                tvTitle.setTextColor(Color.parseColor("#0F172A"))
            }

            container.setOnClickListener {
                onDomainSelected(domain)
            }
        }

        private fun formatDomainGridTitle(domain: JarvisDomain): String {
            return when (domain.id.uppercase()) {
                "ASOSIY" -> "Asosiy"
                "TALABA" -> "Talaba"
                "OQITUVCHI", "O'QITUVCHI" -> "O'qituvchi"
                "TIL" -> "Til O'rganish"
                "DOKON", "DO'KON" -> "Do'kon"
                "ONLAYN_SAVDO" -> "Onlayn Savdo"
                "KAFE" -> "Kafe & Restoran"
                "SALON" -> "Go'zallik Saloni"
                "HAYDOVCHI" -> "Haydovchi (Taxi)"
                "AVTOUSTA" -> "Avtoservis"
                "USTA" -> "Qurilish / Ta'mir"
                "MULK" -> "Ko'chmas Mulk"
                "FRILANSER" -> "Frilanser"
                "MENEJER" -> "Menejment"
                "HR" -> "HR & Xodimlar"
                "YURIST" -> "Yurist & Soliq"
                "TADBIRKOR" -> "Tadbirkorlik"
                "DIN" -> "Din & Qur'on"
                "DASTURCHI" -> "Dasturchi"
                "MOLIYA" -> "Moliya & Audit"
                "UY" -> "Aqlli Uy"
                "SALOMATLIK" -> "Salomatlik"
                "IT_ADMIN" -> "IT-Admin"
                "KONTENT" -> "Media / Kontent"
                "GAMER" -> "Geymer"
                "FERMER" -> "Agro / Fermer"
                "SPORT" -> "Sport & Fitnes"
                "SAYOHAT" -> "Sayohat"
                "OILA" -> "Oila"
                "KEKSA" -> "Keksa Parvarishi"
                "HAYVON" -> "Uy Hayvonlari"
                else -> domain.titleUz.split(" / ", " · ").firstOrNull()?.trim() ?: domain.titleUz
            }
        }

    }
}
