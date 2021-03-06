package hu.bugadani.notifilter

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
    private val enabledFilters =  HashMap<String, FilterOption>()
    private lateinit var viewModel: AppListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        viewModel = AppListViewModel(packageManager)

        appListView = findViewById<RecyclerView>(R.id.appList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = AppListItemAdapter(enabledFilters)
            setHasFixedSize(true)

            val itemTouchHelper = ItemTouchHelper(AppListSwipeController())
            itemTouchHelper.attachToRecyclerView(this)
        }
        appsLoadingView = findViewById(R.id.appsLoading)
    }

    override fun onResume() {
        Log.d(NotificationListener.TAG, "onResume")

        super.onResume()

        SettingsHelper.load(this, enabledFilters)

        viewModel.appListItems.observe(this, Observer { items ->
            (appListView.adapter as AppListItemAdapter).submitList(items)
            appsLoadingView.visibility = View.GONE
        })
    }

    override fun onPause() {
        Log.d(TAG, "Saving preferences")
        SettingsHelper.save(this, enabledFilters)

        super.onPause()
    }
}
