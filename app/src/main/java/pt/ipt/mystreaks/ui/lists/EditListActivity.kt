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
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.data.model.AppLog
import pt.ipt.mystreaks.data.model.MyList
import pt.ipt.mystreaks.data.model.Tag
import pt.ipt.mystreaks.data.repository.LogRepository
import pt.ipt.mystreaks.data.repository.MyListRepository
import pt.ipt.mystreaks.databinding.ActivityEditListBinding
import pt.ipt.mystreaks.ui.settings.TagDropdownAdapter
import pt.ipt.mystreaks.utils.AppPreferences
import pt.ipt.mystreaks.utils.EditTextUndoRedo
import pt.ipt.mystreaks.utils.HexagonColorPickerView
import pt.ipt.mystreaks.utils.setInfoTooltip

class EditListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditListBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { MyListRepository(database.myListDao()) }
    private val logRepository by lazy { LogRepository(database.appLogDao()) }

    private var currentListId: Int = -1
    private var loadedList: MyList? = null
    private var currentBgColor: String = "#FFFFFF"
    private var isPinned: Boolean = false
    private var isArchived: Boolean = false

    private var isBoldActive = false
    private var isItalicActive = false
    private var isUnderlineActive = false
    private var currentTextSizeFactor = 1.0f
    private var currentTextColor: Int? = null
    private lateinit var undoRedo: EditTextUndoRedo

    override fun onCreate(savedInstanceState: Bundle?) {
        AppPreferences.applyDarkMode(AppPreferences.isDarkMode(this))
        AppPreferences.applyLanguage(AppPreferences.getLanguage(this))
        super.onCreate(savedInstanceState)
        binding = ActivityEditListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentListId = intent.getIntExtra(EXTRA_LIST_ID, -1)
        undoRedo = EditTextUndoRedo(binding.etListContent)

        setupToolbars()
        setupFormatting()
        setupTagsDropdown()
        setupListeners()
        loadListData()
    }

    private fun setupToolbars() {
        binding.tvToolbarTitle.text = if (currentListId == -1) getString(R.string.new_list) else getString(R.string.edit_list_title)

        binding.btnBack.setInfoTooltip(R.string.cancel)
        binding.btnPin.setInfoTooltip(R.string.tooltip_pin)
        binding.btnBgColor.setInfoTooltip(R.string.tooltip_color)
        binding.btnArchiveList.setInfoTooltip(R.string.tooltip_archive)
        binding.btnDeleteList.setInfoTooltip(R.string.tooltip_delete)
        binding.btnSaveList.setInfoTooltip(R.string.save)

        binding.btnFormat.setInfoTooltip("Formatação de texto")
        binding.btnUndo.setInfoTooltip("Desfazer")
        binding.btnRedo.setInfoTooltip("Refazer")
    }

    private fun setupTagsDropdown() {
        lifecycleScope.launch(Dispatchers.IO) {
            val tags = database.tagDao().getAllTagsSyncList().filter { it.type == "L" }
            withContext(Dispatchers.Main) {
                binding.etTag.setAdapter(TagDropdownAdapter(this@EditListActivity, tags))
                binding.etTag.setOnClickListener { binding.etTag.showDropDown() }
                binding.etTag.setOnItemClickListener { parent, _, position, _ ->
                    val tag = parent.getItemAtPosition(position) as Tag
                    binding.etTag.setText(tag.name, false)
                }
            }
        }
    }

    private fun setupFormatting() {
        binding.btnFormat.setOnClickListener {
            val isVisible = binding.formatToolbar.visibility == View.VISIBLE
            binding.formatToolbar.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        binding.btnUndo.setOnClickListener { undoRedo.undo() }
        binding.btnRedo.setOnClickListener { undoRedo.redo() }

        binding.btnBold.setOnClickListener {
            isBoldActive = !isBoldActive
            updateButtonStates()
            applyStyleToSelection(StyleSpan(Typeface.BOLD))
        }

        binding.btnItalic.setOnClickListener {
            isItalicActive = !isItalicActive
            updateButtonStates()
            applyStyleToSelection(StyleSpan(Typeface.ITALIC))
        }

        binding.btnUnderline.setOnClickListener {
            isUnderlineActive = !isUnderlineActive
            updateButtonStates()
            applyStyleToSelection(UnderlineSpan())
        }

        binding.btnTitle1.setOnClickListener {
            currentTextSizeFactor = 1.4f
            applyStyleToSelection(RelativeSizeSpan(1.4f))
        }

        binding.btnTitle2.setOnClickListener {
            currentTextSizeFactor = 1.2f
            applyStyleToSelection(RelativeSizeSpan(1.2f))
        }

        binding.btnNormalText.setOnClickListener {
            currentTextSizeFactor = 1.0f
            applyStyleToSelection(RelativeSizeSpan(1.0f))
        }

        binding.btnClearFormat.setOnClickListener {
            isBoldActive = false
            isItalicActive = false
            isUnderlineActive = false
            currentTextSizeFactor = 1.0f
            currentTextColor = null
            if (binding.etListContent.hasSelection()) {
                val start = binding.etListContent.selectionStart
                val end = binding.etListContent.selectionEnd
                val editable = binding.etListContent.text
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
            Toast.makeText(this, "Formatação reposta", Toast.LENGTH_SHORT).show()
        }

        binding.btnTextColor.setOnClickListener {
            showTextColorPickerDialog()
        }

        binding.etListContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s == null) return
                val cursor = binding.etListContent.selectionStart
                if (cursor <= 0) return

                if (isBoldActive) s.setSpan(StyleSpan(Typeface.BOLD), cursor - 1, cursor, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isItalicActive) s.setSpan(StyleSpan(Typeface.ITALIC), cursor - 1, cursor, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (isUnderlineActive) s.setSpan(UnderlineSpan(), cursor - 1, cursor, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (currentTextSizeFactor != 1.0f) s.setSpan(RelativeSizeSpan(currentTextSizeFactor), cursor - 1, cursor, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (currentTextColor != null) s.setSpan(ForegroundColorSpan(currentTextColor!!), cursor - 1, cursor, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        })
    }

    private fun applyStyleToSelection(span: Any) {
        if (binding.etListContent.hasSelection()) {
            val start = binding.etListContent.selectionStart
            val end = binding.etListContent.selectionEnd
            val editable = binding.etListContent.text
            editable?.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun updateButtonStates() {
        binding.btnBold.apply {
            alpha = if (isBoldActive) 1f else 0.45f
            setBackgroundColor(if (isBoldActive) Color.parseColor("#30000000") else Color.TRANSPARENT)
        }
        binding.btnItalic.apply {
            alpha = if (isItalicActive) 1f else 0.45f
            setBackgroundColor(if (isItalicActive) Color.parseColor("#30000000") else Color.TRANSPARENT)
        }
        binding.btnUnderline.apply {
            alpha = if (isUnderlineActive) 1f else 0.45f
            setBackgroundColor(if (isUnderlineActive) Color.parseColor("#30000000") else Color.TRANSPARENT)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            saveAndFinish()
        }

        binding.btnSaveList.setOnClickListener {
            saveAndFinish()
        }

        binding.btnPin.setOnClickListener {
            isPinned = !isPinned
            updatePinUI()
            val msg = if (isPinned) getString(R.string.list_pinned) else getString(R.string.list_unpinned)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnBgColor.setOnClickListener {
            showBgColorPickerDialog()
        }

        binding.btnArchiveList.setOnClickListener {
            isArchived = !isArchived
            updateArchiveUI()
            val msg = if (isArchived) getString(R.string.list_archived) else getString(R.string.list_unarchived)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnDeleteList.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.list_delete_confirm_title)
                .setMessage(R.string.list_delete_confirm_msg)
                .setPositiveButton(R.string.delete) { _, _ ->
                    loadedList?.let { target ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            repository.delete(target)
                            logRepository.insertLog(AppLog(type = "LISTA", message = "Eliminou a nota '${target.name}'"))
                        }
                    }
                    Toast.makeText(this, R.string.list_deleted, Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun updatePinUI() {
        binding.btnPin.setImageResource(if (isPinned) R.drawable.ic_pin else R.drawable.ic_pin_off)
        binding.btnPin.imageTintList = ColorStateList.valueOf(
            if (isPinned) ContextCompat.getColor(this, R.color.primary) else Color.parseColor("#757575")
        )
    }

    private fun updateArchiveUI() {
        binding.btnArchiveList.setImageResource(if (isArchived) R.drawable.ic_undo else R.drawable.ic_archive)
    }

    private fun updateBgColor(colorHex: String) {
        currentBgColor = colorHex
        try {
            val colorInt = Color.parseColor(colorHex)
            binding.rootEditList.setBackgroundColor(colorInt)
        } catch (e: Exception) {
            // Ignore color parse error
        }
    }

    private fun showTextColorPickerDialog() {
        val colors = arrayOf("Azul", "Verde", "Vermelho", "Laranja", "Preto", "Personalizada...")
        val hexColors = arrayOf("#2196F3", "#4CAF50", "#F44336", "#FF9800", "#000000")
        MaterialAlertDialogBuilder(this)
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

                    MaterialAlertDialogBuilder(this)
                        .setView(pickerDialogView)
                        .setPositiveButton("OK") { _, _ ->
                            val colorInt = Color.parseColor(colorPicker.currentColorHex)
                            currentTextColor = colorInt
                            applyStyleToSelection(ForegroundColorSpan(colorInt))
                        }.show()
                } else {
                    val colorInt = Color.parseColor(hexColors[which])
                    currentTextColor = colorInt
                    applyStyleToSelection(ForegroundColorSpan(colorInt))
                }
            }.show()
    }

    private fun showBgColorPickerDialog() {
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

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.tooltip_color)
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

                    MaterialAlertDialogBuilder(this)
                        .setView(pickerDialogView)
                        .setPositiveButton("OK") { _, _ ->
                            updateBgColor(colorPicker.currentColorHex)
                        }.show()
                } else {
                    updateBgColor(hexColors[which])
                }
            }.show()
    }

    private fun loadListData() {
        if (currentListId != -1) {
            binding.btnArchiveList.visibility = View.VISIBLE
            binding.btnDeleteList.visibility = View.VISIBLE

            lifecycleScope.launch(Dispatchers.IO) {
                val list = database.myListDao().getAllListsSync().find { it.id == currentListId }
                loadedList = list
                if (list != null) {
                    withContext(Dispatchers.Main) {
                        binding.etListName.setText(list.name)
                        binding.etTag.setText(list.tag ?: "", false)
                        try {
                            binding.etListContent.setText(HtmlCompat.fromHtml(list.content, HtmlCompat.FROM_HTML_MODE_COMPACT))
                        } catch (e: Exception) {
                            binding.etListContent.setText(list.content)
                        }
                        isPinned = list.isPinned
                        isArchived = list.isArchived
                        currentBgColor = list.backgroundColor ?: "#FFFFFF"
                        updatePinUI()
                        updateArchiveUI()
                        updateBgColor(currentBgColor)
                    }
                }
            }
        }
    }

    private fun saveAndFinish() {
        val name = binding.etListName.text.toString().trim()
        val tagText = binding.etTag.text.toString().trim()
        val finalTag = tagText.ifEmpty { null }
        val contentEditable = binding.etListContent.text ?: SpannableString("")
        val htmlContent = HtmlCompat.toHtml(contentEditable, HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)

        if (name.isNotBlank() || contentEditable.isNotBlank()) {
            val finalName = name.ifBlank { "Sem título" }

            lifecycleScope.launch(Dispatchers.IO) {
                if (finalTag != null) {
                    val tags = database.tagDao().getAllTagsSyncList().filter { it.type == "L" }
                    val exists = tags.any { it.name.trim().equals(finalTag, true) }
                    if (!exists) {
                        database.tagDao().insert(Tag(name = finalTag, color = "#757575", type = "L"))
                    }
                }

                if (loadedList != null) {
                    repository.update(
                        loadedList!!.copy(
                            name = finalName,
                            content = htmlContent,
                            tag = finalTag,
                            backgroundColor = currentBgColor,
                            isPinned = isPinned,
                            isArchived = isArchived
                        )
                    )
                    logRepository.insertLog(AppLog(type = "LISTA", message = "Atualizou a nota '$finalName'"))
                } else {
                    repository.insert(
                        MyList(
                            name = finalName,
                            content = htmlContent,
                            tag = finalTag,
                            backgroundColor = currentBgColor,
                            isPinned = isPinned,
                            isArchived = isArchived
                        )
                    )
                    logRepository.insertLog(AppLog(type = "LISTA_NOVA", message = "Criou a nota '$finalName'"))
                }
            }
            Toast.makeText(this, R.string.list_saved, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onBackPressed() {
        saveAndFinish()
    }

    companion object {
        const val EXTRA_LIST_ID = "EXTRA_LIST_ID"
    }
}
