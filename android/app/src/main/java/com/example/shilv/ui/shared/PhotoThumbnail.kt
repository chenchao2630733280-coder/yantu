package com.example.shilv.ui.shared

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.example.shilv.ui.theme.Line
import com.example.shilv.ui.theme.Paper

/**
 * 照片缩略图：按需从 MediaStore 加载并显示，对应 iOS PhotoThumbnail。
 * loader 由 AppModel 提供。
 */
@Composable
fun PhotoThumbnail(
    id: String?,
    height: Int,
    cornerRadius: Int = 16,
    modifier: Modifier = Modifier,
    loader: suspend (String, Int) -> Bitmap?,
) {
    val density = LocalDensity.current
    val heightDp: Dp = with(density) { height.toDp() }
    val bitmap by produceState<Bitmap?>(initialValue = null, id) {
        value = if (id == null) null else loader(id, height * 2)
    }
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Line, Paper))),
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "旅行照片",
                modifier = Modifier.fillMaxWidth().height(heightDp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxWidth().height(heightDp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.height(with(density) { 22.dp }),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** 底部渐变遮罩，用于大图上的白色文字可读性，对应 iOS LinearGradient。 */
@Composable
fun BoxScope.GradientOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                ),
            ),
    )
}