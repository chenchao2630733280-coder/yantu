const assert = require('assert')

const HOUR = 3600, DAY = 86400
const distance = (a,b) => {
  const r=6371000, p=Math.PI/180, dLat=(b.lat-a.lat)*p, dLon=(b.lon-a.lon)*p
  const h=Math.sin(dLat/2)**2+Math.cos(a.lat*p)*Math.cos(b.lat*p)*Math.sin(dLon/2)**2
  return 2*r*Math.asin(Math.sqrt(h))
}
const centroid = points => ({lat:points.reduce((n,p)=>n+p.lat,0)/points.length,lon:points.reduce((n,p)=>n+p.lon,0)/points.length})
const splitAwayAnchors = (photos,home,gap=72*HOUR) => photos.reduce((groups,p)=>{
  if(!p.point||distance(p.point,home)<80000){if(groups.at(-1)?.length)groups.push([]);return groups}
  const current=groups.at(-1),last=current?.at(-1)
  if(last&&p.date-last.date>gap)groups.push([])
  groups.at(-1).push(p);return groups
},[[]]).filter(Boolean).filter(g=>g.length)
function inferHome(photos){
  const buckets=new Map
  for(const p of photos.filter(x=>x.point)){const key=`${Math.round(p.point.lat*5)},${Math.round(p.point.lon*5)}`;if(!buckets.has(key))buckets.set(key,[]);buckets.get(key).push(p)}
  const best=[...buckets.values()].sort((a,b)=>new Set(b.map(x=>Math.floor(x.date/DAY))).size-new Set(a.map(x=>Math.floor(x.date/DAY))).size||b.length-a.length)[0]
  return best?.length>=5?centroid(best.map(x=>x.point)):null
}
function detect(photos){
  photos=[...photos].sort((a,b)=>a.date-b.date);const eligible=photos.filter(x=>!x.screenshot),home=inferHome(eligible);if(!home)return[]
  return splitAwayAnchors(eligible.filter(x=>x.point),home).filter(g=>g.length>=5).map(g=>{const start=g[0].date-6*HOUR,end=g.at(-1).date+6*HOUR,all=eligible.filter(x=>x.date>=start&&x.date<=end);return{all,days:new Set(all.map(x=>Math.floor(x.date/DAY))).size}}).filter(x=>x.all.length>=12&&(x.days>=2||x.all.length>=30))
}
const photo=(id,date,point)=>({id,date,point})
const shanghai={lat:31.2304,lon:121.4737},kyoto={lat:35.0116,lon:135.7681},near={lat:31.30,lon:121.60},base=1700000000
const home=Array.from({length:30},(_,i)=>photo(`h${i}`,base+i*DAY,shanghai))
const trip=Array.from({length:24},(_,i)=>photo(`t${i}`,base+40*DAY+i*2*HOUR,i===7?null:kyoto))
const found=detect([...home,...trip])
assert.equal(found.length,1,'far multi-day trip should be detected')
assert.equal(found[0].all.length,24,'photo without GPS inside trip window should be included')
const weekend=Array.from({length:40},(_,i)=>photo(`w${i}`,base+40*DAY+i*1800,near))
assert.equal(detect([...home,...weekend]).length,0,'nearby weekend should not be travel')
const tripA=Array.from({length:15},(_,i)=>photo(`a${i}`,base+60*DAY+i*2*HOUR,kyoto))
const returned=photo('returned',base+62*DAY,shanghai)
const tripB=Array.from({length:15},(_,i)=>photo(`b${i}`,base+63*DAY+i*2*HOUR,kyoto))
assert.equal(detect([...home,...tripA,returned,...tripB]).length,2,'a home photo must separate nearby trips')
const screenshots=Array.from({length:40},(_,i)=>({...photo(`s${i}`,base+80*DAY+i*HOUR,kyoto),screenshot:true}))
assert.equal(detect([...home,...screenshots]).length,0,'screenshots should not create a trip')
assert(distance(shanghai,kyoto)>800000,'distance baseline')
console.log('Trip detector contract tests ok: far trip, home boundary, nearby and screenshot exclusion, no-GPS inclusion')
