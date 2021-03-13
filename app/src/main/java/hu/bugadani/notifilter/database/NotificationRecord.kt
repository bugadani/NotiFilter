package hu.bugadani.notifilter.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*


@Entity(tableName = "notification_record")
data class NotificationRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Date = Date(System.currentTimeMillis()),
    @ColumnInfo(name = "package_id") val packageId: String?,
    @ColumnInfo(name = "group_key") val groupKey: String?,
    @ColumnInfo(name = "details") val details: String?,
)

data class RecordedApp(
    @ColumnInfo(name = "package_id") val packageId: String?,
)