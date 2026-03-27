package pt.ipt.mystreaks

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pt.ipt.mystreaks.databinding.ActivityLogsBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogsBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { LogRepository(database.appLogDao()) }
    private val viewModel: LogViewModel by viewModels { LogViewModelFactory(repository) }

    private var currentLogs = emptyList<AppLog>()

    // Lançador para exportar o relatório TXT
    private val exportLogsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { exportLogsToFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Botão Voltar
        binding.btnBack.setOnClickListener { finish() }

        // Configurar Lista (RecyclerView)
        val adapter = LogAdapter()
        binding.rvLogs.adapter = adapter
        binding.rvLogs.layoutManager = LinearLayoutManager(this)

        // Observar Dados
        viewModel.allLogs.observe(this) { logs ->
            currentLogs = logs ?: emptyList()
            adapter.submitList(currentLogs)
        }

        // Botão Exportar (Ícone da partilha)
        binding.btnExportLogs.setOnClickListener {
            if (currentLogs.isEmpty()) {
                Toast.makeText(this, "Não há registos!", Toast.LENGTH_SHORT).show()
            } else {
                exportLogsLauncher.launch("mystreaks_logs_report.txt")
            }
        }

        // Botão Limpar Tudo
        binding.btnClearLogs.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Limpar Histórico?")
                .setMessage("Esta ação irá apagar todos os registos de ações permanentemente.")
                .setPositiveButton("Sim, Limpar") { _, _ ->
                    viewModel.deleteAll()
                    Toast.makeText(this, "Histórico limpo! 🧹", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun exportLogsToFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { os ->
                val writer = os.bufferedWriter()
                writer.write("--- DIÁRIO DE AÇÕES: MYSTREAKS ---\n")
                writer.write("Relatório gerado em: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}\n\n")

                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                currentLogs.forEach { log ->
                    val date = sdf.format(Date(log.timestamp))
                    writer.write("[$date] [${log.type}] ${log.message}\n")
                }
                writer.flush()
            }
            Toast.makeText(this, "Relatório TXT guardado com sucesso! 📄", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao exportar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}