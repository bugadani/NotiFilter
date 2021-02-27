package hu.bugadani.notifilter

import android.app.Service
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
import androidx.appcompat.app.AppCompatActivity


class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(appContext: Context?, intent: Intent) {
        val serviceIntent = Intent(appContext, NotificationListener::class.java)
        when(intent.action) {
            ACTION_SCREEN_ON -> appContext?.stopService(serviceIntent)
            ACTION_SCREEN_OFF -> appContext?.startService(serviceIntent)
        }
    }
}

class NotificationListener : NotificationListenerService() {
    private var proxied: HashSet<NotificationGroup> = HashSet()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!shouldProxyForApp(sbn)) {
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
            // new notification group
            TODO("Implement proxying notification")
        }
    }

    private fun notificationGroup(sbn: StatusBarNotification): NotificationGroup {
        return NotificationGroup(sbn.packageName, sbn.groupKey)
    }

    data class NotificationGroup(val pkg: String, val groupKey: String)
}

class StartupService : Service() {
    private val receiver = UnlockReceiver()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

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