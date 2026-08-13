const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

const iosRoot = path.resolve(__dirname, '..')
const projectDir = path.join(iosRoot, 'ShiLv.xcodeproj')
const sourceRoot = path.join(iosRoot, 'ShiLv')
const testsRoot = path.join(iosRoot, 'ShiLvTests')
const id = value => crypto.createHash('sha1').update(value).digest('hex').slice(0, 24).toUpperCase()
const q = value => `"${value.replaceAll('\\', '/')}"`

function filesUnder(root, extension) {
  const result = []
  function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name)
      if (entry.isDirectory()) walk(full)
      else if (!extension || entry.name.endsWith(extension)) result.push(full)
    }
  }
  walk(root)
  return result.sort()
}

const appSources = filesUnder(sourceRoot, '.swift').map(full => ({
  name: path.basename(full),
  path: path.relative(iosRoot, full).replaceAll('\\', '/'),
  fileID: id(`file:${full}`),
  buildID: id(`build:${full}`)
}))
const testSources = filesUnder(testsRoot, '.swift').map(full => ({
  name: path.basename(full),
  path: path.relative(iosRoot, full).replaceAll('\\', '/'),
  fileID: id(`file:${full}`),
  buildID: id(`build:${full}`)
}))
const resources = [
  { name: 'Assets.xcassets', path: 'ShiLv/Resources/Assets.xcassets', type: 'folder.assetcatalog' },
  { name: 'PrivacyInfo.xcprivacy', path: 'ShiLv/Resources/PrivacyInfo.xcprivacy', type: 'text.xml' }
].map(item => ({ ...item, fileID: id(`file:${item.path}`), buildID: id(`build:${item.path}`) }))

const IDs = {
  project: id('project'), mainGroup: id('mainGroup'), appGroup: id('appGroup'), testsGroup: id('testsGroup'), resourcesGroup: id('resourcesGroup'), productsGroup: id('productsGroup'),
  appTarget: id('appTarget'), testsTarget: id('testsTarget'), appProduct: id('appProduct'), testsProduct: id('testsProduct'),
  appSources: id('appSources'), appResources: id('appResources'), appFrameworks: id('appFrameworks'),
  testsSources: id('testsSources'), testsResources: id('testsResources'), testsFrameworks: id('testsFrameworks'),
  targetDependency: id('targetDependency'), containerProxy: id('containerProxy'),
  projectConfigList: id('projectConfigList'), appConfigList: id('appConfigList'), testsConfigList: id('testsConfigList'),
  projectDebug: id('projectDebug'), projectRelease: id('projectRelease'), appDebug: id('appDebug'), appRelease: id('appRelease'), testsDebug: id('testsDebug'), testsRelease: id('testsRelease'),
  infoPlist: id('infoPlist')
}

const buildFiles = [...appSources, ...testSources, ...resources].map(file => `\t\t${file.buildID} /* ${file.name} in ${testSources.includes(file) ? 'Sources' : resources.includes(file) ? 'Resources' : 'Sources'} */ = {isa = PBXBuildFile; fileRef = ${file.fileID} /* ${file.name} */; };`).join('\n')
const fileRefs = [
  ...appSources.map(file => `\t\t${file.fileID} /* ${file.name} */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = ${q(file.path)}; sourceTree = SOURCE_ROOT; };`),
  ...testSources.map(file => `\t\t${file.fileID} /* ${file.name} */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = ${q(file.path)}; sourceTree = SOURCE_ROOT; };`),
  ...resources.map(file => `\t\t${file.fileID} /* ${file.name} */ = {isa = PBXFileReference; lastKnownFileType = ${file.type}; path = ${q(file.path)}; sourceTree = SOURCE_ROOT; };`),
  `\t\t${IDs.infoPlist} /* Info.plist */ = {isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = "ShiLv/Resources/Info.plist"; sourceTree = SOURCE_ROOT; };`,
  `\t\t${IDs.appProduct} /* ShiLv.app */ = {isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = ShiLv.app; sourceTree = BUILT_PRODUCTS_DIR; };`,
  `\t\t${IDs.testsProduct} /* ShiLvTests.xctest */ = {isa = PBXFileReference; explicitFileType = wrapper.cfbundle; includeInIndex = 0; path = ShiLvTests.xctest; sourceTree = BUILT_PRODUCTS_DIR; };`
].join('\n')

