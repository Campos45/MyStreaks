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

            // Coloca apenas o número (o emoji de fogo já está no XML)
            binding.tvStreakCount.text = streak.count.toString()

            // Define o texto e as cores da etiqueta consoante a frequência
            val context = binding.root.context
            when (streak.type) {
                "D" -> {
                    binding.tvType.text = "Diária"
                    binding.tvType.setTextColor(context.getColor(R.color.badge_daily_text))
                    binding.tvType.setBackgroundColor(context.getColor(R.color.badge_daily))
                }
                "S" -> {
                    binding.tvType.text = "Semanal"
                    binding.tvType.setTextColor(context.getColor(R.color.badge_weekly_text))
                    binding.tvType.setBackgroundColor(context.getColor(R.color.badge_weekly))
                }
                "M" -> {
                    binding.tvType.text = "Mensal"
                    binding.tvType.setTextColor(context.getColor(R.color.badge_monthly_text))
                    binding.tvType.setBackgroundColor(context.getColor(R.color.badge_monthly))
                }
            }

            binding.cbCompleted.setOnCheckedChangeListener(null)
            binding.cbCompleted.isChecked = streak.isCompleted

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