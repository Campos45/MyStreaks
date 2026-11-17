package pt.ipt.mystreaks

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ipt.mystreaks.data.MyFullBackup
import pt.ipt.mystreaks.data.model.DiaryEntry
import pt.ipt.mystreaks.data.model.Task

class TaskCompletionNoteTest {

    private val gson = Gson()

    @Test
    fun newTask_hasNullCompletionNoteByDefault() {
        val task = Task(name = "Estudar Kotlin", notes = "Capítulos 1 a 3")
        assertNull(task.completionNote)
        assertEquals("Capítulos 1 a 3", task.notes)
    }

    @Test
    fun completingTask_preservesDetailedNotesAndAddsCompletionNote() {
        val task = Task(id = 7, name = "Entregar relatório", notes = "Incluir gráficos de desempenho")
        val now = System.currentTimeMillis()
        val completed = task.copy(
            isCompleted = true,
            completionDate = now,
            completionNote = "Enviado por email e aprovado pelo orientador"
        )

        assertTrue(completed.isCompleted)
        assertEquals(now, completed.completionDate)
        assertEquals("Incluir gráficos de desempenho", completed.notes)
        assertEquals("Enviado por email e aprovado pelo orientador", completed.completionNote)
    }

    @Test
    fun backupSerialization_preservesCompletionNoteAndDiaryEntries() {
        val task = Task(
            id = 1,
            name = "Deploy v1.2",
            isCompleted = true,
            completionDate = 1700000000000L,
            notes = "Verificar migração DB",
            completionNote = "Todos os testes passaram!"
        )
        val diary = DiaryEntry(
            dateMillis = 1700000000000L,
            content = "Hoje lancei a nova versão da app."
        )

        val backup = MyFullBackup(tasks = listOf(task), diaryEntries = listOf(diary))
        val json = gson.toJson(backup)
        val restored = gson.fromJson(json, MyFullBackup::class.java)

        assertNotNull(restored)
        assertEquals("Todos os testes passaram!", restored.tasks?.first()?.completionNote)
        assertEquals("Verificar migração DB", restored.tasks?.first()?.notes)
        assertEquals(1, restored.diaryEntries?.size)
        assertEquals("Hoje lancei a nova versão da app.", restored.diaryEntries?.first()?.content)
    }

    @Test
    fun oldBackupWithoutCompletionNoteOrDiary_deserializesSafely() {
        val legacyJson = """{"tasks":[{"id":1,"name":"Tarefa antiga","isCompleted":false,"priority":3,"orderIndex":0,"isArchived":false,"subTasks":[]}]}"""
        val restored = gson.fromJson(legacyJson, MyFullBackup::class.java)

        assertNotNull(restored)
        assertEquals(1, restored.tasks?.size)
        assertNull(restored.tasks?.first()?.completionNote)
        assertNull(restored.diaryEntries)
    }
}
