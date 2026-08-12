const store = require('../../services/store')
const photos = require('../../services/photo-adapter')
const nav = require('../../utils/nav')
Page({
  data: { trips: [], scanning: false, toast: '' },
  onShow() { this.setData({ trips: store.getState().trips.filter(item => !item.hidden) }) },
  openTrip(e) { nav.trip(e.currentTarget.dataset.id) },
  async scan() {
    this.setData({ scanning: true })
    try {
      const files = await photos.chooseTravelPhotos()
      if (!files.length) return
      const result = await photos.detectTrips(files)
      store.addImported(result.importedCount)
      this.flash(`已在本机读取 ${result.importedCount} 张照片`)
    } catch (e) { this.flash('无法读取照片，请检查相册权限') }
    finally { this.setData({ scanning: false }) }
  },
  flash(toast) { this.setData({ toast }); setTimeout(() => this.setData({ toast: '' }), 1800) }
})
