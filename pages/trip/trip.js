const store = require('../../services/store'); const nav = require('../../utils/nav')
Page({
  data:{trip:{}, toast:''},
  onLoad(q){this.id=q.id||'kansai-2026'}, onShow(){this.setData({trip:store.getTrip(this.id)||store.getTrip('kansai-2026')})},
  back:nav.back, openDay(e){nav.day(e.currentTarget.dataset.day)}, openMap(){wx.navigateTo({url:'/pages/map/map'})}, openMemory(){wx.navigateTo({url:'/pages/memory/memory'})},
  confirm(){store.patchTrip(this.id,{confirmed:true});this.setData({trip:store.getTrip(this.id),toast:'已保存为我的旅行'});setTimeout(()=>this.setData({toast:''}),1600)},
  reject(){wx.showModal({title:'这不是一次旅行？',content:'你可以先保留，之后仍可在回忆中查看。',confirmText:'暂时隐藏',success:r=>{if(r.confirm){store.patchTrip(this.id,{hidden:true});wx.switchTab({url:'/pages/home/home'})}}})}
})
