package hu.bugadani.notifilter

import android.app.*
import android.content.*
import android.content.Intent.*
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Binder
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class UnlockReceiver(private val context: StartupService) : BroadcastReceiver() {
    companion object {
        const val TAG = "UnlockReceiver"
    }

    override fun onReceive(appContext: Context?, intent: Intent) {
        val serviceIntent = Intent(context, NotificationListener::class.java).apply {
            putExtra("action", intent.action)
            putExtra("enabled", context.enabledFilters)
        }
        Log.d(TAG, "onReceive: " + intent.action)
        context.startService(serviceIntent)
    }
}

class NotificationListener : NotificationListenerService() {
    companion object {
        const val TAG = "NotificationListener"
        const val RELAX_TIME_MS = 1000 * 60
    }

    private var proxied: HashMap<NotificationGroup, Long> = HashMap()
    private val channel = NotificationChannel("P", "Proxied Notifications", NotificationManager.IMPORTANCE_LOW).apply {
        this.description = "The proxied notifications"
    }
    private var enabled = false
    private var connected = false
    private var hasFilters = false
    private var id = 0
    private var enabledFilters = HashSet<String>()

    override fun onListenerConnected() {
        Log.d(TAG, "NotificationListener: connected")
        connected = true
        super.onListenerConnected()
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "NotificationListener: disconnected")
        connected = false
        super.onListenerDisconnected()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationManager = getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        proxied.clear()

        when (intent?.extras?.get("action")) {
            ACTION_SCREEN_ON -> {
                Log.d(TAG, "NotificationListener stopped")
                notificationManager.cancelAll()
                enabled = false
            }
            ACTION_SCREEN_OFF -> {
                Log.d(TAG, "NotificationListener started")
                val extras = intent.extras?.get("enabled")
                if (extras != null) {
                    enabledFilters = extras as HashSet<String>
                    hasFilters = true
                }
                enabled = true
            }
        }

        // In case we get killed and restarted, intent may be null. Try to read filter from preferences
        if (!hasFilters) {
            val preferences = getSharedPreferences("appSettings", Context.MODE_PRIVATE)
            val set = preferences.getStringSet("filter", HashSet())
            if (set != null) {
                enabledFilters.addAll(set)
            }
            hasFilters = true
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        val notificationManager = getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        Log.d(TAG, "NotificationListener destroyed")

        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification posted: " + sbn.notification.tickerText)
        if (!connected || !enabled) {
            Log.d(TAG, "Notification ignored: disabled")
            return
        }
        if (sbn.notification.tickerText == null) {
            Log.d(TAG, "Notification ignored: no ticker text")
            return
        }
        if (!shouldProxyForApp(sbn)) {
            Log.d(TAG, "Notification ignored: app ignored")
            return
        }
        if (isRepost(sbn)) {
            Log.d(TAG, "Notification ignored: already notified")
            return
        }
        proxyNotification(sbn)
    }

    private fun isRepost(sbn: StatusBarNotification): Boolean {
        val group = notificationGroup(sbn)
        val old = proxied[group]

        return old != null && old > System.currentTimeMillis() + RELAX_TIME_MS
    }

    private fun shouldProxyForApp(sbn: StatusBarNotification): Boolean {
        return enabledFilters.contains(sbn.packageName)
    }

    private fun proxyNotification(sbn: StatusBarNotification) {
        val group = notificationGroup(sbn)

        proxied[group] = System.currentTimeMillis()

        Log.d(TAG, "Proxying notification: " + sbn.notification.tickerText)
        Log.d(TAG, "Group: $group")
        Log.d(TAG, "Category: ${sbn.notification.category}")

        // new notification group
        try {
            val notification = Notification.Builder(this, channel.id)
                    .setExtras(sbn.notification.extras)
                    .setSmallIcon(sbn.notification.smallIcon)
                    .setContentIntent(sbn.notification.contentIntent)
                    .setCustomContentView(sbn.notification.contentView)
                    .setCustomBigContentView(sbn.notification.bigContentView)
                    .setCategory(sbn.notification.category)
                    .setTicker(sbn.notification.tickerText)
                    .setAutoCancel(sbn.notification.flags.and(Notification.FLAG_AUTO_CANCEL) != 0)
                    .build()

            val notificationManager = getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(id, notification)
            id += 1
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Exception: $e")
        }
    }

    private fun notificationGroup(sbn: StatusBarNotification): NotificationGroup {
        return NotificationGroup(sbn.packageName, sbn.groupKey)
    }

    data class NotificationGroup(val pkg: String, val groupKey: String)
}

class StartupService : Service() {
    companion object {
        const val TAG = "Startup service"
        const val ONGOING_NOTIFICATION_ID = 1
    }

    private val receiver = UnlockReceiver(this)
    private val channel = NotificationChannel("N", "Foreground Service Notification", NotificationManager.IMPORTANCE_LOW).apply {
        this.description = "Sorry"
    }
    private val binder = LocalBinder()

    val enabledFilters = HashSet<String>()

    override fun onCreate() {
        super.onCreate()

        val preferences = getSharedPreferences("appSettings", Context.MODE_PRIVATE)
        val set = preferences.getStringSet("filter", HashSet())
        if (set != null) {
            enabledFilters.addAll(set)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        val pendingIntent: PendingIntent =
                Intent(this, MainActivity::class.java).let { notificationIntent ->
                    PendingIntent.getActivity(this, 0, notificationIntent, 0)
                }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channel.id)
                .setContentTitle(getText(R.string.notification_title))
                .setContentText(getText(R.string.notification_message))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setTicker(getText(R.string.ticker_text))
                .build()

        startForeground(ONGOING_NOTIFICATION_ID, notification)

        registerReceiver(receiver, IntentFilter(ACTION_SCREEN_ON))
        registerReceiver(receiver, IntentFilter(ACTION_SCREEN_OFF))

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun persistPreferences() {
        Log.d(TAG, "Saving preferences")
        val preferences = getSharedPreferences("appSettings", Context.MODE_PRIVATE)
        with(preferences.edit()) {
            putStringSet("filter", enabledFilters)
            apply()
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): StartupService {
            return this@StartupService
        }
    }
}

class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var linearLayoutManager: LinearLayoutManager
    private var service: StartupService? = null

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            val s = (iBinder as StartupService.LocalBinder).getService()

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

        startForegroundService(Intent(this, StartupService::class.java))
        bindService(Intent(this, StartupService::class.java), connection, Context.BIND_AUTO_CREATE)
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

class AppInfoElement(appInfo: ApplicationInfo, context: Context) {
    val appName: String = appInfo.loadLabel(context.packageManager).toString()
    val packageName: String = appInfo.packageName
    val appIcon: Drawable = appInfo.loadIcon(context.packageManager)
}

class AppListItemAdapter(private val dataSet: ArrayList<AppInfoElement>, private val enabledFilters: HashSet<String>) :
        RecyclerView.Adapter<AppListItemAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appNameView: TextView = view.findViewById(R.id.appName)
        val appIconView: ImageView = view.findViewById(R.id.appIcon)
        val appEnabledView: Switch = view.findViewById(R.id.appEnabled)
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
                Log.d("MainActivity", "App filter change: " + view.tag + " -> " + isChecked)
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
