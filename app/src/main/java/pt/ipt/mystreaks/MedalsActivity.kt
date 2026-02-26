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
        binding.rvMedals.layoutManager = GridLayoutManager(this, 2)

        calculateMedals()
    }

    private fun calculateMedals() {
        lifecycleScope.launch(Dispatchers.IO) {
            val streaks = database.streakDao().getAllStreaksSync()
            val tasks = database.taskDao().getAllTasksSync()

            // Descobre o Recorde Máximo e qual foi a Streak que o atingiu
            val bestStreak = streaks.maxByOrNull { s -> maxOf(s.count, s.history.maxOfOrNull { it.count } ?: 0) }
            val maxStreakEver = bestStreak?.let { maxOf(it.count, it.history.maxOfOrNull { h -> h.count } ?: 0) } ?: 0
            val bestStreakName = bestStreak?.name

            val completedTasks = tasks.filter { it.isCompleted }
            val totalTags = streaks.mapNotNull { it.tag }.distinct().size

            // Descobre as Tarefas notáveis
            val firstTaskName = completedTasks.minByOrNull { it.completionDate ?: Long.MAX_VALUE }?.name
            val complexTaskName = completedTasks.find { it.subTasks.size >= 5 }?.name

            var hasMadrugador = false
            var madrugadorName: String? = null
            var hasCoruja = false
            var corujaName: String? = null
            var hasFimDeSemana = false
            var fdsName: String? = null

            completedTasks.forEach { task ->
                if (task.completionDate != null) {
                    val cal = Calendar.getInstance().apply { timeInMillis = task.completionDate!! }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

                    if (hour < 8 && !hasMadrugador) { hasMadrugador = true; madrugadorName = task.name }
                    if (hour == 23 && !hasCoruja) { hasCoruja = true; corujaName = task.name }
                    if ((dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) && !hasFimDeSemana) {
                        hasFimDeSemana = true; fdsName = task.name
                    }
                }
            }

            // Lista com os nomes passados no último parâmetro
            val medalsList = listOf(
                Medal("A Faísca", "3 dias seguidos", "🔥", maxStreakEver >= 3, bestStreakName),
                Medal("A Fogueira", "7 dias seguidos", "⛺", maxStreakEver >= 7, bestStreakName),
                Medal("O Vulcão", "30 dias seguidos", "🌋", maxStreakEver >= 30, bestStreakName),
                Medal("Lenda Viva", "100 dias seguidos", "👑", maxStreakEver >= 100, bestStreakName),
                Medal("Primeiro Passo", "1ª tarefa concluída", "🌱", completedTasks.isNotEmpty(), firstTaskName),
                Medal("A Máquina", "50 tarefas concluídas", "⚙️", completedTasks.size >= 50, null),
                Medal("Perfeccionista", "Tarefa com 5+ passos", "🧩", complexTaskName != null, complexTaskName),
                Medal("Mestre da Organização", "Usar 3+ Tags", "🎨", totalTags >= 3, null),
                Medal("O Madrugador", "Tarefa antes das 8h", "🌅", hasMadrugador, madrugadorName),
                Medal("A Coruja", "Tarefa na última hora", "🦉", hasCoruja, corujaName),
                Medal("Fim de Semana", "Concluir ao Sáb/Dom", "🦸‍♂️", hasFimDeSemana, fdsName)
            )

            withContext(Dispatchers.Main) {
                binding.rvMedals.adapter = MedalAdapter(medalsList)
            }
        }
    }
}