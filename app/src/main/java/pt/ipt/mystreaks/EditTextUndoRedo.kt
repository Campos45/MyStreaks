package pt.ipt.mystreaks

import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.widget.EditText

class EditTextUndoRedo(private val editText: EditText) {
    // Guarda o histórico com todas as cores e formatações intactas
    private val history = mutableListOf<CharSequence>()
    private var historyIndex = -1
    private var isUndoingOrRedoing = false

    init {
        saveState()

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!isUndoingOrRedoing) {
                    saveState()
                }
            }
        })
    }

    private fun saveState() {
        // Se escrevermos algo novo depois de termos feito "Undo", apagamos as versões futuras
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }

        // Guarda uma cópia exata do texto atual (limita a 30 passos para não pesar o telemóvel)
        history.add(SpannableStringBuilder(editText.text))
        if (history.size > 30) {
            history.removeAt(0)
        } else {
            historyIndex = history.size - 1
        }
    }

    fun undo() {
        if (historyIndex > 0) {
            isUndoingOrRedoing = true
            historyIndex--
            editText.text = SpannableStringBuilder(history[historyIndex])
            editText.setSelection(editText.text.length)
            isUndoingOrRedoing = false
        }
    }

    fun redo() {
        if (historyIndex < history.size - 1) {
            isUndoingOrRedoing = true
            historyIndex++
            editText.text = SpannableStringBuilder(history[historyIndex])
            editText.setSelection(editText.text.length)
            isUndoingOrRedoing = false
        }
    }
}