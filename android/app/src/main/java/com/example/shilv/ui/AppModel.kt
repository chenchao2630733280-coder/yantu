package com.example.shilv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import com.example.shilv.data.DiscoveredTrip
import com.example.shilv.data.GeoPoint
import com.example.shilv.data.PhotoAccessState
import com.example.shilv.data.PhotoRecord
import com.example.shilv.data.ScanPhase
import com.example.shilv.data.ScanSnapshot
import com.example.shilv.domain.TripDetector
import com.example.shilv.service.MemoryAnalysisService
import com.example.shilv.service.PhotoLibraryService
import com.example.shilv.service.PlaceNameService
import com.example.shilv.service.TripStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** 全局状态：对应 iOS AppModel。 */
class AppModel(app: Application) : AndroidViewModel(app) {

    private val photoLibrary = PhotoLibraryService(app)
    private val store = TripStore(File(app.filesDir, "shilv-index"))
    private val detector = TripDetector()
    private val placeNames = PlaceNameService(app)

    private val _scanPhase = MutableStateFlow<ScanPhase>(ScanPhase.Idle)
    val scanPhase: StateFlow<ScanPhase> = _scanPhase.asStateFlow()

    private val _accessState = MutableStateFlow(photoLibrary.accessState)
    val accessState: StateFlow<PhotoAccessState> = _accessState.asStateFlow()

    private val _analysisProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val analysisProgress: StateFlow<Pair<Int, Int>?> = _analysisProgress.asStateFlow()

    private val _thumbnailCacheSize = MutableStateFlow(0L)
    val thumbnailCacheSize: StateFlow<Long> = _thumbnailCacheSize.asStateFlow()

    private val _dataRevision = MutableStateFlow(0)
    val dataRevision: StateFlow<Int> = _dataRevision.asStateFlow()

    private val _presentedError = MutableStateFlow<String?>(null)
    val presentedError: StateFlow<String?> = _presentedError.asStateFlow()

    val snapshot: ScanSnapshot? get() = store.snapshot
    val trips: List<DiscoveredTrip> get() = store.snapshot?.trips?.filter { !it.isHidden } ?: emptyList()
    val storedDataSize: Long get() = store.storedDataSize

    fun consumeError() { _presentedError.value = null }

    fun start() {
        _accessState.value = photoLibrary.accessState
        if (photoLibrary.accessState == PhotoAccessState.Full && store.snapshot == null) {
            scanLibrary()
        }
        refreshStorageUsage()
    }

    fun requestAndScan() {
        if (_scanPhase.value == ScanPhase.RequestingPermission) return
        _scanPhase.value = ScanPhase.RequestingPermission
        viewModelScope.launch {
            _accessState.value = photoLibrary.accessState
            if (photoLibrary.accessState != PhotoAccessState.Full) {
                _scanPhase.value = ScanPhase.Idle
                return@launch
            }
            _scanPhase.value = ScanPhase.Idle
            scanLibrary()
        }
    }

    fun scanLibrary() {
        if (photoLibrary.accessState != PhotoAccessState.Full) {
            _scanPhase.value = ScanPhase.Idle
            return
        }
        viewModelScope.launch {
            _scanPhase.value = ScanPhase.ReadingMetadata(0, 0)
            val records = photoLibrary.fetchMetadata { current, total ->
                _scanPhase.value = ScanPhase.ReadingMetadata(current, total)
            }
            if (photoLibrary.accessState != PhotoAccessState.Full) {
                _scanPhase.value = ScanPhase.Idle
                return@launch
            }
            _scanPhase.value = ScanPhase.DetectingTrips
            val snapshot = detector.detect(records)
            _scanPhase.value = ScanPhase.Saving
            store.replace(snapshot)
            _dataRevision.value += 1
            _scanPhase.value = ScanPhase.Complete
        }
    }

    fun confirmAndAnalyze(trip: DiscoveredTrip) {
        val eventCount = maxOf(1, trip.eventCount)
        _analysisProgress.value = 0 to eventCount
        viewModelScope.launch {
            val service = MemoryAnalysisService(photoLibrary, placeNames)
            val enriched = service.enrich(trip) { current, total ->
                _analysisProgress.value = current to total
            }
            update(enriched)
            _analysisProgress.value = null
        }
    }

    fun update(trip: DiscoveredTrip) {
        val reconciled = trip.also { it.reconcileDerivedFields() }
        store.updateTrip(reconciled)
        _dataRevision.value += 1
    }

    fun resetIndex() {
        viewModelScope.launch {
            store.deleteLocalIndex()
            _dataRevision.value += 1
            _scanPhase.value = ScanPhase.Idle
        }
    }

    fun refreshStorageUsage() {
        _thumbnailCacheSize.value = photoLibrary.thumbnailCacheSize()
    }

    fun clearThumbnailCache() {
        photoLibrary.clearThumbnailCache()
        refreshStorageUsage()
    }

    fun resolveLocation(name: String): GeoPoint? = null // 由调用方在协程中执行
    suspend fun resolveLocationAsync(name: String): GeoPoint? = placeNames.locate(name)

    suspend fun loadImage(id: String, targetSize: Int): Bitmap? =
        photoLibrary.requestImage(id, targetSize)
}

/** 供 UI 在协程中解析地点。 */
suspend fun AppModel.resolve(name: String): GeoPoint? = resolveLocationAsync(name)