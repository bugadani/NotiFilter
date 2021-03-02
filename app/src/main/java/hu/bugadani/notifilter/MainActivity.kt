package hu.bugadani.notifilter

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var appListView: RecyclerView
    private lateinit var appsLoadingView: ProgressBar
    private val enabledFilters = HashSet<String>()
    private lateinit var viewModel: AppListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        viewModel = AppListViewModel(packageManager)

        appListView = findViewById<RecyclerView>(R.id.appList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = AppListItemAdapter(enabledFilters)
            setHasFixedSize(true)
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

        viewModel.appListItems.observe(this, Observer { items ->
            (appListView.adapter as AppListItemAdapter).submitList(items)
            appsLoadingView.visibility = View.GONE
        })
    }

    override fun onPause() {
        Log.d(TAG, "Saving preferences")
        val preferences = getPreferences()
        with(preferences.edit()) {
            putStringSet("filter", enabledFilters)
            apply()
        }

        super.onPause()
    }
}
