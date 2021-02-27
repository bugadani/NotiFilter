package hu.bugadani.notifilter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Display
import androidx.appcompat.app.AppCompatActivity


class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(appContext: Context?, intent: Intent) {
         if (intent.action.equals(Intent.ACTION_SCREEN_ON, ignoreCase = true)) {
            // SCREEN_ON: clear all notifications created by the app
        }
    }
}

class NotificationListener : NotificationListenerService() {
    private var proxied: HashSet<NotificationGroup> = HashSet()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (deviceInUse()) {
            return
        }
        if (!shouldProxyForApp(sbn)) {
            return
        }
        proxyNotification(sbn)
    }

    private fun deviceInUse(): Boolean {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        for (display in displayManager.displays) {
            if (display.state != Display.STATE_OFF) {
                return true
            }
        }
        return false
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

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}