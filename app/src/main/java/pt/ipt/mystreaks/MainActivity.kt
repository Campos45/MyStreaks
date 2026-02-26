package pt.ipt.mystreaks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipt.mystreaks.databinding.ActivityMainBinding
import pt.ipt.mystreaks.databinding.DialogAddStreakBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { StreakRepository(database.streakDao()) }
    private val viewModel: StreakViewModel by viewModels { StreakViewModelFactory(repository) }
    private val logRepository by lazy { LogRepository(database.appLogDao()) }
    private val logViewModel: LogViewModel by viewModels { LogViewModelFactory(logRepository) }

    private var isShowingArchive = false
    private var activeList = emptyList<Streak>()
    private var archivedList = emptyList<Streak>()
    private lateinit var adapter: StreakAdapter
    private var currentTagFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        adapter = StreakAdapter(
            onStreakCheckChanged = { streak, isChecked ->
                if (streak.isCompleted == isChecked) return@StreakAdapter

                if (isShowingArchive) {
                    Toast.makeText(this, "Restaura a atividade primeiro para a marcares!", Toast.LENGTH_SHORT).show()
                    binding.recyclerViewStreaks.adapter?.notifyDataSetChanged()
                    return@StreakAdapter
                }

                val newCount = if (isChecked) streak.count + 1 else if (streak.count > 0) streak.count - 1 else 0

                var newStartDate = streak.currentStartDate
                if (isChecked && streak.count == 0) {
                    newStartDate = System.currentTimeMillis()
                } else if (!isChecked && newCount == 0) {
                    newStartDate = null
                }

                // NOVO: Regista o dia exato no calendário (Normalizado para a meia-noite para não haver erros de horas)
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val todayMidnight = cal.timeInMillis

                val updatedDates = streak.completedDates.toMutableList()
                if (isChecked) {
                    if (!updatedDates.contains(todayMidnight)) updatedDates.add(todayMidnight)
                } else {
                    updatedDates.remove(todayMidnight)
                }

                val updatedStreak = streak.copy(
                    count = newCount, isCompleted = isChecked,
                    currentStartDate = newStartDate, completedDates = updatedDates // Guarda as datas!
                )
                viewModel.update(updatedStreak)

                val acao = if (isChecked) "Concluiu" else "Desmarcou"
                logViewModel.registrarAcao("STREAK", "$acao a atividade '${streak.name}' (Fogo: $newCount)")
            },
            onHistoryClicked = { streak -> showStreakHistoryDialog(streak) },
            onEditClicked = { streak -> showAddStreakDialog(streak) } // NOVO
        )

        binding.recyclerViewStreaks.adapter = adapter
        binding.recyclerViewStreaks.layoutManager = LinearLayoutManager(this)

        val swipeAndDragCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                if (isShowingArchive) return false

                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                val currentList = adapter.currentList.toMutableList()
                val itemMoved = currentList.removeAt(fromPosition)
                currentList.add(toPosition, itemMoved)

                adapter.submitList(currentList)

                currentList.forEachIndexed { index, streak ->
                    if (streak.orderIndex != index) {
                        viewModel.update(streak.copy(orderIndex = index))
                    }
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val streak = adapter.currentList[position]

                if (!isShowingArchive) {
                    viewModel.update(streak.copy(isArchived = true))
                    logViewModel.registrarAcao("STREAK", "Arquivou a atividade '${streak.name}'")
                    Snackbar.make(binding.root, "${streak.name} arquivada 📁", Snackbar.LENGTH_LONG)
                        .setAction("DESFAZER") { viewModel.update(streak.copy(isArchived = false)) }
                        .show()
                } else {
                    if (direction == ItemTouchHelper.RIGHT) {
                        viewModel.update(streak.copy(isArchived = false))
                        logViewModel.registrarAcao("STREAK", "Restaurou '${streak.name}'")
                        Snackbar.make(binding.root, "${streak.name} restaurada 🔥", Snackbar.LENGTH_LONG)
                            .setAction("DESFAZER") { viewModel.update(streak.copy(isArchived = true)) }
                            .show()
                    } else {
                        viewModel.delete(streak)
                        logViewModel.registrarAcao("STREAK", "Eliminou definitivamente '${streak.name}'")
                        Snackbar.make(binding.root, "${streak.name} eliminada 🗑️", Snackbar.LENGTH_LONG)
                            .setAction("DESFAZER") { viewModel.insert(streak) }
                            .show()
                    }
                }
            }
        }
        ItemTouchHelper(swipeAndDragCallback).attachToRecyclerView(binding.recyclerViewStreaks)

        viewModel.activeStreaks.observe(this) { streaks ->
            activeList = streaks ?: emptyList()
            if (!isShowingArchive) refreshUI()
        }

        viewModel.archivedStreaks.observe(this) { streaks ->
            archivedList = streaks ?: emptyList()
            if (isShowingArchive) refreshUI()
        }

        binding.tvToggleArchive.setOnClickListener {
            isShowingArchive = !isShowingArchive
            refreshUI()
        }
        binding.tvNavTasks.setOnClickListener {
            startActivity(Intent(this, TasksActivity::class.java))
        }
        binding.tvNavMedals.setOnClickListener {
            startActivity(android.content.Intent(this, MedalsActivity::class.java))
        }
        binding.fabLogs.setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
        binding.fabAddStreak.setOnClickListener { showAddStreakDialog() }

        // --- NOVO: LÓGICA DO FILTRO ---
        binding.ivFilter.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val tags = database.streakDao().getAllTagsSync()
                withContext(Dispatchers.Main) {
                    if (tags.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Ainda não tens categorias criadas.", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    // Adiciona a opção de ver todas as streaks no topo da lista
                    val options = arrayOf("🌟 Todas") + tags.toTypedArray()

                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Filtrar por Categoria")
                        .setItems(options) { _, which ->
                            currentTagFilter = if (which == 0) null else options[which]
                            refreshUI() // Atualiza o ecrã com o novo filtro
                        }
                        .show()
                }
            }
        }

        val workRequest = androidx.work.PeriodicWorkRequestBuilder<StreakWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("StreakCheck", androidx.work.ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    private fun showStreakHistoryDialog(streak: Streak) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_calendar, null)
        val tvMonthName = dialogView.findViewById<android.widget.TextView>(R.id.tvMonthName)
        val rvCalendar = dialogView.findViewById<RecyclerView>(R.id.rvCalendar)
        val tvRecordsList = dialogView.findViewById<android.widget.TextView>(R.id.tvRecordsList)

        val calendar = java.util.Calendar.getInstance()
        val currentMonth = calendar.get(java.util.Calendar.MONTH)
        val currentYear = calendar.get(java.util.Calendar.YEAR)

        val monthNames = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
        tvMonthName.text = "${monthNames[currentMonth]} $currentYear"

        // Constrói a lista de dias do calendário
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0=Dom, 1=Seg...
        val daysInMonth = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)

        val daysList = mutableListOf<CalendarDay>()

        // Espaços vazios antes do dia 1 (para o mês começar no dia da semana correto)
        for (i in 0 until firstDayOfWeek) { daysList.add(CalendarDay("", false, false)) }

        // Dias reais do mês
        for (i in 1..daysInMonth) {
            val checkCal = java.util.Calendar.getInstance()
            checkCal.set(currentYear, currentMonth, i, 0, 0, 0)
            checkCal.set(java.util.Calendar.MILLISECOND, 0)

            val isCompleted = streak.completedDates.contains(checkCal.timeInMillis)
            daysList.add(CalendarDay(i.toString(), isCompleted, true))
        }

        rvCalendar.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 7)
        rvCalendar.adapter = CalendarAdapter(daysList)

        // Preenche o histórico de Recordes em texto que já tínhamos
        val sortedHistory = streak.history.sortedByDescending { it.count }
        if (sortedHistory.isEmpty()) {
            tvRecordsList.text = "Ainda não tens recordes guardados."
        } else {
            val sdf = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
            val sb = java.lang.StringBuilder()
            sortedHistory.take(5).forEachIndexed { index, record ->
                val start = sdf.format(java.util.Date(record.startDate))
                val end = sdf.format(java.util.Date(record.endDate))
                sb.append("${index + 1}º -> 🔥 ${record.count} dias | $start a $end\n")
            }
            tvRecordsList.text = sb.toString()
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun refreshUI() {
        val baseList = if (isShowingArchive) archivedList else activeList

        // Aplica o filtro se existir um selecionado
        val currentList = if (currentTagFilter != null) {
            baseList.filter { it.tag == currentTagFilter }
        } else {
            baseList
        }
        adapter.submitList(currentList)

        if (currentList.isEmpty()) {
            binding.recyclerViewStreaks.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            if (isShowingArchive) {
                binding.tvEmptyEmoji.text = "🗄️"
                binding.tvEmptyTitle.text = "Arquivo Vazio"
                binding.tvEmptyDesc.text = "As tuas atividades arquivadas vão aparecer aqui."
            } else {
                binding.tvEmptyEmoji.text = "🏕️"
                binding.tvEmptyTitle.text = "Nenhuma atividade"
                binding.tvEmptyDesc.text = "Clica em 'Nova' para começares a tua streak!"
            }
        } else {
            binding.recyclerViewStreaks.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
        }

        if (isShowingArchive) {
            binding.fabAddStreak.hide()
            binding.tvAppTitle.text = "Arquivo 🗄️"
            binding.tvToggleArchive.text = "⬅️ Voltar"
        } else {
            binding.fabAddStreak.show()
            binding.tvAppTitle.text = "MyStreaks 🔥"
            binding.tvToggleArchive.text = "📁 Arquivo"
        }

        Thread {
            try {
                val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this@MainActivity)
                val widgetComponent = android.content.ComponentName(this@MainActivity, StreakWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widgetListView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun showAddStreakDialog(streakToEdit: Streak? = null) {
        val dialogBinding = DialogAddStreakBinding.inflate(LayoutInflater.from(this))
        val isEditing = streakToEdit != null

        var selectedHour: Int? = streakToEdit?.remindHour
        var selectedMinute: Int? = streakToEdit?.remindMinute

        // --- PREENCHE OS CAMPOS SE ESTIVERMOS A EDITAR ---
        if (isEditing) {
            dialogBinding.etActivityName.setText(streakToEdit!!.name)
            dialogBinding.etTag.setText(streakToEdit.tag ?: "")

            when (streakToEdit.type) {
                "D" -> dialogBinding.rgFrequency.check(R.id.rbDaily)
                "S" -> dialogBinding.rgFrequency.check(R.id.rbWeekly)
                "M" -> dialogBinding.rgFrequency.check(R.id.rbMonthly)
            }

            if (selectedHour != null && selectedMinute != null) {
                dialogBinding.switchReminder.isChecked = true
                dialogBinding.layoutReminderDetails.visibility = View.VISIBLE
                dialogBinding.btnTimePicker.text = String.format("Hora: %02d:%02d", selectedHour, selectedMinute)
                if (streakToEdit.type != "D" && streakToEdit.remindExtra != null) {
                    dialogBinding.etExtraDay.setText(streakToEdit.remindExtra.toString())
                }
            }
        }

        // Carregar Tags para as Sugestões Dropdown
        lifecycleScope.launch(Dispatchers.IO) {
            val existingTags = database.streakDao().getAllTagsSync()
            withContext(Dispatchers.Main) {
                val arrayAdapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, existingTags)
                dialogBinding.etTag.setAdapter(arrayAdapter)
            }
        }

        dialogBinding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.layoutReminderDetails.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        dialogBinding.rgFrequency.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbDaily) {
                dialogBinding.layoutExtraDay.visibility = View.GONE
            } else {
                dialogBinding.layoutExtraDay.visibility = View.VISIBLE
                if (checkedId == R.id.rbWeekly) dialogBinding.layoutExtraDay.hint = "Dia da Semana (1=Dom... 7=Sáb)"
                if (checkedId == R.id.rbMonthly) dialogBinding.layoutExtraDay.hint = "Dia do Mês (1 a 31)"
            }
        }

        dialogBinding.btnTimePicker.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            android.app.TimePickerDialog(this, { _, hourOfDay, minute ->
                selectedHour = hourOfDay
                selectedMinute = minute
                dialogBinding.btnTimePicker.text = String.format("Hora: %02d:%02d", hourOfDay, minute)
            }, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), true).show()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isEditing) "Editar Atividade" else "Nova Atividade")
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar") { dialog, _ ->
                val activityName = dialogBinding.etActivityName.text.toString()
                if (activityName.isNotBlank()) {
                    val selectedTypeId = dialogBinding.rgFrequency.checkedRadioButtonId
                    val selectedRadioButton = dialogBinding.root.findViewById<RadioButton>(selectedTypeId)
                    val type = when (selectedRadioButton.text.toString()) {
                        "Semanal" -> "S"
                        "Mensal" -> "M"
                        else -> "D"
                    }

                    val finalExtraDay = if (dialogBinding.switchReminder.isChecked && type != "D") {
                        dialogBinding.etExtraDay.text.toString().toIntOrNull()
                    } else { null }

                    if (!dialogBinding.switchReminder.isChecked) {
                        selectedHour = null
                        selectedMinute = null
                    }

                    val tagName = dialogBinding.etTag.text.toString().trim()
                    val finalTag = if (tagName.isNotEmpty()) tagName else null

                    // SE FOR EDIÇÃO, ATUALIZA. SE FOR NOVA, INSERE.
                    if (isEditing) {
                        val updatedStreak = streakToEdit!!.copy(
                            name = activityName, type = type,
                            remindHour = selectedHour, remindMinute = selectedMinute, remindExtra = finalExtraDay,
                            tag = finalTag
                        )
                        viewModel.update(updatedStreak)
                        logViewModel.registrarAcao("STREAK_EDIT", "Editou a atividade '$activityName'")
                    } else {
                        val newStreak = Streak(
                            name = activityName, type = type,
                            remindHour = selectedHour, remindMinute = selectedMinute, remindExtra = finalExtraDay,
                            tag = finalTag
                        )
                        viewModel.insert(newStreak)
                        logViewModel.registrarAcao("STREAK_NOVA", "Criou a atividade '$activityName' ($type)")
                    }

                    // Agenda o alarme se foi ativado
                    if (selectedHour != null && selectedMinute != null) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            kotlinx.coroutines.delay(500)
                            val streaks = database.streakDao().getActiveStreaksList()
                            val insertedStreak = streaks.find { it.name == activityName && it.remindHour == selectedHour }
                            if (insertedStreak != null) {
                                withContext(Dispatchers.Main) {
                                    scheduleStreakAlarm(insertedStreak)
                                }
                            }
                        }
                    }

                } else {
                    Toast.makeText(this, "O nome não pode estar vazio", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            .show()
    }
    private fun scheduleStreakAlarm(streak: Streak) {
        if (streak.remindHour == null || streak.remindMinute == null) return

        val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, StreakAlarmReceiver::class.java).apply {
            putExtra("STREAK_ID", streak.id)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, streak.id, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, streak.remindHour!!)
        calendar.set(java.util.Calendar.MINUTE, streak.remindMinute!!)
        calendar.set(java.util.Calendar.SECOND, 0)

        when (streak.type) {
            "D" -> {
                if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            "S" -> {
                val targetDay = streak.remindExtra ?: java.util.Calendar.MONDAY
                calendar.set(java.util.Calendar.DAY_OF_WEEK, targetDay)
                if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(java.util.Calendar.WEEK_OF_YEAR, 1)
            }
            "M" -> {
                val targetDay = streak.remindExtra ?: 1
                calendar.set(java.util.Calendar.DAY_OF_MONTH, targetDay)
                if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(java.util.Calendar.MONTH, 1)
            }
        }

        try {
            alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } catch (e: SecurityException) {
            alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }
}