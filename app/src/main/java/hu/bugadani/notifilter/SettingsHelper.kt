package hu.bugadani.notifilter

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.FileNotFoundException

class SettingsHelper {
    companion object {
        fun save(context: Context, settings: HashMap<String, FilterOption>) {
            val gson = Gson()
            val path = context.getFileStreamPath("settings.json")
            val settingsJson = gson.toJson(settings)

            path.writeText(settingsJson)
        }

        fun load(context: Context, settings: HashMap<String, FilterOption>) {
            val gson = Gson()
            val path = context.getFileStreamPath("settings.json")

            try {
                val token = object : TypeToken<HashMap<String, FilterOption>>() {}.type
                val map: HashMap<String, FilterOption> = gson.fromJson(path.reader(), token)
                settings.clear()
                settings.putAll(map)
            } catch (e: FileNotFoundException) {
                Log.w("SettingsHelper", "File not found: $e")
            }
        }
    }
}
