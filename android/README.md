# 拾旅原生 Android

原生 Kotlin + Jetpack Compose 版本。它不是 WebView，也不依赖微信小程序或 iOS。用户授予“照片”权限后，App 通过 MediaStore 枚举整个已授权照片库，用照片日期和 GPS 在设备本地发现旅行。与 iOS 版（SwiftUI + PhotoKit）功能完全对齐。

## 环境

- Android Studio 或更新的版本
- JDK 17+（本工程使用 Temurin 21 验证）
- compileSdk 35，minSdk 26，targetSdk 35
- Android Gradle Plugin 8.5.2，Kotlin 2.0.20，Compose BOM 2024.09.03

## 运行

1. 用 Android Studio 打开本目录 `android/`（首次会生成 Gradle Wrapper）。
2. 等待 Gradle 同步完成。
3. 连接真机（建议真机，模拟器通常缺少带 GPS 的真实照片），选择设备并运行 `app`。
4. 首次启动点“开始发现我的旅行”，在系统弹窗允许“照片”权限。

地图页当前使用 Canvas 示意图（无需 Google Maps API Key），离线可用；如需真实地图可替换为 Google Maps / 高德 SDK（见“生产接入边界”）。

## 验证

```powershell
node scripts/validate-project.js
```

该脚本校验：已声明 `READ_MEDIA_IMAGES` 权限与隐私文案、不引用外部网络/密钥、必需资源齐全、包名一致、关键模块与测试齐全。

单元测试（对齐 iOS `TripDetectorTests`）位于 `app/src/test`，在具备 Android SDK 的环境执行：

```powershell
gradle :app:testDebugUnitTest
```

## 目录结构

```
android/
  ShiLv/                 # 工程根
    app/
      src/main/java/com/example/shilv/
        data/Models.kt           # 数据模型与序列化（对应 iOS Models.swift）
        domain/TripDetector.kt   # 旅行检测算法（对应 iOS TripDetector.swift）
        service/                 # PhotoLibrary(MediaStore) / TripStore / MemoryAnalysis / PlaceName
        ui/                      # AppModel + Compose 屏幕（发现/时间线/旅行/Day/事件/地图/回忆卡/设置）
      src/main/res/              # 图标、配色、strings、备份规则
      src/test/                  # 单元测试
    scripts/validate-project.js  # 静态校验
```

## 生产接入边界

- `service/PhotoLibraryService.kt`：读取 MediaStore 元数据（时间/位置/本机 URI/尺寸），缩略图按需加载并写入可清理缓存。
- `service/TripStore.kt`：旅行索引写入 App 内部 `files/shilv-index/travel-index-v1.json`，保留用户编辑。
- 地图为 Canvas 示意图，逆地理编码需接入合规地图服务；视觉识别当前为基础颜色分类，可替换为 ML Kit Image Labeling。
- 当前版本没有开发者服务器、账号、广告追踪或照片上传。确认旅行或纠正地点时，有限坐标会交给系统 Geocoder 解析。

## 隐私

- 扫描阶段只读取 MediaStore 元数据：时间、位置、本机 URI、像素尺寸。
- 缩略图只在页面可见时按需读取，进入可清理缓存；“我 → 清理缩略图缓存”只删除缓存，不删除旅行结构或系统照片。
- 旅行索引使用 App 内部存储保存，卸载 App 或“删除本机旅行索引”即可清除。
- 不修改或删除系统照片，不上传原图、位置或记忆。