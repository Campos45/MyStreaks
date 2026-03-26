package pt.ipt.mystreaks

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class TagAdapter(
    private val onDeleteClick: (Tag) -> Unit,
    private val onTagClick: (Tag) -> Unit // Para quando o utilizador quiser selecionar esta tag para uma Tarefa
) : ListAdapter<Tag, TagAdapter.TagViewHolder>(TagDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tag, parent, false)
        return TagViewHolder(view)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTagName: TextView = itemView.findViewById(R.id.tvTagName)
        private val ivDeleteTag: ImageView = itemView.findViewById(R.id.ivDeleteTag)
        private val cardTag: MaterialCardView = itemView.findViewById(R.id.cardTag)

        fun bind(tag: Tag) {
            tvTagName.text = tag.name

            // MAGIA: Transforma o texto Hexadecimal da base de dados numa cor real no ecrã!
            try {
                cardTag.setCardBackgroundColor(Color.parseColor(tag.color))
            } catch (e: Exception) {
                cardTag.setCardBackgroundColor(Color.GRAY) // Cor de segurança caso haja erro na base de dados
            }

            // Ações de clique
            ivDeleteTag.setOnClickListener { onDeleteClick(tag) }
            cardTag.setOnClickListener { onTagClick(tag) }
        }
    }

    class TagDiffCallback : DiffUtil.ItemCallback<Tag>() {
        override fun areItemsTheSame(oldItem: Tag, newItem: Tag): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Tag, newItem: Tag): Boolean = oldItem == newItem
    }
}