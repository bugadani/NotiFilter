package hu.bugadani.notifilter

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable

class AppInfoElement(appInfo: ApplicationInfo, context: Context) {
    val appName: String = appInfo.loadLabel(context.packageManager).toString()
    val packageName: String = appInfo.packageName
    val appIcon: Drawable = appInfo.loadIcon(context.packageManager)
}