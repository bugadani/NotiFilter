package hu.bugadani.notifilter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.appcompat.app.AppCompatActivity


class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(appContext: Context?, intent: Intent) {
         if (intent.action.equals(Intent.ACTION_SCREEN_ON, ignoreCase = true)) {
            // SCREEN_ON: clear all notifications created by the app
        }
    }
}

class NotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (deviceInUse()) {
            return
        }
        if (!shouldProxyForApp(sbn)) {
            return
        }
        if (notificationAlreadyProxied(sbn)) {
            return
        }
        proxyNotification(sbn)
    }

    private fun deviceInUse(): Boolean {
        // needs some heuristic to see if device is in use
        return true
    }

    private fun shouldProxyForApp(sbn: StatusBarNotification): Boolean {
        // needs a list of apps
        return true
    }

    private fun notificationAlreadyProxied(sbn: StatusBarNotification): Boolean {
        // needs to maintain a list of posted notifications
        return false
    }

    private fun proxyNotification(sbn: StatusBarNotification) {
        TODO("Not yet implemented")
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}