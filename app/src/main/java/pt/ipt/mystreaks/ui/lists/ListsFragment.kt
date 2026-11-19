package pt.ipt.mystreaks.ui.lists

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import pt.ipt.mystreaks.utils.EditTextUndoRedo
import pt.ipt.mystreaks.utils.HexagonColorPickerView
import pt.ipt.mystreaks.ui.logs.LogViewModel
import pt.ipt.mystreaks.ui.logs.LogViewModelFactory
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.ui.settings.TagDropdownAdapter
import pt.ipt.mystreaks.ui.settings.TagViewModel
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.data.model.MyList
import pt.ipt.mystreaks.data.model.Tag
import pt.ipt.mystreaks.data.repository.LogRepository
import pt.ipt.mystreaks.data.repository.MyListRepository
import pt.ipt.mystreaks.databinding.FragmentListsBinding
import pt.ipt.mystreaks.utils.setInfoTooltip

class ListsFragment : Fragment(R.layout.fragment_lists) {

    private var _binding: FragmentListsBinding? = null
    private val binding get() = _binding!!

    private val database by lazy { AppDatabase.getDatabase(requireContext()) }
    private val repository by lazy { MyListRepository(database.myListDao()) }
    private val viewModel: MyListViewModel by viewModels { MyListViewModelFactory(repository) }
    private val logRepository by lazy { LogRepository(database.appLogDao()) }
    private val logViewModel: LogViewModel by viewModels { LogViewModelFactory(logRepository) }
    private val tagViewModel: TagViewModel by viewModels()

    private lateinit var adapter: MyListAdapter
    private var isShowingArchive = false
    private var currentSearchQuery: String = ""
    private var currentTagFilter: String = "ALL"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListsBinding.bind(view)

        adapter = MyListAdapter(
            onEditClicked = { openEditList(it) }
        )

        binding.recyclerViewLists.adapter = adapter
        binding.recyclerViewLists.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL).apply {
            gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
        }

        tagViewModel.allTags.observe(viewLifecycleOwner) { allTags ->
            adapter.setTags(allTags.filter { it.type == "L" })
        }

        viewModel.activeLists.observe(viewLifecycleOwner) { if (!isShowingArchive) updateUI(it) }
        viewModel.archivedLists.observe(viewLifecycleOwner) { if (isShowingArchive) updateUI(it) }

        binding.toggleGroupLists.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isShowingArchive = checkedId == R.id.btnArchivedLists
                val targetList = if (isShowingArchive) viewModel.archivedLists.value else viewModel.activeLists.value
                updateUI(targetList ?: emptyList())
            }
        }

        binding.fabAddList.setOnClickListener { openEditList(null) }

        binding.ivSearch.setOnClickListener {
            binding.etSearch.visibility = if (binding.etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Tooltips informativos nos botões das Listas
        binding.ivFilter.setInfoTooltip(R.string.tooltip_filter)
        binding.ivSearch.setInfoTooltip(R.string.tooltip_search)
        binding.fabAddList.setInfoTooltip(R.string.tooltip_add_list)
        binding.btnActiveLists.setInfoTooltip(R.string.tooltip_tab_active)
        binding.btnArchivedLists.setInfoTooltip(R.string.tooltip_tab_archived)

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                val targetList = if (isShowingArchive) viewModel.archivedLists.value else viewModel.activeLists.value
                updateUI(targetList ?: emptyList())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.ivFilter.setOnClickListener {
            val listTags = tagViewModel.allTags.value?.filter { it.type == "L" } ?: emptyList()
            if (listTags.isEmpty()) {
                Toast.makeText(requireContext(), "Ainda não tens categorias nas listas.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val filterOptions = mutableListOf("🌟 Todas", "🚫 Sem Categoria")
            filterOptions.addAll(listTags.map { it.name })

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filtrar por Categoria")
                .setItems(filterOptions.toTypedArray()) { _, which ->
                    currentTagFilter = when (which) {
                        0 -> "ALL"
                        1 -> "NONE"
                        else -> filterOptions[which]
                    }
                    val targetList = if (isShowingArchive) viewModel.archivedLists.value else viewModel.activeLists.value
                    updateUI(targetList ?: emptyList())
                }
                .show()
        }
    }

    private fun updateUI(lists: List<MyList>?) {
        val safeList = lists ?: emptyList()

        val filteredLists = safeList.filter { list ->
            val matchesSearch = currentSearchQuery.isEmpty() ||
                    list.name.contains(currentSearchQuery, ignoreCase = true) ||
                    list.content.contains(currentSearchQuery, ignoreCase = true)

            val matchesTag = when (currentTagFilter) {
                "ALL" -> true
                "NONE" -> list.tag.isNullOrBlank()
                else -> list.tag == currentTagFilter
            }
            matchesSearch && matchesTag
        }

        adapter.submitList(filteredLists)

        binding.recyclerViewLists.visibility = if (filteredLists.isEmpty()) View.GONE else View.VISIBLE
        binding.layoutEmptyState.visibility = if (filteredLists.isEmpty()) View.VISIBLE else View.GONE

        // Empty state contextual e elegante
        if (filteredLists.isEmpty()) {
            if (currentSearchQuery.isNotEmpty()) {
                binding.tvEmptyEmoji.text = "🔍"
                binding.tvEmptyTitle.text = "Nenhum resultado"
                binding.tvEmptyDesc.text = "Não encontramos notas para '$currentSearchQuery'"
            } else if (isShowingArchive) {
                binding.tvEmptyEmoji.text = "📁"
                binding.tvEmptyTitle.text = "Arquivo Vazio"
                binding.tvEmptyDesc.text = "As notas que arquivares vão aparecer aqui."
            } else {
                binding.tvEmptyEmoji.text = "📝"
                binding.tvEmptyTitle.text = "Sem Notas"
                binding.tvEmptyDesc.text = "Clica no botão para criares a tua primeira nota!"
            }
        }

        if (isShowingArchive) binding.fabAddList.hide() else binding.fabAddList.show()
    }

    private fun openEditList(listToEdit: MyList? = null) {
        val intent = Intent(requireContext(), EditListActivity::class.java).apply {
            if (listToEdit != null) {
                putExtra(EditListActivity.EXTRA_LIST_ID, listToEdit.id)
            }
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
