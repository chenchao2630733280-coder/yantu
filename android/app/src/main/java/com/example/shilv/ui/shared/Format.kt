package com.example.shilv.ui.shared

import com.example.shilv.data.TripDates
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val tripRangeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA)
private val dayLabelFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
private val shortDateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)

fun formatTripRange(start: Long, end: Long, zone: ZoneId = TripDates.zone): String {
    val s = Instant.ofEpochMilli(start).atZone(zone).toLocalDateTime().format(tripRangeFormatter)
    val e = Instant.ofEpochMilli(end).atZone(zone).toLocalDateTime().format(tripRangeFormatter)
    return "$s – $e"
}

fun formatDayLabel(millis: Long, zone: ZoneId = TripDates.zone): String =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().format(dayLabelFormatter)

fun formatTime(millis: Long, zone: ZoneId = TripDates.zone): String =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().format(timeFormatter)

fun formatShortDateTime(millis: Long, zone: ZoneId = TripDates.zone): String =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().format(shortDateTimeFormatter)

fun durationShortText(millis: Long): String {
    val minutes = maxOf(1, (millis / 60_000).toInt())
    return if (minutes >= 60) "${minutes / 60} 小时 ${minutes % 60} 分" else "$minutes 分钟"
}

fun durationMapText(millis: Long): String {
    val minutes = maxOf(1, (millis / 60_000).toInt())
    return if (minutes >= 60) "${minutes / 60}.${(minutes % 60) / 6} 小时" else "$minutes 分钟"
}

fun byteCountText(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.CHINA, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024 * 1024 -> String.format(Locale.CHINA, "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format(Locale.CHINA, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}