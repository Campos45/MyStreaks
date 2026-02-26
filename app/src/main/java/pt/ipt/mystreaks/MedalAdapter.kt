package pt.ipt.mystreaks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

// 1. Adicionámos a variável 'source' à classe Medalha
data class Medal(val name: String, val description: String, val emoji: String, val isUnlocked: Boolean, val source: String? = null)

class MedalAdapter(private val medals: List<Medal>) : RecyclerView.Adapter<MedalAdapter.MedalViewHolder>() {

    class MedalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardMedal: MaterialCardView = view.findViewById(R.id.cardMedal)
        val tvEmoji: TextView = view.findViewById(R.id.tvEmoji)
        val tvMedalName: TextView = view.findViewById(R.id.tvMedalName)
        val tvMedalDesc: TextView = view.findViewById(R.id.tvMedalDesc)
        val tvMedalSource: TextView = view.findViewById(R.id.tvMedalSource) // 2. Ligar o novo texto
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_medal, parent, false)
        return MedalViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedalViewHolder, position: Int) {
        val medal = medals[position]
        holder.tvEmoji.text = medal.emoji
        holder.tvMedalName.text = medal.name
        holder.tvMedalDesc.text = medal.description

        if (medal.isUnlocked) {
            holder.cardMedal.alpha = 1.0f
            holder.cardMedal.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF8E1"))

            // 3. Se estiver desbloqueado e tiver origem, mostra a origem!
            if (medal.source != null) {
                holder.tvMedalSource.visibility = View.VISIBLE
                holder.tvMedalSource.text = "Alcançado com:\n${medal.source}"
            } else {
                holder.tvMedalSource.visibility = View.GONE
            }

        } else {
            holder.cardMedal.alpha = 0.4f
            holder.cardMedal.setCardBackgroundColor(android.graphics.Color.WHITE)
            holder.tvEmoji.text = "🔒"
            holder.tvMedalSource.visibility = View.GONE
        }
    }

    override fun getItemCount() = medals.size
}