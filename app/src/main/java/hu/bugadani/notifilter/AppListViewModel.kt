package hu.bugadani.notifilter

import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppListViewModel(packageManager: PackageManager) : ViewModel() {

    val appListItems: LiveData<ArrayList<AppListItem>> = liveData {
        val elements = ArrayList<AppListItem>()
        withContext(Dispatchers.IO) {
            val packages = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN, null), 0)
            val seen = HashSet<String>()
            for (resInfo in packages) {
                if (resInfo.activityInfo.packageName == "hu.bugadani.notifilter") {
                    continue
                }

                val appInfo = packageManager.getApplicationInfo(resInfo.activityInfo.packageName, 0)
                val appName = appInfo.loadLabel(packageManager).toString()

                if (seen.add(appName)) {
                    val icon = appInfo.loadIcon(packageManager)

                    elements.add(
                        AppListItem(
                            appName,
                            appInfo.packageName,
                            icon
                        )
                    )
                }
            }
            elements.sortBy { it.appName }
        }
        emit(elements)
    }
}