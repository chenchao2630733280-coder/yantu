function back() {
  const pages = getCurrentPages()
  if (pages.length > 1) wx.navigateBack()
  else wx.switchTab({ url: '/pages/home/home' })
}

function trip(id = 'kansai-2026') { wx.navigateTo({ url: `/pages/trip/trip?id=${id}` }) }
function day(value = 2) { wx.navigateTo({ url: `/pages/day/day?day=${value}` }) }
function event(id) { wx.navigateTo({ url: `/pages/event/event?id=${id}` }) }

module.exports = { back, trip, day, event }
