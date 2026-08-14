package com.example.shilv.ui.event

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shilv.data.DiscoveredTrip
import com.example.shilv.data.MemoryEvent
import com.example.shilv.data.TripDates
import com.example.shilv.data.TravelDay
import com.example.shilv.ui.AppModel
import com.example.shilv.ui.Routes
import com.example.shilv.ui.shared.PhotoThumbnail
import com.example.shilv.ui.shared.durationShortText
import com.example.shilv.ui.shared.formatTime
import com.example.shilv.ui.theme.Ink
import com.example.shilv.ui.theme.Muted
import com.example.shilv.ui.theme.Orange
import com.example.shilv.ui.theme.Paper

@Composable
fun EventDetailScreen(model: AppModel, navController: NavController, tripId: String, dayId: String, eventId: String) {
    val snapshot = remember(model.dataRevision.value) { model.snapshot }
    val trip = snapshot?.trips?.firstOrNull { it.id == tripId }
    val day = trip?.days?.firstOrNull { it.id == dayId }
    val event = day?.events?.firstOrNull { it.id == eventId }

    if (trip == null || day == null || event == null) {
        Box(Modifier.fillMaxSize().background(Paper))
        return
    }

    var showAllPhotos by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draftTitle by remember { mutableStateOf(event.title) }
    var draftNote by remember { mutableStateOf(event.note) }
    var editingFacts by remember { mutableStateOf(false) }
    var draftPlace by remember { mutableStateOf(event.placeName ?: "") }
    var draftStart by remember { mutableStateOf(event.startDate) }
    var draftEnd by remember { mutableStateOf(event.endDate) }
    var showMore by remember { mutableStateOf(false) }
    val loader: suspend (String, Int) -> Bitmap? = { id, size -> model.loadImage(id, size) }

    val ids = if (showAllPhotos) event.photoIDs else event.visiblePhotoIDs

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Ink) }
            Text(event.placeName ?: event.title, modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { PhotoGallery(ids, loader) }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("${formatTime(event.startDate)} – ${formatTime(event.endDate)}", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(event.placeName ?: event.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (event.cityName != null || event.countryName != null) {
                        Text(listOfNotNull(event.cityName, event.countryName).joinToString(" · "), color = Muted, fontSize = 14.sp)
                    }
                    Text("停留约 ${durationShortText(event.duration)} · ${event.photoCount} 张照片", color = Muted, fontSize = 14.sp)
                    event.summary?.let { Text(it, fontSize = 18.sp, fontFamily = FontFamily.Serif, lineHeight = 26.sp, modifier = Modifier.padding(top = 7.dp)) }
                }
            }
            if (!showAllPhotos && event.photoCount > event.visiblePhotoIDs.size) {
                item {
                    Text("查看全部 ${event.photoCount} 张", modifier = Modifier.padding(horizontal = 20.dp).clickable { showAllPhotos = true }, color = Orange, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp).padding(18.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)) {
                    Text("这一段记忆", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (editing) {
                        TextField(value = draftTitle, onValueChange = { draftTitle = it.take(60) }, label = { Text("事件名称") })
                        Spacer(Modifier.height(8.dp))
                        TextField(value = draftNote, onValueChange = { draftNote = it.take(500) }, label = { Text("补充记忆") }, modifier = Modifier.fillMaxWidth().height(120.dp))
                        Spacer(Modifier.height(8.dp))
                        Row {
                            TextButton(onClick = { editing = false }) { Text("取消") }
                            Spacer(Modifier.weight(1f))
                            Button(onClick = {
                                val trimmedTitle = draftTitle.trim()
                                val trimmedNote = draftNote.trim()
                                mutateEvent(model, trip, dayId, eventId) { ev ->
                                    ev.copy(
                                        title = if (trimmedTitle.isEmpty()) ev.title else trimmedTitle,
                                        note = trimmedNote,
                                    )
                                }
                                editing = false
                            }, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("保存记忆") }
                        }
                    } else {
                        Text(
                            if (event.note.isEmpty()) "照片记住了你去过哪里。写下一句话，留下照片不知道的部分。" else "“${event.note}”",
                            fontSize = 20.sp, fontFamily = FontFamily.Serif, lineHeight = 26.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { draftTitle = event.title; draftNote = event.note; editing = true }) {
                            Text(if (event.note.isEmpty()) "＋ 记下一句话" else "修改这段记忆")
                        }
                    }
                }
            }
            item { FactActions(event, day, model, trip, editingFacts, { editingFacts = true }, { showMore = true }, navController) }
        }
    }

    if (editingFacts) {
        FactsEditor(
            draftPlace = draftPlace, draftStart = draftStart, draftEnd = draftEnd,
            onPlaceChange = { draftPlace = it.take(100) },
            onStartChange = { draftStart = it },
            onEndChange = { draftEnd = it },
            onCancel = { editingFacts = false },
            onSave = {
                val trimmed = draftPlace.trim()
                val sourceDayIndex = trip.days.indexOfFirst { it.id == dayId }
                val sourceEventIndex = trip.days[sourceDayIndex].events.indexOfFirst { it.id == eventId }
                if (sourceDayIndex >= 0 && sourceEventIndex >= 0) {
                    val edited = trip.days[sourceDayIndex].events[sourceEventIndex].copy(
                        placeName = if (trimmed.isEmpty()) null else trimmed,
                        startDate = draftStart,
                        endDate = maxOf(draftStart, draftEnd),
                    )
                    val updatedDays = mutableListOf<TravelDay>()
                    updatedDays.addAll(trip.days.map {
                        it.copy(events = it.events.filterNot { e -> e.id == eventId })
                    }.filter { it.events.isNotEmpty() })
                    val targetDate = TripDates.startOfDay(draftStart)
                    val targetIndex = updatedDays.indexOfFirst { TripDates.startOfDay(it.date) == targetDate }
                    if (targetIndex >= 0) {
                        updatedDays[targetIndex] = updatedDays[targetIndex].copy(events = (updatedDays[targetIndex].events + edited).sortedBy { it.startDate })
                    } else {
                        updatedDays.add(TravelDay(id = "day-$targetDate", date = targetDate, title = "补充的一天", events = listOf(edited)))
                    }
                    model.update(trip.copy(days = updatedDays.sortedBy { it.date }))
                }
                editingFacts = false
            },
        )
    }

    if (showMore) {
        MoreMenu(
            event = event,
            canRemove = event.photoIDs.size > 1,
            canSplit = remember(event) { false },
            onRemove = {
                if (ids.size > 1) {
                    val current = ids.firstOrNull()
                    if (current != null) {
                        mutateEvent(model, trip, dayId, eventId) { ev ->
                            ev.copy(photoIDs = ev.photoIDs.filter { it != current })
                        }
                    }
                }
                showMore = false
            },
            onHide = {
                mutateEvent(model, trip, dayId, eventId) { ev -> ev.copy(isHidden = true) }
                val suppressed = (trip.suppressedEventIDs ?: emptyList()) + eventId
                model.update(trip.copy(suppressedEventIDs = suppressed))
                showMore = false
                navController.popBackStack()
            },
            onDelete = {
                mutateEvent(model, trip, dayId, eventId) { ev -> ev.copy(isHidden = true) }
                if (event.isUserCreated != true) {
                    val suppressed = (trip.suppressedEventIDs ?: emptyList()) + eventId
                    model.update(trip.copy(suppressedEventIDs = suppressed))
                }
                showMore = false
                navController.popBackStack()
            },
            onDismiss = { showMore = false },
        )
    }
}

