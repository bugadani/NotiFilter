package hu.bugadani.notifilter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListItemAdapter(private val dataSet: ArrayList<AppInfoElement>, private val enabledFilters: HashSet<String>) :
        RecyclerView.Adapter<AppListItemAdapter.ViewHolder>() {

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
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.app_list_item, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val appInfo = dataSet[position]

        viewHolder.appNameView.text = appInfo.appName
        viewHolder.appIconView.setImageDrawable(appInfo.appIcon)
        viewHolder.appEnabledView.tag = appInfo.packageName
        viewHolder.appEnabledView.isChecked = enabledFilters.contains(appInfo.packageName)
        viewHolder.appEnabledView.setOnCheckedChangeListener { view, isChecked ->
            run {
                Log.d(
                    "MainActivity",
                    "App filter change: " + view.tag + " -> " + isChecked
                )
                if (isChecked) {
                    enabledFilters.add(view.tag as String)
                } else {
                    enabledFilters.remove(view.tag as String)
                }
            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = dataSet.size
}