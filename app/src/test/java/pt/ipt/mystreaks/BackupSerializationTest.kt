package pt.ipt.mystreaks

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import pt.ipt.mystreaks.data.MyFullBackup
import pt.ipt.mystreaks.data.model.MyList
import pt.ipt.mystreaks.data.model.Streak
import pt.ipt.mystreaks.data.model.SubTask
import pt.ipt.mystreaks.data.model.Tag
import pt.ipt.mystreaks.data.model.Task

class BackupSerializationTest {

    private val gson = Gson()

    @Test
    fun fullBackup_serializesAndDeserializesCorrectly() {
        val streaks = listOf(
            Streak(id = 1, name = "Beber Água", type = "D", count = 5, remindHour = 10, remindMinute = 30)
        )
        val tasks = listOf(
            Task(
                id = 1,
                name = "Trabalho Final",
                isCompleted = false,
                priority = 4,
                subTasks = listOf(SubTask("Introdução", true), SubTask("Desenvolvimento", false)),
                dueDate = 1700000000000L
            )
        )
        val lists = listOf(
            MyList(id = 1, name = "Ideias de Projeto", content = "<b>App Android</b>", backgroundColor = "#FFFBEB", isPinned = true)
        )
        val tags = listOf(
            Tag(id = 1, name = "Faculdade", color = "#448AFF", type = "T")
        )

        val originalBackup = MyFullBackup(
            streaks = streaks,
            tasks = tasks,
            lists = lists,
            tags = tags
        )

        val json = gson.toJson(originalBackup)
        val restoredBackup = gson.fromJson(json, MyFullBackup::class.java)

        assertNotNull(restoredBackup)
        assertEquals(1, restoredBackup.streaks?.size)
        assertEquals("Beber Água", restoredBackup.streaks?.first()?.name)
        assertEquals(1, restoredBackup.tasks?.size)
        assertEquals(2, restoredBackup.tasks?.first()?.subTasks?.size)
        assertEquals(1, restoredBackup.lists?.size)
        assertTrue(restoredBackup.lists?.first()?.isPinned == true)
        assertEquals(1, restoredBackup.tags?.size)
    }

    private fun assertTrue(b: Boolean) {
        org.junit.Assert.assertTrue(b)
    }
}
