package com.example.shilv.ui.map

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shilv.data.DiscoveredTrip
import com.example.shilv.data.GeoPoint
import com.example.shilv.data.MemoryEvent
import com.example.shilv.ui.AppModel
import com.example.shilv.ui.Routes
import com.example.shilv.ui.shared.PhotoThumbnail
import com.example.shilv.ui.shared.durationMapText
import com.example.shilv.ui.shared.formatShortDateTime
import com.example.shilv.ui.theme.Ink
import com.example.shilv.ui.theme.Muted
import com.example.shilv.ui.theme.Orange
import com.example.shilv.ui.theme.Paper

/**
 * 记忆地图：使用组建绘制的示意图（无需 Google Maps API Key）。
 * 生产接入边界：可替换为 Google Maps / 高德地图 SDK，本实现保证离线可用。
 */
@Composable
fun TripMapScreen(model: AppModel, navController: NavController, tripId: String) {
    val trip = remember(model.dataRevision.value) { model.trips.firstOrNull { it.id == tripId } }
    if (trip == null) {
        Box(Modifier.fillMaxSize().background(Paper))
        return
    }
    var selectedDayID by remember { mutableStateOf<String?>(null) }
    var selectedEventID by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableStateOf(1f) }
    val loader: suspend (String, Int) -> Bitmap? = { id, size -> model.loadImage(id, size) }

    val days = if (selectedDayID != null) trip.days.filter { it.id == selectedDayID } else trip.days
    val events = days.flatMap { it.events }.filter { !it.isHidden && it.location != null }
    val markerEvents = events.sortedByDescending { it.photoCount }.take(12).sortedBy { it.startDate }
    val selectedEvent = events.firstOrNull { it.id == selectedEventID }

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Ink) }
            Text(trip.title, modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Box(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
            detectTransformGestures { _, _, zoom, _ ->
                scale = (scale * zoom).coerceIn(0.5f, 3f)
            }
        }) {
            RouteCanvas(events = markerEvents, selectedEventID = selectedEventID, scale = scale, onSelect = { selectedEventID = it })
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedDayID = null; selectedEventID = null },
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedDayID == null) Ink else Muted.copy(alpha = 0.6f)),
            ) { Text("全部", fontSize = 12.sp) }
            trip.days.forEachIndexed { index, day ->
                Button(
                    onClick = { selectedDayID = day.id; selectedEventID = null },
                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedDayID == day.id) Ink else Muted.copy(alpha = 0.6f)),
                ) { Text("Day ${index + 1}", fontSize = 12.sp) }
            }
        }

        if (selectedEvent != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).clickable {
                    val eventDay = trip.days.firstOrNull { it.events.any { e -> e.id == selectedEvent.id } }
                    if (eventDay != null) {
                        navController.navigate(Routes.eventDetail(trip.id, eventDay.id, selectedEvent.id))
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhotoThumbnail(id = selectedEvent.coverPhotoID, height = 88, cornerRadius = 14, modifier = Modifier.width(105.dp), loader = loader)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatShortDateTime(selectedEvent.startDate), color = Orange, fontSize = 12.sp)
                    Text(selectedEvent.placeName ?: selectedEvent.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("停留 ${durationMapText(selectedEvent.duration)} · ${selectedEvent.photoCount} 张照片", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            Spacer(Modifier.height(20.dp))
            Text("点一个位置，回到那段记忆", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun RouteCanvas(
    events: List<MemoryEvent>,
    selectedEventID: String?,
    scale: Float,
    onSelect: (String) -> Unit,
) {
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(1, 1)) }
    val projections = remember(events, scale, canvasSize) { computeProjections(events, scale, canvasSize) }
    Canvas(
        modifier = Modifier.fillMaxSize().onSizeChanged { canvasSize = it }.pointerInput(events, scale, canvasSize) {
            detectTapGestures { tap ->
                val hit = projections.minByOrNull { (_, offset) ->
                    kotlin.math.hypot(tap.x - offset.x, tap.y - offset.y)
                }
                if (hit != null && kotlin.math.hypot(tap.x - hit.second.x, tap.y - hit.second.y) < 44f) {
                    onSelect(hit.first)
                }
            }
        },
    ) {
        val located = projections.map { (id, offset) -> id to offset }
        if (located.isEmpty()) return@Canvas
        if (located.size > 1) {
            val path = Path()
            path.moveTo(located.first().second.x, located.first().second.y)
            located.drop(1).forEach { path.lineTo(it.second.x, it.second.y) }
            drawPath(path, color = Orange.copy(alpha = 0.75f), style = Stroke(width = 8f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        located.forEach { (id, offset) ->
            val selected = id == selectedEventID
            if (selected) {
                drawCircle(color = Color.White, radius = 30f * scale, center = offset)
                drawCircle(color = Orange, radius = 22f * scale, center = offset)
            } else {
                drawCircle(color = Color.White, radius = 24f * scale, center = offset)
                drawCircle(color = Orange, radius = 17f * scale, center = offset)
            }
        }
    }
}

private fun computeProjections(events: List<MemoryEvent>, scale: Float, size: androidx.compose.ui.unit.IntSize): List<Pair<String, Offset>> {
    val located = events.mapNotNull { e -> e.location?.let { e.id to it } }
    if (located.isEmpty()) return emptyList()
    val minLat = located.minOf { it.second.latitude }
    val maxLat = located.maxOf { it.second.latitude }
    val minLon = located.minOf { it.second.longitude }
    val maxLon = located.maxOf { it.second.longitude }
    val pad = 40f
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val usableW = w * scale - pad * 2
    val usableH = h * scale - pad * 2
    val cx = w / 2
    val cy = h / 2
    return located.map { (id, p) ->
        val x = pad + ((p.longitude - minLon) / (if (maxLon > minLon) maxLon - minLon else 1e-9)) * usableW
        val rawY = h * scale - (pad + ((p.latitude - minLat) / (if (maxLat > minLat) maxLat - minLat else 1e-9)) * usableH)
        val offset = Offset(cx + (x - cx) * scale, cy + (rawY - cy) * scale)
        id to offset
    }
}