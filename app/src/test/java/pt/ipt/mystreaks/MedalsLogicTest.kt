package pt.ipt.mystreaks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ipt.mystreaks.data.model.Streak
import pt.ipt.mystreaks.data.model.SubTask
import pt.ipt.mystreaks.data.model.Task
import java.util.Calendar

class MedalsLogicTest {

    @Test
    fun streakMedals_evaluatesThresholdsCorrectly() {
        val streaks = listOf(
            Streak(name = "Treino", type = "D", count = 35)
        )

        val bestStreak = streaks.maxByOrNull { s -> maxOf(s.count, s.history.maxOfOrNull { it.count } ?: 0) }
        val maxStreakEver = bestStreak?.let { maxOf(it.count, it.history.maxOfOrNull { h -> h.count } ?: 0) } ?: 0

        assertTrue("A Faísca (>= 3)", maxStreakEver >= 3)
        assertTrue("A Fogueira (>= 7)", maxStreakEver >= 7)
        assertTrue("O Vulcão (>= 30)", maxStreakEver >= 30)
        assertFalse("Lenda Viva (>= 100)", maxStreakEver >= 100)
    }

    @Test
    fun perfectionistMedal_requires5Subtasks() {
        val tasks = listOf(
            Task(
                name = "Grande Projeto",
                isCompleted = true,
                subTasks = (1..5).map { SubTask("Passo $it", true) }
            )
        )

        val complexTask = tasks.find { it.isCompleted && it.subTasks.size >= 5 }
        assertTrue(complexTask != null)
    }

    @Test
    fun nightOwlMedal_evaluatesLateNightCompletions() {
        val lateNightCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 30)
        }

        val task = Task(
            name = "Rever notas",
            isCompleted = true,
            completionDate = lateNightCal.timeInMillis
        )

        val cal = Calendar.getInstance().apply { timeInMillis = task.completionDate!! }
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        assertTrue("Coruja (hour >= 22)", hour >= 22)
    }

    @Test
    fun earlyBirdMedal_evaluatesEarlyMorningCompletions() {
        val earlyCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 15)
        }

        val task = Task(
            name = "Yoga matinal",
            isCompleted = true,
            completionDate = earlyCal.timeInMillis
        )

        val cal = Calendar.getInstance().apply { timeInMillis = task.completionDate!! }
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        assertTrue("Madrugador (hour < 8)", hour < 8)
    }
}
