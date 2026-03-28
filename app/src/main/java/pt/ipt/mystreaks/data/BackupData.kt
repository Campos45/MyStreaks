package pt.ipt.mystreaks.data

import pt.ipt.mystreaks.data.model.MyList
import pt.ipt.mystreaks.data.model.Streak
import pt.ipt.mystreaks.data.model.Tag
import pt.ipt.mystreaks.data.model.Task

data class MyFullBackup(
    val streaks: List<Streak>? = null,
    val tasks: List<Task>? = null,
    val lists: List<MyList>? = null,
    val tags: List<Tag>? = null
)