package pt.ipt.mystreaks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.ipt.mystreaks.databinding.ItemStreakBinding

// Este Adapter recebe uma função (onStreakCheckChanged) que vai avisar a MainActivity sempre que clicares numa checkbox
class StreakAdapter(
    private val onStreakCheckChanged: (Streak, Boolean) -> Unit
) : ListAdapter<Streak, StreakAdapter.StreakViewHolder>(StreakComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreakViewHolder {
        // Usa o ViewBinding para ligar o layout xml (item_streak.xml) ao código
        val binding = ItemStreakBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StreakViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StreakViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
    }

    inner class StreakViewHolder(private val binding: ItemStreakBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(streak: Streak) {
            binding.tvActivityName.text = streak.name
            binding.tvType.text = streak.type
            binding.tvStreakCount.text = "🔥 ${streak.count}"

            // Removemos o listener temporariamente para evitar falsos positivos quando a lista faz scroll
            binding.cbCompleted.setOnCheckedChangeListener(null)

            // Define se a checkbox deve aparecer marcada ou desmarcada com base na base de dados
            binding.cbCompleted.isChecked = streak.isCompleted

            // Fica à escuta de quando o utilizador clica na checkbox
            binding.cbCompleted.setOnCheckedChangeListener { _, isChecked ->
                onStreakCheckChanged(streak, isChecked)
            }
        }
    }

    // O Comparator ajuda o RecyclerView a saber exatamente que itens mudaram para atualizar a lista com uma animação suave
    class StreakComparator : DiffUtil.ItemCallback<Streak>() {
        override fun areItemsTheSame(oldItem: Streak, newItem: Streak): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Streak, newItem: Streak): Boolean {
            return oldItem == newItem
        }
    }
}