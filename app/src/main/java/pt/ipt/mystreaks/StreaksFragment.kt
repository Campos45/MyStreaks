package pt.ipt.mystreaks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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
                if (isChecked) { if (!updatedDates.contains(todayMidnight)) updatedDates.add(todayMidnight) }
                else { updatedDates.remove(todayMidnight) }

                var newCount = streak.count
                if (isChecked) newCount++ else if (newCount > 0) newCount--

                viewModel.update(streak.copy(count = newCount, isCompleted = isChecked, completedDates = updatedDates))
            },
            onHistoryClicked = { streak -> showStreakHistoryDialog(streak) },
            onEditClicked = { streak -> showAddStreakDialog(streak) },
            onArchiveClicked = { streak ->
                viewModel.update(streak.copy(isArchived = !isShowingArchive))
            },
            onDeleteClicked = { streak ->
                viewModel.delete(streak)
            }
        )

        binding.recyclerViewStreaks.adapter = adapter
        binding.recyclerViewStreaks.layoutManager = LinearLayoutManager(requireContext())

        tagViewModel.allTags.observe(viewLifecycleOwner) { allTags ->
            val streakTags = allTags.filter { it.type == "S" }
            adapter.setTags(streakTags)
        }

        viewModel.activeStreaks.observe(viewLifecycleOwner) { streaks ->
            activeList = streaks ?: emptyList()
            if (!isShowingArchive) refreshUI()
        }

        viewModel.archivedStreaks.observe(viewLifecycleOwner) { streaks ->
            archivedList = streaks ?: emptyList()
            if (isShowingArchive) refreshUI()
        }

        binding.toggleGroupStreaks.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isShowingArchive = checkedId == R.id.btnArchivedStreaks
                refreshUI()
            }
        }

        binding.fabAddStreak.setOnClickListener { showAddStreakDialog() }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                refreshUI()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.ivFilter.setOnClickListener {
            val tagsS = tagViewModel.allTags.value?.filter { it.type == "S" } ?: emptyList()
            val options = arrayOf("🌟 Todas") + tagsS.map { it.name }.toTypedArray()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filtrar Categoria")
                .setItems(options) { _, which ->
                    currentTagFilter = if (which == 0) null else options[which]
                    refreshUI()
                }.show()
        }
        tagViewModel.allTags.observe(viewLifecycleOwner) { allTags ->
            val streakTags = allTags.filter { it.type == "S" }

            // ISTO VAI APARECER NO TEU LOGCAT:
            println("DEBUG_FRAGMENT: Recebi ${allTags.size} tags da BD. Destas, ${streakTags.size} são do tipo S.")

            adapter.setTags(streakTags)
        }
    }

    private fun refreshUI() {
        val baseList = if (isShowingArchive) archivedList else activeList
        val filtered = baseList.filter {
            (currentTagFilter == null || it.tag == currentTagFilter) &&
                    (currentSearchQuery.isEmpty() || it.name.contains(currentSearchQuery, ignoreCase = true))
        }
        adapter.submitList(filtered)
        binding.layoutEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddStreakDialog(streakToEdit: Streak? = null) {
        val dialogBinding = pt.ipt.mystreaks.databinding.DialogAddStreakBinding.inflate(layoutInflater)
        val isEditing = streakToEdit != null

        val streakTags = tagViewModel.allTags.value?.filter { it.type == "S" } ?: emptyList()
        dialogBinding.etTag.setAdapter(TagDropdownAdapter(requireContext(), streakTags))
        dialogBinding.etTag.setOnClickListener { dialogBinding.etTag.showDropDown() }

        dialogBinding.etTag.setOnItemClickListener { parent, _, position, _ ->
            val selectedTag = parent.getItemAtPosition(position) as Tag
            // Outro "false" aqui para quando escolheres uma da lista, ele não a esconder a seguir
            dialogBinding.etTag.setText(selectedTag.name, false)
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
                val tagName = dialogBinding.etTag.text.toString().trim() // Limpa espaços aqui!
                val finalTag = if (tagName.isNotEmpty()) tagName else null

                if (name.isNotBlank()) {
                    // Criar a tag na BD se ela não existir (para garantir a cor cinzenta inicial)
                    if (finalTag != null) {
                        val exists = tagViewModel.allTags.value?.any { it.name.trim().equals(finalTag, true) } ?: false
                        if (!exists) {
                            tagViewModel.insert(Tag(name = finalTag, color = "#757575", type = "S"))
                        }
                    }

                    if (isEditing) {
                        // AQUI ESTAVA O ERRO: Temos de passar o finalTag no copy!
                        val streakAtualizada = streakToEdit!!.copy(
                            name = name,
                            tag = finalTag
                        )
                        viewModel.update(streakAtualizada)
                        Toast.makeText(requireContext(), "Atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.insert(Streak(name = name, tag = finalTag, type = "D"))
                    }
                }
            }.show()
    }

    private fun showStreakHistoryDialog(streak: Streak) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_calendar, null)
        val tvMonthName = dialogView.findViewById<android.widget.TextView>(R.id.tvMonthName)
        val rvCalendar = dialogView.findViewById<RecyclerView>(R.id.rvCalendar)

        val cal = Calendar.getInstance()
        val monthNames = arrayOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

        tvMonthName.text = "${monthNames[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"

        val daysList = mutableListOf<CalendarDay>()
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..daysInMonth) {
            val check = Calendar.getInstance().apply { set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), i, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
            daysList.add(CalendarDay(i.toString(), streak.completedDates.contains(check.timeInMillis), true))
        }

        rvCalendar.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 7)
        rvCalendar.adapter = CalendarAdapter(daysList)

        MaterialAlertDialogBuilder(requireContext()).setView(dialogView).setPositiveButton("Fechar", null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}