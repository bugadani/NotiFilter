package hu.bugadani.notifilter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class UnlockReceiver : BroadcastReceiver() {
    override fun onReceive(appContext: Context?, intent: Intent) {
        if (intent.action.equals(Intent.ACTION_SCREEN_OFF, ignoreCase = true)) {
            // SCREEN_OFF: enable proxying notification
        } else if (intent.action.equals(Intent.ACTION_SCREEN_ON, ignoreCase = true)) {
            // SCREEN_ON: clear all notifications created by the app
        }
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}