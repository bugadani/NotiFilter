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

class AppListItemAdapter(private val enabledFilters: HashMap<String, FilterOption>) :
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
        val backgroundButtons1Minute: View = backgroundButtons.findViewById(R.id.setting_one_minute)
        val backgroundButtons5Minutes: View =
            backgroundButtons.findViewById(R.id.setting_five_minutes)
        val backgroundButtonsManual: View = backgroundButtons.findViewById(R.id.setting_manual)
        val foreground: View = view.findViewById(R.id.foreground)
        var isChanging: Boolean = false
        var menuOpen: Boolean = false
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
        viewHolder.menuOpen = false
        viewHolder.foreground.translationX = 0f
        viewHolder.background.visibility = View.GONE

        viewHolder.appEnabledView.setOnCheckedChangeListener { view, isChecked ->
            if (!viewHolder.isChanging) {
                viewHolder.isChanging = true
                Log.d(
                    "MainActivity",
                    "App filter change: " + view.tag + " -> " + isChecked
                )
                updateSelection(viewHolder, isChecked, FilterOption.ManualReset)
                updateButtons(viewHolder, isChecked)
                viewHolder.isChanging = false
            }
        }

        viewHolder.backgroundButtons.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!viewHolder.isChanging) {
                viewHolder.isChanging = true
                try {
                    val filter = when (checkedId) {
                        viewHolder.backgroundButtons1Minute.id -> FilterOption.AutoReset1Minute
                        viewHolder.backgroundButtons5Minutes.id -> FilterOption.AutoReset5Minutes
                        viewHolder.backgroundButtonsManual.id -> FilterOption.ManualReset
                        else -> throw IllegalStateException("Invalid id selected: $checkedId")
                    }
                    updateSelection(viewHolder, isChecked, filter)
                    updateSwitch(viewHolder)
                } catch (e: IllegalStateException) {
                    Log.e("AppListItemAdapter", "Exception: $e")
                }
                viewHolder.isChanging = false
            }
        }

        viewHolder.isChanging = true
        updateSwitch(viewHolder)
        updateButtons(viewHolder, viewHolder.appEnabledView.isChecked)
        viewHolder.isChanging = false
    }

    private fun updateSelection(viewHolder: ViewHolder, isChecked: Boolean, kind: FilterOption) {
        if (isChecked) {
            enabledFilters[viewHolder.appEnabledView.tag as String] = kind
        } else {
            enabledFilters.remove(viewHolder.appEnabledView.tag as String)
        }
    }

    private fun updateSwitch(viewHolder: ViewHolder) {
        viewHolder.appEnabledView.isChecked = enabledFilters.contains(viewHolder.appEnabledView.tag)
    }

    private fun updateButtons(viewHolder: ViewHolder, isChecked: Boolean) {
        if (isChecked) {
            when (enabledFilters[viewHolder.appEnabledView.tag]) {
                FilterOption.AutoReset1Minute -> viewHolder.backgroundButtons.check(viewHolder.backgroundButtons1Minute.id)
                FilterOption.AutoReset5Minutes -> viewHolder.backgroundButtons.check(viewHolder.backgroundButtons5Minutes.id)
                FilterOption.ManualReset -> viewHolder.backgroundButtons.check(viewHolder.backgroundButtonsManual.id)
            }
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