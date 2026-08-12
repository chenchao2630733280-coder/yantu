const store = require('./services/store')

App({
  onLaunch() {
    store.bootstrap()
  },
  globalData: {
    productName: '拾旅'
  }
})
