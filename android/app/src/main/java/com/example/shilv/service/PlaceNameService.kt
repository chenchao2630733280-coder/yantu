package com.example.shilv.service

import android.content.Context
import android.location.Geocoder
import com.example.shilv.data.GeoPoint
import java.util.Locale

/**
 * 地点名称解析服务：对应 iOS CLGeocoder。
 *  - 缓存避免重复请求
 *  - 仅按需解析有限坐标
 */
class PlaceNameService(private val context: Context) {

    data class PlaceName(
        val shortName: String,
        val city: String?,
        val country: String?,
    )

    private val cache = mutableMapOf<String, PlaceName?>()
    private var lastRequestAt: Long = 0L

    suspend fun resolve(point: GeoPoint): PlaceName? {
        val key = cacheKey(point)
        if (cache.containsKey(key)) return cache[key]
        respectRateLimit()
        return try {
            val geocoder = Geocoder(context, Locale.CHINA)
            val marks = geocoder.getFromLocation(point.latitude, point.longitude, 1)
            lastRequestAt = System.currentTimeMillis()
            if (marks.isNullOrEmpty()) { cache[key] = null; return null }
            val mark = marks.first()
            val city = mark.locality ?: mark.subAdminArea ?: mark.adminArea
            val short = mark.featureName ?: mark.subLocality ?: city ?: mark.countryName ?: "旅途中的一站"
            val value = PlaceName(shortName = short, city = city, country = mark.countryName)
            cache[key] = value
            value
        } catch (t: Throwable) {
            lastRequestAt = System.currentTimeMillis()
            cache[key] = null
            null
        }
    }

    suspend fun locate(query: String): GeoPoint? {
        if (query.isBlank()) return null
        respectRateLimit()
        return try {
            val geocoder = Geocoder(context, Locale.CHINA)
            val marks = geocoder.getFromLocationName(query, 1)
            lastRequestAt = System.currentTimeMillis()
            if (marks.isNullOrEmpty()) return null
            val mark = marks.first()
            GeoPoint(mark.latitude, mark.longitude)
        } catch (t: Throwable) {
            lastRequestAt = System.currentTimeMillis()
            null
        }
    }

    private fun cacheKey(point: GeoPoint) =
        "${(point.latitude * 100).toLong() / 100.0},${(point.longitude * 100).toLong() / 100.0}"

    private fun respectRateLimit() {
        val elapsed = System.currentTimeMillis() - lastRequestAt
        if (elapsed < 250) {
            try { Thread.sleep(250 - elapsed) } catch (_: InterruptedException) { /* ignore */ }
        }
    }
}