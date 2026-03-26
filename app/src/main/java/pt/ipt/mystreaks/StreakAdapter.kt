package pt.ipt.mystreaks

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zerobranch.layout.SwipeLayout

class StreakAdapter(
    private val onStreakCheckChanged: (Streak, Boolean) -> Unit,
    private val onHistoryClicked: (Streak) -> Unit,
    private val onEditClicked: (Streak) -> Unit,
    private val onArchiveClicked: (Streak) -> Unit,
    private val onDeleteClicked: (Streak) -> Unit
) : ListAdapter<Streak, StreakAdapter.StreakViewHolder>(StreakDiffCallback()) {

    private var currentlyOpenLayout: SwipeLayout? = null
    private var tagsList: List<Tag> = emptyList()

    fun setTags(tags: List<Tag>) {
        this.tagsList = tags
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreakViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_streak, parent, false)
        return StreakViewHolder(view)
    }

    override fun onBindViewHolder(holder: StreakViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StreakViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvActivityName: TextView = itemView.findViewById(R.id.tvActivityName)
        private val tvType: TextView = itemView.findViewById(R.id.tvType)
        private val tvTag: TextView = itemView.findViewById(R.id.tvTag)
        private val tvStreakCount: TextView = itemView.findViewById(R.id.tvStreakCount)
        private val cbCompleted: CheckBox = itemView.findViewById(R.id.cbCompleted)
        private val ivEdit: ImageView = itemView.findViewById(R.id.ivEdit)
        private val ivHistory: ImageView = itemView.findViewById(R.id.ivHistory)
        private val swipeLayout: SwipeLayout = itemView.findViewById(R.id.swipe_layout)
        private val botoesEscondidos: View = itemView.findViewById(R.id.botoes_escondidos)
        private val btnArchive: ImageView = itemView.findViewById(R.id.btnArchive)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)

        fun bind(streak: Streak) {
            tvActivityName.text = streak.name
            tvType.text = when (streak.type) { "S" -> "Semanal"; "M" -> "Mensal"; else -> "Diária" }
            tvStreakCount.text = streak.count.toString()

            // Lógica de cores "Blindada"
            if (!streak.tag.isNullOrBlank()) {
                tvTag.visibility = View.VISIBLE
                val cleanTagName = streak.tag?.trim() ?: ""
                tvTag.text = cleanTagName

                // Procurar a cor com máxima precisão
                val tagEncontrada = tagsList.find {
                    it.name.trim().equals(cleanTagName, ignoreCase = true)
                }

                val tagColorHex = tagEncontrada?.color ?: "#757575"

                try {
                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 32f
                        setColor(Color.parseColor(tagColorHex))
                    }
                    tvTag.background = shape
                    tvTag.setTextColor(Color.WHITE)
                } catch (e: Exception) {
                    tvTag.setBackgroundColor(Color.GRAY)
                }
            } else {
                tvTag.visibility = View.GONE
            }

            cbCompleted.setOnCheckedChangeListener(null)
            cbCompleted.isChecked = streak.isCompleted
            cbCompleted.setOnCheckedChangeListener { _, isChecked ->
                currentlyOpenLayout?.close(true)
                onStreakCheckChanged(streak, isChecked)
            }

            ivEdit.setOnClickListener { currentlyOpenLayout?.close(true); onEditClicked(streak) }
            ivHistory.setOnClickListener { currentlyOpenLayout?.close(true); onHistoryClicked(streak) }
            btnArchive.setOnClickListener { swipeLayout.close(true); onArchiveClicked(streak) }
            btnDelete.setOnClickListener { swipeLayout.close(true); onDeleteClicked(streak) }

            swipeLayout.setOnActionsListener(object : SwipeLayout.SwipeActionsListener {
                override fun onOpen(direction: Int, isContinuous: Boolean) {
                    if (currentlyOpenLayout != swipeLayout) {
                        currentlyOpenLayout?.close(true)
                        currentlyOpenLayout = swipeLayout
                    }
                }
                override fun onClose() {
                    if (currentlyOpenLayout == swipeLayout) currentlyOpenLayout = null
                }
            })

            // --- FORÇA BRUTA PARA PINTAR A TAG ---
            if (!streak.tag.isNullOrBlank()) {
                tvTag.visibility = View.VISIBLE
                val cleanName = streak.tag!!.trim()
                tvTag.text = cleanName

                // Removemos TODOS os espaços (até os do meio) e ignoramos maiúsculas para o Match não falhar NUNCA
                val tagEncontrada = tagsList.find {
                    it.name.replace("\\s+".toRegex(), "").equals(cleanName.replace("\\s+".toRegex(), ""), ignoreCase = true)
                }

                // Se não encontrar, ou se for a tag automática, vai ser #757575.
                val finalHex = tagEncontrada?.color ?: "#757575"

                try {
                    val colorInt = android.graphics.Color.parseColor(finalHex)

                    // 1. Obriga o Fundo a mudar (substitui regras do XML)
                    val shape = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 32f
                        setColor(colorInt)
                    }
                    tvTag.background = shape

                    // 2. Tira qualquer "Tint" que o XML te esteja a forçar
                    androidx.core.view.ViewCompat.setBackgroundTintList(tvTag, android.content.res.ColorStateList.valueOf(colorInt))

                    // 3. Texto branco para ler bem
                    tvTag.setTextColor(android.graphics.Color.WHITE)

                } catch (e: Exception) {
                    tvTag.setBackgroundColor(android.graphics.Color.RED) // Se der erro fica vermelho choque para sabermos
                }
            } else {
                tvTag.visibility = View.GONE
            }
        }

    }

    class StreakDiffCallback : DiffUtil.ItemCallback<Streak>() {
        override fun areItemsTheSame(oldItem: Streak, newItem: Streak): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Streak, newItem: Streak): Boolean = oldItem == newItem
    }
}