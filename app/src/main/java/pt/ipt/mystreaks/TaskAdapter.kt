package pt.ipt.mystreaks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pt.ipt.mystreaks.databinding.ItemTaskBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private val onTaskUpdate: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskComparator()) {

    // Guarda a memória de quais cartões estão expandidos (abertos)
    private val expandedTasks = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(task: Task) {
            binding.tvTaskName.text = task.name

            // Previne loops de clique ao fazer scroll
            binding.cbTaskCompleted.setOnCheckedChangeListener(null)
            binding.cbTaskCompleted.isChecked = task.isCompleted

            // Mostrar data se concluída
            if (task.isCompleted && task.completionDate != null) {
                binding.tvCompletionDate.visibility = View.VISIBLE
                val sdf = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
                binding.tvCompletionDate.text = "Concluída em: ${sdf.format(Date(task.completionDate!!))}"
            } else {
                binding.tvCompletionDate.visibility = View.GONE
            }

            // Limpa as visualizações antigas
            binding.layoutSubTasks.removeAllViews()

            if (task.subTasks.isNotEmpty()) {
                binding.ivExpand.visibility = View.VISIBLE
                val isExpanded = expandedTasks.contains(task.id)
                binding.layoutSubTasks.visibility = if (isExpanded) View.VISIBLE else View.GONE

                // Roda a seta
                binding.ivExpand.rotation = if (isExpanded) 180f else 0f

                binding.ivExpand.setOnClickListener {
                    if (expandedTasks.contains(task.id)) expandedTasks.remove(task.id) else expandedTasks.add(task.id)
                    notifyItemChanged(adapterPosition)
                }

                // Cria as caixas para cada sub-passo dinamicamente
                val inflater = LayoutInflater.from(binding.root.context)
                task.subTasks.forEachIndexed { index, subTask ->
                    val cbSubTask = inflater.inflate(R.layout.item_subtask, binding.layoutSubTasks, false) as CheckBox
                    cbSubTask.text = subTask.name
                    cbSubTask.isChecked = subTask.isCompleted

                    cbSubTask.setOnCheckedChangeListener { _, isChecked ->
                        val updatedSubTasks = task.subTasks.toMutableList()
                        updatedSubTasks[index] = subTask.copy(isCompleted = isChecked)

                        // Se todos os sub-passos forem marcados, a tarefa completa-se sozinha!
                        val allCompleted = updatedSubTasks.all { it.isCompleted }

                        val updatedTask = task.copy(
                            subTasks = updatedSubTasks,
                            isCompleted = allCompleted,
                            completionDate = if (allCompleted) System.currentTimeMillis() else null
                        )
                        onTaskUpdate(updatedTask)
                    }
                    binding.layoutSubTasks.addView(cbSubTask)
                }
            } else {
                binding.ivExpand.visibility = View.GONE
                binding.layoutSubTasks.visibility = View.GONE
            }

            binding.cbTaskCompleted.setOnCheckedChangeListener { _, isChecked ->
                // Se o utilizador marcar a tarefa toda, todos os sub-passos ficam concluídos!
                val updatedSubTasks = task.subTasks.map { it.copy(isCompleted = isChecked) }
                val updatedTask = task.copy(
                    isCompleted = isChecked,
                    subTasks = updatedSubTasks,
                    completionDate = if (isChecked) System.currentTimeMillis() else null
                )
                onTaskUpdate(updatedTask)
            }
        }
    }

    class TaskComparator : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task) = oldItem == newItem
    }
}