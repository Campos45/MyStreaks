package pt.ipt.mystreaks.ui.tasks

import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.data.model.Task

object TaskDialogs {
    fun showCompletionNoteDialog(
        context: Context,
        task: Task,
        onComplete: (Task) -> Unit,
        onCancel: () -> Unit
    ) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padH = (20 * context.resources.displayMetrics.density).toInt()
            val padV = (8 * context.resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)
        }

        val input = EditText(context).apply {
            hint = context.getString(R.string.completion_note_prompt_hint)
            minLines = 2
            maxLines = 4
            setText(task.completionNote ?: "")
        }
        container.addView(input)

        var handled = false
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.completion_note_prompt_title)
            .setMessage(R.string.completion_note_prompt_msg)
            .setView(container)
            .setPositiveButton(R.string.btn_complete_with_note) { dialog, _ ->
                handled = true
                val note = input.text.toString().trim().ifBlank { null }
                val completedTask = task.copy(
                    isCompleted = true,
                    completionDate = System.currentTimeMillis(),
                    completionNote = note
                )
                onComplete(completedTask)
                dialog.dismiss()
            }
            .setNeutralButton(R.string.btn_complete_no_note) { dialog, _ ->
                handled = true
                val completedTask = task.copy(
                    isCompleted = true,
                    completionDate = System.currentTimeMillis(),
                    completionNote = null
                )
                onComplete(completedTask)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                handled = true
                onCancel()
                dialog.dismiss()
            }
            .setOnCancelListener {
                if (!handled) onCancel()
            }
            .show()
    }
}
