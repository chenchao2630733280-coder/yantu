package com.example.shilv.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shilv.ui.AppModel
import com.example.shilv.ui.theme.Ink
import com.example.shilv.ui.theme.Muted
import com.example.shilv.ui.theme.Orange
import com.example.shilv.ui.theme.Paper

@Composable
fun PhotoPermissionScreen(model: AppModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier.size(112.dp).background(Orange.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = Orange, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(26.dp))
        Text("让照片，重新变成旅途", fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "拾旅会读取照片中的时间和地点，自动发现旅行，并整理成旅行故事。",
            color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 24.sp,
        )
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PermissionRow(Icons.Filled.Lock, "你的照片不会被复制到拾旅")
            PermissionRow(Icons.Filled.Place, "地点名称由系统服务按需解析")
            PermissionRow(Icons.Filled.CheckCircle, "全部照片：扫描整个照片库，发现完整旅程")
            PermissionRow(Icons.Filled.Settings, "随时可在系统设置中更改权限")
        }
        Spacer(Modifier.height(26.dp))
        Button(
            onClick = { model.requestAndScan() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
            shape = RoundedCornerShape(16.dp),
        ) { Text("开始发现我的旅行", fontSize = 16.sp) }
        Spacer(Modifier.height(12.dp))
        Text("你可以先退出，准备好后再回来；拾旅不会在后台读取照片。", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, text: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}