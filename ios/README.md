# 拾旅原生 iOS

原生 SwiftUI + PhotoKit 版本。它不是 WebView，也不依赖微信小程序。用户授予“所有照片”权限后，App 可以枚举整个已授权照片库，通过照片日期和 GPS 在设备本地发现旅行。

## 环境

- macOS 14 或更新版本
- Xcode 16 或更新版本
- iOS 17.0+
- Apple Developer 账号（真机安装可使用免费个人团队；TestFlight/App Store 需要付费计划）

## 运行

1. 在 Mac 上打开 `ShiLv.xcodeproj`。
2. 选中 `ShiLv` Target → Signing & Capabilities，选择你的 Team。
3. 将 Bundle Identifier `com.example.ShiLv` 改为自己的唯一标识。
4. 连接 iPhone，选择设备并运行。
5. 首次启动点“允许访问照片”，在系统弹窗选择“允许访问所有照片”。

模拟器通常没有足够的真实 GPS 照片，建议用真机验证全库扫描。也可以向模拟器照片库导入带时间和位置的照片。

完整权限、iCloud、删原图与大相册验收见 `REAL_DEVICE_ACCEPTANCE.md`。

## 验证

```bash
./scripts/check.sh
./scripts/verify-on-macos.sh
```

`check.sh` 验证工程引用、资产、plist、Swift 结构和聚类契约；`verify-on-macos.sh` 继续执行模拟器构建、单元测试和无签名 Release 构建。

## 隐私

- 扫描阶段只枚举 PhotoKit 元数据：时间、位置、本机标识、像素尺寸。
- 缩略图只在页面可见时按需读取，iCloud 原图不会在全库扫描时批量下载。
- 按需缩略图会写入可清理缓存；“我 → 清理缩略图缓存”只删除缓存，不删除旅行结构或系统照片。
- 用户确认旅行后，Apple Vision 最多分析 100 个事件、每个事件一张代表图，在本机归纳餐饮、交通、自然、建筑等事件语义；其余事件仍按时间和地点生成克制描述。
- 旅行索引使用完整文件保护保存在 Application Support。
- 当前版本没有开发者服务器、账号、广告追踪或照片上传。确认旅行后可调用联网的 Apple 系统地理编码服务，把有限数量的坐标转换为城市/地点名称；手动纠正地点时也会解析用户输入的地点词。
- 分享偏好仅写入本 App 的 `UserDefaults`，缓存统计只读取 App 容器内文件大小；隐私清单按 Apple Required Reason API 规则声明 `CA92.1` 和 `C617.1`。

## 当前 MVP

已覆盖新版需求中的发现、确认、Day/事件聚类、最多 5 张精选、地点/时间纠正、事件合并与拆分、隐藏与删除、旅行/事件封面、移除照片、地图双向跳转、跨城交通过渡、补一句记忆、往年今日、随机回忆、收藏、JSON 导出和保存/分享。

图标由内置图像生成工具创建，最终提示方向为“深炭黑背景上的朱红折叠路线，连接照片碎片并形成记忆坐标，无文字、无相机图标”。
