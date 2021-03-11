package hu.bugadani.notifilter.database

import android.content.Context
import androidx.room.*
import java.util.*


class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

@Database(entities = arrayOf(NotificationRecord::class), version = 1)
@TypeConverters(Converters::class)
abstract class NotificationDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao

    companion object {
        var INSTANCE: NotificationDatabase? = null

        fun getDataBase(context: Context): NotificationDatabase {
            if (INSTANCE == null) {
                synchronized(NotificationDatabase::class) {
                    INSTANCE = Room.databaseBuilder(
                        context.applicationContext,
                        NotificationDatabase::class.java,
                        "notification-db"
                    )
                    .allowMainThreadQueries() // FIXME
                    .build()
                }
            }
            return INSTANCE!!
        }

        fun destroyDataBase() {
            INSTANCE = null
        }
    }
}
