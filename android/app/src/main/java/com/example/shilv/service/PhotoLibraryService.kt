package com.example.shilv.service

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.Manifest
import android.content.ContentUris
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.shilv.data.GeoPoint
import com.example.shilv.data.PhotoRecord
import com.example.shilv.data.PhotoAccessState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 照片库服务：对应 iOS PhotoKit。
 *  - 通过 MediaStore 读取照片元数据（时间/位置/本机 URI/尺寸）
 *  - 按需通过 ContentResolver 加载缩略图，并写入可清理缓存
 *  - 不修改、不删除系统照片，不上传
 */
class PhotoLibraryService(private val context: Context) {

    private val thumbCacheDir: File = File(context.cacheDir, "ShiLvThumbnails").apply { mkdirs() }
    private val generation = AtomicLong(0)

    val accessState: PhotoAccessState
        get() {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
            return if (granted) PhotoAccessState.Full else PhotoAccessState.NotDetermined
        }

    /** 枚举整库照片元数据。返回按时间排序的 PhotoRecord。 */
    suspend fun fetchMetadata(onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }): List<PhotoRecord> =
        withContext(Dispatchers.IO) {
            if (accessState != PhotoAccessState.Full) return@withContext emptyList()
            val records = mutableListOf<PhotoRecord>()
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.LATITUDE,
                MediaStore.Images.Media.LONGITUDE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
            )
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            context.contentResolver.query(
                uri, projection, null, null,
                "${MediaStore.Images.Media.DATE_TAKEN} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val latCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.LATITUDE)
                val lonCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.LONGITUDE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val total = cursor.count
                var index = 0
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val date = cursor.getLong(dateCol).takeIf { it > 0 } ?: 0L
                    val lat = cursor.getDouble(latCol)
                    val lon = cursor.getDouble(lonCol)
                    val location = if (lat != 0.0 || lon != 0.0) GeoPoint(lat, lon) else null
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    if (date > 0) {
                        records.add(
                            PhotoRecord(
                                id = contentUriFor(id).toString(),
                                creationDate = date,
                                location = location,
                                pixelWidth = width,
                                pixelHeight = height,
                                isFavorite = false,
                                isScreenshot = false,
                            ),
                        )
                    }
                    index++
                    if (index % 250 == 0 || index == total - 1) onProgress(index + 1, total)
                }
            }
            records
        }

    /** 按需加载缩略图，带本机缓存。 */
    suspend fun requestImage(id: String, targetSize: Int): Bitmap? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(id)
        val key = cacheKey(id, targetSize)
        val cacheFile = File(thumbCacheDir, key)
        if (cacheFile.exists()) {
            return@withContext try { android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath) } catch (t: Throwable) { null }
        }
        val bitmap = try {
            context.contentResolver.loadThumbnail(uri, android.util.Size(targetSize, targetSize), null)
        } catch (t: Throwable) {
            null
        } ?: decodeFallback(uri, targetSize)
        if (bitmap != null) {
            try {
                cacheFile.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out) }
            } catch (t: Throwable) { /* ignore cache write failure */ }
        }
        bitmap
    }

    private fun decodeFallback(uri: Uri, targetSize: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.let { input ->
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeStream(input, null, bounds)
                input.close()
                val sample = maxOf(1, (minOf(bounds.outWidth, bounds.outHeight) / targetSize))
                context.contentResolver.openInputStream(uri)?.let { input2 ->
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = android.graphics.BitmapFactory.decodeStream(input2, null, opts)
                    input2.close()
                    bmp
                }
            }
        } catch (t: Throwable) { null }
    }

    fun thumbnailCacheSize(): Long =
        thumbCacheDir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clearThumbnailCache(): Result<Unit> = runCatching {
        generation.incrementAndGet()
        thumbCacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun contentUriFor(id: Long): Uri = ContentUris.withAppendedId(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id,
    )

    private fun cacheKey(id: String, targetSize: Int): String {
        var hash = 14_695_981_039_346_656_037UL
        val raw = "$id-$targetSize"
        for (byte in raw.toByteArray(Charsets.UTF_8)) {
            hash = (hash xor byte.toULong()) * 1_099_511_628_211UL
        }
        return hash.toString(16) + ".jpg"
    }
}