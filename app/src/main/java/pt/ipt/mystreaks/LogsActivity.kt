package pt.ipt.mystreaks

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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

    // O mecanismo moderno do Android para criar um ficheiro TXT
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { exportLogsToFile(it) } // Se o utilizador escolheu onde guardar, disparamos a função!
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

        binding.tvExport.setOnClickListener {
            if (currentLogs.isEmpty()) {
                Toast.makeText(this, "Não há logs para exportar!", Toast.LENGTH_SHORT).show()
            } else {
                // Sugere o nome do ficheiro e abre a janela de guardar
                exportLauncher.launch("mystreaks_logs.txt")
            }
        }
    }

    private fun exportLogsToFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val writer = outputStream.bufferedWriter()
                writer.write("--- DIÁRIO DE AÇÕES: MYSTREAKS ---\n\n")

                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

                // Escreve cada log numa linha nova no ficheiro
                currentLogs.forEach { log ->
                    val date = sdf.format(Date(log.timestamp))
                    writer.write("[$date] [${log.type}] ${log.message}\n")
                }
                writer.flush()
            }
            Toast.makeText(this, "Ficheiro TXT guardado com sucesso!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao exportar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}