package pt.ipt.mystreaks.ui.lists

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
            onEditClicked = { showAddListDialog(it) }
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

        binding.fabAddList.setOnClickListener { showAddListDialog() }

        binding.ivSearch.setOnClickListener {
            binding.etSearch.visibility = if (binding.etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.etSearch.setTextColor(Color.BLACK)
        binding.etSearch.setHintTextColor(Color.DKGRAY)

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

    private fun showAddListDialog(listToEdit: MyList? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_list, null)
        val etListName = dialogView.findViewById<TextInputEditText>(R.id.etListName)
        val etListContent = dialogView.findViewById<TextInputEditText>(R.id.etListContent)
        val etTag = dialogView.findViewById<AutoCompleteTextView>(R.id.etTag)

        etListName.setTextColor(Color.BLACK)
        etListName.setHintTextColor(Color.DKGRAY)

        etListContent.setTextColor(Color.BLACK)
        etListContent.setHintTextColor(Color.DKGRAY)

        etTag.setTextColor(Color.BLACK)
        etTag.setHintTextColor(Color.DKGRAY)

        val btnPin = dialogView.findViewById<ImageButton>(R.id.btnPin)
        val btnArchiveList = dialogView.findViewById<ImageButton>(R.id.btnArchiveList)
        val btnDeleteList = dialogView.findViewById<ImageButton>(R.id.btnDeleteList)

        var currentBgColor = listToEdit?.backgroundColor ?: "#FFFFFF"
        dialogView.setBackgroundColor(Color.parseColor(currentBgColor))

        val listTags = tagViewModel.allTags.value?.filter { it.type == "L" } ?: emptyList()
        etTag.setAdapter(TagDropdownAdapter(requireContext(), listTags))
        etTag.setOnClickListener { etTag.showDropDown() }
        etTag.setOnItemClickListener { parent, _, position, _ ->
            etTag.setText((parent.getItemAtPosition(position) as Tag).name, false)
        }

        val undoRedo = EditTextUndoRedo(etListContent)
        dialogView.findViewById<ImageButton>(R.id.btnUndo).setOnClickListener { undoRedo.undo() }
        dialogView.findViewById<ImageButton>(R.id.btnRedo).setOnClickListener { undoRedo.redo() }

        var isBoldActive = false
        var isItalicActive = false
        var isUnderlineActive = false
        var currentTextSizeFactor = 1.0f
        var currentTextColor: Int? = null

        fun updateButtonStates() {
            dialogView.findViewById<ImageButton>(R.id.btnBold).apply {
                alpha = if (isBoldActive) 1f else 0.45f
                setBackgroundColor(if (isBoldActive) Color.parseColor("#E0E0E0") else Color.TRANSPARENT)
            }
            dialogView.findViewById<ImageButton>(R.id.btnItalic).apply {
                alpha = if (isItalicActive) 1f else 0.45f
                setBackgroundColor(if (isItalicActive) Color.parseColor("#E0E0E0") else Color.TRANSPARENT)
            }
            dialogView.findViewById<ImageButton>(R.id.btnUnderline).apply {
                alpha = if (isUnderlineActive) 1f else 0.45f
                setBackgroundColor(if (isUnderlineActive) Color.parseColor("#E0E0E0") else Color.TRANSPARENT)
            }
            dialogView.findViewById<ImageButton>(R.id.btnTextColor).apply {
                imageTintList = if (currentTextColor != null) ColorStateList.valueOf(currentTextColor!!) else ColorStateList.valueOf(Color.BLACK)
            }
        }
        updateButtonStates()

        etListContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null) return
                val cursorPosition = etListContent.selectionStart
                if (cursorPosition <= 0) return

                if (isBoldActive) s.setSpan(StyleSpan(Typeface.BOLD), cursorPosition - 1, cursorPosition, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isItalicActive) s.setSpan(StyleSpan(Typeface.ITALIC), cursorPosition - 1, cursorPosition, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isUnderlineActive) s.setSpan(UnderlineSpan(), cursorPosition - 1, cursorPosition, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (currentTextSizeFactor != 1.0f) s.setSpan(RelativeSizeSpan(currentTextSizeFactor), cursorPosition - 1, cursorPosition, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (currentTextColor != null) s.setSpan(ForegroundColorSpan(currentTextColor!!), cursorPosition - 1, cursorPosition, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        })

        dialogView.findViewById<ImageButton>(R.id.btnFormat).setOnClickListener {
            val formatToolbar = dialogView.findViewById<View>(R.id.formatToolbar)
            formatToolbar.visibility = if (formatToolbar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        dialogView.findViewById<ImageButton>(R.id.btnBold).setOnClickListener {
            isBoldActive = !isBoldActive
            updateButtonStates()
            if (etListContent.hasSelection()) {
                val start = etListContent.selectionStart
                val end = etListContent.selectionEnd
                val editable = etListContent.text
                if (editable != null) {
                    editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        dialogView.findViewById<ImageButton>(R.id.btnItalic).setOnClickListener {
            isItalicActive = !isItalicActive
            updateButtonStates()
            if (etListContent.hasSelection()) {
                val start = etListContent.selectionStart
                val end = etListContent.selectionEnd
                val editable = etListContent.text
                if (editable != null) {
                    editable.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        dialogView.findViewById<ImageButton>(R.id.btnUnderline).setOnClickListener {
            isUnderlineActive = !isUnderlineActive
            updateButtonStates()
            if (etListContent.hasSelection()) {
                val start = etListContent.selectionStart
                val end = etListContent.selectionEnd
                val editable = etListContent.text
                if (editable != null) {
                    editable.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        dialogView.findViewById<View>(R.id.btnTitle1).setOnClickListener {
            currentTextSizeFactor = 1.4f
            updateButtonStates()
            if (etListContent.hasSelection()) {
                val start = etListContent.selectionStart
                val end = etListContent.selectionEnd
                val editable = etListContent.text
                if (editable != null) {
                    editable.setSpan(RelativeSizeSpan(1.4f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        dialogView.findViewById<View>(R.id.btnTitle2).setOnClickListener {
            currentTextSizeFactor = 1.2f
            updateButtonStates()
            if (etListContent.hasSelection()) {
                val start = etListContent.selectionStart
                val end = etListContent.selectionEnd
                val editable = etListContent.text
                if (editable != null) {
                    editable.setSpan(RelativeSizeSpan(1.2f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        dialogView.findViewById<View>(R.id.btnNormalText).setOnClickListener {
            currentTextSizeFactor = 1.0f
            updateButtonStates()
            if (etListContent.hasSelection()) {
                val start = etListContent.selectionStart
                val end = etListContent.selectionEnd
                val editable = etListContent.text
                if (editable != null) {
                    editable.setSpan(RelativeSizeSpan(1.0f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        dialogView.findViewById<ImageButton>(R.id.btnClearFormat).setOnClickListener {
            isBoldActive = false
            isItalicActive = false
            isUnderlineActive = false
            currentTextSizeFactor = 1.0f
            currentTextColor = null
            if (etListContent.hasSelection()) {
                val start = etListContent.selectionStart
                val end = etListContent.selectionEnd
                val editable = etListContent.text
                if (editable != null) {
                    val spans = editable.getSpans(start, end, Object::class.java)
                    for (span in spans) {
                        if (span is StyleSpan || span is UnderlineSpan || span is RelativeSizeSpan || span is ForegroundColorSpan) {
                            editable.removeSpan(span)
                        }
                    }
                }
            }
            updateButtonStates()
            Toast.makeText(context, "Formatação reposta", Toast.LENGTH_SHORT).show()
        }

        fun applyColorToSelection(colorInt: Int) {
            if (etListContent.hasSelection()) {
                val start = etListContent.selectionStart
                val end = etListContent.selectionEnd
                val editable = etListContent.text
                if (editable != null) {
                    editable.setSpan(ForegroundColorSpan(colorInt), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        dialogView.findViewById<ImageButton>(R.id.btnTextColor).setOnClickListener {
            val colors = arrayOf("Azul", "Verde", "Vermelho", "Laranja", "Preto", "Personalizada...")
            val hexColors = arrayOf("#2196F3", "#4CAF50", "#F44336", "#FF9800", "#000000")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cor do Texto")
                .setItems(colors) { _, which ->
                    if (which == colors.size - 1) {
                        val pickerDialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
                        val colorPicker = pickerDialogView.findViewById<HexagonColorPickerView>(R.id.hexagonPicker)
                        val cardPreview = pickerDialogView.findViewById<MaterialCardView>(R.id.cardColorPreview)
                        val tvHex = pickerDialogView.findViewById<TextView>(R.id.tvHexPreview)

                        colorPicker.onColorChangeListener = { hex ->
                            cardPreview.setCardBackgroundColor(Color.parseColor(hex))
                            tvHex.text = hex
                        }

                        MaterialAlertDialogBuilder(requireContext())
                            .setView(pickerDialogView)
                            .setPositiveButton("OK") { _, _ ->
                                val colorInt = Color.parseColor(colorPicker.currentColorHex)
                                currentTextColor = colorInt
                                applyColorToSelection(colorInt)
                                updateButtonStates()
                            }.show()
                    } else {
                        val colorInt = Color.parseColor(hexColors[which])
                        currentTextColor = colorInt
                        applyColorToSelection(colorInt)
                        updateButtonStates()
                    }
                }.show()
        }

        // --- PALETA MODERNA ESTILO KEEP / NOTION ---
        dialogView.findViewById<ImageButton>(R.id.btnBgColor).setOnClickListener {
            val colorLabels = arrayOf(
                "⚪ Branco Puro",
                "🌾 Areia Suave",
                "🌿 Menta Fresco",
                "🔮 Lavanda Delicado",
                "🌊 Céu Pastel",
                "🍑 Pêssego",
                "🩶 Grafite Moderno",
                "🎨 Personalizada..."
            )
            val hexColors = arrayOf("#FFFFFF", "#FFFBEB", "#ECFDF5", "#F3E8FF", "#F0F9FF", "#FFF7ED", "#2D3748")

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cor de Fundo da Nota")
                .setItems(colorLabels) { _, which ->
                    if (which == colorLabels.size - 1) {
                        val pickerDialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
                        val colorPicker = pickerDialogView.findViewById<HexagonColorPickerView>(R.id.hexagonPicker)
                        val cardPreview = pickerDialogView.findViewById<MaterialCardView>(R.id.cardColorPreview)
                        val tvHex = pickerDialogView.findViewById<TextView>(R.id.tvHexPreview)

                        colorPicker.onColorChangeListener = { hex ->
                            cardPreview.setCardBackgroundColor(Color.parseColor(hex))
                            tvHex.text = hex
                        }

                        MaterialAlertDialogBuilder(requireContext())
                            .setView(pickerDialogView)
                            .setPositiveButton("OK") { _, _ ->
                                currentBgColor = colorPicker.currentColorHex
                                dialogView.setBackgroundColor(Color.parseColor(currentBgColor))
                            }.show()
                    } else {
                        currentBgColor = hexColors[which]
                        dialogView.setBackgroundColor(Color.parseColor(currentBgColor))
                    }
                }.show()
        }

        var isPinned = listToEdit?.isPinned ?: false
        fun updatePinIcon() {
            btnPin.setImageResource(if (isPinned) R.drawable.ic_pin else R.drawable.ic_pin_off)
            btnPin.imageTintList = ColorStateList.valueOf(
                if (isPinned) ContextCompat.getColor(requireContext(), R.color.primary) else Color.parseColor("#757575")
            )
        }
        updatePinIcon()
        btnPin.setOnClickListener {
            isPinned = !isPinned
            updatePinIcon()
            Toast.makeText(context, if (isPinned) "Nota afixada no topo 📌" else "Nota desafixada", Toast.LENGTH_SHORT).show()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Guardar") { dialogInterface, _ ->
                val name = etListName.text.toString().trim()
                val tagText = etTag.text.toString().trim()
                val finalTag = if (tagText.isNotEmpty()) tagText else null
                val htmlContent = HtmlCompat.toHtml(etListContent.text ?: SpannableString(""), HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)

                if (name.isNotBlank()) {
                    if (finalTag != null) {
                        val exists = tagViewModel.allTags.value?.any { it.name.trim().equals(finalTag, true) && it.type == "L" } ?: false
                        if (!exists) tagViewModel.insert(
                            Tag(
                                name = finalTag,
                                color = "#757575",
                                type = "L"
                            )
                        )
                    }
                    if (listToEdit != null) {
                        viewModel.update(listToEdit.copy(name = name, content = htmlContent, tag = finalTag, backgroundColor = currentBgColor, isPinned = isPinned))
                        logViewModel.registrarAcao("LISTA", "Atualizou a nota '$name'")
                    } else {
                        viewModel.insert(
                            MyList(
                                name = name,
                                content = htmlContent,
                                tag = finalTag,
                                backgroundColor = currentBgColor,
                                isPinned = isPinned
                            )
                        )
                        logViewModel.registrarAcao("LISTA_NOVA", "Criou a nota '$name'")
                    }
                }
                dialogInterface.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .create()

        if (listToEdit != null) {
            btnArchiveList.visibility = View.VISIBLE
            btnDeleteList.visibility = View.VISIBLE

            if (listToEdit.isArchived) {
                btnArchiveList.setImageResource(R.drawable.ic_undo)
            } else {
                btnArchiveList.setImageResource(R.drawable.ic_archive)
            }

            btnArchiveList.setOnClickListener {
                if (listToEdit.isArchived) {
                    viewModel.update(listToEdit.copy(isArchived = false))
                    logViewModel.registrarAcao("LISTA", "Restaurou a nota '${listToEdit.name}'")
                    Toast.makeText(context, "Nota restaurada! 📥", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.update(listToEdit.copy(isArchived = true))
                    logViewModel.registrarAcao("LISTA", "Arquivou a nota '${listToEdit.name}'")
                    Toast.makeText(context, "Nota arquivada! 📁", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }

            btnDeleteList.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Eliminar Nota?")
                    .setMessage("Tens a certeza que queres eliminar '${listToEdit.name}'?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        viewModel.delete(listToEdit)
                        logViewModel.registrarAcao("LISTA", "Eliminou a nota '${listToEdit.name}'")
                        Snackbar.make(binding.root, "Nota eliminada", Snackbar.LENGTH_LONG)
                            .setAction("DESFAZER") {
                                viewModel.insert(listToEdit)
                            }
                            .show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }

            etListName.setText(listToEdit.name)
            etTag.setText(listToEdit.tag ?: "", false)
            try {
                etListContent.setText(HtmlCompat.fromHtml(listToEdit.content, HtmlCompat.FROM_HTML_MODE_COMPACT))
            } catch (e: Exception) {
                etListContent.setText(listToEdit.content)
            }
        }

        dialog.show()
        val window = dialog.window
        if (window != null) {
            val displayMetrics = requireContext().resources.displayMetrics
            window.setLayout((displayMetrics.widthPixels * 0.95).toInt(), (displayMetrics.heightPixels * 0.92).toInt())
            dialogView.minimumHeight = (displayMetrics.heightPixels * 0.90).toInt()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
