const trip = {
  id: 'kansai-2026',
  title: '日本关西之旅',
  dateRange: '2026.07.18 – 2026.07.24',
  startDate: '2026-07-18',
  endDate: '2026-07-24',
  days: 7,
  photos: 438,
  places: 23,
  cities: 4,
  distance: '128.6 km',
  route: ['大阪', '京都', '奈良', '神户'],
  summary: '从大阪热闹的夜晚，到京都安静的寺庙，这是一次穿过关西四座城市的夏日旅行。',
  confirmed: false,
  discovered: true,
  coverTile: 0,
  dayList: [
    { day: 1, date: '7.18', title: '大阪初印象', subtitle: '抵达大阪 · 心斋桥 · 道顿堀晚餐', photos: 89, color: '#3478d4', tiles: [4, 0, 6] },
    { day: 2, date: '7.19', title: '京都的古与静', subtitle: '伏见稻荷 · 祇园 · 清水寺 · 鸭川', photos: 102, color: '#f36b2b', tiles: [1, 2, 3, 7] },
    { day: 3, date: '7.20', title: '奈良的慢时光', subtitle: '奈良公园 · 东大寺 · 返回大阪', photos: 76, color: '#6d8a53', tiles: [5, 3, 4] },
    { day: 4, date: '7.21', title: '神户的海与坂', subtitle: '北野异人馆 · 港口 · 摩耶山夜景', photos: 53, color: '#7b61c9', tiles: [6, 2, 7] }
  ]
}

const events = [
  { id: 'fushimi', day: 2, time: '08:30', endTime: '11:00', period: '上午', title: '伏见稻荷大社', duration: '停留约 2.5 小时', photos: 42, tile: 1, tag: '景点', lat: 34.9671, lng: 135.7727, note: '', story: '你一早就到了伏见稻荷大社，沿着千本鸟居一路向上走。阳光穿过朱红色的鸟居，像给这段记忆加了一层温暖的滤镜。' },
  { id: 'gion', day: 2, time: '11:30', endTime: '12:30', period: '上午', title: '祇园花见小路', duration: '停留约 1 小时', photos: 28, tile: 2, tag: '街区', lat: 35.0037, lng: 135.7752, note: '', story: '午前走进祇园，木格子町屋和窄窄的石板路让脚步不自觉慢下来。' },
  { id: 'kiyomizu', day: 2, time: '13:00', endTime: '14:30', period: '下午', title: '清水寺', duration: '停留约 1.5 小时', photos: 36, tile: 3, tag: '景点', lat: 34.9948, lng: 135.785, note: '', story: '下午从清水舞台望向京都，城市在山色里缓缓铺开。' },
  { id: 'sannenzaka', day: 2, time: '15:30', endTime: '16:30', period: '下午', title: '二年坂·三年坂', duration: '停留约 1 小时', photos: 18, tile: 0, tag: '街区', lat: 34.997, lng: 135.7809, note: '', story: '下山经过二年坂和三年坂，在旧街的屋檐下收集了许多细碎画面。' },
  { id: 'kamogawa', day: 2, time: '17:20', endTime: '18:50', period: '晚上', title: '鸭川河边散步', duration: '停留约 1.5 小时', photos: 31, tile: 7, tag: '自然', lat: 35.0091, lng: 135.771, note: '', story: '傍晚在鸭川边待到天色变暗，这一天最终落在安静的水光里。' }
]

const otherTrips = [
  { id: 'xiamen-2026', title: '厦门周末之旅', dateRange: '2026.04.03 – 2026.04.05', days: 3, photos: 126, places: 9, cities: 1, route: ['厦门', '鼓浪屿'], coverTile: 6, confirmed: true },
  { id: 'beijing-2025', title: '北京冬日之旅', dateRange: '2025.12.30 – 2026.01.02', days: 4, photos: 215, places: 14, cities: 1, route: ['北京'], coverTile: 3, confirmed: true },
  { id: 'chengdu-2025', title: '成都慢生活', dateRange: '2025.10.02 – 2025.10.05', days: 4, photos: 198, places: 11, cities: 1, route: ['成都'], coverTile: 5, confirmed: true },
  { id: 'hangzhou-2025', title: '杭州西湖之旅', dateRange: '2025.08.16 – 2025.08.17', days: 2, photos: 82, places: 7, cities: 1, route: ['杭州'], coverTile: 7, confirmed: true }
]

module.exports = { trip, events, otherTrips }
