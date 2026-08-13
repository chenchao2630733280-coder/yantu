package com.example.shilv.ui.discovery

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sparkles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import com.example.shilv.ui.AppModel
import com.example.shilv.ui.Routes
import com.example.shilv.ui.shared.GradientOverlay
import com.example.shilv.ui.shared.PhotoThumbnail
import com.example.shilv.ui.shared.formatTripRange
import com.example.shilv.ui.theme.Ink
import com.example.shilv.ui.theme.Muted
import com.example.shilv.ui.theme.Orange
import com.example.shilv.ui.theme.Paper

@Composable
fun TripDiscoveryScreen(model: AppModel, navController: NavController, tripId: String) {
    val trip = remember(model.dataRevision.value) { model.trips.firstOrNull { it.id == tripId } }
    if (trip == null) {
        Box(Modifier.fillMaxSize().background(Paper))
        return
    }
    var isConfirming by remember { mutableStateOf(false) }
    val analysisProgress by model.analysisProgress.collectAsState()
    val loader: suspend (String, Int) -> Bitmap? = { id, size -> model.loadImage(id, size) }

    Column(
        modifier = Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 35.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(430.dp).clip(RoundedCornerShape(28.dp))) {
            PhotoThumbnail(id = trip.coverPhotoID, height = 430, cornerRadius = 28, loader = loader)
            GradientOverlay()
            Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Sparkles, contentDescription = null, tint = Color.White, modifier = Modifier.width(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("发现一段旅程", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(trip.title, color = Color.White, fontSize = 38.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                Text(formatTripRange(trip.startDate, trip.endDate), color = Color.White, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row {
            DiscoveryStat("${trip.dayCount}", "天")
            DiscoveryStat("${trip.photoCount}", "张照片")
            DiscoveryStat("${trip.placeCount}", "个地点")
        }
        if (trip.cityCount > 0) {
            Spacer(Modifier.height(10.dp))
            Text("途经 ${trip.cityCount} 个城市", color = Muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text("我们根据照片的时间和地点发现，这可能是一次旅行。", fontSize = 20.sp, fontFamily = FontFamily.Serif, lineHeight = 28.sp)
        if (trip.locatedEventCount > 1) {
            Spacer(Modifier.height(8.dp))
            Text(
                trip.visibleEvents.take(4).map { it.placeName ?: it.title }.joinToString("  →  "),
                color = Orange, fontWeight = FontWeight.Bold, maxLines = 2,
            )
        }
        Spacer(Modifier.height(24.dp))
        if (isConfirming) {
            Column(Modifier.fillMaxWidth().padding(20.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)) {
                val progress = analysisProgress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.first.toFloat() / maxOf(1, progress.second) },
                        modifier = Modifier.fillMaxWidth(), color = Orange,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(statusText(progress.first, progress.second), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    CircularProgressIndicator(color = Orange)
                    Spacer(Modifier.height(8.dp))
                    Text("正在准备整理旅程……", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("原始照片始终留在系统相册", color = Muted, fontSize = 12.sp)
            }
        } else {
            Button(
                onClick = {
                    isConfirming = true
                    model.confirmAndAnalyze(trip)
                    navController.navigate(Routes.tripOverview(trip.id)) {
                        popUpTo(Routes.Discovery)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) { Text("这是我的旅行", fontSize = 16.sp) }
            Spacer(Modifier.height(8.dp))
            Text(
                "可能不是这次旅行",
                modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally).widthIn(min = 0.dp),
                color = Muted, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DiscoveryStat(value: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

private fun statusText(current: Int, total: Int): String {
    val progress = if (total > 0) current.toDouble() / total else 0.0
    return when {
        progress < 0.25 -> "正在整理照片……"
        progress < 0.55 -> "正在识别你去过的地方……"
        progress < 0.85 -> "正在还原旅程……"
        else -> "正在生成旅行故事……"
    }
}