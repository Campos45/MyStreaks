package pt.ipt.mystreaks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

data class Medal(val name: String, val description: String, val emoji: String, val isUnlocked: Boolean)

class MedalAdapter(private val medals: List<Medal>) : RecyclerView.Adapter<MedalAdapter.MedalViewHolder>() {

    class MedalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardMedal: MaterialCardView = view.findViewById(R.id.cardMedal)
        val tvEmoji: TextView = view.findViewById(R.id.tvEmoji)
        val tvMedalName: TextView = view.findViewById(R.id.tvMedalName)
        val tvMedalDesc: TextView = view.findViewById(R.id.tvMedalDesc)
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
            holder.cardMedal.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF8E1")) // Dourado claro
        } else {
            // Medalha Bloqueada (Cinzento e transparente)
            holder.cardMedal.alpha = 0.4f
            holder.cardMedal.setCardBackgroundColor(android.graphics.Color.WHITE)
            holder.tvEmoji.text = "🔒"
        }
    }

    override fun getItemCount() = medals.size
}