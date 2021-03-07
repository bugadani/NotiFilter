package hu.bugadani.notifilter

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    data class ProxiedNotificationData(
        val group: NotificationGroup,
        val key: String,
        val flags: Int
    )

    companion object {
        const val TAG = "NotificationListener"
        const val RELAX_ONE_MINUTE = 1000 * 60
        const val RELAX_FIVE_MINUTES = 1000 * 60 * 5
    }

    private val receiver = UnlockReceiver(this)
    private var proxied: HashMap<NotificationGroup, Long> = HashMap()
    private var proxiedNotifications: HashMap<Int, ProxiedNotificationData> = HashMap()
    private var perAppOptions = HashMap<String, AppOptions>()

    private val channel = NotificationChannel(
        "P",
        "Proxied Notifications",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "The proxied notifications"
    }
    private var enabled = false
    private var connected = false

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "onCreate")

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        Log.d(TAG, "onCreate: register screen state receiver")
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private fun onScreenOn() {
        Log.d(TAG, "Screen on")
        enabled = false
        clearAllNotifications()
    }

    private fun onScreenOff() {
        enabled = true
        Log.d(TAG, "Screen off - enabled: $enabled connected: $connected")
        updateOptions()
    }

    private fun updateOptions() {
        SettingsHelper.load(this, perAppOptions)
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

        proxied.clear()
        proxiedNotifications.clear()

        updateOptions()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        clearAllNotifications()
        unregisterReceiver(receiver)
        Log.d(TAG, "NotificationListener destroyed")

        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification posted: $sbn")
        Log.d(TAG, "Group: ${sbn.groupKey}")
        Log.d(TAG, "Category: ${sbn.notification.category}")
        Log.d(TAG, "Ticker: ${sbn.notification.tickerText}")
        Log.d(TAG, "Content text: ${sbn.notification.extras[Notification.EXTRA_TEXT]}")

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

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        Log.d(TAG, "Notification removed: ${sbn.id} - ${sbn.notification}")

        if (!connected || !enabled) {
            Log.d(TAG, "Notification removal ignored: service disabled")
            return
        }

        val notificationData = proxiedNotifications.remove(sbn.id)
        if (notificationData == null) {
            Log.d(TAG, "Notification removal ignored: notification not proxied")
            return
        }

        // We only hit this line if our notification is the removed one
        // (i.e. not the source or an untracked one).
        proxied.remove(notificationData.group)

        if (notificationData.flags.and(Notification.FLAG_AUTO_CANCEL) != 0) {
            cancelNotification(notificationData.key)
        }
    }

    private fun isRepost(sbn: StatusBarNotification): Boolean {
        val group = notificationGroup(sbn)

        // If the group is not stored (first in group or manually reset), we allow the notification
        val posted = proxied[group] ?: return false

        val age = System.currentTimeMillis() - posted
        return when (perAppOptions[sbn.packageName]?.filterOption) {
            // We may allow notification to go through after a certain time
            FilterOption.AutoReset1Minute -> age < RELAX_ONE_MINUTE
            FilterOption.AutoReset5Minutes -> age < RELAX_FIVE_MINUTES

            // Anything else gets blocked
            else -> true
        }
    }

    private fun shouldProxyForApp(sbn: StatusBarNotification): Boolean {
        val appOptions = perAppOptions[sbn.packageName] ?: return false

        return appOptions.filterOption != FilterOption.Ignore
    }

    private fun isGroupOverrideActive(packageName: String): Boolean {
        return perAppOptions[packageName]?.overrideGroups ?: return false
    }

    private fun proxyNotification(sbn: StatusBarNotification) {
        val group = notificationGroup(sbn)

        proxied[group] = System.currentTimeMillis()

        Log.d(TAG, "Proxying notification")

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

            // We can't use a list because notifications may be removed in an arbitrary order
            val id = proxiedNotifications.size
            notificationManager.notify(id, notification)

            proxiedNotifications[id] = ProxiedNotificationData(
                group, sbn.key, sbn.notification.flags
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Exception: $e")
        }
    }

    private fun notificationGroup(sbn: StatusBarNotification): NotificationGroup {
        val group = if (isGroupOverrideActive(sbn.packageName)) {
            "appGroup"
        } else {
            sbn.groupKey
        }

        return NotificationGroup(sbn.packageName, group)
    }

    private fun clearAllNotifications() {
        if (connected) {
            val notificationManager =
                getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager

            proxied.clear()
            proxiedNotifications.clear()

            // This should only clear our notifications
            notificationManager.cancelAll()
        }
    }

    data class NotificationGroup(val pkg: String, val groupKey: String)
}