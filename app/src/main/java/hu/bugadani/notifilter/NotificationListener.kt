package hu.bugadani.notifilter

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    class UnlockReceiver(private val context: NotificationListener) : BroadcastReceiver() {
        override fun onReceive(appContext: Context?, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> context.onScreenOn()
                Intent.ACTION_SCREEN_OFF -> context.onScreenOff()
            }
        }
    }

    companion object {
        const val TAG = "NotificationListener"
        const val RELAX_TIME_MS = 1000 * 60
        const val ONGOING_NOTIFICATION_ID = 1
    }

    private val receiver = UnlockReceiver(this)
    private var proxied: HashMap<NotificationGroup, Long> = HashMap()
    private val channel = NotificationChannel(
        "P",
        "Proxied Notifications",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        this.description = "The proxied notifications"
    }
    private var enabled = false
    private var connected = false
    private var hasFilters = false
    private var id = 0
    var enabledFilters = HashSet<String>()
    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()

        val preferences = getSharedPreferences(
            "appSettings",
            Context.MODE_PRIVATE
        )
        val set = preferences.getStringSet("filter", HashSet())
        if (set != null) {
            enabledFilters.addAll(set)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_ON)
        )
        registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF)
        )
    }

    private fun onScreenOn() {
        Log.d(TAG, "Screen on")
        enabled = false
        clearAllNotifications()
    }

    private fun onScreenOff() {
        enabled = true
        Log.d(TAG, "Screen off")
    }

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
        Log.d(TAG, "onStartCommand")

        val pendingIntent: PendingIntent =
            Intent(
                this,
                MainActivity::class.java
            ).let { notificationIntent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    notificationIntent,
                    0
                )
            }

        val notification = Notification.Builder(this, channel.id)
            .setContentTitle(getText(R.string.notification_title))
            .setContentText(getText(R.string.notification_message))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setTicker(getText(R.string.ticker_text))
            .build()

        startForeground(ONGOING_NOTIFICATION_ID, notification)

        proxied.clear()

        // In case we get killed and restarted, intent may be null. Try to read filter from preferences
        if (!hasFilters) {
            val preferences = getSharedPreferences(
                "appSettings",
                Context.MODE_PRIVATE
            )
            val set = preferences.getStringSet("filter", HashSet())
            if (set != null) {
                enabledFilters.addAll(set)
            }
            hasFilters = true
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        cancelAllNotifications()

        Log.d(TAG, "NotificationListener destroyed")

        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(
            TAG,
            "Notification posted: " + sbn.notification.tickerText
        )
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

        Log.d(
            TAG,
            "Proxying notification: " + sbn.notification.tickerText
        )
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

            val notificationManager =
                getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(id, notification)
            id += 1
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Exception: $e")
        }
    }

    private fun notificationGroup(sbn: StatusBarNotification): NotificationGroup {
        return NotificationGroup(sbn.packageName, sbn.groupKey)
    }

    private fun clearAllNotifications() {
        val notificationManager =
            getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun persistPreferences() {
        Log.d(TAG, "Saving preferences")
        val preferences = getSharedPreferences(
            "appSettings",
            Context.MODE_PRIVATE
        )
        with(preferences.edit()) {
            putStringSet("filter", enabledFilters)
            apply()
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): NotificationListener {
            return this@NotificationListener
        }
    }

    data class NotificationGroup(val pkg: String, val groupKey: String)
}