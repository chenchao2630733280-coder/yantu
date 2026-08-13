package com.example.shilv.ui.timeline

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.shilv.data.TripDates
import com.example.shilv.ui.AppModel
import com.example.shilv.ui.Routes
import com.example.shilv.ui.shared.PhotoThumbnail
import com.example.shilv.ui.shared.formatTripRange
import com.example.shilv.ui.theme.Muted
import com.example.shilv.ui.theme.Orange
import com.example.shilv.ui.theme.Paper

@Composable
fun AllTripsTimelineScreen(model: AppModel, navController: NavController) {
    val trips = remember(model.dataRevision.value) { model.trips.filter { it.isConfirmed } }
    val loader: suspend (String, Int) -> Bitmap? = { id, size -> model.loadImage(id, size) }
    val grouped = trips.groupBy { TripDates.yearOf(it.startDate) }
        .map { (year, list) -> year to list.sortedByDescending { it.startDate } }
        .sortedByDescending { it.first }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Paper),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("时间线", fontSize = 38.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                Text("照片替你记得每一次出发", color = Muted, fontSize = 14.sp)
            }
        }
        if (grouped.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(androidx.compose.material.icons.Icons.Filled.CalendarMonth, contentDescription = null, tint = Muted, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("还没有旅行", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("完成照片库扫描后，旅行会按年份出现在这里。", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        grouped.forEach { (year, tripsInYear) ->
            item { Text(year.toString(), fontSize = 36.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }
            items(tripsInYear) { trip ->
                TimelineTripCard(trip, loader) { navController.navigate(Routes.tripOverview(trip.id)) }
            }
        }
    }
}

@Composable
private fun TimelineTripCard(trip: DiscoveredTrip, loader: suspend (String, Int) -> Bitmap?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Orange))
        Spacer(Modifier.width(14.dp))
        PhotoThumbnail(id = trip.coverPhotoID, height = 105, cornerRadius = 15, modifier = Modifier.width(125.dp), loader = loader)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(trip.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(formatTripRange(trip.startDate, trip.endDate), color = Muted, fontSize = 12.sp)
            Text("${trip.dayCount} 天 · ${trip.photoCount} 张照片", color = Muted, fontSize = 12.sp)
        }
    }
}