const pbx = `// !$*UTF8*$!
{
\tarchiveVersion = 1;
\tclasses = {};
\tobjectVersion = 56;
\tobjects = {

/* Begin PBXBuildFile section */
${buildFiles}
/* End PBXBuildFile section */

/* Begin PBXContainerItemProxy section */
\t\t${IDs.containerProxy} /* PBXContainerItemProxy */ = {isa = PBXContainerItemProxy; containerPortal = ${IDs.project} /* Project object */; proxyType = 1; remoteGlobalIDString = ${IDs.appTarget}; remoteInfo = ShiLv; };
/* End PBXContainerItemProxy section */

/* Begin PBXFileReference section */
${fileRefs}
/* End PBXFileReference section */

/* Begin PBXFrameworksBuildPhase section */
\t\t${IDs.appFrameworks} /* Frameworks */ = {isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0; };
\t\t${IDs.testsFrameworks} /* Frameworks */ = {isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0; };
/* End PBXFrameworksBuildPhase section */

/* Begin PBXGroup section */
\t\t${IDs.mainGroup} = {isa = PBXGroup; children = (${IDs.appGroup} /* ShiLv */, ${IDs.testsGroup} /* ShiLvTests */, ${IDs.productsGroup} /* Products */); sourceTree = "<group>"; };
\t\t${IDs.appGroup} /* ShiLv */ = {isa = PBXGroup; children = (${appSources.map(x => `${x.fileID} /* ${x.name} */`).join(', ')}, ${IDs.resourcesGroup} /* Resources */); name = ShiLv; sourceTree = "<group>"; };
\t\t${IDs.testsGroup} /* ShiLvTests */ = {isa = PBXGroup; children = (${testSources.map(x => `${x.fileID} /* ${x.name} */`).join(', ')}); name = ShiLvTests; sourceTree = "<group>"; };
\t\t${IDs.resourcesGroup} /* Resources */ = {isa = PBXGroup; children = (${resources.map(x => `${x.fileID} /* ${x.name} */`).join(', ')}, ${IDs.infoPlist} /* Info.plist */); name = Resources; sourceTree = "<group>"; };
\t\t${IDs.productsGroup} /* Products */ = {isa = PBXGroup; children = (${IDs.appProduct} /* ShiLv.app */, ${IDs.testsProduct} /* ShiLvTests.xctest */); name = Products; sourceTree = "<group>"; };
/* End PBXGroup section */

/* Begin PBXNativeTarget section */
\t\t${IDs.appTarget} /* ShiLv */ = {isa = PBXNativeTarget; buildConfigurationList = ${IDs.appConfigList}; buildPhases = (${IDs.appSources} /* Sources */, ${IDs.appFrameworks} /* Frameworks */, ${IDs.appResources} /* Resources */); buildRules = (); dependencies = (); name = ShiLv; productName = ShiLv; productReference = ${IDs.appProduct} /* ShiLv.app */; productType = "com.apple.product-type.application"; };
\t\t${IDs.testsTarget} /* ShiLvTests */ = {isa = PBXNativeTarget; buildConfigurationList = ${IDs.testsConfigList}; buildPhases = (${IDs.testsSources} /* Sources */, ${IDs.testsFrameworks} /* Frameworks */, ${IDs.testsResources} /* Resources */); buildRules = (); dependencies = (${IDs.targetDependency} /* PBXTargetDependency */); name = ShiLvTests; productName = ShiLvTests; productReference = ${IDs.testsProduct} /* ShiLvTests.xctest */; productType = "com.apple.product-type.bundle.unit-test"; };
/* End PBXNativeTarget section */

/* Begin PBXProject section */
\t\t${IDs.project} /* Project object */ = {isa = PBXProject; attributes = {BuildIndependentTargetsInParallel = 1; LastSwiftUpdateCheck = 1600; LastUpgradeCheck = 1600; TargetAttributes = {${IDs.appTarget} = {CreatedOnToolsVersion = 16.0; }; ${IDs.testsTarget} = {CreatedOnToolsVersion = 16.0; TestTargetID = ${IDs.appTarget}; }; }; }; buildConfigurationList = ${IDs.projectConfigList}; compatibilityVersion = "Xcode 14.0"; developmentRegion = zh-Hans; hasScannedForEncodings = 0; knownRegions = (Base, "zh-Hans", en); mainGroup = ${IDs.mainGroup}; productRefGroup = ${IDs.productsGroup}; projectDirPath = ""; projectRoot = ""; targets = (${IDs.appTarget} /* ShiLv */, ${IDs.testsTarget} /* ShiLvTests */); };
/* End PBXProject section */

/* Begin PBXResourcesBuildPhase section */
\t\t${IDs.appResources} /* Resources */ = {isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = (${resources.map(x => `${x.buildID} /* ${x.name} in Resources */`).join(', ')}); runOnlyForDeploymentPostprocessing = 0; };
\t\t${IDs.testsResources} /* Resources */ = {isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0; };
/* End PBXResourcesBuildPhase section */

/* Begin PBXSourcesBuildPhase section */
\t\t${IDs.appSources} /* Sources */ = {isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = (${appSources.map(x => `${x.buildID} /* ${x.name} in Sources */`).join(', ')}); runOnlyForDeploymentPostprocessing = 0; };
\t\t${IDs.testsSources} /* Sources */ = {isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = (${testSources.map(x => `${x.buildID} /* ${x.name} in Sources */`).join(', ')}); runOnlyForDeploymentPostprocessing = 0; };
/* End PBXSourcesBuildPhase section */

/* Begin PBXTargetDependency section */
\t\t${IDs.targetDependency} /* PBXTargetDependency */ = {isa = PBXTargetDependency; target = ${IDs.appTarget} /* ShiLv */; targetProxy = ${IDs.containerProxy} /* PBXContainerItemProxy */; };
/* End PBXTargetDependency section */

/* Begin XCBuildConfiguration section */
\t\t${IDs.projectDebug} /* Debug */ = {isa = XCBuildConfiguration; buildSettings = {ALWAYS_SEARCH_USER_PATHS = NO; CLANG_ENABLE_MODULES = YES; COPY_PHASE_STRIP = NO; DEBUG_INFORMATION_FORMAT = dwarf; ENABLE_TESTABILITY = YES; GCC_OPTIMIZATION_LEVEL = 0; ONLY_ACTIVE_ARCH = YES; SDKROOT = iphoneos; SWIFT_ACTIVE_COMPILATION_CONDITIONS = "DEBUG $(inherited)"; SWIFT_OPTIMIZATION_LEVEL = "-Onone"; }; name = Debug; };
\t\t${IDs.projectRelease} /* Release */ = {isa = XCBuildConfiguration; buildSettings = {ALWAYS_SEARCH_USER_PATHS = NO; CLANG_ENABLE_MODULES = YES; COPY_PHASE_STRIP = NO; DEBUG_INFORMATION_FORMAT = "dwarf-with-dsym"; SDKROOT = iphoneos; SWIFT_COMPILATION_MODE = wholemodule; VALIDATE_PRODUCT = YES; }; name = Release; };
\t\t${IDs.appDebug} /* Debug */ = {isa = XCBuildConfiguration; buildSettings = {ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon; ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor; CODE_SIGN_STYLE = Automatic; CURRENT_PROJECT_VERSION = 1; DEVELOPMENT_TEAM = ""; ENABLE_PREVIEWS = YES; GENERATE_INFOPLIST_FILE = NO; INFOPLIST_FILE = ShiLv/Resources/Info.plist; IPHONEOS_DEPLOYMENT_TARGET = 17.0; LD_RUNPATH_SEARCH_PATHS = "$(inherited) @executable_path/Frameworks"; MARKETING_VERSION = 1.0.0; PRODUCT_BUNDLE_IDENTIFIER = com.example.ShiLv; PRODUCT_NAME = "$(TARGET_NAME)"; SWIFT_EMIT_LOC_STRINGS = YES; SWIFT_STRICT_CONCURRENCY = complete; SWIFT_VERSION = 5.0; TARGETED_DEVICE_FAMILY = "1,2"; }; name = Debug; };
\t\t${IDs.appRelease} /* Release */ = {isa = XCBuildConfiguration; buildSettings = {ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon; ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor; CODE_SIGN_STYLE = Automatic; CURRENT_PROJECT_VERSION = 1; DEVELOPMENT_TEAM = ""; GENERATE_INFOPLIST_FILE = NO; INFOPLIST_FILE = ShiLv/Resources/Info.plist; IPHONEOS_DEPLOYMENT_TARGET = 17.0; LD_RUNPATH_SEARCH_PATHS = "$(inherited) @executable_path/Frameworks"; MARKETING_VERSION = 1.0.0; PRODUCT_BUNDLE_IDENTIFIER = com.example.ShiLv; PRODUCT_NAME = "$(TARGET_NAME)"; SWIFT_EMIT_LOC_STRINGS = YES; SWIFT_STRICT_CONCURRENCY = complete; SWIFT_VERSION = 5.0; TARGETED_DEVICE_FAMILY = "1,2"; }; name = Release; };
\t\t${IDs.testsDebug} /* Debug */ = {isa = XCBuildConfiguration; buildSettings = {BUNDLE_LOADER = "$(TEST_HOST)"; CODE_SIGN_STYLE = Automatic; CURRENT_PROJECT_VERSION = 1; GENERATE_INFOPLIST_FILE = YES; IPHONEOS_DEPLOYMENT_TARGET = 17.0; MARKETING_VERSION = 1.0; PRODUCT_BUNDLE_IDENTIFIER = com.example.ShiLvTests; PRODUCT_NAME = "$(TARGET_NAME)"; SWIFT_VERSION = 5.0; TARGETED_DEVICE_FAMILY = "1,2"; TEST_HOST = "$(BUILT_PRODUCTS_DIR)/ShiLv.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/ShiLv"; }; name = Debug; };
\t\t${IDs.testsRelease} /* Release */ = {isa = XCBuildConfiguration; buildSettings = {BUNDLE_LOADER = "$(TEST_HOST)"; CODE_SIGN_STYLE = Automatic; CURRENT_PROJECT_VERSION = 1; GENERATE_INFOPLIST_FILE = YES; IPHONEOS_DEPLOYMENT_TARGET = 17.0; MARKETING_VERSION = 1.0; PRODUCT_BUNDLE_IDENTIFIER = com.example.ShiLvTests; PRODUCT_NAME = "$(TARGET_NAME)"; SWIFT_VERSION = 5.0; TARGETED_DEVICE_FAMILY = "1,2"; TEST_HOST = "$(BUILT_PRODUCTS_DIR)/ShiLv.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/ShiLv"; }; name = Release; };
/* End XCBuildConfiguration section */

/* Begin XCConfigurationList section */
\t\t${IDs.projectConfigList} = {isa = XCConfigurationList; buildConfigurations = (${IDs.projectDebug} /* Debug */, ${IDs.projectRelease} /* Release */); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; };
\t\t${IDs.appConfigList} = {isa = XCConfigurationList; buildConfigurations = (${IDs.appDebug} /* Debug */, ${IDs.appRelease} /* Release */); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; };
\t\t${IDs.testsConfigList} = {isa = XCConfigurationList; buildConfigurations = (${IDs.testsDebug} /* Debug */, ${IDs.testsRelease} /* Release */); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release; };
/* End XCConfigurationList section */
\t};
\trootObject = ${IDs.project} /* Project object */;
}
`

fs.mkdirSync(projectDir, { recursive: true })
fs.writeFileSync(path.join(projectDir, 'project.pbxproj'), pbx)
console.log(`Generated Xcode project with ${appSources.length} app sources, ${testSources.length} test sources, and ${resources.length} resources.`)
