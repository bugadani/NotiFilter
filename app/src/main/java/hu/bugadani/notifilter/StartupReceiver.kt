package hu.bugadani.notifilter

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StartupReceiver : BroadcastReceiver() {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        val startServiceIntent = Intent(context, NotificationListener::class.java)
        context.startForegroundService(startServiceIntent)
    }
}