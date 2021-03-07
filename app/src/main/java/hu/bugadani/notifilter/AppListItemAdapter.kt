package hu.bugadani.notifilter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.ToggleButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup

class AppListItemAdapter(private val perAppOptions: HashMap<String, AppOptions>) :
    ListAdapter<AppListItem, AppListItemAdapter.ViewHolder>(Differ()) {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appNameView: TextView = view.findViewById(R.id.appName)
        val appIconView: ImageView = view.findViewById(R.id.appIcon)
        val appEnabledView: Switch = view.findViewById(R.id.appEnabled)
        val foreground: View = view.findViewById(R.id.foreground)
        val background: View = view.findViewById(R.id.background)
        var isChanging: Boolean = false
        var menuOpen: Boolean = false

        val filterOptionButtons: MaterialButtonToggleGroup = view.findViewById(R.id.background_buttons)
        val filterOption1Minute: View = filterOptionButtons.findViewById(R.id.setting_one_minute)
        val filterOption5Minutes: View = filterOptionButtons.findViewById(R.id.setting_five_minutes)
        val filterOptionManual: View = filterOptionButtons.findViewById(R.id.setting_manual)

        val groupOverrideButton: ToggleButton = view.findViewById(R.id.group_override_button)

        private fun getFilterOption(appOptions: AppOptions?): FilterOption {
            return appOptions?.filterOption ?: FilterOption.Ignore
        }

        private fun isFiltered(appOptions: AppOptions?): Boolean {
            return getFilterOption(appOptions) != FilterOption.Ignore
        }

        private fun isGroupOverrideActive(appOptions: AppOptions?): Boolean {
            return appOptions?.overrideGroups ?: false
        }

        private fun updateSwitch(appOptions: AppOptions?) {
            appEnabledView.isChecked = isFiltered(appOptions)
        }

        private fun updateGroupOverrideButton(appOptions: AppOptions?) {
            groupOverrideButton.isChecked = isGroupOverrideActive(appOptions)
        }

        private fun updateButtons(appOptions: AppOptions?) {
            when (getFilterOption(appOptions)) {
                FilterOption.Ignore -> filterOptionButtons.clearChecked()
                FilterOption.AutoReset1Minute -> filterOptionButtons.check(filterOption1Minute.id)
                FilterOption.AutoReset5Minutes -> filterOptionButtons.check(filterOption5Minutes.id)
                FilterOption.ManualReset -> filterOptionButtons.check(filterOptionManual.id)
            }
        }

        fun update(appOptions: AppOptions?) {
            isChanging = true
            updateButtons(appOptions)
            updateSwitch(appOptions)
            updateGroupOverrideButton(appOptions)
            isChanging = false
        }
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
        viewHolder.groupOverrideButton.tag = appInfo.packageName
        viewHolder.menuOpen = false
        viewHolder.foreground.translationX = 0f

        viewHolder.groupOverrideButton.setOnCheckedChangeListener { view, isChecked ->
            if (!viewHolder.isChanging) {
                Log.d(
                    "MainActivity",
                    "App group override: ${view.tag} -> $isChecked"
                )

                updateGroupOverride(viewHolder, isChecked)
                updateViewHolder(viewHolder)
            }
        }

        viewHolder.appEnabledView.setOnCheckedChangeListener { view, isChecked ->
            if (!viewHolder.isChanging) {
                Log.d(
                    "MainActivity",
                    "App filter change: ${view.tag} -> $isChecked"
                )
                val filter = if (isChecked) FilterOption.ManualReset else FilterOption.Ignore

                updateFilterOption(viewHolder, filter)
                updateViewHolder(viewHolder)
            }
        }

        viewHolder.filterOptionButtons.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!viewHolder.isChanging) {
                try {
                    val filter = if (isChecked) {
                        when (checkedId) {
                            viewHolder.filterOption1Minute.id -> FilterOption.AutoReset1Minute
                            viewHolder.filterOption5Minutes.id -> FilterOption.AutoReset5Minutes
                            viewHolder.filterOptionManual.id -> FilterOption.ManualReset
                            else -> throw IllegalStateException("Invalid id selected: $checkedId")
                        }
                    } else {
                        FilterOption.Ignore
                    }
                    updateFilterOption(viewHolder, filter)
                    updateViewHolder(viewHolder)
                } catch (e: IllegalStateException) {
                    Log.e("AppListItemAdapter", "Exception: $e")
                }
            }
        }

        updateViewHolder(viewHolder)
    }

    private fun updateViewHolder(viewHolder: ViewHolder) {
        val key = viewHolder.appEnabledView.tag as String
        viewHolder.update(perAppOptions[key])
    }

    private fun updateFilterOption(viewHolder: ViewHolder, kind: FilterOption) {
        val key = viewHolder.appEnabledView.tag as String
        val old = perAppOptions.getOrDefault(key, AppOptions(kind, false))
        val new = AppOptions(kind, old.overrideGroups)

        updateOptions(key, new)
    }

    private fun updateGroupOverride(viewHolder: ViewHolder, groupOverride: Boolean) {
        val key = viewHolder.appEnabledView.tag as String
        val old = perAppOptions.getOrDefault(key, AppOptions(FilterOption.Ignore, groupOverride))
        val new = AppOptions(old.filterOption, groupOverride)

        updateOptions(key, new)
    }

    private fun updateOptions(key: String, appOptions: AppOptions) {
        if (appOptions.isEmpty()) {
            perAppOptions.remove(key)
        } else {
            perAppOptions[key] = appOptions
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