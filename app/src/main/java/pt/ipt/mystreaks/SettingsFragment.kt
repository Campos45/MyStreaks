package pt.ipt.mystreaks

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import pt.ipt.mystreaks.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var tagViewModel: TagViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentSettingsBinding.bind(view)
        tagViewModel = androidx.lifecycle.ViewModelProvider(this)[TagViewModel::class.java]

        // --- AGORA TEMOS 3 BOTÕES PARA 3 TIPOS DE TAGS ---

        binding.btnManageTagsStreaks.setOnClickListener {
            showManageTagsDialog("S") // S de Streaks
        }

        binding.btnManageTagsTasks.setOnClickListener {
            showManageTagsDialog("T") // T de Tarefas
        }

        binding.btnManageTagsLists.setOnClickListener {
            showManageTagsDialog("L") // L de Listas
        }

        binding.btnMedals.setOnClickListener {
            startActivity(Intent(requireContext(), MedalsActivity::class.java))
        }

        binding.btnLogs.setOnClickListener {
            startActivity(Intent(requireContext(), LogsActivity::class.java))
        }
    }

    // A função agora recebe o "type" como parâmetro
    private fun showManageTagsDialog(type: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manage_tags, null)

        // Mudar o título do diálogo dinamicamente para o utilizador saber onde está
        val title = when(type) {
            "S" -> "Categorias de Streaks 🏷️"
            "T" -> "Categorias de Tarefas 📝"
            else -> "Categorias de Listas 📋"
        }

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        var editingTag: Tag? = null
        var selectedColorHex = "#448AFF"
        var customColorHex = "#E0E0E0"

        val etNewTagName = dialogView.findViewById<android.widget.EditText>(R.id.etNewTagName)
        val btnSaveTag = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveTag)

        // ... (Cores e Hexágono - mantêm-se iguais ao teu código) ...
        val colorCustom = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorCustom)
        val colorPink = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorPink)
        val colorPurple = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorPurple)
        val colorBlue = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorBlue)
        val colorGreen = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorGreen)
        val colorOrange = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.colorOrange)

        val staticColors = mapOf(
            "#FF4081" to colorPink,
            "#7C4DFF" to colorPurple,
            "#448AFF" to colorBlue,
            "#4CAF50" to colorGreen,
            "#FFAB40" to colorOrange
        )

        fun updateColorSelection(selectedHex: String) {
            if (selectedHex == customColorHex) {
                colorCustom?.strokeWidth = 6
                colorCustom?.strokeColor = android.graphics.Color.BLACK
            } else {
                colorCustom?.strokeWidth = 0
            }
            for ((hex, view) in staticColors) {
                if (hex == selectedHex) {
                    view?.strokeWidth = 6
                    view?.strokeColor = android.graphics.Color.BLACK
                } else {
                    view?.strokeWidth = 0
                }
            }
        }
        updateColorSelection(selectedColorHex)

        for ((hex, view) in staticColors) {
            view?.setOnClickListener {
                selectedColorHex = hex
                updateColorSelection(hex)
            }
        }

        colorCustom?.setOnClickListener {
            val pickerDialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
            val hexagonPicker = pickerDialogView.findViewById<pt.ipt.mystreaks.HexagonColorPickerView>(R.id.hexagonPicker)
            val cardColorPreview = pickerDialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardColorPreview)
            val tvHex = pickerDialogView.findViewById<android.widget.TextView>(R.id.tvHexPreview)

            try {
                cardColorPreview.setCardBackgroundColor(android.graphics.Color.parseColor(customColorHex))
                tvHex.text = customColorHex
            } catch (e: Exception) {}

            hexagonPicker.onColorChangeListener = { novaCorHex ->
                tvHex.text = novaCorHex
                try {
                    cardColorPreview.setCardBackgroundColor(android.graphics.Color.parseColor(novaCorHex))
                } catch (e: Exception) {}
            }

            android.app.AlertDialog.Builder(requireContext())
                .setView(pickerDialogView)
                .setPositiveButton("Confirmar Cor") { _, _ ->
                    customColorHex = hexagonPicker.currentColorHex
                    try {
                        colorCustom.setCardBackgroundColor(android.graphics.Color.parseColor(customColorHex))
                    } catch (e: Exception) {}
                    selectedColorHex = customColorHex
                    updateColorSelection(selectedColorHex)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // --- FILTRAR A LISTA PELO TIPO ESCOLHIDO ---
        val rvTags = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvExistingTags)
        if (rvTags != null) {
            rvTags.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
            val tagAdapter = TagAdapter(
                onDeleteClick = { tag -> tagViewModel.delete(tag) },
                onTagClick = { tag ->
                    editingTag = tag
                    etNewTagName?.setText(tag.name)
                    selectedColorHex = tag.color
                    if (!staticColors.containsKey(tag.color)) {
                        customColorHex = tag.color
                        colorCustom?.setCardBackgroundColor(android.graphics.Color.parseColor(customColorHex))
                    }
                    updateColorSelection(tag.color)
                    btnSaveTag?.text = "Atualizar Tag"
                }
            )
            rvTags.adapter = tagAdapter

            // FILTRO: Só mostra as tags que pertencem a este menu (S, T ou L)
            tagViewModel.allTags.observe(viewLifecycleOwner) { allTags ->
                val filtered = allTags.filter { it.type == type }
                tagAdapter.submitList(filtered)
            }
        }

        btnSaveTag?.setOnClickListener {
            val tagName = etNewTagName?.text.toString().trim()
            if (tagName.isNotEmpty()) {
                val newTag = Tag(
                    id = editingTag?.id ?: 0,
                    name = tagName,
                    color = selectedColorHex,
                    type = type // GUARDA COM O TIPO CORRETO!
                )
                tagViewModel.insert(newTag)

                etNewTagName?.text?.clear()
                editingTag = null
                btnSaveTag.text = "Guardar Tag"

                android.widget.Toast.makeText(requireContext(), "Tag guardada! 🎉", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                etNewTagName?.error = "Escreve um nome!"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}