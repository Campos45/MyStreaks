package pt.ipt.mystreaks.ui.lists

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import pt.ipt.mystreaks.R
import pt.ipt.mystreaks.data.model.MyList
import pt.ipt.mystreaks.data.model.Tag

class MyListAdapter(
    private val onEditClicked: (MyList) -> Unit
) : ListAdapter<MyList, MyListAdapter.MyListViewHolder>(MyListComparator()) {

    private var tagsList: List<Tag> = emptyList()

    fun setTags(tags: List<Tag>) {
        this.tagsList = tags
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyListViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_list, parent, false)
        return MyListViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyListViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MyListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvListName: TextView = itemView.findViewById(R.id.tvListName)
        private val tvTag: TextView = itemView.findViewById(R.id.tvTag)
        private val tvListContent: TextView = itemView.findViewById(R.id.tvListContent)
        private val ivPinned: ImageView = itemView.findViewById(R.id.ivPinned)

        fun bind(myList: MyList) {
            tvListName.text = myList.name

            // Só mostramos o Pin se estiver afixada (e não mostramos no arquivo)
            ivPinned.visibility = if (myList.isPinned && !myList.isArchived) View.VISIBLE else View.GONE

            try {
                tvListContent.text = HtmlCompat.fromHtml(myList.content, HtmlCompat.FROM_HTML_MODE_COMPACT)
            } catch (e: Exception) {
                tvListContent.text = myList.content
            }

            if (!myList.tag.isNullOrBlank()) {
                tvTag.visibility = View.VISIBLE
                val cleanName = myList.tag!!.trim()
                tvTag.text = cleanName
                val tagEncontrada = tagsList.find { it.name.replace("\\s+".toRegex(), "").equals(cleanName.replace("\\s+".toRegex(), ""), ignoreCase = true) }
                val finalHex = tagEncontrada?.color ?: "#757575"
                try {
                    val colorInt = Color.parseColor(finalHex)
                    val shape = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 32f; setColor(colorInt) }
                    tvTag.background = shape
                    ViewCompat.setBackgroundTintList(tvTag, ColorStateList.valueOf(colorInt))
                    tvTag.setTextColor(Color.WHITE)
                } catch (e: Exception) {
                    tvTag.setBackgroundColor(Color.GRAY)
                }
            } else {
                tvTag.visibility = View.GONE
            }

            val bgColor = myList.backgroundColor ?: "#FFFFFF"
            (itemView as MaterialCardView).setCardBackgroundColor(Color.parseColor(bgColor))

            // Clique abre o menu de edição (onde vão estar os botões de apagar/arquivar!)
            itemView.setOnClickListener { onEditClicked(myList) }
        }
    }

    class MyListComparator : DiffUtil.ItemCallback<MyList>() {
        override fun areItemsTheSame(oldItem: MyList, newItem: MyList) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MyList, newItem: MyList) = oldItem == newItem
    }
}