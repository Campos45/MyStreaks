package pt.ipt.mystreaks.ui.streak

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pt.ipt.mystreaks.ui.logs.LogViewModel
import pt.ipt.mystreaks.ui.logs.LogViewModelFactory
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.ui.settings.TagDropdownAdapter
import pt.ipt.mystreaks.ui.settings.TagViewModel
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.data.model.Streak
import pt.ipt.mystreaks.data.model.Tag
import pt.ipt.mystreaks.data.repository.LogRepository
import pt.ipt.mystreaks.data.repository.StreakRepository
import pt.ipt.mystreaks.databinding.DialogAddStreakBinding
import pt.ipt.mystreaks.databinding.FragmentStreaksBinding
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class StreaksFragment : Fragment(R.layout.fragment_streaks) {

    private var _binding: FragmentStreaksBinding? = null
    private val binding get() = _binding!!

    private val database by lazy { AppDatabase.getDatabase(requireContext()) }
    private val repository by lazy { StreakRepository(database.streakDao()) }
    private val viewModel: StreakViewModel by viewModels { StreakViewModelFactory(repository) }
    private val logRepository by lazy { LogRepository(database.appLogDao()) }
    private val logViewModel: LogViewModel by viewModels { LogViewModelFactory(logRepository) }
    private val tagViewModel: TagViewModel by viewModels()

    private var isShowingArchive = false
    private var activeList = emptyList<Streak>()
    private var archivedList = emptyList<Streak>()
    private lateinit var adapter: StreakAdapter
    private var currentTagFilter: String? = null
    private var currentSearchQuery: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentStreaksBinding.bind(view)

        binding.tvAppTitle.text = "Streaks 🔥"
        binding.ivSearch.setOnClickListener {
            binding.etSearch.visibility = if (binding.etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.etSearch.setTextColor(Color.BLACK)
        binding.etSearch.setHintTextColor(Color.DKGRAY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        adapter = StreakAdapter(
            onStreakCheckChanged = { streak, isChecked ->
                if (streak.isCompleted == isChecked) return@StreakAdapter
                if (isShowingArchive) {
                    Toast.makeText(
                        requireContext(),
                        "Restaura a atividade primeiro!",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@StreakAdapter
                }

                // API MODERNA: Pega no dia de hoje à meia-noite exata
                val todayLd = LocalDate.now()
                val todayMidnight =
                    todayLd.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val updatedDates = streak.completedDates.toMutableList()
                var newCount = streak.count

                if (isChecked) {
                    if (!updatedDates.contains(todayMidnight)) {
                        val alreadyGotPointThisPeriod = updatedDates.any { pastDate ->
                            val pastDateLd =
                                Instant.ofEpochMilli(pastDate).atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                            when (streak.type) {
                                "D" -> false
                                "S" -> ChronoUnit.WEEKS.between(
                                    pastDateLd.with(DayOfWeek.MONDAY),
                                    todayLd.with(DayOfWeek.MONDAY)
                                ) == 0L

                                "M" -> ChronoUnit.MONTHS.between(
                                    pastDateLd.withDayOfMonth(1),
                                    todayLd.withDayOfMonth(1)
                                ) == 0L

                                else -> false
                            }
                        }
                        if (!alreadyGotPointThisPeriod) newCount++
                        updatedDates.add(todayMidnight)
                    }
                } else {
                    updatedDates.remove(todayMidnight)
                    val hasOtherCompletionsThisPeriod = updatedDates.any { pastDate ->
                        val pastDateLd =
                            Instant.ofEpochMilli(pastDate).atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        when (streak.type) {
                            "D" -> false
                            "S" -> ChronoUnit.WEEKS.between(
                                pastDateLd.with(DayOfWeek.MONDAY),
                                todayLd.with(DayOfWeek.MONDAY)
                            ) == 0L

                            "M" -> ChronoUnit.MONTHS.between(
                                pastDateLd.withDayOfMonth(1),
                                todayLd.withDayOfMonth(1)
                            ) == 0L

                            else -> false
                        }
                    }
                    if (!hasOtherCompletionsThisPeriod && newCount > 0) newCount--
                }

                viewModel.update(
                    streak.copy(
                        count = newCount,
                        isCompleted = isChecked,
                        completedDates = updatedDates,
                        currentStartDate = if (newCount == 1 && streak.count == 0) System.currentTimeMillis() else streak.currentStartDate
                    )
                )
            },
            onHistoryClicked = { showStreakHistoryDialog(it) },
            onEditClicked = { showAddStreakDialog(it) },
            onArchiveClicked = { viewModel.update(it.copy(isArchived = !isShowingArchive)) },
            onDeleteClicked = { viewModel.delete(it) }
        )

        binding.recyclerViewStreaks.adapter = adapter
        binding.recyclerViewStreaks.layoutManager = LinearLayoutManager(requireContext())

        tagViewModel.allTags.observe(viewLifecycleOwner) { allTags ->
            adapter.setTags(allTags.filter { it.type == "S" })
        }

        viewModel.activeStreaks.observe(viewLifecycleOwner) { streaks ->
            activeList = streaks ?: emptyList()

            // --- INÍCIO DO GUARDA-COSTAS (AUTO-RESET) ---
            val todayLd = LocalDate.now()

            activeList.forEach { streak ->
                // Só vale a pena verificar se a streak tiver count > 0 e já tiver sido feita alguma vez
                if (streak.count > 0 && streak.completedDates.isNotEmpty()) {
                    val lastDateMillis = streak.completedDates.maxOrNull() ?: 0L

                    if (lastDateMillis > 0L) {
                        val lastDateLd = Instant.ofEpochMilli(lastDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()

                        // A Lógica Implacável do Tempo
                        val isExpired = when (streak.type) {
                            "D" -> ChronoUnit.DAYS.between(lastDateLd, todayLd) > 1
                            "S" -> ChronoUnit.WEEKS.between(lastDateLd.with(DayOfWeek.MONDAY), todayLd.with(DayOfWeek.MONDAY)) > 1
                            "M" -> ChronoUnit.MONTHS.between(lastDateLd.withDayOfMonth(1), todayLd.withDayOfMonth(1)) > 1
                            else -> false
                        }

                        // Se o prazo expirou, reset ao contador para 0 (mantendo o histórico do calendário intacto!)
                        if (isExpired) {
                            viewModel.update(streak.copy(count = 0))
                        }
                    }
                }
            }
            // --- FIM DO GUARDA-COSTAS ---

            if (!isShowingArchive) refreshUI()
        }
        viewModel.archivedStreaks.observe(viewLifecycleOwner) { archivedList = it ?: emptyList(); if (isShowingArchive) refreshUI() }

        binding.toggleGroupStreaks.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) { isShowingArchive = checkedId == R.id.btnArchivedStreaks; refreshUI() }
        }

        binding.fabAddStreak.setOnClickListener { showAddStreakDialog() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { currentSearchQuery = s.toString(); refreshUI() }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.ivFilter.setOnClickListener {
            val tagsS = tagViewModel.allTags.value?.filter { it.type == "S" } ?: emptyList()
            val options = arrayOf("🌟 Todas", "🚫 Sem Categoria") + tagsS.map { it.name }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filtrar Categoria")
                .setItems(options) { _, which ->
                    currentTagFilter = when(which){
                        0 -> null
                        1 -> "NONE"
                        else -> options[which]
                    }
                    refreshUI()
                }.show()
        }
    }

    private fun refreshUI() {
        val baseList = if (isShowingArchive) archivedList else activeList
        val filtered = baseList.filter {
            val matchesSearch = currentSearchQuery.isEmpty() || it.name.contains(currentSearchQuery, ignoreCase = true)
            val matchesTag = when(currentTagFilter){
                null -> true
                "NONE" -> it.tag.isNullOrBlank()
                else -> it.tag == currentTagFilter
            }
            matchesSearch && matchesTag
        }
        adapter.submitList(filtered)
        binding.layoutEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddStreakDialog(streakToEdit: Streak? = null) {
        val dialogBinding = DialogAddStreakBinding.inflate(layoutInflater)
        val isEditing = streakToEdit != null

        dialogBinding.etActivityName.setTextColor(Color.BLACK)
        dialogBinding.etActivityName.setHintTextColor(Color.DKGRAY)
        dialogBinding.etTag.setTextColor(Color.BLACK)
        dialogBinding.etTag.setHintTextColor(Color.DKGRAY)

        val streakTags = tagViewModel.allTags.value?.filter { it.type == "S" } ?: emptyList()
        dialogBinding.etTag.setAdapter(TagDropdownAdapter(requireContext(), streakTags))
        dialogBinding.etTag.setOnClickListener { dialogBinding.etTag.showDropDown() }
        dialogBinding.etTag.setOnItemClickListener { parent, _, position, _ -> dialogBinding.etTag.setText((parent.getItemAtPosition(position) as Tag).name, false) }

        var selectedHour = 9
        var selectedMinute = 0

        dialogBinding.switchReminder.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                val timePicker = TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
                    selectedHour = hourOfDay
                    selectedMinute = minute
                    buttonView.text = String.format("Aviso às %02d:%02d ⏰", hourOfDay, minute)
                }, selectedHour, selectedMinute, true)

                timePicker.setOnCancelListener {
                    buttonView.isChecked = false
                }
                timePicker.show()
            } else {
                buttonView.text = "Notificação Personalizada ⏰"
            }
        }

        if (isEditing) {
            dialogBinding.etActivityName.setText(streakToEdit!!.name)
            dialogBinding.etTag.setText(streakToEdit.tag ?: "", false)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isEditing) "Editar" else "Nova Atividade")
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar") { _, _ ->
                val name = dialogBinding.etActivityName.text.toString().trim()
                val tagName = dialogBinding.etTag.text.toString().trim()
                val finalTag = if (tagName.isNotEmpty()) tagName else null

                if (name.isNotBlank()) {
                    if (finalTag != null) {
                        val exists = tagViewModel.allTags.value?.any { it.name.trim().equals(finalTag, true) } ?: false
                        if (!exists) tagViewModel.insert(
                            Tag(
                                name = finalTag,
                                color = "#757575",
                                type = "S"
                            )
                        )
                    }

                    if (isEditing) {
                        viewModel.update(streakToEdit!!.copy(name = name, tag = finalTag))
                        Toast.makeText(requireContext(), "Atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.insert(Streak(name = name, tag = finalTag, type = "D"))
                    }
                }
            }.show()
    }

    private fun showStreakHistoryDialog(streak: Streak) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_calendar, null)

        val tvMonthName = dialogView.findViewById<TextView>(R.id.tvMonthName)
        val rvCalendar = dialogView.findViewById<RecyclerView>(R.id.rvCalendar)
        val btnPrev = dialogView.findViewById<ImageView>(R.id.btnPrevMonth)
        val btnNext = dialogView.findViewById<ImageView>(R.id.btnNextMonth)
        val tvRecordsList = dialogView.findViewById<TextView>(R.id.tvRecordsList)

        rvCalendar.layoutManager = GridLayoutManager(requireContext(), 7)

        // API MODERNA: Usa YearMonth em vez de Calendar
        var currentMonth = YearMonth.now()
        val monthNames = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

        fun updateCalendar() {
            tvMonthName.text = "${monthNames[currentMonth.monthValue - 1]} ${currentMonth.year}"

            val daysList = mutableListOf<CalendarDay>()
            val daysInMonth = currentMonth.lengthOfMonth()

            for (i in 1..daysInMonth) {
                val checkDateMillis = currentMonth.atDay(i).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                daysList.add(
                    CalendarDay(
                        i.toString(),
                        streak.completedDates.contains(checkDateMillis),
                        true
                    )
                )
            }
            rvCalendar.adapter = CalendarAdapter(daysList)
        }

        btnPrev.setOnClickListener { currentMonth = currentMonth.minusMonths(1); updateCalendar() }
        btnNext.setOnClickListener { currentMonth = currentMonth.plusMonths(1); updateCalendar() }
        updateCalendar()

        // 2. Cálculo dos Recordes Ultra Seguro
        var maxStreak = 0
        var currentStreakCalc = 0
        var previousDateLd: LocalDate? = null

        val sortedDates = streak.completedDates.sorted()
        for (dateMillis in sortedDates) {
            val calLd = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()

            if (previousDateLd == null) {
                currentStreakCalc = 1
            } else {
                when (streak.type) {
                    "D" -> {
                        val diffDays = ChronoUnit.DAYS.between(previousDateLd!!, calLd)
                        if (diffDays == 1L) currentStreakCalc++ else if (diffDays > 1L) currentStreakCalc = 1
                    }
                    "S" -> {
                        val diffWeeks = ChronoUnit.WEEKS.between(previousDateLd!!.with(DayOfWeek.MONDAY), calLd.with(DayOfWeek.MONDAY))
                        if (diffWeeks == 1L) currentStreakCalc++ else if (diffWeeks > 1L) currentStreakCalc = 1
                    }
                    "M" -> {
                        val diffMonths = ChronoUnit.MONTHS.between(previousDateLd!!.withDayOfMonth(1), calLd.withDayOfMonth(1))
                        if (diffMonths == 1L) currentStreakCalc++ else if (diffMonths > 1L) currentStreakCalc = 1
                    }
                }
            }
            if (currentStreakCalc > maxStreak) maxStreak = currentStreakCalc
            previousDateLd = calLd
        }

        val typeText = when(streak.type) {
            "S" -> "semanas"
            "M" -> "meses"
            else -> "dias"
        }

        tvRecordsList.text = "🔥 Maior sequência: $maxStreak $typeText\n⚡ Sequência atual: ${streak.count} $typeText"

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Fechar", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}