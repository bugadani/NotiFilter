package hu.bugadani.notifilter.discard

import android.service.notification.StatusBarNotification

class NotificationDiscarder {
    private val filters = HashMap<String, List<Predicate<StatusBarNotification>>>()

    init {
        filters["com.microsoft.skydrive"] = listOf(
            TickerText(Equals("Preparing..."))
        )
        filters["com.facebook.orca"] = listOf(
            Category(Equals("service"))
        )
    }

    fun discard(sbn: StatusBarNotification): Boolean {
        val appFilters = filters[sbn.packageName] ?: return false

        appFilters.forEach {
            if (it.matches(sbn)) {
                return true
            }
        }

        return false
    }
}