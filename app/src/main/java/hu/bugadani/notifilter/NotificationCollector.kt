package hu.bugadani.notifilter

import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.room.Room
import com.google.gson.Gson
import hu.bugadani.notifilter.database.NotificationDatabase
import hu.bugadani.notifilter.database.NotificationRecord

class NotificationCollector(context: Context) {
    private val list = ArrayList<StatusBarNotification>()
    private val db: NotificationDatabase = NotificationDatabase.getDataBase(context)

    init {
        // Remove old notifications
        db.notificationDao().gc()
    }

    fun record(statusBarNotification: StatusBarNotification) {
        list.add(statusBarNotification)
    }

    fun store() {
        if (list.isNotEmpty()) {
            val gson = Gson()
            val dao = db.notificationDao()
            Log.d("NotificationCollector", "Storing notifications")

            for (sbn in list) {
                val record = NotificationRecord(
                    packageId = sbn.packageName,
                    groupKey = sbn.groupKey,
                    details = gson.toJson(sbn.notification),
                )

                val id = dao.insert(record)
                Log.d("NotificationCollector", "Saved notification with id $id")
            }

            list.clear()
        } else {
            Log.d("NotificationCollector", "No notifications recorded")
        }
    }
}