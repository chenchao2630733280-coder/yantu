function chooseTravelPhotos() {
  return new Promise((resolve, reject) => {
    wx.chooseMedia({
      count: 9,
      mediaType: ['image'],
      sourceType: ['album'],
      success: result => resolve(result.tempFiles || []),
      fail: error => error.errMsg && error.errMsg.includes('cancel') ? resolve([]) : reject(error)
    })
  })
}

// 生产版在此替换为本地 EXIF 时间/GPS 聚类；当前演示不上传照片。
function detectTrips(files) {
  return Promise.resolve({ importedCount: files.length, candidateId: 'kansai-2026' })
}

module.exports = { chooseTravelPhotos, detectTrips }
