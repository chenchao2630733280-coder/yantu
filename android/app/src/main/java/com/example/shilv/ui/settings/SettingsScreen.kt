package com.example.shilv.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.shilv.data.PhotoAccessState
import com.example.shilv.ui.AppModel
import com.example.shilv.ui.shared.byteCountText
import com.example.shilv.ui.theme.Ink
import com.example.shilv.ui.theme.Muted
import com.example.shilv.ui.theme.Paper

@Composable
fun SettingsScreen(model: AppModel) {
    val dataRevision by model.dataRevision.collectAsState()
    val thumbnailCacheSize by model.thumbnailCacheSize.collectAsState()
    val snapshot = remember(dataRevision) { model.snapshot }
    var showReset by remember { mutableStateOf(false) }
    var shareIncludesMemories by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(62.dp).clip(CircleShape).background(Ink),
                contentAlignment = Alignment.Center,
            ) { Text("旅", color = Color.White, fontSize = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(15.dp))
            Column {
                Text("我的拾旅", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("记忆索引只保存在这台设备", color = Muted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(22.dp))
        SectionTitle("照片与隐私")
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("照片权限", modifier = Modifier.weight(1f), fontSize = 14.sp)
            Text(accessLabel(model.accessState.value), color = Muted, fontSize = 14.sp)
        }
        Text("照片如何被使用：读取拍摄时间和地点，在本机自动发现旅行。", color = Muted, fontSize = 12.sp)

        Spacer(Modifier.height(22.dp))
        SectionTitle("本机数据")
        LabeledRow("已扫描", "${snapshot?.accessiblePhotoCount ?: 0} 张")
        LabeledRow("已发现旅行", "${model.trips.size} 次")
        LabeledRow("缩略图缓存", byteCountText(thumbnailCacheSize))
        LabeledRow("旅行数据", byteCountText(model.storedDataSize))
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { model.clearThumbnailCache() },
            enabled = thumbnailCacheSize > 0,
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
        ) { Text("清理缩略图缓存") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { model.scanLibrary() }, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("重新扫描整个照片库") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { showReset = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) { Text("删除本机旅行索引") }

        Spacer(Modifier.height(22.dp))
        SectionTitle("分享设置")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("回忆卡包含我的补充记忆", modifier = Modifier.weight(1f), fontSize = 14.sp)
            Switch(checked = shareIncludesMemories, onCheckedChange = { shareIncludesMemories = it })
        }
        Text("关闭后，保存和分享的回忆卡不会带上你写下的话。", color = Muted, fontSize = 12.sp)

        Spacer(Modifier.height(22.dp))
        SectionTitle("关于")
        LabeledRow("版本", "1.0.0")
        Text("拾旅不会修改或删除系统照片，不会将原图、位置或记忆发送到服务器，也不使用广告跟踪。确认旅行或纠正地点时，有限坐标会交给系统地理编码服务解析。", color = Muted, fontSize = 12.sp)
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("删除本机旅行索引？") },
            text = { Text("这不会删除系统照片。旅行确认、名称和补充记忆会从拾旅中移除。") },
            confirmButton = {
                TextButton(onClick = { model.resetIndex(); showReset = false }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Text(value, color = Muted, fontSize = 14.sp)
    }
}

private fun accessLabel(state: PhotoAccessState): String = when (state) {
    PhotoAccessState.Full -> "所有照片"
    PhotoAccessState.Limited -> "部分照片"
    PhotoAccessState.Denied -> "已拒绝"
    PhotoAccessState.Restricted -> "受限制"
    PhotoAccessState.NotDetermined -> "未设置"
}