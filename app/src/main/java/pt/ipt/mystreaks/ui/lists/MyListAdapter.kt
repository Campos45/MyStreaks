package pt.ipt.mystreaks.ui.lists

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
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
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val tvListName: TextView = itemView.findViewById(R.id.tvListName)
        private val tvTag: TextView = itemView.findViewById(R.id.tvTag)
        private val tvListContent: TextView = itemView.findViewById(R.id.tvListContent)
        private val ivPinned: ImageView = itemView.findViewById(R.id.ivPinned)

        fun bind(myList: MyList) {
            tvListName.text = myList.name

            // Só mostramos o Pin se estiver afixada (e não mostramos no arquivo)
            ivPinned.visibility = if (myList.isPinned && !myList.isArchived) View.VISIBLE else View.GONE

            val rawContent = myList.content
            if (rawContent.isNotBlank()) {
                tvListContent.visibility = View.VISIBLE
                try {
                    tvListContent.text = HtmlCompat.fromHtml(rawContent, HtmlCompat.FROM_HTML_MODE_COMPACT)
                } catch (e: Exception) {
                    tvListContent.text = rawContent
                }
            } else {
                tvListContent.visibility = View.GONE
            }

            if (!myList.tag.isNullOrBlank()) {
                tvTag.visibility = View.VISIBLE
                val cleanName = myList.tag!!.trim()
                tvTag.text = cleanName
                val tagEncontrada = tagsList.find {
                    it.name.replace("\\s+".toRegex(), "").equals(cleanName.replace("\\s+".toRegex(), ""), ignoreCase = true)
                }
                val finalHex = tagEncontrada?.color ?: "#757575"
                try {
                    val colorInt = Color.parseColor(finalHex)
                    val shape = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 32f
                        setColor(colorInt)
                    }
                    tvTag.background = shape
                    ViewCompat.setBackgroundTintList(tvTag, ColorStateList.valueOf(colorInt))
                    tvTag.setTextColor(Color.WHITE)
                } catch (e: Exception) {
                    tvTag.setBackgroundColor(Color.GRAY)
                }
            } else {
                tvTag.visibility = View.GONE
            }

            // --- CONTRASTE INTELIGENTE DA NOTA ---
            val bgColorHex = myList.backgroundColor ?: "#FFFFFF"
            val bgColorInt = try {
                Color.parseColor(bgColorHex)
            } catch (e: Exception) {
                Color.WHITE
            }
            cardView.setCardBackgroundColor(bgColorInt)

            val luminance = ColorUtils.calculateLuminance(bgColorInt)
            val isDark = luminance < 0.45

            if (isDark) {
                tvListName.setTextColor(Color.WHITE)
                tvListContent.setTextColor(Color.parseColor("#E5E7EB"))
                ivPinned.imageTintList = ColorStateList.valueOf(Color.parseColor("#FFCA28"))
                cardView.strokeWidth = 0
            } else {
                tvListName.setTextColor(Color.parseColor("#1F2937"))
                tvListContent.setTextColor(Color.parseColor("#4B5563"))
                ivPinned.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF6D00"))
                // Borda suave apenas em fundos claros/brancos
                val isNearWhite = luminance > 0.85
                cardView.strokeWidth = if (isNearWhite) 2 else 0
                cardView.strokeColor = Color.parseColor("#14000000")
            }

            // Clique abre o menu de edição
            itemView.setOnClickListener { onEditClicked(myList) }
        }
    }

    class MyListComparator : DiffUtil.ItemCallback<MyList>() {
        override fun areItemsTheSame(oldItem: MyList, newItem: MyList) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MyList, newItem: MyList) = oldItem == newItem
    }
}
