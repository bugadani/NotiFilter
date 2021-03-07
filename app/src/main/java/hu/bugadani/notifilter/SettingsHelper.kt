package hu.bugadani.notifilter

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.FileNotFoundException

class SettingsHelper {
    companion object {
        fun save(context: Context, settings: HashMap<String, AppOptions>) {
            val gson = Gson()
            val path = context.getFileStreamPath("settings.json")
            val settingsJson = gson.toJson(settings)

            path.writeText(settingsJson)
        }

        fun load(context: Context, settings: HashMap<String, AppOptions>) {
            val gson = Gson()
            val path = context.getFileStreamPath("settings.json")

            val map: Map<String, AppOptions> = try {
                val token = object : TypeToken<HashMap<String, AppOptions>>() {}.type
                gson.fromJson(path.reader(), token)
            } catch (e: JsonSyntaxException) {
                try {
                    // Try to migrate from old format
                    val token = object : TypeToken<HashMap<String, FilterOption>>() {}.type
                    val filterOptionMap: HashMap<String, FilterOption> =
                        gson.fromJson(path.reader(), token)

                    filterOptionMap.mapValues { AppOptions(it.value, false) }
                } catch (e: Exception) {
                    Log.w("SettingsHelper", "Failed to load settings: $e")

                    // If all else fails, fall back to an empty map
                    mapOf()
                }
            } catch (e: FileNotFoundException) {
                Log.w("SettingsHelper", "File not found: $e")
                return
            }

            settings.clear()
            settings.putAll(map)
        }
    }
}
