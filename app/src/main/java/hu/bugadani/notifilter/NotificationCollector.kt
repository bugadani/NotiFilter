package hu.bugadani.notifilter

import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationCollector(val context: Context) {
    private val list = ArrayList<StatusBarNotification>()

    init {
        // TODO Remove old notifications
    }

    fun record(statusBarNotification: StatusBarNotification) {
        list.add(statusBarNotification)
    }

    fun store() {
        if (list.isNotEmpty()) {
            Log.d("NotificationCollector", "Storing notifications")

            // TODO save notifications

            list.clear()
        } else {
            Log.d("NotificationCollector", "No notifications recorded")
        }
    }
}