package pt.ipt.mystreaks.data

import pt.ipt.mystreaks.data.model.AppLog
import pt.ipt.mystreaks.data.model.Streak
import pt.ipt.mystreaks.data.model.Task

// Esta classe serve para agrupar tudo num só pacote de exportação
data class BackupData(
    val streaks: List<Streak>,
    val tasks: List<Task>,
    val logs: List<AppLog>
)