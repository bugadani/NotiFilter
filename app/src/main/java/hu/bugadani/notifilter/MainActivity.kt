package hu.bugadani.notifilter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var linearLayoutManager: LinearLayoutManager
    private var service: NotificationListener? = null

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            val s = (iBinder as NotificationListener.LocalBinder).getService()

            setContentView(R.layout.activity_main)
            linearLayoutManager = LinearLayoutManager(this@MainActivity)

            // List installed apps
            val packages = packageManager.queryIntentActivities(Intent(ACTION_MAIN, null), 0)
            val elements = ArrayList<AppInfoElement>()
            val seen = HashSet<CharSequence>()
            for (resInfo in packages) {
                if (resInfo.activityInfo.packageName == "hu.bugadani.notifilter") {
                    continue
                }
                val appInfo = packageManager.getApplicationInfo(resInfo.activityInfo.packageName, 0)

                if (seen.add(appInfo.loadLabel(packageManager))) {
                    elements.add(AppInfoElement(appInfo, this@MainActivity))
                }
            }

            elements.sortBy { it.appName }

            val appList: RecyclerView = findViewById(R.id.appList)
            appList.layoutManager = linearLayoutManager
            appList.adapter = AppListItemAdapter(elements, s.enabledFilters)

            service = s
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            service = null
        }
    }

    override fun onResume() {
        super.onResume()

        startForegroundService(Intent(this, NotificationListener::class.java))
        bindService(Intent(this, NotificationListener::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onPause() {
        try {
            service?.persistPreferences()
            unbindService(connection)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Exception: $e")
        }
        super.onPause()
    }
}
