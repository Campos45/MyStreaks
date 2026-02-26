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
    private val onTaskUpdate: (Task) -> Unit,
    private val onEditClicked: (Task) -> Unit // NOVO: Listener para o botão Editar
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskComparator()) {

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

            // --- NOVO: Mostrar a Etiqueta (Tag) ---
            if (!task.tag.isNullOrBlank()) {
                binding.tvTag.visibility = View.VISIBLE
                binding.tvTag.text = task.tag
            } else {
                binding.tvTag.visibility = View.GONE
            }

            binding.cbTaskCompleted.setOnCheckedChangeListener(null)
            binding.cbTaskCompleted.isChecked = task.isCompleted

            if (task.isCompleted && task.completionDate != null) {
                binding.tvCompletionDate.visibility = View.VISIBLE
                val sdf = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
                binding.tvCompletionDate.text = "Concluída em: ${sdf.format(Date(task.completionDate!!))}"
                binding.ivEdit.visibility = View.GONE // Não deixamos editar tarefas já concluídas
            } else {
                binding.tvCompletionDate.visibility = View.GONE
                binding.ivEdit.visibility = View.VISIBLE
            }

            // Ação do Botão Editar
            binding.ivEdit.setOnClickListener {
                onEditClicked(task)
            }

            binding.layoutSubTasks.removeAllViews()

            if (task.subTasks.isNotEmpty()) {
                binding.ivExpand.visibility = View.VISIBLE
                val isExpanded = expandedTasks.contains(task.id)
                binding.layoutSubTasks.visibility = if (isExpanded) View.VISIBLE else View.GONE

                binding.ivExpand.rotation = if (isExpanded) 180f else 0f

                binding.ivExpand.setOnClickListener {
                    if (expandedTasks.contains(task.id)) expandedTasks.remove(task.id) else expandedTasks.add(task.id)
                    notifyItemChanged(adapterPosition)
                }

                val inflater = LayoutInflater.from(binding.root.context)
                task.subTasks.forEachIndexed { index, subTask ->
                    val cbSubTask = inflater.inflate(R.layout.item_subtask, binding.layoutSubTasks, false) as CheckBox
                    cbSubTask.text = subTask.name
                    cbSubTask.isChecked = subTask.isCompleted

                    cbSubTask.setOnCheckedChangeListener { _, isChecked ->
                        val updatedSubTasks = task.subTasks.toMutableList()
                        updatedSubTasks[index] = subTask.copy(isCompleted = isChecked)

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