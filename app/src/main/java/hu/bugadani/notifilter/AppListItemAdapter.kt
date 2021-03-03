package hu.bugadani.notifilter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup

class AppListItemAdapter(private val enabledFilters: HashSet<String>) :
    ListAdapter<AppListItem, AppListItemAdapter.ViewHolder>(Differ()) {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appNameView: TextView = view.findViewById(
            R.id.appName
        )
        val appIconView: ImageView = view.findViewById(
            R.id.appIcon
        )
        val appEnabledView: Switch = view.findViewById(
            R.id.appEnabled
        )
        val background: View = view.findViewById(R.id.background)
        val backgroundButtons: MaterialButtonToggleGroup =
            view.findViewById(R.id.background_buttons)
        val foreground: View = view.findViewById(R.id.foreground)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val inflater = LayoutInflater.from(viewGroup.context)

        return ViewHolder(inflater.inflate(R.layout.app_list_item, viewGroup, false))
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val appInfo = getItem(position)

        viewHolder.appNameView.text = appInfo.appName
        viewHolder.appIconView.setImageDrawable(appInfo.icon)
        viewHolder.appEnabledView.tag = appInfo.packageName
        updateSwitch(viewHolder)
        viewHolder.appEnabledView.setOnCheckedChangeListener { view, isChecked ->
            Log.d(
                "MainActivity",
                "App filter change: " + view.tag + " -> " + isChecked
            )
            updateSelection(viewHolder, isChecked)
            updateButtons(viewHolder, isChecked)
        }
        updateButtons(viewHolder, viewHolder.appEnabledView.isChecked)

        viewHolder.backgroundButtons.addOnButtonCheckedListener { group, checkedId, isChecked ->
            when (checkedId) {
                R.id.setting_one_minute -> {
                    updateSelection(viewHolder, isChecked)
                }
                R.id.setting_five_minutes -> {
                    updateSelection(viewHolder, isChecked)
                }
                R.id.setting_manual -> {
                    updateSelection(viewHolder, isChecked)
                }
            }
            updateSwitch(viewHolder)
        }
    }

    private fun updateSelection(viewHolder: ViewHolder, isChecked: Boolean) {
        if (isChecked) {
            enabledFilters.add(viewHolder.appEnabledView.tag as String)
        } else {
            enabledFilters.remove(viewHolder.appEnabledView.tag as String)
        }
    }

    private fun updateSwitch(viewHolder: ViewHolder) {
        viewHolder.appEnabledView.isChecked = enabledFilters.contains(viewHolder.appEnabledView.tag)
    }

    private fun updateButtons(viewHolder: ViewHolder, isChecked: Boolean) {
        if (isChecked) {
            viewHolder.backgroundButtons.check(R.id.setting_manual)
        } else {
            viewHolder.backgroundButtons.clearChecked()
        }
    }
}

class Differ : DiffUtil.ItemCallback<AppListItem>() {
    override fun areItemsTheSame(oldItem: AppListItem, newItem: AppListItem): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: AppListItem, newItem: AppListItem): Boolean {
        return oldItem == newItem
    }
}