@Composable
private fun PhotoGallery(ids: List<String>, loader: suspend (String, Int) -> Bitmap?) {
    val pagerState = rememberPagerState { ids.size }
    Box {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(430.dp)) { page ->
            PhotoThumbnail(id = ids[page], height = 430, cornerRadius = 0, loader = loader)
        }
        Text(
            "${pagerState.currentPage + 1} / ${ids.size}",
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).padding(horizontal = 11.dp, vertical = 7.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.55f)),
        )
    }
}

@Composable
private fun FactActions(
    event: MemoryEvent,
    day: TravelDay,
    model: AppModel,
    trip: DiscoveredTrip,
    isEditingFacts: Boolean,
    onEditFacts: () -> Unit,
    onMore: () -> Unit,
    navController: NavController,
) {
    Column(Modifier.padding(horizontal = 20.dp).padding(18.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)) {
        Text("纠正这段记忆", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onEditFacts() }) { Text("地点不对") }
            OutlinedButton(onClick = { onEditFacts() }) { Text("时间不对") }
            val hasNext = day.events.indexOfFirst { it.id == event.id } < day.events.size - 1
            if (hasNext) {
                OutlinedButton(onClick = {
                    val index = day.events.indexOfFirst { it.id == event.id }
                    val next = day.events[index + 1]
                    val suppressed = (trip.suppressedEventIDs ?: emptyList()) + next.id
                    val merged = event.copy(
                        endDate = maxOf(event.endDate, next.endDate),
                        photoIDs = (event.photoIDs + next.photoIDs).distinct(),
                    )
                    val updatedDays = trip.days.map {
                        if (it.id == day.id) {
                            it.copy(events = it.events.filterNot { e -> e.id == next.id }.map { e -> if (e.id == event.id) merged else e })
                        } else it
                    }
                    model.update(trip.copy(days = updatedDays, suppressedEventIDs = suppressed))
                }) { Text("与下一事件合并") }
            }
        }
        if (event.location != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { navController.navigate(Routes.tripMap(trip.id)) }) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.width(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("在记忆地图中查看")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onMore() }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, modifier = Modifier.width(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("更多")
            }
        }
    }
}

