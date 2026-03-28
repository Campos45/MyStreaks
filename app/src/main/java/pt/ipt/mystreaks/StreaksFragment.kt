package pt.ipt.mystreaks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pt.ipt.mystreaks.databinding.FragmentStreaksBinding
import java.util.Calendar

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

        // 1. O Título Correto e a Lupa no Topo
        binding.tvAppTitle.text = "Streaks 🔥"
        binding.ivSearch.setOnClickListener {
            binding.etSearch.visibility = if (binding.etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // --- FORÇAR PRETO NA CAIXA DE PESQUISA (Fix do Dark Mode) ---
        binding.etSearch.setTextColor(android.graphics.Color.BLACK)
        binding.etSearch.setHintTextColor(android.graphics.Color.DKGRAY)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        adapter = StreakAdapter(
            onStreakCheckChanged = { streak, isChecked ->
                if (streak.isCompleted == isChecked) return@StreakAdapter
                if (isShowingArchive) {
                    Toast.makeText(requireContext(), "Restaura a atividade primeiro!", Toast.LENGTH_SHORT).show()
                    return@StreakAdapter
                }

                val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                val todayMidnight = cal.timeInMillis
                val updatedDates = streak.completedDates.toMutableList()
                var newCount = streak.count

                if (isChecked) {
                    if (!updatedDates.contains(todayMidnight)) {
                        val alreadyGotPointThisPeriod = updatedDates.any { pastDate ->
                            val cPast = Calendar.getInstance().apply { timeInMillis = pastDate }
                            val cNow = Calendar.getInstance()
                            when (streak.type) {
                                "D" -> false
                                "S" -> cPast.get(Calendar.WEEK_OF_YEAR) == cNow.get(Calendar.WEEK_OF_YEAR) && cPast.get(Calendar.YEAR) == cNow.get(Calendar.YEAR)
                                "M" -> cPast.get(Calendar.MONTH) == cNow.get(Calendar.MONTH) && cPast.get(Calendar.YEAR) == cNow.get(Calendar.YEAR)
                                else -> false
                            }
                        }
                        if (!alreadyGotPointThisPeriod) newCount++
                        updatedDates.add(todayMidnight)
                    }
                } else {
                    updatedDates.remove(todayMidnight)
                    val hasOtherCompletionsThisPeriod = updatedDates.any { pastDate ->
                        val cPast = Calendar.getInstance().apply { timeInMillis = pastDate }
                        val cNow = Calendar.getInstance()
                        when (streak.type) {
                            "D" -> false
                            "S" -> cPast.get(Calendar.WEEK_OF_YEAR) == cNow.get(Calendar.WEEK_OF_YEAR) && cPast.get(Calendar.YEAR) == cNow.get(Calendar.YEAR)
                            "M" -> cPast.get(Calendar.MONTH) == cNow.get(Calendar.MONTH) && cPast.get(Calendar.YEAR) == cNow.get(Calendar.YEAR)
                            else -> false
                        }
                    }
                    if (!hasOtherCompletionsThisPeriod && newCount > 0) newCount--
                }

                viewModel.update(streak.copy(count = newCount, isCompleted = isChecked, completedDates = updatedDates, currentStartDate = if (newCount == 1 && streak.count == 0) System.currentTimeMillis() else streak.currentStartDate))
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

        viewModel.activeStreaks.observe(viewLifecycleOwner) { activeList = it ?: emptyList(); if (!isShowingArchive) refreshUI() }
        viewModel.archivedStreaks.observe(viewLifecycleOwner) { archivedList = it ?: emptyList(); if (isShowingArchive) refreshUI() }

        binding.toggleGroupStreaks.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) { isShowingArchive = checkedId == R.id.btnArchivedStreaks; refreshUI() }
        }

        binding.fabAddStreak.setOnClickListener { showAddStreakDialog() }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { currentSearchQuery = s.toString(); refreshUI() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Ligar a Lupa e Filtro nas Streaks (Bug do duplo click corrigido)
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
        val dialogBinding = pt.ipt.mystreaks.databinding.DialogAddStreakBinding.inflate(layoutInflater)
        val isEditing = streakToEdit != null

        // --- FORÇAR PRETO NAS CAIXAS DE ADICIONAR STREAK (Fix Dark Mode) ---
        dialogBinding.etActivityName.setTextColor(android.graphics.Color.BLACK)
        dialogBinding.etActivityName.setHintTextColor(android.graphics.Color.DKGRAY)
        dialogBinding.etTag.setTextColor(android.graphics.Color.BLACK)
        dialogBinding.etTag.setHintTextColor(android.graphics.Color.DKGRAY)

        val streakTags = tagViewModel.allTags.value?.filter { it.type == "S" } ?: emptyList()
        dialogBinding.etTag.setAdapter(TagDropdownAdapter(requireContext(), streakTags))
        dialogBinding.etTag.setOnClickListener { dialogBinding.etTag.showDropDown() }
        dialogBinding.etTag.setOnItemClickListener { parent, _, position, _ -> dialogBinding.etTag.setText((parent.getItemAtPosition(position) as Tag).name, false) }

        var selectedHour = 9
        var selectedMinute = 0

        dialogBinding.switchReminder.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                val timePicker = android.app.TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
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
                        if (!exists) tagViewModel.insert(Tag(name = finalTag, color = "#757575", type = "S"))
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

        // Elementos do Calendário
        val tvMonthName = dialogView.findViewById<TextView>(R.id.tvMonthName)
        val rvCalendar = dialogView.findViewById<RecyclerView>(R.id.rvCalendar)
        val btnPrev = dialogView.findViewById<ImageView>(R.id.btnPrevMonth)
        val btnNext = dialogView.findViewById<ImageView>(R.id.btnNextMonth)

        // Elementos dos Recordes
        val tvRecordsList = dialogView.findViewById<TextView>(R.id.tvRecordsList)

        rvCalendar.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 7)

        val currentCal = Calendar.getInstance()
        val monthNames = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

        // 1. Função que atualiza o calendário ao mudar de mês
        fun updateCalendar() {
            tvMonthName.text = "${monthNames[currentCal.get(Calendar.MONTH)]} ${currentCal.get(Calendar.YEAR)}"

            val daysList = mutableListOf<CalendarDay>()
            val daysInMonth = currentCal.getActualMaximum(Calendar.DAY_OF_MONTH)

            for (i in 1..daysInMonth) {
                val check = Calendar.getInstance().apply {
                    set(currentCal.get(Calendar.YEAR), currentCal.get(Calendar.MONTH), i, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                daysList.add(CalendarDay(i.toString(), streak.completedDates.contains(check.timeInMillis), true))
            }
            rvCalendar.adapter = CalendarAdapter(daysList)
        }

        // Ligar Setas
        btnPrev.setOnClickListener { currentCal.add(Calendar.MONTH, -1); updateCalendar() }
        btnNext.setOnClickListener { currentCal.add(Calendar.MONTH, 1); updateCalendar() }

        // Atualiza a primeira vez
        updateCalendar()

        // 2. Cálculo dos Recordes para o tvRecordsList
        var maxStreak = 0
        var currentStreakCalc = 0
        var previousDate: Long? = null

        val sortedDates = streak.completedDates.sorted()
        for (dateMillis in sortedDates) {
            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }

            if (previousDate == null) {
                currentStreakCalc = 1
            } else {
                val prevCal = Calendar.getInstance().apply { timeInMillis = previousDate!! }

                // Ignorar as horas para o cálculo ser exato
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                prevCal.set(Calendar.HOUR_OF_DAY, 0); prevCal.set(Calendar.MINUTE, 0); prevCal.set(Calendar.SECOND, 0); prevCal.set(Calendar.MILLISECOND, 0)

                val diffMillis = cal.timeInMillis - prevCal.timeInMillis
                val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)

                when (streak.type) {
                    "D" -> {
                        if (diffDays == 1L) currentStreakCalc++ else if (diffDays > 1L) currentStreakCalc = 1
                    }
                    "S" -> {
                        val calWeek = cal.get(Calendar.WEEK_OF_YEAR)
                        val prevWeek = prevCal.get(Calendar.WEEK_OF_YEAR)
                        if (calWeek == prevWeek + 1 || (prevWeek == 52 && calWeek == 1)) currentStreakCalc++ else if (calWeek != prevWeek) currentStreakCalc = 1
                    }
                    "M" -> {
                        val calMonth = cal.get(Calendar.MONTH)
                        val prevMonth = prevCal.get(Calendar.MONTH)
                        if (calMonth == prevMonth + 1 || (prevMonth == 11 && calMonth == 0)) currentStreakCalc++ else if (calMonth != prevMonth) currentStreakCalc = 1
                    }
                }
            }
            if (currentStreakCalc > maxStreak) maxStreak = currentStreakCalc
            previousDate = dateMillis
        }

        val typeText = when(streak.type) {
            "S" -> "semanas"
            "M" -> "meses"
            else -> "dias"
        }

        // Escrever os dados na TextView dos recordes
        tvRecordsList.text = "🔥 Maior sequência: $maxStreak $typeText\n⚡ Sequência atual: ${streak.count} $typeText"

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Fechar", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}