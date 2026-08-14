package com.example.shilv.ui.discovery

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shilv.data.DiscoveredTrip
import com.example.shilv.data.ScanPhase
import com.example.shilv.data.TripDates
import com.example.shilv.ui.AppModel
import com.example.shilv.ui.Routes
import com.example.shilv.ui.shared.GradientOverlay
import com.example.shilv.ui.shared.PhotoThumbnail
import com.example.shilv.ui.shared.formatTripRange
import com.example.shilv.ui.theme.Green
import com.example.shilv.ui.theme.Ink
import com.example.shilv.ui.theme.Muted
import com.example.shilv.ui.theme.Orange
import com.example.shilv.ui.theme.Paper

@Composable
fun DiscoveryScreen(model: AppModel, navController: NavController) {
    val scanPhase by model.scanPhase.collectAsState()
    val trips = remember(model.dataRevision.value) { model.trips }
    val loader: suspend (String, Int) -> Bitmap? = { id, size -> model.loadImage(id, size) }

    val confirmed = trips.filter { it.isConfirmed }.sortedByDescending { it.startDate }
    val featured = confirmed.firstOrNull()
    val anniversary = anniversaryTrip(confirmed)
    val random = randomTrip(confirmed, anniversary, featured)
    val otherTrips = trips.filter { it.id != featured?.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Paper),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Header(model, loader) }
        item { ScanStatus(model, scanPhase, trips) }
        if (trips.isEmpty() && scanPhase == ScanPhase.Complete) {
            item { EmptyState() }
        }
        if (featured != null) {
            item { SectionTitle("最近的旅程") }
            item {
                MemoryHeroCard(featured, loader) {
                    navController.navigate(Routes.tripOverview(featured.id))
                }
            }
        }
        if (anniversary != null) {
            item { SectionTitle("几年前的今天") }
            item { ReflectionCard(anniversary, "照片替你记得这一天", loader) { navController.navigate(Routes.tripOverview(anniversary.id)) } }
        }
        if (random != null) {
            item { SectionTitle("随机回忆") }
            item { ReflectionCard(random, "再看一眼走过的地方", loader) { navController.navigate(Routes.tripOverview(random.id)) } }
        }
        if (featured != null) {
            item { SectionTitle("为你生成的回忆卡") }
            item { ReflectionCard(featured, "把这段旅程保存下来", loader) { navController.navigate(Routes.memoryCard(featured.id)) } }
        }
        if (otherTrips.isNotEmpty()) {
            item { SectionTitle(if (trips.any { !it.isConfirmed }) "新发现与其他旅行" else "其他旅行") }
            items(otherTrips) { trip ->
                TripDiscoveryCard(trip, loader) {
                    val route = if (trip.isConfirmed) Routes.tripOverview(trip.id) else Routes.tripDiscovery(trip.id)
                    navController.navigate(route)
                }
            }
        }
    }
}

@Composable
private fun Header(model: AppModel, loader: suspend (String, Int) -> Bitmap?) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column {
            Text("拾旅", fontSize = 38.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Text("让照片，重新变成旅途", color = Muted, fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White)
                .clickable { model.scanLibrary() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Refresh, contentDescription = "重新扫描照片库", tint = Ink) }
    }
}

@Composable
private fun ScanStatus(model: AppModel, phase: ScanPhase, trips: List<DiscoveredTrip>) {
    if (phase != ScanPhase.Idle && phase != ScanPhase.Complete) {
        Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(phase.label, fontSize = 14.sp)
            }
            phase.progress?.let {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth(), color = Orange)
            }
            Spacer(Modifier.height(8.dp))
            Text("只读取时间和地点，不下载原始照片", color = Muted, fontSize = 12.sp)
        }
    } else if (model.snapshot != null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp).clip(RoundedCornerShape(16.dp)).background(Green.copy(alpha = 0.10f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Green)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("已在本机扫描 ${model.snapshot?.accessiblePhotoCount ?: 0} 张照片", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("发现 ${trips.size} 次可能的旅行", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().height(340.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Place, contentDescription = null, tint = Muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("还没有发现旅行", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("旅行检测依赖照片的时间和 GPS。你可以在相机设置中开启定位。", color = Muted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 20.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
}

@Composable
private fun MemoryHeroCard(trip: DiscoveredTrip, loader: suspend (String, Int) -> Bitmap?, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(430.dp).clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick),
    ) {
        PhotoThumbnail(id = trip.coverPhotoID, height = 430, cornerRadius = 26, loader = loader)
        GradientOverlay()
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            Text("${TripDates.monthOf(trip.startDate)} 月，你去了", color = Color.White, fontSize = 14.sp)
            Text("${trip.title} ${trip.dayCount} 天", color = Color.White, fontSize = 32.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            Text("${trip.photoCount} 张照片 · ${trip.placeCount} 个地点", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text("查看旅程  →", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReflectionCard(trip: DiscoveredTrip, caption: String, loader: suspend (String, Int) -> Bitmap?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)
            .clickable(onClick = onClick),
    ) {
        PhotoThumbnail(id = trip.coverPhotoID, height = 120, cornerRadius = 18, modifier = Modifier.width(138.dp), loader = loader)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(caption, color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(trip.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(formatTripRange(trip.startDate, trip.endDate), color = Muted, fontSize = 12.sp)
            Text(trip.summary ?: "重新走进这段旅程", color = Muted, fontSize = 12.sp, maxLines = 2)
        }
    }
}

@Composable
private fun TripDiscoveryCard(trip: DiscoveredTrip, loader: suspend (String, Int) -> Bitmap?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoThumbnail(id = trip.coverPhotoID, height = 112, cornerRadius = 16, modifier = Modifier.width(132.dp), loader = loader)
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            if (!trip.isConfirmed) {
                Text("新发现", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
            }
            Text(trip.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(formatTripRange(trip.startDate, trip.endDate), color = Muted, fontSize = 12.sp)
            Text("${trip.dayCount} 天 · ${trip.photoCount} 张 · ${trip.eventCount} 个事件", color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Muted)
    }
}

private fun anniversaryTrip(confirmed: List<DiscoveredTrip>): DiscoveredTrip? {
    val today = TripDates.monthDay(System.currentTimeMillis())
    return confirmed.firstOrNull { trip ->
        trip.days.any { day -> TripDates.monthDay(day.date) == today }
    }
}

private fun randomTrip(confirmed: List<DiscoveredTrip>, anniversary: DiscoveredTrip?, featured: DiscoveredTrip?): DiscoveredTrip? {
    val candidates = confirmed.filter { it.id != anniversary?.id && it.id != featured?.id }
    if (candidates.isEmpty()) return null
    val day = System.currentTimeMillis() / 86_400_000L
    return candidates[(day % candidates.size).toInt()]
}