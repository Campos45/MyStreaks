package pt.ipt.mystreaks.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.withTransaction
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipt.mystreaks.ui.logs.LogsActivity
import pt.ipt.mystreaks.ui.medals.MedalsActivity
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.data.AppDatabase
import pt.ipt.mystreaks.data.MyFullBackup
import pt.ipt.mystreaks.data.model.Tag
import pt.ipt.mystreaks.databinding.FragmentSettingsBinding
import pt.ipt.mystreaks.utils.HexagonColorPickerView
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var tagViewModel: TagViewModel
    private val database by lazy { AppDatabase.getDatabase(requireContext()) }
    private val gson = Gson()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val streaks = database.streakDao().getAllStreaksSync()
                    val tasks = database.taskDao().getAllTasksSync()
                    val lists = database.myListDao().getAllListsSync()
                    val tags = database.tagDao().getAllTagsSyncList()

                    val backup = MyFullBackup(streaks, tasks, lists, tags)
                    val json = gson.toJson(backup)

                    requireContext().contentResolver.openOutputStream(it)?.use { os ->
                        OutputStreamWriter(os).use { writer -> writer.write(json) }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Backup exportado! 📤", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val json = InputStreamReader(requireContext().contentResolver.openInputStream(it)).readText()
                    val backup = gson.fromJson(json, MyFullBackup::class.java)

                    // 1. INSERE NA BASE DE DADOS
                    database.withTransaction {
                        backup.tags?.forEach { tag ->
                            if (tag.type.isNullOrEmpty()) {
                                database.tagDao().insert(tag.copy(id = 0, type = "S"))
                                database.tagDao().insert(tag.copy(id = 0, type = "T"))
                                database.tagDao().insert(tag.copy(id = 0, type = "L"))
                            } else {
                                database.tagDao().insert(tag)
                            }
                        }

                        backup.tasks?.forEach { task ->
                            val p = if (task.priority == 0) 3 else task.priority
                            database.taskDao().insert(task.copy(priority = p))
                        }

                        backup.streaks?.forEach { database.streakDao().insert(it) }
                        backup.lists?.forEach { database.myListDao().insert(it) }
                    }

                    // --- A CURA DA AMNÉSIA: 2. RECONSTRUIR OS ALARMES NO ANDROID ---
                    val alarmManager = requireContext().getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager

                    // Reagendar Tarefas
                    backup.tasks?.forEach { task ->
                        // FIX: Criar uma variável local imutável (val) para o Kotlin confiar
                        val safeDueDate = task.dueDate

                        if (!task.isCompleted && !task.isArchived && safeDueDate != null && safeDueDate > System.currentTimeMillis()) {
                            val intent = Intent(requireContext(), pt.ipt.mystreaks.services.TaskAlarmReceiver::class.java).apply {
                                putExtra("TASK_NAME", task.name)
                            }
                            val pendingIntent = android.app.PendingIntent.getBroadcast(
                                requireContext(), task.name.hashCode(), intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, safeDueDate, pendingIntent)
                                } else {
                                    alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, safeDueDate, pendingIntent)
                                }
                            } catch (e: SecurityException) {
                                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, safeDueDate, pendingIntent)
                            }
                        }
                    }

                    // Reagendar Streaks
                    backup.streaks?.forEach { streak ->
                        val safeHour = streak.remindHour
                        val safeMinute = streak.remindMinute

                        if (!streak.isArchived && safeHour != null && safeMinute != null) {
                            val intent = Intent(requireContext(), pt.ipt.mystreaks.services.StreakAlarmReceiver::class.java).apply {
                                putExtra("STREAK_NAME", streak.name)
                            }
                            val pendingIntent = android.app.PendingIntent.getBroadcast(
                                requireContext(), streak.name.hashCode(), intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                            )

                            val cal = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.HOUR_OF_DAY, safeHour)
                                set(java.util.Calendar.MINUTE, safeMinute)
                                set(java.util.Calendar.SECOND, 0)
                                if (before(java.util.Calendar.getInstance())) add(java.util.Calendar.DATE, 1)
                            }

                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                    alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
                                } else {
                                    alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
                                }
                            } catch (e: SecurityException) {
                                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
                            }
                        }
                    }
                    // --------------------------------------------------------------

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Importado com sucesso e Alarmes reativados! 📥⏰", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Erro ao importar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)
        tagViewModel = ViewModelProvider(this)[TagViewModel::class.java]

        binding.btnManageTagsStreaks.setOnClickListener { showManageTagsDialog("S") }
        binding.btnManageTagsTasks.setOnClickListener { showManageTagsDialog("T") }
        binding.btnManageTagsLists.setOnClickListener { showManageTagsDialog("L") }
        binding.btnMedals.setOnClickListener { startActivity(Intent(requireContext(), MedalsActivity::class.java)) }
        binding.btnLogs.setOnClickListener { startActivity(Intent(requireContext(), LogsActivity::class.java)) }

        binding.btnExportData.setOnClickListener {
            val options = arrayOf("📤 Exportar Backup", "📥 Importar Backup")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Backup")
                .setItems(options) { _, which ->
                    if (which == 0) exportLauncher.launch("MyStreaks_Backup.json")
                    else importLauncher.launch("application/json")
                }.show()
        }
    }

    private fun showManageTagsDialog(type: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manage_tags, null)
        val title = when(type) {
            "S" -> "Categorias de Streaks 🔥"
            "T" -> "Categorias de Tarefas 📝"
            else -> "Categorias de Listas 📋"
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title).setView(dialogView).create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        var editingTag: Tag? = null
        var selectedColor = "#448AFF" // Cor default (Azul)

        val et = dialogView.findViewById<EditText>(R.id.etNewTagName)
        et.setTextColor(Color.BLACK)
        et.setHintTextColor(Color.DKGRAY)

        val btn = dialogView.findViewById<MaterialButton>(R.id.btnSaveTag)

        // --- LIGAR A TUA PALETA DE CORES ---
        val colorCustom = dialogView.findViewById<MaterialCardView>(R.id.colorCustom)
        val colorPink = dialogView.findViewById<MaterialCardView>(R.id.colorPink)
        val colorPurple = dialogView.findViewById<MaterialCardView>(R.id.colorPurple)
        val colorBlue = dialogView.findViewById<MaterialCardView>(R.id.colorBlue)
        val colorGreen = dialogView.findViewById<MaterialCardView>(R.id.colorGreen)
        val colorOrange = dialogView.findViewById<MaterialCardView>(R.id.colorOrange)

        // Cliques nas cores fixas
        colorPink.setOnClickListener { selectedColor = "#FF4081"; Toast.makeText(context, "Rosa", Toast.LENGTH_SHORT).show() }
        colorPurple.setOnClickListener { selectedColor = "#7C4DFF"; Toast.makeText(context, "Roxo", Toast.LENGTH_SHORT).show() }
        colorBlue.setOnClickListener { selectedColor = "#448AFF"; Toast.makeText(context, "Azul", Toast.LENGTH_SHORT).show() }
        colorGreen.setOnClickListener { selectedColor = "#4CAF50"; Toast.makeText(context, "Verde", Toast.LENGTH_SHORT).show() }
        colorOrange.setOnClickListener { selectedColor = "#FFAB40"; Toast.makeText(context, "Laranja", Toast.LENGTH_SHORT).show() }

        // Clique no botão Lápis (Personalizada)
        colorCustom.setOnClickListener {
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
                    selectedColor = colorPicker.currentColorHex
                    // Pinta o fundo do botão do Lápis com a cor que escolheste para dar feedback!
                    colorCustom.setCardBackgroundColor(Color.parseColor(selectedColor))
                }.show()
        }

        val rv = dialogView.findViewById<RecyclerView>(R.id.rvExistingTags)
        rv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        val tagAdapter = TagAdapter(
            onDeleteClick = { tagViewModel.delete(it) },
            onTagClick = { tag ->
                editingTag = tag
                et.setText(tag.name)
                selectedColor = tag.color
                // Se estiver a editar, pintar a bolinha do lápis com a cor da tag
                colorCustom.setCardBackgroundColor(Color.parseColor(selectedColor))
                btn.text = "Atualizar"
            }
        )
        rv.adapter = tagAdapter

        tagViewModel.allTags.observe(viewLifecycleOwner) { tags ->
            tagAdapter.submitList(tags.filter { it.type == type })
        }

        btn.setOnClickListener {
            val name = et.text.toString().trim()
            if (name.isNotEmpty()) {
                tagViewModel.insert(
                    Tag(
                        id = editingTag?.id ?: 0,
                        name = name,
                        color = selectedColor,
                        type = type
                    )
                )
                et.text.clear()
                editingTag = null
                btn.text = "Guardar"

                // Reset à cor
                selectedColor = "#448AFF"
                colorCustom.setCardBackgroundColor(Color.parseColor("#E0E0E0")) // Volta ao cinza
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}