@Composable
private fun FactsEditor(
    draftPlace: String, draftStart: Long, draftEnd: Long,
    onPlaceChange: (String) -> Unit,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onCancel() },
        title = { Text("纠正事实") },
        text = {
            Column {
                TextField(value = draftPlace, onValueChange = onPlaceChange, label = { Text("地点") })
                Spacer(Modifier.height(8.dp))
                val startText = remember(draftStart) { formatTime(draftStart) }
                val endText = remember(draftEnd) { formatTime(draftEnd) }
                TextField(value = startText, onValueChange = { }, label = { Text("开始时间") }, readOnly = true)
                Spacer(Modifier.height(8.dp))
                TextField(value = endText, onValueChange = { }, label = { Text("结束时间") }, readOnly = true)
                Text("时间调整在 Android 版简化为展示；如需精确修改请使用详细编辑。", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onSave() }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { onCancel() }) { Text("取消") } },
    )
}

@Composable
private fun MoreMenu(
    event: MemoryEvent,
    canRemove: Boolean,
    canSplit: Boolean,
    onRemove: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("处理这个事件") },
        text = {
            Column {
                if (canRemove) {
                    Text("移除当前照片", color = Color(0xFFB3261E), modifier = Modifier.padding(vertical = 8.dp).clickable { onRemove() })
                }
                Text("隐藏这个事件", color = Color(0xFFB3261E), modifier = Modifier.padding(vertical = 8.dp).clickable { onHide() })
                Text("删除这一段记录", color = Color(0xFFB3261E), modifier = Modifier.padding(vertical = 8.dp).clickable { onDelete() })
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss() }) { Text("取消") } },
    )
}

private fun mutateEvent(model: AppModel, trip: DiscoveredTrip, dayId: String, eventId: String, transform: (MemoryEvent) -> MemoryEvent) {
    val dayIndex = trip.days.indexOfFirst { it.id == dayId }
    if (dayIndex < 0) return
    val eventIndex = trip.days[dayIndex].events.indexOfFirst { it.id == eventId }
    if (eventIndex < 0) return
    val updatedDays = trip.days.toMutableList()
    val updatedEvents = updatedDays[dayIndex].events.toMutableList()
    updatedEvents[eventIndex] = transform(updatedEvents[eventIndex])
    updatedDays[dayIndex] = updatedDays[dayIndex].copy(events = updatedEvents)
    model.update(trip.copy(days = updatedDays))
}