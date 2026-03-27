package pt.ipt.mystreaks

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zerobranch.layout.SwipeLayout
import pt.ipt.mystreaks.databinding.ItemTaskBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private val onTaskUpdate: (Task) -> Unit,
    private val onEditClicked: (Task) -> Unit,
    // NOVO: Ações para os botões do Swipe
    private val onArchiveClicked: (Task) -> Unit,
    private val onDeleteClicked: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskComparator()) {

    private val expandedTasks = mutableSetOf<Int>()
    private var currentlyOpenLayout: SwipeLayout? = null

    // --- LISTA DE TAGS EM MEMÓRIA ---
    private var tagsList: List<Tag> = emptyList()

    fun setTags(tags: List<Tag>) {
        this.tagsList = tags
        notifyDataSetChanged()
    }

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

            // --- LÓGICA BLINDADA DE CORES DA TAG ---
            if (!task.tag.isNullOrBlank()) {
                binding.tvTag.visibility = View.VISIBLE
                val cleanName = task.tag!!.trim()
                binding.tvTag.text = cleanName

                val tagEncontrada = tagsList.find {
                    it.name.replace("\\s+".toRegex(), "").equals(cleanName.replace("\\s+".toRegex(), ""), ignoreCase = true)
                }

                val finalHex = tagEncontrada?.color ?: "#757575"

                try {
                    val colorInt = Color.parseColor(finalHex)
                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 32f
                        setColor(colorInt)
                    }
                    binding.tvTag.background = shape
                    ViewCompat.setBackgroundTintList(binding.tvTag, ColorStateList.valueOf(colorInt))
                    binding.tvTag.setTextColor(Color.WHITE)
                } catch (e: Exception) {
                    binding.tvTag.setBackgroundColor(Color.GRAY)
                }
            } else {
                binding.tvTag.visibility = View.GONE
            }

            val ratingPriority: android.widget.RatingBar = itemView.findViewById(R.id.itemRatingPriority)
            ratingPriority.rating = task.priority.toFloat()

            // --- Mostrar Data Limite (Prazo) ---
            if (task.dueDate != null && !task.isCompleted) {
                binding.tvDueDate.visibility = View.VISIBLE
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

                if (System.currentTimeMillis() > task.dueDate!!) {
                    binding.tvDueDate.setTextColor(Color.RED)
                    binding.tvDueDate.text = "⚠️ Atrasado: ${sdf.format(Date(task.dueDate!!))}"
                } else {
                    binding.tvDueDate.setTextColor(Color.parseColor("#E65100"))
                    binding.tvDueDate.text = "⏳ Prazo: ${sdf.format(Date(task.dueDate!!))}"
                }
            } else {
                binding.tvDueDate.visibility = View.GONE
            }

            // Garante que, se clicares na checkbox, o cartão do swipe fecha se estiver aberto
            binding.cbTaskCompleted.setOnCheckedChangeListener(null)
            binding.cbTaskCompleted.isChecked = task.isCompleted

            if (task.isCompleted && task.completionDate != null) {
                binding.tvCompletionDate.visibility = View.VISIBLE
                val sdf = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
                binding.tvCompletionDate.text = "Concluída em: ${sdf.format(Date(task.completionDate!!))}"
                binding.ivEdit.visibility = View.GONE
            } else {
                binding.tvCompletionDate.visibility = View.GONE
                binding.ivEdit.visibility = View.VISIBLE
            }

            binding.ivEdit.setOnClickListener { currentlyOpenLayout?.close(true); onEditClicked(task) }

            // --- LÓGICA DAS SUB-TAREFAS ---
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
                        currentlyOpenLayout?.close(true)
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
                currentlyOpenLayout?.close(true)
                val updatedSubTasks = task.subTasks.map { it.copy(isCompleted = isChecked) }
                val updatedTask = task.copy(
                    isCompleted = isChecked,
                    subTasks = updatedSubTasks,
                    completionDate = if (isChecked) System.currentTimeMillis() else null
                )
                onTaskUpdate(updatedTask)
            }

            // --- MAGIA DO SWIPE (ARRASTAR) ---
            val swipeLayout: SwipeLayout = itemView.findViewById(R.id.swipe_layout)
            val btnArchive: android.widget.ImageView = itemView.findViewById(R.id.btnArchive)
            val btnDelete: android.widget.ImageView = itemView.findViewById(R.id.btnDelete)

            // Cliques nos botões do fundo
            btnArchive.setOnClickListener { swipeLayout.close(true); onArchiveClicked(task) }
            btnDelete.setOnClickListener { swipeLayout.close(true); onDeleteClicked(task) }

            // Gestão de qual layout está aberto
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
        }
    }

    class TaskComparator : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task) = oldItem == newItem
    }
}