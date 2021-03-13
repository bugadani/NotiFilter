package hu.bugadani.notifilter.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_record")
    fun listAll(): List<NotificationRecord>

    @Query("SELECT DISTINCT package_id FROM notification_record")
    fun listApps(): List<RecordedApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(record: NotificationRecord)

    @Query("DELETE FROM notification_record WHERE created_at < DATETIME('NOW', '-1 days')")
    fun gc()
}
