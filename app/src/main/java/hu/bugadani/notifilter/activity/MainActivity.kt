package hu.bugadani.notifilter.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import hu.bugadani.notifilter.*


class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var appListView: RecyclerView
    private lateinit var appsLoadingView: ProgressBar
    private val perAppOptions = HashMap<String, AppOptions>()
    private val viewModel: AppListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        appListView = findViewById<RecyclerView>(R.id.appList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = AppListItemAdapter(perAppOptions)
            setHasFixedSize(true)

            val itemTouchHelper = ItemTouchHelper(AppListSwipeController())
            itemTouchHelper.attachToRecyclerView(this)
        }
        appsLoadingView = findViewById(R.id.appsLoading)

        val enabledApps = NotificationManagerCompat.getEnabledListenerPackages(this)
        if (!enabledApps.contains(BuildConfig.APPLICATION_ID)) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    override fun onResume() {
        Log.d(NotificationListener.TAG, "onResume")

        super.onResume()

        SettingsHelper.load(this, perAppOptions)

        viewModel.appListItems.observe(this, Observer { items ->
            (appListView.adapter as AppListItemAdapter).submitList(items)
            appsLoadingView.visibility = View.GONE
        })
    }

    override fun onPause() {
        Log.d(TAG, "Saving preferences")
        SettingsHelper.save(this, perAppOptions)

        super.onPause()
    }
}
