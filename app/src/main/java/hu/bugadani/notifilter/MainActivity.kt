package hu.bugadani.notifilter

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SCREEN_OFF
import android.content.Intent.ACTION_SCREEN_ON
import android.content.IntentFilter
import android.os.Bundle
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class UnlockReceiver(val context: StartupService) : BroadcastReceiver() {
    private val TAG = "UnlockReceiver"

    override fun onReceive(appContext: Context?, intent: Intent) {
        val serviceIntent = Intent(context, NotificationListener::class.java).apply {
            putExtra("action", intent.action)
        }
        Log.d(TAG, "onReceive: " + intent.action)
        context.startService(serviceIntent)
    }
}

class NotificationListener : NotificationListenerService() {
    private var proxied: HashSet<NotificationGroup> = HashSet()
    private val TAG = "NotificationListener"
    private val channel = NotificationChannel("P", "Proxied Notifications", NotificationManager.IMPORTANCE_LOW).apply {
        this.description = "The proxied notifications"
    }
    private var enabled = false
    private var connected = false
    private var id = 0

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
                enabled = true
            }
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
        proxyNotification(sbn)
    }

    private fun shouldProxyForApp(sbn: StatusBarNotification): Boolean {
        // needs a list of apps
        if (sbn.packageName == "hu.bugadani.notifilter") {
            return false
        }
        return true
    }

    private fun proxyNotification(sbn: StatusBarNotification) {
        val group = notificationGroup(sbn)

        if (proxied.add(group)) {
            Log.d(TAG, "Proxying notification: " + sbn.notification.tickerText)
            Log.d(TAG, "Group: $group")

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
                        .build()

                val notificationManager = getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(id, notification)
                id += 1
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Exception: $e")
            }
        } else {
            Log.d(TAG, "Notification ignored: already notified")
        }
    }

    private fun notificationGroup(sbn: StatusBarNotification): NotificationGroup {
        return NotificationGroup(sbn.packageName, sbn.groupKey)
    }

    data class NotificationGroup(val pkg: String, val groupKey: String)
}

class StartupService : Service() {
    private val receiver = UnlockReceiver(this)
    private val TAG = "Startup service"
    private val ONGOING_NOTIFICATION_ID = 1
    private val channel = NotificationChannel("N", "Foreground Service Notification", NotificationManager.IMPORTANCE_LOW).apply {
        this.description = "Sorry"
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

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, StartupService::class.java))
        setContentView(R.layout.activity_main)
    }
}