package pt.ipt.mystreaks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.lifecycle.lifecycleScope
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

    private var currentSearchQuery: String = ""

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

                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val todayMidnight = cal.timeInMillis

                val updatedDates = streak.completedDates.toMutableList()
                if (isChecked) {
                    if (!updatedDates.contains(todayMidnight)) updatedDates.add(todayMidnight)
                } else {
                    updatedDates.remove(todayMidnight)
                }

                val updatedStreak = streak.copy(
                    count = newCount, isCompleted = isChecked,
                    currentStartDate = newStartDate, completedDates = updatedDates
                )
                viewModel.update(updatedStreak)

                val acao = if (isChecked) "Concluiu" else "Desmarcou"
                logViewModel.registrarAcao("STREAK", "$acao a atividade '${streak.name}' (Fogo: $newCount)")
            },
            onHistoryClicked = { streak -> showStreakHistoryDialog(streak) },
            onEditClicked = { streak -> showAddStreakDialog(streak) }
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

                // 1. Apenas trocamos visualmente (Super Rápido, não quebra o arrasto!)
                val currentList = adapter.currentList.toMutableList()
                java.util.Collections.swap(currentList, fromPosition, toPosition)

                // O segredo está nesta linha:
                adapter.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            // NOVO: Só grava na Base de Dados quando LARGARES o item!
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val currentList = adapter.currentList
                currentList.forEachIndexed { index, streak ->
                    if (streak.orderIndex != index) {
                        viewModel.update(streak.copy(orderIndex = index))
                    }
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // ... (O teu código do onSwiped do MainActivity mantém-se rigorosamente igual aqui) ...
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

        binding.ivFilter.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val tags = database.streakDao().getAllTagsSync()
                withContext(Dispatchers.Main) {
                    if (tags.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Ainda não tens categorias criadas.", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    val options = arrayOf("🌟 Todas") + tags.toTypedArray()

                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Filtrar por Categoria")
                        .setItems(options) { _, which ->
                            currentTagFilter = if (which == 0) null else options[which]
                            refreshUI()
                        }
                        .show()
                }
            }
        }

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
            startActivity(Intent(this, MedalsActivity::class.java))
        }
        binding.fabLogs.setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }
        binding.fabAddStreak.setOnClickListener { showAddStreakDialog() }

        // Lógica da Barra de Pesquisa
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                refreshUI()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Mostra ou esconde a barra de pesquisa com UM CLIQUE na Lupa
        binding.ivSearch.setOnClickListener {
            binding.etSearch.visibility = if (binding.etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        val workRequest = androidx.work.PeriodicWorkRequestBuilder<StreakWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("StreakCheck", androidx.work.ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }

    private fun showStreakHistoryDialog(streak: Streak) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_calendar, null)
        val tvMonthName = dialogView.findViewById<android.widget.TextView>(R.id.tvMonthName)
        val rvCalendar = dialogView.findViewById<RecyclerView>(R.id.rvCalendar)
        val tvRecordsList = dialogView.findViewById<android.widget.TextView>(R.id.tvRecordsList)
        val btnPrevMonth = dialogView.findViewById<android.widget.ImageView>(R.id.btnPrevMonth)
        val btnNextMonth = dialogView.findViewById<android.widget.ImageView>(R.id.btnNextMonth)

        var displayMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        var displayYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val monthNames = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

        fun renderCalendar() {
            tvMonthName.text = "${monthNames[displayMonth]} $displayYear"
            val calendar = java.util.Calendar.getInstance()
            calendar.set(displayYear, displayMonth, 1)

            val firstDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1
            val daysInMonth = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            val daysList = mutableListOf<CalendarDay>()

            for (i in 0 until firstDayOfWeek) { daysList.add(CalendarDay("", false, false)) }
            for (i in 1..daysInMonth) {
                val checkCal = java.util.Calendar.getInstance()
                checkCal.set(displayYear, displayMonth, i, 0, 0, 0)
                checkCal.set(java.util.Calendar.MILLISECOND, 0)
                val isCompleted = streak.completedDates.contains(checkCal.timeInMillis)
                daysList.add(CalendarDay(i.toString(), isCompleted, true))
            }
            rvCalendar.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 7)
            rvCalendar.adapter = CalendarAdapter(daysList)
        }

        btnPrevMonth.setOnClickListener {
            displayMonth--
            if (displayMonth < 0) { displayMonth = 11; displayYear-- }
            renderCalendar()
        }

        btnNextMonth.setOnClickListener {
            displayMonth++
            if (displayMonth > 11) { displayMonth = 0; displayYear++ }
            renderCalendar()
        }

        renderCalendar() // Renderiza a primeira vez

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

        // Aplica o filtro da TAG e depois o filtro da PESQUISA
        val currentList = baseList.filter {
            (currentTagFilter == null || it.tag == currentTagFilter) &&
                    (currentSearchQuery.isEmpty() || it.name.contains(currentSearchQuery, ignoreCase = true))
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
            binding.tvAppTitle.text = "📁"
            binding.tvToggleArchive.text = "⬅️"
        } else {
            binding.fabAddStreak.show()
            binding.tvAppTitle.text = "🔥"
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
        val dialogBinding = pt.ipt.mystreaks.databinding.DialogAddStreakBinding.inflate(layoutInflater)
        val isEditing = streakToEdit != null

        var selectedHour: Int? = streakToEdit?.remindHour
        var selectedMinute: Int? = streakToEdit?.remindMinute

        // CARREGAR AS TAGS PARA AS SUGESTÕES
        // CARREGAR AS TAGS PARA AS SUGESTÕES
        lifecycleScope.launch(Dispatchers.IO) {
            val existingTags = database.streakDao().getAllTagsSync()
            withContext(Dispatchers.Main) {
                val arrayAdapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, existingTags)
                dialogBinding.etTag.setAdapter(arrayAdapter)
            }
        }

        // --- NOVO: GERAR OS QUADRADOS AUTOMATICAMENTE ---
        val weekDays = listOf("D", "S", "T", "Q", "Q", "S", "S")

        // 1. Quadrados Semanais
        for (i in 0 until 7) {
            val tb = android.widget.ToggleButton(this).apply {
                textOn = weekDays[i]
                textOff = weekDays[i]
                text = weekDays[i]
                textSize = 12f
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = android.widget.GridLayout.spec(i, 1f) // Distribui igualmente
                    setMargins(4, 4, 4, 4)
                }
                // Se estivermos a editar, pinta de azul os dias que já estavam selecionados
                isChecked = streakToEdit?.type == "S" && streakToEdit.notifyDays.contains(i + 1)
            }
            dialogBinding.gridWeekly.addView(tb)
        }

        // 2. Quadrados Mensais (1 a 31)
        for (i in 1..31) {
            val tb = android.widget.ToggleButton(this).apply {
                textOn = i.toString()
                textOff = i.toString()
                text = i.toString()
                textSize = 10f
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = android.widget.GridLayout.spec((i - 1) % 7, 1f) // 7 por linha
                    setMargins(2, 2, 2, 2)
                }
                // Se estivermos a editar, pinta os dias que já estavam selecionados
                isChecked = streakToEdit?.type == "M" && streakToEdit.notifyDays.contains(i)
            }
            dialogBinding.gridMonthly.addView(tb)
        }

        // REGRAS DE MOSTRAR/ESCONDER GRELHAS
        dialogBinding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.layoutReminderDetails.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        dialogBinding.rgFrequency.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbDaily) {
                dialogBinding.layoutSpecificDays.visibility = android.view.View.GONE
            } else {
                dialogBinding.layoutSpecificDays.visibility = android.view.View.VISIBLE
                if (checkedId == R.id.rbWeekly) {
                    dialogBinding.tvDaysHint.text = "Em que dias da semana?"
                    dialogBinding.gridWeekly.visibility = android.view.View.VISIBLE
                    dialogBinding.gridMonthly.visibility = android.view.View.GONE
                } else {
                    dialogBinding.tvDaysHint.text = "Em que dias do mês?"
                    dialogBinding.gridWeekly.visibility = android.view.View.GONE
                    dialogBinding.gridMonthly.visibility = android.view.View.VISIBLE
                }
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

        // PREENCHER DADOS SE FOR EDIÇÃO
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
                dialogBinding.btnTimePicker.text = String.format("Hora: %02d:%02d", selectedHour, selectedMinute)
            }
        }

        // MOSTRAR A JANELA E GUARDAR
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(if (isEditing) "Editar Atividade" else "Nova Atividade")
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar") { dialog, _ ->
                val activityName = dialogBinding.etActivityName.text.toString()
                if (activityName.isNotBlank()) {
                    val selectedTypeId = dialogBinding.rgFrequency.checkedRadioButtonId
                    val selectedRadioButton = dialogBinding.root.findViewById<android.widget.RadioButton>(selectedTypeId)
                    val type = when (selectedRadioButton.text.toString()) {
                        "Semanal" -> "S"
                        "Mensal" -> "M"
                        else -> "D"
                    }

                    // --- NOVO: LER QUAIS OS QUADRADOS QUE O UTILIZADOR CLICOU ---
                    val finalSelectedDays = mutableListOf<Int>()
                    if (dialogBinding.switchReminder.isChecked) {
                        if (type == "S") {
                            for (i in 0 until dialogBinding.gridWeekly.childCount) {
                                val tb = dialogBinding.gridWeekly.getChildAt(i) as android.widget.ToggleButton
                                if (tb.isChecked) finalSelectedDays.add(i + 1) // 1=Dom, 2=Seg...
                            }
                        } else if (type == "M") {
                            for (i in 0 until dialogBinding.gridMonthly.childCount) {
                                val tb = dialogBinding.gridMonthly.getChildAt(i) as android.widget.ToggleButton
                                if (tb.isChecked) finalSelectedDays.add(i + 1) // 1 a 31
                            }
                        }
                    }

                    if (!dialogBinding.switchReminder.isChecked) {
                        selectedHour = null
                        selectedMinute = null
                    }

                    val tagName = dialogBinding.etTag.text.toString().trim()
                    val finalTag = if (tagName.isNotEmpty()) tagName else null

                    if (isEditing) {
                        val updatedStreak = streakToEdit!!.copy(
                            name = activityName, type = type,
                            remindHour = selectedHour, remindMinute = selectedMinute,
                            notifyDays = finalSelectedDays, tag = finalTag // Guarda a lista de dias!
                        )
                        viewModel.update(updatedStreak)
                        logViewModel.registrarAcao("STREAK_EDIT", "Editou a atividade '$activityName'")
                    } else {
                        val newStreak = Streak(
                            name = activityName, type = type,
                            remindHour = selectedHour, remindMinute = selectedMinute,
                            notifyDays = finalSelectedDays, tag = finalTag // Guarda a lista de dias!
                        )
                        viewModel.insert(newStreak)
                        logViewModel.registrarAcao("STREAK_NOVA", "Criou a atividade '$activityName' ($type)")
                    }

                    // (O código de agendar o alarme virá no próximo passo)

                } else {
                    android.widget.Toast.makeText(this, "O nome não pode estar vazio", android.widget.Toast.LENGTH_SHORT).show()
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

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, streak.remindHour!!)
        calendar.set(Calendar.MINUTE, streak.remindMinute!!)
        calendar.set(Calendar.SECOND, 0)

        when (streak.type) {
            "D" -> {
                if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            "S" -> {
                val targetDay = streak.remindExtra ?: Calendar.MONDAY
                calendar.set(Calendar.DAY_OF_WEEK, targetDay)
                if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(Calendar.WEEK_OF_YEAR, 1)
            }
            "M" -> {
                val targetDay = streak.remindExtra ?: 1
                calendar.set(Calendar.DAY_OF_MONTH, targetDay)
                if (calendar.timeInMillis <= System.currentTimeMillis()) calendar.add(Calendar.MONTH, 1)
            }
        }

        try {
            alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } catch (e: SecurityException) {
            alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }
}