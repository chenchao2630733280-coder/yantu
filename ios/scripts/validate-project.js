const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const project = fs.readFileSync(path.join(root, 'ShiLv.xcodeproj/project.pbxproj'), 'utf8')
const scheme = fs.readFileSync(path.join(root, 'ShiLv.xcodeproj/xcshareddata/xcschemes/ShiLv.xcscheme'), 'utf8')
const errors = []

function walk(dir, predicate, output = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full, predicate, output)
    else if (predicate(full)) output.push(full)
  }
  return output
}

const appSources = walk(path.join(root, 'ShiLv'), file => file.endsWith('.swift'))
const testSources = walk(path.join(root, 'ShiLvTests'), file => file.endsWith('.swift'))
for (const file of [...appSources, ...testSources]) {
  const relative = path.relative(root, file).replaceAll('\\', '/')
  const occurrences = project.split(`path = "${relative}"`).length - 1
  if (occurrences !== 1) errors.push(`${relative} project reference count is ${occurrences}`)
  const text = fs.readFileSync(file, 'utf8')
  if (/https?:\/\//.test(text)) errors.push(`${relative} contains a network URL`)
  if (/\btry!\b|\bas!\b/.test(text)) errors.push(`${relative} contains forced operation`)
  if (/AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9]{20,}|AppSecret/i.test(text)) errors.push(`${relative} contains credential-like content`)
}

for (const relative of ['ShiLv/Resources/Assets.xcassets', 'ShiLv/Resources/PrivacyInfo.xcprivacy']) {
  if (!project.includes(`path = "${relative}"`)) errors.push(`missing project resource ${relative}`)
}

const targetMatches = [...project.matchAll(/^\s*([A-F0-9]{24}) \/\* (ShiLv|ShiLvTests) \*\/ = \{isa = PBXNativeTarget/gm)]
for (const [, targetID, name] of targetMatches) if (!scheme.includes(`BlueprintIdentifier="${targetID}"`) ) errors.push(`scheme missing ${name} target ${targetID}`)
if (targetMatches.length !== 2) errors.push(`expected two targets, found ${targetMatches.length}`)
const testTarget = targetMatches.find(match => match[2] === 'ShiLvTests')
if (testTarget && !scheme.includes(`buildForTesting="YES"`) ) errors.push('scheme does not build tests')
if (testTarget && scheme.split(`BlueprintIdentifier="${testTarget[1]}"`).length - 1 < 2) errors.push('test target must appear in build and test actions')

const info = fs.readFileSync(path.join(root, 'ShiLv/Resources/Info.plist'), 'utf8')
if (!info.includes('<key>NSPhotoLibraryUsageDescription</key>')) errors.push('missing NSPhotoLibraryUsageDescription')
if (!info.includes('不会自动上传原图')) errors.push('photo purpose string does not state upload behavior')
const privacy = fs.readFileSync(path.join(root, 'ShiLv/Resources/PrivacyInfo.xcprivacy'), 'utf8')
if (!privacy.includes('<key>NSPrivacyTracking</key><false/>')) errors.push('privacy manifest must disable tracking')
if (appSources.some(file => fs.readFileSync(file, 'utf8').includes('@AppStorage'))) {
  if (!privacy.includes('NSPrivacyAccessedAPICategoryUserDefaults') || !privacy.includes('CA92.1')) errors.push('privacy manifest must declare CA92.1 for app-only UserDefaults preferences')
}
if (appSources.some(file => /fileSizeKey|\.fileSize\b/.test(fs.readFileSync(file, 'utf8')))) {
  if (!privacy.includes('NSPrivacyAccessedAPICategoryFileTimestamp') || !privacy.includes('C617.1')) errors.push('privacy manifest must declare C617.1 for file metadata inside the app container')
}

const icon = path.join(root, 'ShiLv/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png')
const atlas = path.join(root, 'ShiLv/Resources/Assets.xcassets/KansaiAtlas.imageset/kansai-atlas.png')
for (const asset of [icon, atlas]) if (!fs.existsSync(asset) || fs.statSync(asset).size === 0) errors.push(`missing asset ${asset}`)

const sourceBuildReferences = [...project.matchAll(/\/\* [^*]+ in Sources \*\//g)].length
if (sourceBuildReferences < appSources.length + testSources.length) errors.push('project source build phase count is too small')

if (errors.length) {
  console.error(errors.join('\n'))
  process.exit(1)
}
console.log(`iOS project validation ok: ${appSources.length} app sources, ${testSources.length} test source, 2 targets, privacy metadata present`)
