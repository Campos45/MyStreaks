package pt.ipt.mystreaks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.ipt.mystreaks.databinding.ItemStreakBinding

class StreakAdapter(
    private val onStreakCheckChanged: (Streak, Boolean) -> Unit,
    private val onHistoryClicked: (Streak) -> Unit,
    private val onEditClicked: (Streak) -> Unit // NOVO: Botão Lápis
) : ListAdapter<Streak, StreakAdapter.StreakViewHolder>(StreakComparator()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreakViewHolder {
        val binding = ItemStreakBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StreakViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StreakViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StreakViewHolder(private val binding: ItemStreakBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(streak: Streak) {
            binding.tvActivityName.text = streak.name
            binding.tvStreakCount.text = streak.count.toString()

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

            // Mostrar a Etiqueta (Tag)
            if (!streak.tag.isNullOrBlank()) {
                binding.tvTag.visibility = View.VISIBLE
                binding.tvTag.text = streak.tag
            } else {
                binding.tvTag.visibility = View.GONE
            }

            binding.cbCompleted.setOnCheckedChangeListener(null)
            binding.cbCompleted.isChecked = streak.isCompleted

            binding.cbCompleted.setOnCheckedChangeListener { _, isChecked ->
                onStreakCheckChanged(streak, isChecked)
            }

            binding.ivHistory.setOnClickListener { onHistoryClicked(streak) }
            binding.ivEdit.setOnClickListener { onEditClicked(streak) } // NOVO: Evento de Editar
        }
    }

    class StreakComparator : DiffUtil.ItemCallback<Streak>() {
        override fun areItemsTheSame(oldItem: Streak, newItem: Streak) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Streak, newItem: Streak) = oldItem == newItem
    }
}