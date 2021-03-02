package hu.bugadani.notifilter

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var appListView: RecyclerView
    private lateinit var appsLoadingView: ProgressBar
    private val enabledFilters = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        appListView = findViewById<RecyclerView>(R.id.appList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = AppListItemAdapter(enabledFilters)
        }
        appsLoadingView = findViewById(R.id.appsLoading)
    }

    private fun getPreferences(): SharedPreferences {
        return getSharedPreferences(
            "appSettings",
            Context.MODE_PRIVATE
        )
    }

    override fun onResume() {
        Log.d(NotificationListener.TAG, "onResume")

        super.onResume()

        val preferences = getPreferences()
        val set = preferences.getStringSet("filter", HashSet())
        if (set != null) {
            enabledFilters.addAll(set)
        }

        // List installed apps

        val packages = packageManager.queryIntentActivities(Intent(ACTION_MAIN, null), 0)
        val seen = HashSet<String>()
        val elements = ArrayList<AppListItem>()
        for (resInfo in packages) {
            if (resInfo.activityInfo.packageName == "hu.bugadani.notifilter") {
                continue
            }

            val appInfo = packageManager.getApplicationInfo(resInfo.activityInfo.packageName, 0)
            val appName = appInfo.loadLabel(packageManager).toString()

            if (seen.add(appName)) {
                elements.add(
                    AppListItem(
                        appName,
                        appInfo.packageName,
                        appInfo.loadIcon(packageManager)
                    )
                )
            }
        }

        elements.sortBy { it.appName }

        appsLoadingView.visibility = View.GONE
        (appListView.adapter as AppListItemAdapter).submitList(elements)
    }

    override fun onPause() {
        Log.d(TAG, "Saving preferences")
        val preferences = getPreferences()
        with(preferences.edit()) {
            putStringSet("filter", enabledFilters)
            apply()
        }

        val pingServiceIntent = Intent(this, NotificationListener::class.java)
        this.startService(pingServiceIntent)

        super.onPause()
    }
}
