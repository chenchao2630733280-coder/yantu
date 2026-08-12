const demo = require('../data/demo')

const KEY = 'shilv_state_v1'

function clone(value) { return JSON.parse(JSON.stringify(value)) }

function seed() {
  return {
    trips: [clone(demo.trip)].concat(clone(demo.otherTrips)),
    events: clone(demo.events),
    settings: { localFirst: true, anniversary: true, wifiOnly: true },
    importedCount: 0
  }
}

function bootstrap() {
  if (!wx.getStorageSync(KEY)) wx.setStorageSync(KEY, seed())
}

function decorate(state) {
  state.trips = state.trips.map(item => Object.assign({}, item, { routeText: (item.route || []).join(' · ') }))
  state.events = state.events.map(item => Object.assign({}, item, { previewTiles: [item.tile, 2, 3, 7] }))
  return state
}
function getState() { bootstrap(); return decorate(wx.getStorageSync(KEY)) }
function save(state) { wx.setStorageSync(KEY, state); return state }
function reset() { return save(seed()) }
function getTrip(id) { return getState().trips.find(item => item.id === id) }
function getEvents(day) { return getState().events.filter(item => !item.hidden && (!day || item.day === Number(day))) }

function patchTrip(id, patch) {
  const state = getState()
  const index = state.trips.findIndex(item => item.id === id)
  if (index >= 0) state.trips[index] = Object.assign({}, state.trips[index], patch)
  save(state)
  return state.trips[index]
}

function patchEvent(id, patch) {
  const state = getState()
  const index = state.events.findIndex(item => item.id === id)
  if (index >= 0) state.events[index] = Object.assign({}, state.events[index], patch)
  save(state)
  return state.events[index]
}

function patchSettings(patch) {
  const state = getState()
  state.settings = Object.assign({}, state.settings, patch)
  save(state)
  return state.settings
}

function addImported(count) {
  const state = getState()
  state.importedCount += count
  save(state)
  return state.importedCount
}

module.exports = { KEY, seed, bootstrap, getState, save, reset, getTrip, getEvents, patchTrip, patchEvent, patchSettings, addImported }
