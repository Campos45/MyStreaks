package pt.ipt.mystreaks

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
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
    private var currentTagFilter: String = "ALL" // NOVO: Filtro ativo

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListsBinding.bind(view)

        // --- ADAPTADOR LIMPO ---
        adapter = MyListAdapter(
            onEditClicked = { showAddListDialog(it) }
        )

        binding.recyclerViewLists.adapter = adapter
        binding.recyclerViewLists.layoutManager = GridLayoutManager(requireContext(), 2)

        tagViewModel.allTags.observe(viewLifecycleOwner) { allTags ->
            adapter.setTags(allTags.filter { it.type == "L" })
        }

        viewModel.activeLists.observe(viewLifecycleOwner) { if (!isShowingArchive) updateUI(it) }
        viewModel.archivedLists.observe(viewLifecycleOwner) { if (isShowingArchive) updateUI(it) }

        binding.toggleGroupLists.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isShowingArchive = checkedId == R.id.btnArchivedLists
                if (isShowingArchive) updateUI(viewModel.archivedLists.value) else updateUI(viewModel.activeLists.value)
            }
        }

        binding.fabAddList.setOnClickListener { showAddListDialog() }

        binding.ivSearch.setOnClickListener {
            binding.etSearch.visibility = if (binding.etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.etSearch.setTextColor(android.graphics.Color.BLACK)
        binding.etSearch.setHintTextColor(android.graphics.Color.DKGRAY)
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString()
                if (isShowingArchive) updateUI(viewModel.archivedLists.value) else updateUI(viewModel.activeLists.value)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // --- NOVO: Ligar o botão de Filtro! ---
        binding.ivFilter.setOnClickListener {
            val listTags = tagViewModel.allTags.value?.filter { it.type == "L" } ?: emptyList()
            if (listTags.isEmpty()) {
                Toast.makeText(requireContext(), "Ainda não tens categorias nas listas.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val options = arrayOf("🌟 Todas as Categorias", "🚫 Sem Categoria") + listTags.map { it.name }.toTypedArray()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filtrar por Categoria")
                .setItems(options) { _, which ->
                    currentTagFilter = when (which) {
                        0 -> "ALL"
                        1 -> "NONE"
                        else -> options[which]
                    }
                    if (isShowingArchive) updateUI(viewModel.archivedLists.value) else updateUI(viewModel.activeLists.value)
                }.show()
        }
    }

    private fun updateUI(lists: List<MyList>?) {
        val safeLists = lists ?: emptyList()

        val filteredLists = safeLists.filter { list ->
            val matchesSearch = currentSearchQuery.isEmpty() || list.name.contains(currentSearchQuery, ignoreCase = true)
            // NOVO: Aplica o Filtro Visual
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

        if (isShowingArchive) binding.fabAddList.hide() else binding.fabAddList.show()
    }

    private fun showAddListDialog(listToEdit: MyList? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_list, null)
        val etListName = dialogView.findViewById<TextInputEditText>(R.id.etListName)
        val etListContent = dialogView.findViewById<TextInputEditText>(R.id.etListContent)
        val etTag = dialogView.findViewById<AutoCompleteTextView>(R.id.etTag)

        // NOVO: OS BOTÕES DO TOPO (Arquivar, Apagar, Pin)
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
            dialogView.findViewById<ImageButton>(R.id.btnBold).apply { alpha = if (isBoldActive) 1f else 0.4f; setBackgroundColor(if (isBoldActive) Color.LTGRAY else Color.TRANSPARENT) }
            dialogView.findViewById<ImageButton>(R.id.btnItalic).apply { alpha = if (isItalicActive) 1f else 0.4f; setBackgroundColor(if (isItalicActive) Color.LTGRAY else Color.TRANSPARENT) }
            dialogView.findViewById<View>(R.id.btnUnderline).apply { alpha = if (isUnderlineActive) 1f else 0.4f; setBackgroundColor(if (isUnderlineActive) Color.LTGRAY else Color.TRANSPARENT) }
            dialogView.findViewById<ImageButton>(R.id.btnTextColor).apply { imageTintList = if (currentTextColor != null) ColorStateList.valueOf(currentTextColor!!) else ColorStateList.valueOf(Color.BLACK) }
        }
        updateButtonStates()

        etListContent.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s == null) return
                val cursorPosition = etListContent.selectionStart
                if (cursorPosition <= 0) return

                if (isBoldActive) s.setSpan(StyleSpan(Typeface.BOLD), cursorPosition - 1, cursorPosition, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isItalicActive) s.setSpan(StyleSpan(Typeface.ITALIC), cursorPosition - 1, cursorPosition, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isUnderlineActive) s.setSpan(android.text.style.UnderlineSpan(), cursorPosition - 1, cursorPosition, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (currentTextSizeFactor != 1.0f) s.setSpan(android.text.style.RelativeSizeSpan(currentTextSizeFactor), cursorPosition - 1, cursorPosition, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (currentTextColor != null) s.setSpan(ForegroundColorSpan(currentTextColor!!), cursorPosition - 1, cursorPosition, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        })

        dialogView.findViewById<ImageButton>(R.id.btnFormat).setOnClickListener {
            val formatToolbar = dialogView.findViewById<View>(R.id.formatToolbar)
            formatToolbar.visibility = if (formatToolbar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        dialogView.findViewById<ImageButton>(R.id.btnBold).setOnClickListener { isBoldActive = !isBoldActive; updateButtonStates() }
        dialogView.findViewById<ImageButton>(R.id.btnItalic).setOnClickListener { isItalicActive = !isItalicActive; updateButtonStates() }
        dialogView.findViewById<View>(R.id.btnUnderline).setOnClickListener { isUnderlineActive = !isUnderlineActive; updateButtonStates() }
        dialogView.findViewById<View>(R.id.btnTitle1).setOnClickListener { currentTextSizeFactor = 1.5f; updateButtonStates(); Toast.makeText(context, "Modo Título 1", Toast.LENGTH_SHORT).show() }
        dialogView.findViewById<View>(R.id.btnTitle2).setOnClickListener { currentTextSizeFactor = 1.2f; updateButtonStates(); Toast.makeText(context, "Modo Título 2", Toast.LENGTH_SHORT).show() }
        dialogView.findViewById<View>(R.id.btnNormalText).setOnClickListener { currentTextSizeFactor = 1.0f; updateButtonStates(); Toast.makeText(context, "Tamanho Normal", Toast.LENGTH_SHORT).show() }

        dialogView.findViewById<ImageButton>(R.id.btnClearFormat).setOnClickListener {
            isBoldActive = false; isItalicActive = false; isUnderlineActive = false; currentTextSizeFactor = 1.0f; currentTextColor = null
            updateButtonStates()
            Toast.makeText(context, "Modo Texto Normal ativado", Toast.LENGTH_SHORT).show()
        }

        dialogView.findViewById<ImageButton>(R.id.btnTextColor).setOnClickListener {
            val colors = arrayOf("Azul", "Verde", "Vermelho", "Amarelo", "Preto", "Outra...")
            val hexColors = arrayOf("#2196F3", "#4CAF50", "#F44336", "#FFEB3B", "#000000")
            MaterialAlertDialogBuilder(requireContext()).setTitle("Escolher Cor").setItems(colors) { _, which ->
                if (which == 5) {
                    val pickerDialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
                    val colorPicker = pickerDialogView.findViewById<HexagonColorPickerView>(R.id.hexagonPicker)
                    MaterialAlertDialogBuilder(requireContext()).setView(pickerDialogView)
                        .setPositiveButton("OK") { _, _ -> currentTextColor = Color.parseColor(colorPicker.currentColorHex); updateButtonStates() }.show()
                } else {
                    currentTextColor = Color.parseColor(hexColors[which]); updateButtonStates()
                }
            }.show()
        }

        dialogView.findViewById<ImageButton>(R.id.btnBgColor).setOnClickListener {
            val colors = arrayOf("Azul Claro", "Verde Claro", "Rosa", "Amarelo", "Branco", "Outra...")
            val hexColors = arrayOf("#E3F2FD", "#E8F5E9", "#FFEBEE", "#FFF9C4", "#FFFFFF")
            MaterialAlertDialogBuilder(requireContext()).setTitle("Cor de Fundo").setItems(colors) { _, which ->
                if (which == 5) {
                    val pickerDialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
                    val colorPicker = pickerDialogView.findViewById<HexagonColorPickerView>(R.id.hexagonPicker)
                    MaterialAlertDialogBuilder(requireContext()).setView(pickerDialogView)
                        .setPositiveButton("OK") { _, _ -> currentBgColor = colorPicker.currentColorHex; dialogView.setBackgroundColor(Color.parseColor(currentBgColor)) }.show()
                } else {
                    currentBgColor = hexColors[which]; dialogView.setBackgroundColor(Color.parseColor(currentBgColor))
                }
            }.show()
        }

        var isPinned = listToEdit?.isPinned ?: false
        fun updatePinIcon() { btnPin.setImageResource(if (isPinned) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off) }
        updatePinIcon()
        btnPin.setOnClickListener { isPinned = !isPinned; updatePinIcon(); Toast.makeText(context, if (isPinned) "Afixada!" else "Desafixada.", Toast.LENGTH_SHORT).show() }

        // MISTÉRIO RESOLVIDO: O Diálogo Principal
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Guardar") { dialogInterface, _ ->
                val name = etListName.text.toString().trim()
                val tagText = etTag.text.toString().trim()
                val finalTag = if (tagText.isNotEmpty()) tagText else null
                val htmlContent = HtmlCompat.toHtml(etListContent.text ?: android.text.SpannableString(""), HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)

                if (name.isNotBlank()) {
                    if (finalTag != null) {
                        val exists = tagViewModel.allTags.value?.any { it.name.trim().equals(finalTag, true) && it.type == "L" } ?: false
                        if (!exists) tagViewModel.insert(Tag(name = finalTag, color = "#757575", type = "L"))
                    }
                    if (listToEdit != null) {
                        viewModel.update(listToEdit.copy(name = name, content = htmlContent, tag = finalTag, backgroundColor = currentBgColor, isPinned = isPinned))
                    } else {
                        viewModel.insert(MyList(name = name, content = htmlContent, tag = finalTag, backgroundColor = currentBgColor, isPinned = isPinned))
                    }
                }
                dialogInterface.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .create()

        // Ligar os botões de Arquivar e Apagar no Diálogo (só aparecem se estiveres a editar uma lista já criada)
        if (listToEdit != null) {
            btnArchiveList?.visibility = View.VISIBLE
            btnDeleteList?.visibility = View.VISIBLE

            if(listToEdit.isArchived) {
                btnArchiveList?.setImageResource(android.R.drawable.ic_menu_revert) // Transforma em botão de restaurar
            }

            btnArchiveList?.setOnClickListener {
                if(listToEdit.isArchived) {
                    viewModel.update(listToEdit.copy(isArchived = false))
                    Toast.makeText(context, "Lista Restaurada!", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.update(listToEdit.copy(isArchived = true))
                    Toast.makeText(context, "Lista Arquivada!", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }

            btnDeleteList?.setOnClickListener {
                viewModel.delete(listToEdit)
                Toast.makeText(context, "Lista Eliminada!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }

            etListName.setText(listToEdit.name)
            etTag.setText(listToEdit.tag ?: "", false)
            try { etListContent.setText(HtmlCompat.fromHtml(listToEdit.content, HtmlCompat.FROM_HTML_MODE_COMPACT)) }
            catch (e: Exception) { etListContent.setText(listToEdit.content) }
        }

        dialog.show()
        val window = dialog.window
        if (window != null) {
            val displayMetrics = requireContext().resources.displayMetrics
            window.setLayout((displayMetrics.widthPixels * 0.95).toInt(), (displayMetrics.heightPixels * 0.95).toInt())
            dialogView.minimumHeight = (displayMetrics.heightPixels * 0.95).toInt()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}