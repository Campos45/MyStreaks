package pt.ipt.mystreaks

// Esta classe serve para agrupar tudo num só pacote de exportação
data class BackupData(
    val streaks: List<Streak>,
    val tasks: List<Task>,
    val logs: List<AppLog>
)