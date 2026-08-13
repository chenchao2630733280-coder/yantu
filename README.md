# 拾旅 · 微信小程序

> 原生 iOS 版本已新增到 [`ios/`](ios/README.md)。iOS 版使用 SwiftUI + PhotoKit，可在用户授权后扫描整个已授权照片库，不受微信小程序单次 9 张限制。

一个以“零整理成本、自动还原旅程、情绪化回忆”为核心的原生微信小程序。当前版本完整实现了发现旅行、确认旅程、Day 时间线、事件详情、事实纠错、补写一句记忆、地图联动、回忆卡片、时间线和隐私设置。

## 运行

1. 打开微信开发者工具，选择“导入项目”。
2. 项目目录选择本文件夹，AppID 可使用测试号或替换 `project.config.json` 中的 `touristappid`。
3. 编译后从“回忆”页进入“日本关西之旅”。地图页需开发者工具允许原生地图组件加载。

项目不依赖 npm 包。照片选择使用 `wx.chooseMedia`，演示数据保存在 `wx.storage`，不会上传照片。

## 验证

```powershell
npm run check
```

若系统没有全局 Node，可运行：

```powershell
& 'C:\Users\26307\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe' scripts\validate.js
& 'C:\Users\26307\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe' scripts\test.js
& 'C:\Users\26307\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe' scripts\build.js
```

## 生产接入边界

- `services/photo-adapter.js`：替换为本地 EXIF 时间/GPS 聚类与照片内容分析。
- `services/store.js`：可换为云开发数据库；应保留本机优先和明确授权策略。
- 地图当前使用微信原生地图和演示坐标；逆地理编码需接入合规地图服务。
- 云端 AI、分享海报保存和订阅提醒需要真实 AppID、服务端凭证及用户授权，当前不伪造这些能力。

原创旅行影像由内置图像生成工具生成；移动端压缩版打包在 `assets/kansai-atlas.png`，原稿 `assets/kansai-atlas-original.png` 仅保留在工程中且不参与主包构建，无外部图片域名依赖。最终生成提示词采用 4×2 关西旅行摄影图集，覆盖京都、伏见稻荷、祇园、清水寺、大阪、奈良、神户和鸭川；生成方式为内置图像工具。
