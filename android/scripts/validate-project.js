#!/usr/bin/env node
// 拾旅 Android 工程静态校验（对齐 ios/scripts/validate-project.js）
// 校验：已声明权限与隐私文案、不引用外部网络/密钥、必需资源存在、包名一致、源码与测试齐全。
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const srcDir = path.join(root, 'app/src/main/java')
const testDir = path.join(root, 'app/src/test/java')
const errors = []

function walk(dir, predicate, output = []) {
  if (!fs.existsSync(dir)) return output
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full, predicate, output)
    else if (predicate(full)) output.push(full)
  }
  return output
}

const appSources = walk(srcDir, f => f.endsWith('.kt'))
const testSources = walk(testDir, f => f.endsWith('.kt'))

if (appSources.length === 0) errors.push('no Kotlin app sources found')
if (testSources.length === 0) errors.push('no Kotlin test sources found')

for (const file of [...appSources, ...testSources]) {
  const text = fs.readFileSync(file, 'utf8')
  if (/https?:\/\/(?!schemas\.android\.com)/.test(text)) errors.push(`${path.relative(root, file)} contains an external network URL`)
  if (/AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9]{20,}|AppSecret/i.test(text)) errors.push(`${path.relative(root, file)} contains credential-like content`)
  if (text.trim().length === 0) errors.push(`${path.relative(root, file)} is empty`)
}

// 包名一致性
const namespace = 'com.example.shilv'
const nsMismatch = appSources.filter(f => {
  const rel = path.relative(srcDir, f)
  return !rel.startsWith(namespace.replace(/\./g, path.sep))
})
if (nsMismatch.length) errors.push(`sources outside package ${namespace}: ${nsMismatch.map(f => path.relative(root, f)).join(', ')}`)

// Manifest 权限与隐私文案
const manifest = fs.readFileSync(path.join(root, 'app/src/main/AndroidManifest.xml'), 'utf8')
if (!manifest.includes('android.permission.READ_MEDIA_IMAGES')) errors.push('missing READ_MEDIA_IMAGES permission')
if (!manifest.includes('applicationId') && !fs.readFileSync(path.join(root, 'app/build.gradle.kts'), 'utf8').includes('com.example.shilv')) errors.push('applicationId mismatch')
const strings = fs.readFileSync(path.join(root, 'app/src/main/res/values/strings.xml'), 'utf8')
if (!strings.includes('photo_usage_description')) errors.push('missing photo_usage_description string')
if (!strings.includes('不会上传照片原图')) errors.push('photo purpose string does not state upload behavior')

// 必需资源
const requiredRes = [
  'app/src/main/res/values/colors.xml',
  'app/src/main/res/values/themes.xml',
  'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
  'app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml',
  'app/src/main/res/drawable/ic_launcher_foreground.xml',
  'app/src/main/res/xml/backup_rules.xml',
  'app/src/main/res/xml/data_extraction_rules.xml',
]
for (const rel of requiredRes) {
  const full = path.join(root, rel)
  if (!fs.existsSync(full) || fs.statSync(full).size === 0) errors.push(`missing resource ${rel}`)
}

// 入口与导航
const mainActivity = path.join(srcDir, 'com/example/shilv/MainActivity.kt')
const rootScreen = path.join(srcDir, 'com/example/shilv/ui/RootScreen.kt')
if (!fs.existsSync(mainActivity)) errors.push('missing MainActivity.kt')
if (!fs.existsSync(rootScreen)) errors.push('missing RootScreen.kt')

// 结构完整性：关键服务与屏幕模块（相对包根 com/example/shilv）
const pkgBase = path.join(srcDir, 'com/example/shilv')
const keyModules = [
  'data/Models.kt',
  'domain/TripDetector.kt',
  'service/TripStore.kt',
  'service/PhotoLibraryService.kt',
  'service/MemoryAnalysisService.kt',
  'service/PlaceNameService.kt',
  'ui/AppModel.kt',
  'ui/discovery/DiscoveryScreen.kt',
  'ui/trip/TripOverviewScreen.kt',
  'ui/trip/DayTimelineScreen.kt',
  'ui/event/EventDetailScreen.kt',
  'ui/map/TripMapScreen.kt',
  'ui/memory/MemoryCardScreen.kt',
  'ui/settings/SettingsScreen.kt',
]
for (const rel of keyModules) {
  const full = path.join(pkgBase, rel)
  if (!fs.existsSync(full)) errors.push(`missing key module ${rel}`)
}

if (errors.length) {
  console.error(errors.join('\n'))
  process.exit(1)
}
console.log(`Android project validation ok: ${appSources.length} app sources, ${testSources.length} test sources, permissions & privacy present, resources complete`)