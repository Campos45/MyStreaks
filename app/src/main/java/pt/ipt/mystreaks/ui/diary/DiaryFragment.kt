package pt.ipt.mystreaks.ui.diary

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.data.model.SubTask
import pt.ipt.mystreaks.data.model.Tag
import pt.ipt.mystreaks.data.model.Task
import pt.ipt.mystreaks.data.repository.DiaryRepository
import pt.ipt.mystreaks.data.repository.LogRepository
import pt.ipt.mystreaks.data.repository.TaskRepository
import pt.ipt.mystreaks.databinding.DialogAddTaskBinding
import pt.ipt.mystreaks.databinding.FragmentDiaryBinding
import pt.ipt.mystreaks.services.DiaryAlarmReceiver
import pt.ipt.mystreaks.services.TaskAlarmReceiver
import pt.ipt.mystreaks.ui.logs.LogViewModel
import pt.ipt.mystreaks.ui.logs.LogViewModelFactory
import pt.ipt.mystreaks.ui.settings.TagDropdownAdapter
import pt.ipt.mystreaks.ui.settings.TagViewModel
import pt.ipt.mystreaks.ui.tasks.TaskAdapter
import pt.ipt.mystreaks.ui.tasks.TaskDialogs
import pt.ipt.mystreaks.utils.setInfoTooltip
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DiaryFragment : Fragment(R.layout.fragment_diary) {

    private var _binding: FragmentDiaryBinding? = null
    private val binding get() = _binding!!

    private val database by lazy { AppDatabase.getDatabase(requireContext()) }
    private val diaryRepository by lazy { DiaryRepository(database.diaryDao()) }
    private val taskRepository by lazy { TaskRepository(database.taskDao()) }
    private val viewModel: DiaryViewModel by viewModels { DiaryViewModelFactory(diaryRepository, taskRepository) }
    private val tagViewModel: TagViewModel by viewModels()
    private val logRepository by lazy { LogRepository(database.appLogDao()) }
    private val logViewModel: LogViewModel by viewModels { LogViewModelFactory(logRepository) }

    private var currentMonth: YearMonth = YearMonth.now()
    private var selectedDate: LocalDate = LocalDate.now()
    private var lastLoadedContent: String = ""
    private var cachedNotesDates: Set<LocalDate> = emptySet()
    private var cachedTasksDates: Set<LocalDate> = emptySet()

    private lateinit var calendarAdapter: DiaryCalendarAdapter
    private lateinit var taskAdapter: TaskAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDiaryBinding.bind(view)

        setupCalendar()
        setupTasksList()
        setupListeners()
        setupObservers()
        setupTooltips()
    }

    private fun setupCalendar() {
        calendarAdapter = DiaryCalendarAdapter { pickedDate ->
            autoSaveCurrentNoteIfChanged()
            selectedDate = pickedDate
            viewModel.selectDate(pickedDate)
            calendarAdapter.setSelectedDate(pickedDate)
            updateSelectedDateHeader(pickedDate)
        }
        binding.rvCalendar.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.rvCalendar.adapter = calendarAdapter
        updateMonthView()
        updateSelectedDateHeader(selectedDate)
    }

    private fun setupTasksList() {
        taskAdapter = TaskAdapter(
            onTaskUpdate = { updatedTask ->
                viewModel.updateTask(updatedTask)
                if (updatedTask.isCompleted) {
                    cancelTaskAlarm(updatedTask.id, updatedTask.name)
                } else if (updatedTask.dueDate != null) {
                    scheduleTaskAlarm(updatedTask.id, updatedTask.name, updatedTask.dueDate)
                }
            },
            onEditClicked = { task -> showAddTaskForDayDialog(task) },
            onArchiveClicked = { task ->
                viewModel.updateTask(task.copy(isArchived = true))
                cancelTaskAlarm(task.id, task.name)
                Snackbar.make(binding.root, getString(R.string.archive), Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.undo)) {
                        viewModel.updateTask(task.copy(isArchived = false))
                    }.show()
            },
            onDeleteClicked = { task ->
                viewModel.deleteTask(task)
                cancelTaskAlarm(task.id, task.name)
                Snackbar.make(binding.root, getString(R.string.delete), Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.undo)) {
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            viewModel.insertTaskAndGetId(task)
                        }
                    }.show()
            },
            onCompleteRequested = { candidateTask, onCancel ->
                TaskDialogs.showCompletionNoteDialog(
                    context = requireContext(),
                    task = candidateTask,
                    onComplete = { completedTask ->
                        viewModel.updateTask(completedTask)
                        cancelTaskAlarm(completedTask.id, completedTask.name)
                        logViewModel.registrarAcao("DIARIO_TAREFA", "Concluiu '${completedTask.name}' no Diário")
                    },
                    onCancel = onCancel
                )
            }
        )
        binding.rvDiaryTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDiaryTasks.adapter = taskAdapter
    }

    private fun setupListeners() {
        binding.btnPrevMonth.setOnClickListener {
            currentMonth = currentMonth.minusMonths(1)
            updateMonthView()
        }

        binding.btnNextMonth.setOnClickListener {
            currentMonth = currentMonth.plusMonths(1)
            updateMonthView()
        }

        binding.btnToday.setOnClickListener {
            autoSaveCurrentNoteIfChanged()
            val today = LocalDate.now()
            currentMonth = YearMonth.from(today)
            selectedDate = today
            viewModel.selectDate(today)
            updateMonthView()
            calendarAdapter.setSelectedDate(today)
            updateSelectedDateHeader(today)
        }

        binding.btnSaveDiaryNote.setOnClickListener {
            val content = binding.etDiaryContent.text.toString()
            viewModel.saveNote(selectedDate, content)
            lastLoadedContent = content.trim()
            DiaryAlarmReceiver.scheduleDailyReminder(requireContext())
            pt.ipt.mystreaks.widget.DiaryWidgetProvider.triggerUpdate(requireContext())
            Toast.makeText(requireContext(), R.string.diary_note_saved, Toast.LENGTH_SHORT).show()
            logViewModel.registrarAcao("DIARIO", "Guardou nota do diário para $selectedDate")
        }

        binding.btnAddDiaryTask.setOnClickListener {
            showAddTaskForDayDialog(null)
        }
    }

    private fun setupObservers() {
        viewModel.currentDayEntry.observe(viewLifecycleOwner) { entry ->
            val text = entry?.content ?: ""
            lastLoadedContent = text
            if (binding.etDiaryContent.text.toString() != text) {
                binding.etDiaryContent.setText(text)
            }
        }

        viewModel.currentDayTasks.observe(viewLifecycleOwner) { tasks ->
            taskAdapter.submitList(tasks)
            if (tasks.isNullOrEmpty()) {
                binding.tvNoDiaryTasks.visibility = View.VISIBLE
                binding.rvDiaryTasks.visibility = View.GONE
            } else {
                binding.tvNoDiaryTasks.visibility = View.GONE
                binding.rvDiaryTasks.visibility = View.VISIBLE
            }
        }

        viewModel.datesWithNotes.observe(viewLifecycleOwner) { dates ->
            cachedNotesDates = dates ?: emptySet()
            calendarAdapter.setIndicators(cachedNotesDates, cachedTasksDates)
        }

        viewModel.datesWithTasks.observe(viewLifecycleOwner) { dates ->
            cachedTasksDates = dates ?: emptySet()
            calendarAdapter.setIndicators(cachedNotesDates, cachedTasksDates)
        }

        tagViewModel.allTags.observe(viewLifecycleOwner) { allTags ->
            taskAdapter.setTags(allTags.filter { it.type == "T" })
        }
    }

    private fun setupTooltips() {
        binding.btnPrevMonth.setInfoTooltip(R.string.tooltip_prev_month)
        binding.btnNextMonth.setInfoTooltip(R.string.tooltip_next_month)
        binding.btnToday.setInfoTooltip(R.string.tooltip_today)
        binding.btnSaveDiaryNote.setInfoTooltip(R.string.tooltip_save_diary)
        binding.btnAddDiaryTask.setInfoTooltip(R.string.tooltip_diary_add_task)
    }

    private fun updateMonthView() {
        val locale = Locale.getDefault()
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        val formatted = currentMonth.format(formatter)
        binding.tvCurrentMonth.text = formatted.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        }
        calendarAdapter.setMonth(currentMonth)
        calendarAdapter.setSelectedDate(selectedDate)
        calendarAdapter.setIndicators(cachedNotesDates, cachedTasksDates)
    }

    private fun updateSelectedDateHeader(date: LocalDate) {
        val locale = Locale.getDefault()
        val formatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", locale)
        val formatted = date.format(formatter).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        }
        val today = LocalDate.now()
        val suffix = when (date) {
            today -> " (${getString(R.string.today)})"
            today.plusDays(1) -> " (${getString(R.string.tomorrow)})"
            today.minusDays(1) -> " (${getString(R.string.yesterday)})"
            else -> ""
        }
        binding.tvSelectedDateHeader.text = "$formatted$suffix"
    }

    private fun autoSaveCurrentNoteIfChanged() {
        val currentText = binding.etDiaryContent.text.toString().trim()
        if (currentText != lastLoadedContent.trim()) {
            viewModel.saveNote(selectedDate, currentText)
            lastLoadedContent = currentText
        }
    }

    private fun showAddTaskForDayDialog(taskToEdit: Task?) {
        val dialogBinding = DialogAddTaskBinding.inflate(layoutInflater)
        val isEditing = taskToEdit != null

        // Pre-fill dueDate with selected day at 09:00
        var selectedDueDate: Long = taskToEdit?.dueDate
            ?: selectedDate.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val taskTags = tagViewModel.allTags.value?.filter { it.type == "T" } ?: emptyList()
        val customAdapter = TagDropdownAdapter(requireContext(), taskTags)
        dialogBinding.etTag.setAdapter(customAdapter)
        dialogBinding.etTag.setOnClickListener { dialogBinding.etTag.showDropDown() }
        dialogBinding.etTag.setOnItemClickListener { parent, _, position, _ ->
            val selectedTag = parent.getItemAtPosition(position) as Tag
            dialogBinding.etTag.setText(selectedTag.name, false)
        }

        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val initialZdt = Instant.ofEpochMilli(selectedDueDate).atZone(ZoneId.systemDefault()).toLocalDateTime()
        dialogBinding.btnDatePicker.text = getString(R.string.task_due, initialZdt.format(formatter))

        if (isEditing) {
            dialogBinding.tvDialogTitle.text = getString(R.string.edit_task_title)
            dialogBinding.etTaskName.setText(taskToEdit?.name)
            dialogBinding.etTag.setText(taskToEdit?.tag ?: "", false)
            dialogBinding.etTaskNotes.setText(taskToEdit?.notes ?: "")
            dialogBinding.ratingPriority.rating = taskToEdit?.priority?.toFloat() ?: 3.0f
        } else {
            dialogBinding.tvDialogTitle.text = getString(R.string.diary_add_task)
            dialogBinding.ratingPriority.rating = 3.0f
        }

        dialogBinding.btnDatePicker.setOnClickListener {
            val currentZdt = Instant.ofEpochMilli(selectedDueDate).atZone(ZoneId.systemDefault()).toLocalDateTime()
            TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
                val zdt = selectedDate.atTime(hourOfDay, minute).atZone(ZoneId.systemDefault())
                selectedDueDate = zdt.toInstant().toEpochMilli()
                dialogBinding.btnDatePicker.text = getString(R.string.task_due, zdt.toLocalDateTime().format(formatter))
            }, currentZdt.hour, currentZdt.minute, true).show()
        }

        fun addSubtaskField(text: String = "") {
            val fieldView = LayoutInflater.from(requireContext()).inflate(R.layout.item_subtask_input, dialogBinding.layoutSubtaskFields, false)
            val editText = fieldView.findViewById<EditText>(R.id.etSubtaskName)
            val btnRemove = fieldView.findViewById<ImageView>(R.id.btnRemoveSubtask)
            val btnUp = fieldView.findViewById<ImageView>(R.id.btnUpSubtask)
            val btnDown = fieldView.findViewById<ImageView>(R.id.btnDownSubtask)

            editText.setText(text)
            btnRemove.setOnClickListener { dialogBinding.layoutSubtaskFields.removeView(fieldView) }
            btnUp.setOnClickListener {
                val parent = dialogBinding.layoutSubtaskFields
                val idx = parent.indexOfChild(fieldView)
                if (idx > 0) { parent.removeView(fieldView); parent.addView(fieldView, idx - 1) }
            }
            btnDown.setOnClickListener {
                val parent = dialogBinding.layoutSubtaskFields
                val idx = parent.indexOfChild(fieldView)
                if (idx < parent.childCount - 1) { parent.removeView(fieldView); parent.addView(fieldView, idx + 1) }
            }
            dialogBinding.layoutSubtaskFields.addView(fieldView)
        }

        if (isEditing) {
            taskToEdit?.subTasks?.forEach { addSubtaskField(it.name) }
        }
        dialogBinding.btnAddSubtask.setOnClickListener { addSubtaskField() }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val taskName = dialogBinding.etTaskName.text.toString().trim()
                val tagName = dialogBinding.etTag.text.toString().trim()
                val finalTag = tagName.ifEmpty { null }
                val priorityValue = dialogBinding.ratingPriority.rating.toInt().coerceIn(1, 5)
                val finalNotes = dialogBinding.etTaskNotes.text.toString().trim().ifEmpty { null }

                if (finalTag != null) {
                    val exists = tagViewModel.allTags.value?.any { it.name.trim().equals(finalTag, true) && it.type == "T" } ?: false
                    if (!exists) tagViewModel.insert(Tag(name = finalTag, color = "#757575", type = "T"))
                }

                if (taskName.isNotBlank()) {
                    val newSubTasksList = mutableListOf<SubTask>()
                    for (i in 0 until dialogBinding.layoutSubtaskFields.childCount) {
                        val row = dialogBinding.layoutSubtaskFields.getChildAt(i)
                        val text = row.findViewById<EditText>(R.id.etSubtaskName)?.text?.toString()?.trim() ?: ""
                        if (text.isNotBlank()) {
                            val wasCompleted = taskToEdit?.subTasks?.find { it.name == text }?.isCompleted ?: false
                            newSubTasksList.add(SubTask(name = text, isCompleted = wasCompleted))
                        }
                    }

                    if (isEditing) {
                        viewModel.updateTask(
                            taskToEdit!!.copy(
                                name = taskName,
                                subTasks = newSubTasksList,
                                tag = finalTag,
                                notes = finalNotes,
                                dueDate = selectedDueDate,
                                priority = priorityValue
                            )
                        )
                        scheduleTaskAlarm(taskToEdit.id, taskName, selectedDueDate)
                    } else {
                        val newTask = Task(
                            name = taskName,
                            subTasks = newSubTasksList,
                            tag = finalTag,
                            notes = finalNotes,
                            dueDate = selectedDueDate,
                            priority = priorityValue
                        )
                        viewLifecycleOwner.lifecycleScope.launch {
                            val newId = withContext(Dispatchers.IO) {
                                viewModel.insertTaskAndGetId(newTask)
                            }
                            scheduleTaskAlarm(newId.toInt(), taskName, selectedDueDate)
                            DiaryAlarmReceiver.scheduleDailyReminder(requireContext())
                            logViewModel.registrarAcao("DIARIO_TAREFA", "Criou tarefa '$taskName' para $selectedDate")
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun scheduleTaskAlarm(taskId: Int, taskName: String, dueDate: Long?) {
        if (dueDate == null || dueDate <= System.currentTimeMillis()) return
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), TaskAlarmReceiver::class.java).apply {
            putExtra("TASK_NAME", taskName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(), taskId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueDate, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, dueDate, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, dueDate, pendingIntent)
        }
    }

    private fun cancelTaskAlarm(taskId: Int, taskName: String) {
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), TaskAlarmReceiver::class.java).apply {
            putExtra("TASK_NAME", taskName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(), taskId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    override fun onPause() {
        super.onPause()
        if (_binding != null) {
            autoSaveCurrentNoteIfChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
