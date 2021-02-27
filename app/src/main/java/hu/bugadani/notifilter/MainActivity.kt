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


class UnlockReceiver : BroadcastReceiver() {
    private val TAG = "UnlockReceiver"

    override fun onReceive(appContext: Context?, intent: Intent) {
        val serviceIntent = Intent(appContext, NotificationListener::class.java)
        Log.d(TAG, "onReceive")
        when(intent.action) {
            ACTION_SCREEN_ON -> appContext?.stopService(serviceIntent)
            ACTION_SCREEN_OFF -> appContext?.startService(serviceIntent)
        }
    }
}

class NotificationListener : NotificationListenerService() {
    private var proxied: HashSet<NotificationGroup> = HashSet()
    private val TAG = "NotificationListener"
    private val channel = NotificationChannel("P", "Proxied Notifications", NotificationManager.IMPORTANCE_LOW).apply {
        this.description = "The proxied notifications"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationManager = getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification posted")
        if (!shouldProxyForApp(sbn)) {
            Log.d(TAG, "Notification ignored: app ignored")
            return
        }
        proxyNotification(sbn)
    }

    private fun shouldProxyForApp(sbn: StatusBarNotification): Boolean {
        // needs a list of apps
        return true
    }

    private fun proxyNotification(sbn: StatusBarNotification) {
        val group = notificationGroup(sbn)

        if (proxied.add(group)) {
            Log.d(TAG, "Proxying notification")
            // new notification group
            TODO("Implement proxying notification")
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
    private val receiver = UnlockReceiver()
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

        val notification: Notification = Notification.Builder(this, channel.id)
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
        startService(Intent(this, StartupService::class.java))
        setContentView(R.layout.activity_main)
    }
}