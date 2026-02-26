package pt.ipt.mystreaks

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipt.mystreaks.databinding.ActivityLogsBinding
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { LogRepository(database.appLogDao()) }
    private val viewModel: LogViewModel by viewModels { LogViewModelFactory(repository) }

    private var currentLogs = emptyList<AppLog>()

    // 1. Exportar Logs em TXT (Como já tinhas)
    private val exportLogsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { exportLogsToFile(it) }
    }

    // 2. Criar ficheiro de Backup JSON
    private val createBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { createBackupFile(it) }
    }

    // 3. Abrir ficheiro de Backup para Restaurar
    private val restoreBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { confirmAndRestoreBackup(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val adapter = LogAdapter()
        binding.recyclerViewLogs.adapter = adapter
        binding.recyclerViewLogs.layoutManager = LinearLayoutManager(this)

        viewModel.allLogs.observe(this) { logs ->
            currentLogs = logs ?: emptyList()
            adapter.submitList(currentLogs)
        }

        binding.btnExportLogs.setOnClickListener {
            if (currentLogs.isEmpty()) Toast.makeText(this, "Não há logs!", Toast.LENGTH_SHORT).show()
            else exportLogsLauncher.launch("mystreaks_logs.txt")
        }

        binding.btnCreateBackup.setOnClickListener {
            createBackupLauncher.launch("mystreaks_backup.json")
        }

        binding.btnRestoreBackup.setOnClickListener {
            restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
    }

    private fun exportLogsToFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val writer = outputStream.bufferedWriter()
                writer.write("--- DIÁRIO DE AÇÕES: MYSTREAKS ---\n\n")

                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                currentLogs.forEach { log ->
                    val date = sdf.format(Date(log.timestamp))
                    writer.write("[$date] [${log.type}] ${log.message}\n")
                }
                writer.flush()
            }
            Toast.makeText(this, "TXT guardado com sucesso!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao exportar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createBackupFile(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Puxa tudo da base de dados!
                val streaks = database.streakDao().getAllStreaksSync()
                val tasks = database.taskDao().getAllTasksSync()
                val logs = database.appLogDao().getAllLogsSync()

                val backupData = BackupData(streaks, tasks, logs)
                val jsonString = Gson().toJson(backupData)

                withContext(Dispatchers.Main) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray())
                    }
                    Toast.makeText(this@LogsActivity, "Backup criado com sucesso!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LogsActivity, "Erro no backup.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmAndRestoreBackup(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Restaurar Dados")
            .setMessage("Isto vai apagar as tuas tarefas e streaks atuais e substituí-las pelas que estão no ficheiro. Queres continuar?")
            .setPositiveButton("Sim, Restaurar") { _, _ -> restoreBackup(uri) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun restoreBackup(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val reader = InputStreamReader(inputStream)
                val backupData = Gson().fromJson(reader, BackupData::class.java)

                if (backupData != null && backupData.streaks != null) {
                    // Magia: Limpa a base de dados atual!
                    database.clearAllTables()

                    // Insere os dados restaurados do passado
                    backupData.streaks.forEach { database.streakDao().insert(it) }
                    backupData.tasks.forEach { database.taskDao().insert(it) }
                    backupData.logs.forEach { database.appLogDao().insertLog(it) }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LogsActivity, "Restauro concluído! 🚀", Toast.LENGTH_LONG).show()
                        finish() // Fecha e volta ao ecrã principal para carregar o passado
                    }
                } else {
                    withContext(Dispatchers.Main) { Toast.makeText(this@LogsActivity, "Ficheiro inválido ou corrompido.", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LogsActivity, "Erro a restaurar: Escolheste um ficheiro JSON de backup?", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}