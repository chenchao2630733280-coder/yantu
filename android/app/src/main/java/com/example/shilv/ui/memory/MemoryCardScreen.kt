package com.example.shilv.ui.memory

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shilv.data.DiscoveredTrip
import com.example.shilv.data.TripDates
import com.example.shilv.ui.AppModel
import com.example.shilv.ui.shared.formatDayLabel
import com.example.shilv.ui.shared.formatTime
import com.example.shilv.ui.shared.formatTripRange
import com.example.shilv.ui.theme.Ink
import com.example.shilv.ui.theme.Line
import com.example.shilv.ui.theme.Muted
import com.example.shilv.ui.theme.Orange
import com.example.shilv.ui.theme.Paper
import com.example.shilv.ui.theme.PosterBg
import kotlinx.coroutines.launch

@Composable
fun MemoryCardScreen(model: AppModel, tripId: String) {
    val trip = remember(model.dataRevision.value) { model.trips.firstOrNull { it.id == tripId } }
    if (trip == null) {
        Box(Modifier.fillMaxSize().background(PosterBg))
        return
    }
    var isFav by remember(trip.favorite, model.dataRevision.value) { mutableStateOf(trip.favorite) }
    var coverImage by remember { mutableStateOf<Bitmap?>(null) }
    val footprintImages = remember { mutableStateMapOf<String, Bitmap>() }
    val scope = rememberCoroutineScope()
    val loader: suspend (String, Int) -> Bitmap? = { id, size -> model.loadImage(id, size) }

    Column(Modifier.fillMaxSize().background(PosterBg).verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(20.dp)) {
            MemoryPoster(trip = trip, coverImage = coverImage, footprintImages = footprintImages, loader = loader)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { isFav = !isFav; model.update(trip.copy(isFavorite = isFav)) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isFav) Orange else Ink),
                ) {
                    Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isFav) "已收藏" else "收藏")
                }
                Button(
                    onClick = {
                        scope.launch {
                            if (trip.coverPhotoID != null) {
                                coverImage = model.loadImage(trip.coverPhotoID!!, 1170)
                            }
                            trip.visibleEvents.take(4).forEach { event ->
                                event.coverPhotoID?.let { id ->
                                    model.loadImage(id, 420)?.let { footprintImages[id] = it }
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                ) { Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("加载回忆卡") }
            }
        }
    }
}

@Composable
private fun MemoryPoster(
    trip: DiscoveredTrip,
    coverImage: Bitmap?,
    footprintImages: Map<String, Bitmap>,
    loader: suspend (String, Int) -> Bitmap?,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White)) {
        Column(Modifier.padding(25.dp)) {
            Text("TRIP MEMORY · ${TripDates.yearOf(trip.startDate)}", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(trip.title, fontSize = 34.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Text(formatTripRange(trip.startDate, trip.endDate), color = Muted, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            if (coverImage != null) {
                androidx.compose.foundation.Image(
                    bitmap = coverImage.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Line, Paper))))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            item { PosterStat("${trip.photoCount}", "照片") }
            item { PosterStat("${trip.dayCount}", "旅行天数") }
            item { PosterStat("${trip.cityCount}", "城市") }
            item { PosterStat("${trip.placeCount}", "地点") }
            item { PosterStat(String.format("%.1f km", trip.routeDistanceMeters / 1000), "移动距离") }
            item { PosterStat(formatTime(trip.endDate), "最后一张") }
        }
        Column(Modifier.padding(25.dp)) {
            trip.mostPhotographedEvent?.let { Text("${it.placeName ?: it.title} · 照片最多的地方", color = Muted, fontSize = 12.sp) }
            trip.busiestDay?.let { Text("${formatDayLabel(it.date)} · 拍照最多的一天", color = Muted, fontSize = 12.sp) }
            Text("${formatTime(trip.latestPhotoTime)} · 最晚拍照时间", color = Muted, fontSize = 12.sp)
        }
        Column(Modifier.padding(25.dp)) {
            Text("旅行足迹", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                trip.visibleEvents.take(4).forEach { event ->
                    Column(Modifier.weight(1f)) {
                        val id = event.coverPhotoID
                        if (id != null && footprintImages.containsKey(id)) {
                            androidx.compose.foundation.Image(
                                bitmap = footprintImages[id]!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(8.dp)).background(Line))
                        }
                        Text(event.placeName ?: event.title, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("旅行回顾", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(story(trip), fontSize = 18.sp, fontFamily = FontFamily.Serif, lineHeight = 26.sp)
            Spacer(Modifier.height(12.dp))
            Text("拾旅 · 让照片重新变成旅途", color = Muted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PosterStat(value: String, label: String) {
    Column(Modifier.fillMaxWidth().padding(15.dp).background(Color.White)) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Orange)
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

private fun story(trip: DiscoveredTrip): String {
    val summary = trip.summary
    if (!summary.isNullOrEmpty()) {
        return "$summary ${trip.dayCount} 天以后，你离开了这段旅程，但 ${trip.photoCount} 张照片把它留了下来。"
    }
    val eventTitles = trip.days.flatMap { it.events }.filter { !it.isHidden }.map { it.placeName ?: it.title }
    val note = trip.visibleEvents.mapNotNull { it.note.takeIf { n -> n.isNotEmpty() } }.firstOrNull()
    val route = eventTitles.take(4).joinToString("、")
    val noteText = if (note != null) "你还记得：“$note”" else "你留下的每一句话，会让它越来越像真正的回忆。"
    return "这是一段从 ${formatDayLabel(trip.startDate)} 开始的旅程。照片记住了${if (route.isEmpty()) "沿途的光景" else route}。$noteText"
}