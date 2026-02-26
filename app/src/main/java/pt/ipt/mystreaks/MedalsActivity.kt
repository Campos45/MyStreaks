package pt.ipt.mystreaks

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.ipt.mystreaks.databinding.ActivityMedalsBinding
import java.util.Calendar

class MedalsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedalsBinding
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedalsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.rvMedals.layoutManager = GridLayoutManager(this, 2) // 2 medalhas por linha

        calculateMedals()
    }

    private fun calculateMedals() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Reutilizamos as funções do Backup para ler tudo de uma vez
            val streaks = database.streakDao().getAllStreaksSync()
            val tasks = database.taskDao().getAllTasksSync()

            // Variáveis de cálculo
            val maxStreakEver = streaks.maxOfOrNull { s ->
                maxOf(s.count, s.history.maxOfOrNull { it.count } ?: 0)
            } ?: 0

            val completedTasks = tasks.filter { it.isCompleted }
            val totalTags = streaks.mapNotNull { it.tag }.distinct().size

            // Verificar os tempos das tarefas (Madrugador/Coruja/Fim de semana)
            var hasMadrugador = false
            var hasCoruja = false
            var hasFimDeSemana = false

            completedTasks.forEach { task ->
                if (task.completionDate != null) {
                    val cal = Calendar.getInstance().apply { timeInMillis = task.completionDate!! }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

                    if (hour < 8) hasMadrugador = true
                    if (hour == 23) hasCoruja = true
                    if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) hasFimDeSemana = true
                }
            }

            // Construir a Lista Final de Medalhas
            val medalsList = listOf(
                // Consistência
                Medal("A Faísca", "3 dias seguidos", "🔥", maxStreakEver >= 3),
                Medal("A Fogueira", "7 dias seguidos", "⛺", maxStreakEver >= 7),
                Medal("O Vulcão", "30 dias seguidos", "🌋", maxStreakEver >= 30),
                Medal("Lenda Viva", "100 dias seguidos", "👑", maxStreakEver >= 100),
                Medal("Meio Ano", "180 dias seguidos", "⏳", maxStreakEver >= 180),
                Medal("Um Ano!", "365 dias seguidos", "🌍", maxStreakEver >= 365),
                Medal("Dois Anos!", "730 dias seguidos", "🌌", maxStreakEver >= 730),
                Medal("Cinco Anos!", "1825 dias seguidos", "💎", maxStreakEver >= 1825),

                // Ação
                Medal("Primeiro Passo", "1ª tarefa concluída", "🌱", completedTasks.isNotEmpty()),
                Medal("A Máquina", "50 tarefas concluídas", "⚙️", completedTasks.size >= 50),
                Medal("Perfeccionista", "Tarefa com 5+ passos", "🧩", completedTasks.any { it.subTasks.size >= 5 }),

                // Especiais
                Medal("Mestre da Organização", "Usar 3+ Tags", "🎨", totalTags >= 3),
                Medal("O Madrugador", "Tarefa antes das 8h", "🌅", hasMadrugador),
                Medal("A Coruja", "Tarefa na última hora (23h)", "🦉", hasCoruja),
                Medal("Fim de Semana Épico", "Concluir ao Sáb/Dom", "🦸‍♂️", hasFimDeSemana)
            )

            withContext(Dispatchers.Main) {
                binding.rvMedals.adapter = MedalAdapter(medalsList)
            }
        }
    }
}