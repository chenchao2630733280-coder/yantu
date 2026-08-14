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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Sparkles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.shilv.ui.AppModel
import com.example.shilv.ui.Routes
import com.example.shilv.ui.shared.GradientOverlay
import com.example.shilv.ui.shared.PhotoThumbnail
import com.example.shilv.ui.shared.formatDayLabel
import com.example.shilv.ui.shared.formatTripRange
import com.example.shilv.ui.theme.Green
import com.example.shilv.ui.theme.Ink
import com.example.shilv.ui.theme.Muted
import com.example.shilv.ui.theme.Orange
import com.example.shilv.ui.theme.Paper

@Composable
fun TripOverviewScreen(model: AppModel, navController: NavController, tripId: String) {
    val trip = remember(model.dataRevision.value) { model.trips.firstOrNull { it.id == tripId } }
    if (trip == null) {
        Box(Modifier.fillMaxSize().background(Paper))
        return
    }
    var showRename by remember { mutableStateOf(false) }
    var draftTitle by remember { mutableStateOf(trip.title) }
    var showCoverPicker by remember { mutableStateOf(false) }
    val analysisProgress by model.analysisProgress.collectAsState()
    val loader: suspend (String, Int) -> Bitmap? = { id, size -> model.loadImage(id, size) }

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Ink) }
            Text(trip.title, modifier = Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { val updated = trip.copy(isFavorite = !trip.favorite); model.update(updated) }) {
                Icon(if (trip.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "收藏", tint = Orange)
            }
            IconButton(onClick = { showCoverPicker = true }) { Icon(Icons.Filled.Photo, contentDescription = "更换封面", tint = Ink) }
            IconButton(onClick = { draftTitle = trip.title; showRename = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = Ink) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { Hero(trip, loader) }
            if (!trip.isConfirmed) { item { Confirmation(trip, model, analysisProgress) } }
            item { Text(trip.summary ?: "照片把一路的时间、地点和停留重新连接了起来。", fontSize = 22.sp, fontFamily = FontFamily.Serif, lineHeight = 30.sp) }
            item { StatsRow(trip) }
            item { Highlights(trip, loader) }
            if (trip.center != null) {
                item {
                    Column {
                        Text("旅行路线", fontSize = 20.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(trip.visibleEvents.take(5).map { it.placeName ?: it.title }.joinToString("  →  "), color = Muted, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { navController.navigate(Routes.tripMap(trip.id)) }) {
                            Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.width(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("打开记忆地图")
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column { Text("旅程故事线", fontSize = 20.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold); Text("点开一天，重新走过当时的故事", color = Muted, fontSize = 12.sp) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { navController.navigate(Routes.memoryCard(trip.id)) }) { Text("回忆卡片 ›", color = Orange, fontWeight = FontWeight.Bold) }
                }
            }
            items(trip.days.filter { it.visibleEvents.isNotEmpty() }) { day ->
                val index = trip.days.indexOfFirst { it.id == day.id }
                DayCard(index, day, loader) {
                    navController.navigate(Routes.dayTimeline(trip.id, day.id))
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("修改旅行名称") },
            text = {
                TextField(value = draftTitle, onValueChange = { draftTitle = it.take(40) }, label = { Text("旅行名称") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = draftTitle.trim()
                    if (trimmed.isNotEmpty()) { model.update(trip.copy(title = trimmed)); showRename = false }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("取消") } },
        )
    }

    if (showCoverPicker) {
        val ids = trip.visibleEvents.flatMap { it.visiblePhotoIDs }.distinct().take(50)
        AlertDialog(
            onDismissRequest = { showCoverPicker = false },
            title = { Text("选择旅行封面") },
            text = {
                LazyVerticalGrid(columns = GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ids) { id ->
                        Box {
                            PhotoThumbnail(id = id, height = 120, cornerRadius = 12, loader = loader)
                            if (id == trip.coverPhotoID) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Orange, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp))
                            }
                            Box(Modifier.matchParentSize().clickable {
                                model.update(trip.copy(coverPhotoIDOverride = id)); showCoverPicker = false
                            })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCoverPicker = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun Hero(trip: DiscoveredTrip, loader: suspend (String, Int) -> Bitmap?) {
    Box(Modifier.fillMaxWidth().height(310.dp).clip(RoundedCornerShape(26.dp))) {
        PhotoThumbnail(id = trip.coverPhotoID, height = 310, cornerRadius = 26, loader = loader)
        GradientOverlay()
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            if (!trip.isConfirmed) {
                Text("新发现", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(Orange))
                Spacer(Modifier.height(6.dp))
            }
            Text(trip.title, color = Color.White, fontSize = 34.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Text(formatTripRange(trip.startDate, trip.endDate), color = Color.White, fontSize = 14.sp)
        }
    }
}

@Composable
private fun StatsRow(trip: DiscoveredTrip) {
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)) {
        StatCell("${trip.dayCount}", "天", Modifier.weight(1f))
        StatCell("${trip.photoCount}", "张照片", Modifier.weight(1f))
        StatCell("${trip.placeCount}", "个地点", Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Highlights(trip: DiscoveredTrip, loader: suspend (String, Int) -> Bitmap?) {
    Column {
        Text("旅程精彩时刻", fontSize = 20.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            trip.visibleEvents.mapNotNull { it.coverPhotoID }.take(6).forEach { id ->
                PhotoThumbnail(id = id, height = 160, cornerRadius = 18, modifier = Modifier.width(128.dp), loader = loader)
            }
        }
    }
}

@Composable
private fun Confirmation(trip: DiscoveredTrip, model: AppModel, progress: Pair<Int, Int>?) {
    Column(Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)) {
        Text("这是你的旅行吗？", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("这些照片远离常驻区域、时间连续，因此被识别为同一段旅程。", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        if (progress != null) {
            LinearProgressIndicator(progress = { progress.first.toFloat() / maxOf(1, progress.second) }, modifier = Modifier.fillMaxWidth(), color = Orange)
            Spacer(Modifier.height(6.dp))
            Text("正在本机理解事件 · ${progress.first}/${progress.second}", color = Muted, fontSize = 12.sp)
        } else {
            Button(
                onClick = { model.confirmAndAnalyze(trip) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) { Text("这是我的旅行") }
        }
        Spacer(Modifier.height(8.dp))
        Text("隐藏这次发现", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
            model.update(trip.copy(isHidden = true))
        })
    }
}

@Composable
private fun DayCard(index: Int, day: com.example.shilv.data.TravelDay, loader: suspend (String, Int) -> Bitmap?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(13.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Day", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (index % 2 == 0) Orange else Green)
            Text("${index + 1}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (index % 2 == 0) Orange else Green)
            Text(formatDayLabel(day.date), fontSize = 9.sp, color = if (index % 2 == 0) Orange else Green, maxLines = 1)
        }
        PhotoThumbnail(id = day.coverPhotoID, height = 90, cornerRadius = 14, modifier = Modifier.width(105.dp), loader = loader)
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(day.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("${day.visibleEvents.size} 个事件 · ${day.photoCount} 张照片", color = Muted, fontSize = 12.sp)
            Text(day.visibleEvents.take(2).map { it.title }.joinToString(" · "), color = Muted, fontSize = 12.sp, maxLines = 2)
        }
    }
}