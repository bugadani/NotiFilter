package hu.bugadani.notifilter

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var appListView: RecyclerView
    private val enabledFilters = HashSet<String>()
    private val elements = ArrayList<AppInfoElement>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        appListView = findViewById<RecyclerView>(R.id.appList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = AppListItemAdapter(elements, enabledFilters)
        }
    }

    override fun onResume() {
        super.onResume()

        Log.d(NotificationListener.TAG, "onCreate: read filter")
        val preferences = getSharedPreferences(
                "appSettings",
                Context.MODE_PRIVATE
        )
        val set = preferences.getStringSet("filter", HashSet())
        if (set != null) {
            enabledFilters.addAll(set)
        }

        // List installed apps
        val packages = packageManager.queryIntentActivities(Intent(ACTION_MAIN, null), 0)
        val seen = HashSet<CharSequence>()
        for (resInfo in packages) {
            if (resInfo.activityInfo.packageName == "hu.bugadani.notifilter") {
                continue
            }
            val appInfo = packageManager.getApplicationInfo(resInfo.activityInfo.packageName, 0)

            if (seen.add(appInfo.loadLabel(packageManager))) {
                elements.add(AppInfoElement(appInfo, this))
            }
        }

        elements.sortBy { it.appName }

        appListView.adapter?.notifyDataSetChanged()
    }

    override fun onPause() {
        Log.d(TAG, "Saving preferences")
        val preferences = getSharedPreferences(
                "appSettings",
                Context.MODE_PRIVATE
        )
        with(preferences.edit()) {
            putStringSet("filter", enabledFilters)
            apply()
        }

        val pingServiceIntent = Intent(this, NotificationListener::class.java)
        this.startService(pingServiceIntent)

        super.onPause()
    }
}
