package pt.ipt.mystreaks.ui.tasks

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RatingBar
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zerobranch.layout.SwipeLayout
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.data.model.Tag
import pt.ipt.mystreaks.data.model.Task
import pt.ipt.mystreaks.databinding.ItemTaskBinding
import pt.ipt.mystreaks.utils.setInfoTooltip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private val onTaskUpdate: (Task) -> Unit,
    private val onEditClicked: (Task) -> Unit,
    private val onArchiveClicked: (Task) -> Unit,
    private val onDeleteClicked: (Task) -> Unit,
    private val onCompleteRequested: ((Task, () -> Unit) -> Unit)? = null
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskComparator()) {

    private val expandedTasks = mutableSetOf<Int>()
    private val expandedNotes = mutableSetOf<Int>()
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
            val ctx = binding.root.context
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

            val ratingPriority: RatingBar = itemView.findViewById(R.id.itemRatingPriority)
            ratingPriority.rating = task.priority.toFloat()

            // --- Mostrar Data Limite (Prazo) ---
            if (task.dueDate != null && !task.isCompleted) {
                binding.tvDueDate.visibility = View.VISIBLE
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val formatted = sdf.format(Date(task.dueDate!!))

                if (System.currentTimeMillis() > task.dueDate!!) {
                    binding.tvDueDate.setTextColor(Color.RED)
                    binding.tvDueDate.text = ctx.getString(R.string.task_overdue, formatted)
                } else {
                    binding.tvDueDate.setTextColor(Color.parseColor("#E65100"))
                    binding.tvDueDate.text = ctx.getString(R.string.task_due, formatted)
                }
            } else {
                binding.tvDueDate.visibility = View.GONE
            }

            // Garante que, se clicares na checkbox, o cartão do swipe fecha se estiver aberto
            binding.cbTaskCompleted.setOnCheckedChangeListener(null)
            binding.cbTaskCompleted.isChecked = task.isCompleted

            if (task.isCompleted && task.completionDate != null) {
                binding.tvCompletionDate.visibility = View.VISIBLE
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                binding.tvCompletionDate.text = ctx.getString(R.string.task_completed_at, sdf.format(Date(task.completionDate!!)))
                binding.ivEdit.visibility = View.GONE
            } else {
                binding.tvCompletionDate.visibility = View.GONE
                binding.ivEdit.visibility = View.VISIBLE
            }

            // --- NOTA DE CONCLUSÃO ---
            if (task.isCompleted && !task.completionNote.isNullOrBlank()) {
                binding.tvCompletionNote.visibility = View.VISIBLE
                binding.tvCompletionNote.text = "${ctx.getString(R.string.completion_note_label)} ${task.completionNote}"
            } else {
                binding.tvCompletionNote.visibility = View.GONE
            }

            // --- NOTAS DETALHADAS EXPANSÍVEIS ---
            if (!task.notes.isNullOrBlank()) {
                binding.ivExpandNotes.visibility = View.VISIBLE
                val isNotesExpanded = expandedNotes.contains(task.id)
                binding.layoutNotes.visibility = if (isNotesExpanded) View.VISIBLE else View.GONE
                binding.ivExpandNotes.rotation = if (isNotesExpanded) 180f else 0f
                binding.tvNotesContent.text = task.notes

                binding.ivExpandNotes.setOnClickListener {
                    val pos = layoutPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        if (expandedNotes.contains(task.id)) expandedNotes.remove(task.id) else expandedNotes.add(task.id)
                        notifyItemChanged(pos)
                    }
                }
            } else {
                binding.ivExpandNotes.visibility = View.GONE
                binding.layoutNotes.visibility = View.GONE
            }

            binding.ivEdit.setOnClickListener { currentlyOpenLayout?.close(true); onEditClicked(task) }

            // Tooltips informativos
            binding.ivEdit.setInfoTooltip(R.string.tooltip_edit)
            binding.ivExpandNotes.setInfoTooltip(R.string.tooltip_expand_notes)
            binding.ivExpand.setInfoTooltip(R.string.tooltip_expand_subtasks)
            binding.cbTaskCompleted.setInfoTooltip(R.string.tooltip_complete_task)

            // --- LÓGICA DAS SUB-TAREFAS OTIMIZADA ---
            if (task.subTasks.isNotEmpty()) {
                binding.ivExpand.visibility = View.VISIBLE
                val isExpanded = expandedTasks.contains(task.id)
                binding.layoutSubTasks.visibility = if (isExpanded) View.VISIBLE else View.GONE
                binding.ivExpand.rotation = if (isExpanded) 180f else 0f

                binding.ivExpand.setOnClickListener {
                    val pos = layoutPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        if (expandedTasks.contains(task.id)) expandedTasks.remove(task.id) else expandedTasks.add(task.id)
                        notifyItemChanged(pos)
                    }
                }

                val currentChildCount = binding.layoutSubTasks.childCount
                val targetChildCount = task.subTasks.size

                if (currentChildCount > targetChildCount) {
                    binding.layoutSubTasks.removeViews(targetChildCount, currentChildCount - targetChildCount)
                }

                val inflater = LayoutInflater.from(binding.root.context)
                task.subTasks.forEachIndexed { index, subTask ->
                    val cbSubTask = if (index < currentChildCount) {
                        binding.layoutSubTasks.getChildAt(index) as CheckBox
                    } else {
                        val newCb = inflater.inflate(R.layout.item_subtask, binding.layoutSubTasks, false) as CheckBox
                        binding.layoutSubTasks.addView(newCb)
                        newCb
                    }

                    cbSubTask.setOnCheckedChangeListener(null)
                    cbSubTask.text = subTask.name
                    cbSubTask.isChecked = subTask.isCompleted

                    cbSubTask.setOnCheckedChangeListener { _, isChecked ->
                        currentlyOpenLayout?.close(true)
                        val updatedSubTasks = task.subTasks.toMutableList()
                        updatedSubTasks[index] = subTask.copy(isCompleted = isChecked)

                        val allCompleted = updatedSubTasks.all { it.isCompleted }
                        if (allCompleted && !task.isCompleted && onCompleteRequested != null) {
                            val candidateTask = task.copy(subTasks = updatedSubTasks)
                            onCompleteRequested.invoke(candidateTask) {
                                // Revert subtask checkbox if cancelled
                                val pos = layoutPosition
                                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos)
                            }
                        } else {
                            val updatedTask = task.copy(
                                subTasks = updatedSubTasks,
                                isCompleted = allCompleted,
                                completionDate = if (allCompleted) System.currentTimeMillis() else null,
                                completionNote = if (allCompleted) task.completionNote else null
                            )
                            onTaskUpdate(updatedTask)
                        }
                    }
                }
            } else {
                binding.ivExpand.visibility = View.GONE
                binding.layoutSubTasks.visibility = View.GONE
                binding.layoutSubTasks.removeAllViews()
            }

            binding.cbTaskCompleted.setOnCheckedChangeListener { _, isChecked ->
                currentlyOpenLayout?.close(true)
                if (isChecked && onCompleteRequested != null) {
                    val updatedSubTasks = task.subTasks.map { it.copy(isCompleted = true) }
                    val candidateTask = task.copy(subTasks = updatedSubTasks)
                    onCompleteRequested.invoke(candidateTask) {
                        val pos = layoutPosition
                        if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos)
                    }
                } else {
                    val updatedSubTasks = task.subTasks.map { it.copy(isCompleted = isChecked) }
                    val updatedTask = task.copy(
                        isCompleted = isChecked,
                        subTasks = updatedSubTasks,
                        completionDate = if (isChecked) System.currentTimeMillis() else null,
                        completionNote = if (isChecked) task.completionNote else null
                    )
                    onTaskUpdate(updatedTask)
                }
            }

            // --- MAGIA DO SWIPE (ARRASTAR) ---
            val swipeLayout: SwipeLayout = itemView.findViewById(R.id.swipe_layout)
            val btnArchive: ImageView = itemView.findViewById(R.id.btnArchive)
            val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)

            btnArchive.setInfoTooltip(R.string.tooltip_archive)
            btnDelete.setInfoTooltip(R.string.tooltip_delete)

            btnArchive.setOnClickListener { swipeLayout.close(true); onArchiveClicked(task) }
            btnDelete.setOnClickListener { swipeLayout.close(true); onDeleteClicked(task) }

            swipeLayout.setOnActionsListener(object : SwipeLayout.SwipeActionsListener {
                override fun onOpen(direction: Int, isContinuous: Boolean) {
                    itemView.parent?.requestDisallowInterceptTouchEvent(true)
                    if (currentlyOpenLayout != swipeLayout) {
                        currentlyOpenLayout?.close(true)
                        currentlyOpenLayout = swipeLayout
                    }
                }
                override fun onClose() {
                    itemView.parent?.requestDisallowInterceptTouchEvent(false)
                    if (currentlyOpenLayout == swipeLayout) currentlyOpenLayout = null
                }
            })

            swipeLayout.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        if (currentlyOpenLayout == swipeLayout) {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }
        }
    }

    class TaskComparator : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task) = oldItem == newItem
    }
}
