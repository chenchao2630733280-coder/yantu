const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')
const sources = {
  models: read('ShiLv/Domain/Models.swift'), app: read('ShiLv/App/AppModel.swift'),
  photo: read('ShiLv/Services/PhotoLibraryService.swift'), detector: read('ShiLv/Services/TripDetector.swift'),
  store: read('ShiLv/Services/TripStore.swift'), analysis: read('ShiLv/Services/MemoryAnalysisService.swift'),
  discovery: read('ShiLv/Features/Discovery/TripDiscoveryView.swift'), home: read('ShiLv/Features/Discovery/DiscoveryView.swift'),
  day: read('ShiLv/Features/Trip/DayTimelineView.swift'), event: read('ShiLv/Features/Event/EventDetailView.swift'),
  map: read('ShiLv/Features/Map/TripMapView.swift'), memory: read('ShiLv/Features/Memory/MemoryCardView.swift'),
  settings: read('ShiLv/Features/Settings/SettingsView.swift')
}

const requirements = [
  ['MVP-01', '相册授权', 'photo', ['requestAuthorization(for: .readWrite)', 'presentLimitedLibraryPicker']],
  ['MVP-02', '读取照片时间', 'photo', ['creationDate']],
  ['MVP-03', '读取照片位置', 'photo', ['asset.location']],
  ['MVP-04', '自动发现旅行', 'detector', ['inferHome', 'splitAwayAnchors']],
  ['MVP-05', '用户确认旅行', 'discovery', ['这是我的旅行', 'confirmAndAnalyze']],
  ['MVP-06', '按照 Day 划分', 'detector', ['buildDays']],
  ['MVP-07', '自动识别地点', 'analysis', ['placeNames.resolve', 'placeName']],
  ['MVP-08', '自动聚类事件', 'detector', ['buildEvents']],
  ['MVP-09', '精选代表照片', 'detector', ['representativePhotoIDs', 'guard source.count > 5', 'source.count * 3 / 4']],
  ['MVP-10', 'Day 时间线', 'day', ['DayPeriod', 'EventRow']],
  ['MVP-11', '旅行地图', 'map', ['MapPolyline', 'selectedDayID']],
  ['MVP-12', '事件详情', 'event', ['photoGallery', '查看全部']],
  ['MVP-13', '简短旅行描述', 'analysis', ['eventSummary', 'tripSummary']],
  ['MVP-14', '补一句记忆', 'event', ['记下一句话', 'note']],
  ['MVP-15', '纠正地点', 'event', ['地点不对', 'resolveLocation']],
  ['MVP-16', '纠正时间', 'event', ['时间不对', 'draftStart']],
  ['MVP-17', '合并和拆分事件', 'event', ['mergeWithNext', 'splitAtCurrentPhoto']],
  ['MVP-18', '隐藏和删除事件', 'event', ['隐藏这个事件', 'deleteEvent']],
  ['MVP-19', '旅行总结卡', 'memory', ['MemoryPoster', '保存 / 分享']],
  ['MVP-20', '本地缓存管理', 'settings', ['缩略图缓存', 'clearThumbnailCache']]
]

const errors = []
for (const [id, title, file, tokens] of requirements) {
  for (const token of tokens) if (!sources[file].includes(token)) errors.push(`${id} ${title}: ${file} missing ${token}`)
}
const crossCutting = [
  ['照片引用而非原图复制', sources.models.includes('photoIDs') && !Object.values(sources).some(text => text.includes('PHPhotoLibrary.shared().performChanges'))],
  ['已确认旅行跨扫描保留', sources.store.includes('preservedMemories')],
  ['往年今日', sources.home.includes('anniversaryTrip')],
  ['随机回忆', sources.home.includes('randomTrip')],
  ['旅行数据导出', sources.settings.includes('TripExportDocument')],
  ['旅行统计使用路线距离', sources.models.includes('routeDistanceMeters')],
  ['纠错后重算旅行字段', sources.models.includes('reconcileDerivedFields') && sources.app.includes('reconcileDerivedFields')]
]
crossCutting.push(
  ['授权成功后启动扫描', sources.app.includes('scanPhase = .idle\n        await scanLibrary()')],
  ['扫描和图片请求可取消', sources.app.includes('cancelActiveScan') && sources.photo.includes('cancelImageRequest') && sources.photo.includes('withTaskCancellationHandler')],
  ['后台中断后恢复扫描', sources.app.includes('resumeInterruptedScanIfNeeded') && sources.app.includes('scanNeedsResume')],
  ['回忆卡读取实时旅行数据', sources.memory.includes('seedTrip') && sources.memory.includes('model.store.snapshot')],
  ['照片库变化去抖', sources.app.includes('scheduleLibraryChangeScan') && sources.app.includes('1_500_000_000')],
  ['深度图片分析最多 100 个事件', sources.analysis.includes('deepAnalysisBudget = 100') && sources.analysis.includes('completed < deepAnalysisBudget')],
  ['删除索引等待扫描结束', sources.app.includes('func resetIndex() async') && sources.app.includes('await active.task.value')],
  ['清缓存阻止旧请求写回', sources.photo.includes('currentGeneration') && sources.photo.includes('requestGeneration == generation')]
)
for (const [title, passed] of crossCutting) if (!passed) errors.push(`cross-cutting requirement failed: ${title}`)
if (errors.length) { console.error(errors.join('\n')); process.exit(1) }
console.log(`iOS requirement contracts ok: ${requirements.length} MVP items, ${crossCutting.length} cross-cutting behaviors`)
