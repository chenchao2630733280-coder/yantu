package com.example.shilv.ui.trip

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shilv.data.MemoryEvent
import com.example.shilv.data.TripDates
import com.example.shilv.data.TravelDay
import com.example.shilv.ui.AppModel
import com.example.shilv.ui.Routes
import com.example.shilv.ui.shared.PhotoThumbnail
import com.example.shilv.ui.shared.durationShortText
import com.example.shilv.ui.shared.formatDayLabel
import com.example.shilv.ui.shared.formatTime
import com.example.shilv.ui.theme.Ink
import com.example.shilv.ui.theme.Muted
import com.example.shilv.ui.theme.Orange
import com.example.shilv.ui.theme.Paper

private enum class DayPeriod(val label: String) {
    All("全部"), Morning("上午"), Afternoon("下午"), Evening("晚上");

    fun contains(hour: Int): Boolean = when (this) {
        All -> true
        Morning -> hour < 12
        Afternoon -> hour in 12 until 18
        Evening -> hour >= 18
    }
}

@Composable
fun DayTimelineScreen(model: AppModel, navController: NavController, tripId: String, dayId: String) {
    val day = remember(model.dataRevision.value) {
        model.snapshot?.trips?.firstOrNull { it.id == tripId }?.days?.firstOrNull { it.id == dayId }
    }
    var period by remember { mutableStateOf(DayPeriod.All) }
    var showEditor by remember { mutableStateOf(false) }
    val loader: suspend (String, Int) -> Bitmap? = { id, size -> model.loadImage(id, size) }

    if (day == null) {
        Box(Modifier.fillMaxSize().background(Paper))
        return
    }
    val events = day.events.filter { !it.isHidden && period.contains(TripDates.hourOf(it.startDate)) }

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Ink) }
            Text(formatDayLabel(day.date), modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DayPeriod.entries.forEach { p ->
                val selected = period == p
                Button(
                    onClick = { period = p },
                    colors = ButtonDefaults.buttonColors(containerColor = if (selected) Ink else Muted.copy(alpha = 0.6f)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(p.label, fontSize = 13.sp) }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text(day.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${formatDayLabel(day.date)} · ${day.photoCount} 张照片", color = Muted, fontSize = 12.sp)
                }
            }
            events.forEachIndexed { index, event ->
                item {
                    EventRow(event, loader) {
                        navController.navigate(Routes.eventDetail(tripId, dayId, event.id))
                    }
                }
                if (index < events.size - 1 && shouldShowTransition(event, events[index + 1])) {
                    item { RouteTransitionRow(event, events[index + 1]) }
                }
            }
        }
        Button(
            onClick = { showEditor = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
        ) { Text("＋ 记下一句话") }
    }

    if (showEditor && events.isNotEmpty()) {
        QuickMemoryEditor(model, tripId, dayId, events.first()) { showEditor = false }
    }
}

private fun shouldShowTransition(from: MemoryEvent, to: MemoryEvent): Boolean {
    val origin = from.location ?: return false
    val destination = to.location ?: return false
    return origin.distance(destination) >= 20_000
}

@Composable
private fun EventRow(event: MemoryEvent, loader: suspend (String, Int) -> Bitmap?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatTime(event.startDate), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(Orange))
        }
        Spacer(Modifier.width(12.dp))
        PhotoThumbnail(id = event.coverPhotoID, height = 118, cornerRadius = 14, modifier = Modifier.width(118.dp), loader = loader)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.placeName ?: event.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("停留约 ${durationShortText(event.duration)} · ${event.photoCount} 张照片", color = Muted, fontSize = 12.sp)
            event.summary?.let { Text(it, color = Muted, fontSize = 12.sp, maxLines = 2) }
            if (event.note.isNotEmpty()) Text("“${event.note}”", color = Muted, fontSize = 12.sp, maxLines = 2)
        }
    }
}

@Composable
private fun RouteTransitionRow(from: MemoryEvent, to: MemoryEvent) {
    val period = when (TripDates.hourOf(to.startDate)) {
        in 0 until 12 -> "上午"
        in 12 until 18 -> "下午"
        else -> "傍晚"
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Orange, modifier = Modifier.width(46.dp).size(16.dp))
        Column {
            Text("$period 从${from.cityName ?: from.placeName ?: from.title}前往${to.cityName ?: to.placeName ?: to.title}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            val origin = from.location
            val destination = to.location
            if (origin != null && destination != null) {
                Text("移动约 ${(origin.distance(destination) / 1000).toInt()} 公里", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun QuickMemoryEditor(model: AppModel, tripId: String, dayId: String, event: MemoryEvent, onClose: () -> Unit) {
    var note by remember { mutableStateOf(event.note) }
    AlertDialog(
        onDismissRequest = { onClose() },
        title = { Text("记下一句话") },
        text = {
            Column {
                Text("把照片不知道的部分留给以后。", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                TextField(value = note, onValueChange = { note = it.take(500) }, modifier = Modifier.fillMaxWidth().height(120.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = note.trim()
                if (trimmed.isNotEmpty() && model.snapshot != null) {
                    val trip = model.snapshot!!.trips.first { it.id == tripId }
                    val dayIndex = trip.days.indexOfFirst { it.id == dayId }
                    val eventIndex = trip.days[dayIndex].events.indexOfFirst { it.id == event.id }
                    val updatedDays = trip.days.toMutableList()
                    val updatedEvents = updatedDays[dayIndex].events.toMutableList()
                    updatedEvents[eventIndex] = updatedEvents[eventIndex].copy(note = trimmed)
                    updatedDays[dayIndex] = updatedDays[dayIndex].copy(events = updatedEvents)
                    model.update(trip.copy(days = updatedDays))
                }
                onClose()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = { onClose() }) { Text("取消") } },
    )
}