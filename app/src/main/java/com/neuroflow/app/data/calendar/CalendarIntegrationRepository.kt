package com.neuroflow.app.data.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.neuroflow.app.data.local.entity.TaskEntity
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarBusyBlock(
    val startMillis: Long,
    val endMillis: Long,
    val title: String
)

@Singleton
class CalendarIntegrationRepository @Inject constructor(
    private val context: Context
) {
    fun hasReadPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    fun readBusyBlocks(startMillis: Long, endMillis: Long): List<CalendarBusyBlock> {
        if (!hasReadPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY
        )
        val selection = "${CalendarContract.Instances.BEGIN} < ? AND ${CalendarContract.Instances.END} > ?"
        val args = arrayOf(endMillis.toString(), startMillis.toString())
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Instances.CONTENT_URI.buildUpon()
                    .appendPath(startMillis.toString())
                    .appendPath(endMillis.toString())
                    .build(),
                projection,
                selection,
                args,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                buildList {
                    while (cursor.moveToNext()) {
                        val begin = cursor.getLong(beginIndex)
                        val end = cursor.getLong(endIndex)
                        if (cursor.getInt(allDayIndex) == 0 && end > begin) {
                            add(CalendarBusyBlock(begin, end, cursor.getString(titleIndex).orEmpty()))
                        }
                    }
                }
            }.orEmpty()
        }.getOrElse { emptyList() }
    }

    fun createTaskEvent(task: TaskEntity, startMillis: Long): Long? {
        if (!hasWritePermission()) return null
        val durationMinutes = task.estimatedDurationMinutes.coerceAtLeast(30)
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, task.title)
            put(CalendarContract.Events.DESCRIPTION, "Created by NeuroFlow autoschedule")
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, startMillis + durationMinutes * 60_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, Calendar.getInstance().timeZone.id)
            put(CalendarContract.Events.CALENDAR_ID, writableCalendarId(context.contentResolver) ?: return null)
        }
        return runCatching {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
        }.getOrNull()
    }

    private fun writableCalendarId(resolver: ContentResolver): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        return resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1",
            null,
            "${CalendarContract.Calendars._ID} ASC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }
}
