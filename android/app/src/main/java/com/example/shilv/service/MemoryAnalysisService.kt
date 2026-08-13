package com.example.shilv.service

import com.example.shilv.data.DiscoveredTrip
import com.example.shilv.data.MemoryEvent
import com.example.shilv.data.TripDates

/**
 * 记忆分析服务：确认旅行后增强事件语义。
 *  - 使用 Android 自带 ML Kit / Image Labeling 识别场景（简化为关键词匹配，模拟 iOS Vision 效果）
 *  - 补充地点名称、城市信息
 *  - 生成事件摘要和旅行总结
 */
class MemoryAnalysisService(
    private val photoLibrary: PhotoLibraryService,
    private val placeNames: PlaceNameService,
) {
    suspend fun enrich(trip: DiscoveredTrip, onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }): DiscoveredTrip {
        val enriched = trip.copy()
        val total = enriched.days.sumOf { it.events.size }
        var completed = 0

        // 根据中心点重新命名旅行
        val center = enriched.center
        if (center != null) {
            val place = placeNames.resolve(center)
            if (place != null) {
                enriched.title = "${place.city ?: place.shortName}之旅"
            }
        }

        val enrichedDays = mutableListOf<com.example.shilv.data.TravelDay>()
        for (day in enriched.days) {
            val events = day.events.toMutableList()
            for (eventIndex in events.indices) {
                val event = events[eventIndex]
                // 尝试获取封面图做视觉识别
                val coverID = event.coverPhotoID
                if (coverID != null && completed < 100) {
                    val bitmap = photoLibrary.requestImage(coverID, 640)
                    if (bitmap != null) {
                        val labels = classify(bitmap)
                        val bestTitle = bestTitleFor(labels, event.startDate)
                        events[eventIndex] = events[eventIndex].copy(title = bestTitle)
                    }
                }
                // 解析地点名称
                val point = event.location
                if (point != null) {
                    val place = placeNames.resolve(point)
                    if (place != null) {
                        events[eventIndex] = events[eventIndex].copy(
                            placeName = place.shortName,
                            cityName = place.city,
                            countryName = place.country,
                        )
                    }
                }
                // 生成摘要
                events[eventIndex] = events[eventIndex].copy(
                    summary = eventSummary(events[eventIndex]),
                )
                completed++
                onProgress(completed, total)
            }
            enrichedDays.add(day.copy(events = events, title = dayTitle(day.copy(events = events))))
        }
        enriched.days = enrichedDays
        enriched.summary = tripSummary(enriched)
        enriched.isConfirmed = true
        return enriched
    }

    private fun eventSummary(event: MemoryEvent): String {
        val place = event.placeName ?: event.title
        val period = when (TripDates.hourOf(event.startDate)) {
            in 5 until 11 -> "清晨"
            in 11 until 14 -> "午间"
            in 14 until 18 -> "下午"
            else -> "傍晚"
        }
        val activity = if (event.title == place) "这一段停留" else "${event.title}的片段"
        return "${period}来到${place}，照片留下了${activity}。"
    }

    private fun tripSummary(trip: DiscoveredTrip): String {
        val places = trip.visibleEvents.mapNotNull { it.placeName }.distinct()
        if (places.isEmpty()) return "这是一段由照片重新整理出来的旅程。"
        return "这是一段沿着${places.take(3).joinToString("、")}慢慢展开的旅行。"
    }

    private fun dayTitle(day: com.example.shilv.data.TravelDay): String {
        val unique = day.events.map { it.title }.distinct()
        return if (unique.size >= 2) "${unique[0]}与${unique[1]}" else unique.firstOrNull() ?: day.title
    }

    /** 模拟 iOS Vision VNClassifyImageRequest 的关键词匹配。 */
    private suspend fun classify(bitmap: android.graphics.Bitmap): List<String> {
        // 简化：使用 Android ML Kit 原生图像标注需额外依赖；这里用 Bitmap 的像素颜色分析做基础内容分类
        // 实际生产可替换为 com.google.mlkit:image-labeling 或 CameraX 分析
        return detectFoodOrScene(bitmap)
    }

    private fun detectFoodOrScene(bitmap: android.graphics.Bitmap): List<String> {
        // 采样像素颜色，做基础场景猜测
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return emptyList()
        var totalR = 0L; var totalG = 0L; var totalB = 0L; var samples = 0
        for (x in 0 until w step maxOf(1, w / 20)) {
            for (y in 0 until h step maxOf(1, h / 20)) {
                val pixel = bitmap.getPixel(x, y)
                totalR += android.graphics.Color.red(pixel)
                totalG += android.graphics.Color.green(pixel)
                totalB += android.graphics.Color.blue(pixel)
                samples++
            }
        }
        if (samples == 0) return emptyList()
        val avgR = totalR / samples; val avgG = totalG / samples; val avgB = totalB / samples
        val results = mutableListOf<String>()
        // 暖色主导 -> 食物/室内
        if (avgR > avgG + 30 && avgR > avgB + 30) results.add("food")
        // 蓝色/绿色主导 -> 自然/户外
        if (avgB > avgR + 20 || avgG > avgR + 20) results.add("nature")
        // 亮度较高 -> 城市/建筑
        val brightness = (avgR + avgG + avgB) / 3
        if (brightness > 180) results.add("architecture")
        // 较暗 -> 夜间
        if (brightness < 80) results.add("night")
        return results
    }

    companion object {
        private val FOOD_KEYWORDS = listOf("food", "dish", "meal", "restaurant", "cuisine", "drink", "coffee", "dessert")
        private val NATURE_KEYWORDS = listOf("mountain", "forest", "tree", "garden", "park", "flower", "nature")
        private val ARCHITECTURE_KEYWORDS = listOf("temple", "church", "shrine", "palace", "castle", "monument", "historic")
        private val CITY_KEYWORDS = listOf("street", "city", "building", "market", "shop", "town", "architecture")
        private val WATER_KEYWORDS = listOf("beach", "ocean", "sea", "lake", "river", "waterfall")
        private val TRANSPORT_KEYWORDS = listOf("train", "aircraft", "airport", "vehicle", "bus", "station", "subway")
        private val ANIMAL_KEYWORDS = listOf("animal", "dog", "cat", "deer", "bird", "zoo")
        private val NIGHT_KEYWORDS = listOf("night", "skyline", "sunset", "sunrise", "light")

        fun bestTitleFor(labels: List<String>, date: Long): String {
            val joined = labels.joinToString(" ")
            val hour = TripDates.hourOf(date)
            if (containsAny(joined, FOOD_KEYWORDS)) {
                return when {
                    hour < 11 -> "旅途早餐"
                    hour < 17 -> "当地味道"
                    else -> "晚餐时光"
                }
            }
            if (containsAny(joined, TRANSPORT_KEYWORDS)) return "在路上"
            if (containsAny(joined, WATER_KEYWORDS)) return "水边的记忆"
            if (containsAny(joined, NATURE_KEYWORDS)) return "走进自然"
            if (containsAny(joined, ARCHITECTURE_KEYWORDS)) return "古迹与建筑"
            if (containsAny(joined, CITY_KEYWORDS)) return "城市漫步"
            if (containsAny(joined, ANIMAL_KEYWORDS)) return "意外的相遇"
            if (containsAny(joined, NIGHT_KEYWORDS)) return if (hour >= 17) "城市入夜" else "追着光走"
            return when (hour) {
                in 0 until 6 -> "夜里的片段"
                in 6 until 11 -> "清晨出发"
                in 11 until 14 -> "午间停留"
                in 14 until 18 -> "下午漫步"
                else -> "傍晚时分"
            }
        }

        private fun containsAny(text: String, keywords: List<String>): Boolean =
            keywords.any { text.contains(it, ignoreCase = true) }
    }
}