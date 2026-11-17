package pt.ipt.mystreaks.utils

import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.widget.TooltipCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

object TooltipHelper {

    fun attachTooltip(view: View, @StringRes messageResId: Int) {
        attachTooltip(view, view.context.getString(messageResId))
    }

    fun attachTooltip(view: View, message: String) {
        if (message.isBlank()) return
        view.contentDescription = message
        TooltipCompat.setTooltipText(view, message)
        view.setOnLongClickListener {
            Toast.makeText(view.context, message, Toast.LENGTH_SHORT).show()
            true
        }
    }

    fun attachBottomNavTooltips(bottomNav: BottomNavigationView, tooltipsById: Map<Int, Int>) {
        tooltipsById.forEach { (itemId, messageResId) ->
            val itemView = bottomNav.findViewById<View>(itemId)
            if (itemView != null) {
                attachTooltip(itemView, messageResId)
            }
        }
    }
}

fun View.setInfoTooltip(@StringRes messageResId: Int) {
    TooltipHelper.attachTooltip(this, messageResId)
}

fun View.setInfoTooltip(message: String) {
    TooltipHelper.attachTooltip(this, message)
}
