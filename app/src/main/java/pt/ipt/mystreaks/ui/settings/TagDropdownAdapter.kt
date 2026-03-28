package pt.ipt.mystreaks.ui.settings

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.data.model.Tag

class TagDropdownAdapter(
    context: Context,
    private val tags: List<Tag>
) : ArrayAdapter<Tag>(context, 0, tags) {

    private var filteredTags: List<Tag> = tags

    override fun getCount(): Int = filteredTags.size
    override fun getItem(position: Int): Tag? = filteredTags[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_dropdown_tag, parent, false)

        val tag = getItem(position)
        val tvTagName = view.findViewById<TextView>(R.id.tvTagName)
        val ivTagColor = view.findViewById<MaterialCardView>(R.id.ivTagColor)

        if (tag != null) {
            tvTagName.text = tag.name
            try {
                ivTagColor.setCardBackgroundColor(Color.parseColor(tag.color))
            } catch (e: Exception) {
                ivTagColor.setCardBackgroundColor(Color.GRAY)
            }
        }
        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val query = constraint?.toString()?.trim() ?: ""

                // O SEGREDO 🚀: Se a caixa estiver vazia, OU se o texto que lá está for
                // exatamente uma tag que já existe (modo Edição), mostramos a lista TODA!
                val isExactMatch = tags.any { it.name.equals(query, ignoreCase = true) }

                if (query.isEmpty() || isExactMatch) {
                    results.values = tags
                    results.count = tags.size
                } else {
                    // Só filtra se a pessoa estiver a escrever uma palavra incompleta (ex: "Gin...")
                    val filteredList = tags.filter { it.name.contains(query, ignoreCase = true) }
                    results.values = filteredList
                    results.count = filteredList.size
                }
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredTags = results?.values as? List<Tag> ?: emptyList()
                notifyDataSetChanged()
            }
        }
    }
}