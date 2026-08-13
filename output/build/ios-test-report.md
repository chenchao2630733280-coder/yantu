# 原生 iOS 测试报告

## 当前环境已执行（Windows）

- `python ios/scripts/validate-swift-structure.py`：通过；22 个 Swift 文件括号/字符串结构平衡，框架导入和强制操作检查通过。
- `node ios/scripts/validate-project.js`：通过；21 个 App 源文件、1 个测试文件、2 个 Target、Scheme ID、Info.plist、隐私清单和资源引用完整。
- `python ios/scripts/validate-assets.py`：通过；AppIcon 精确 1024×1024，旅行图集、资产 JSON 和 plist 可解析。
- `node ios/scripts/test-detector-contract.js`：通过；验证远途多日旅行识别、返家边界、近郊和截图排除，以及旅程时间窗内无 GPS 照片回填。
- `node ios/scripts/validate-requirements.js`：通过；20 个 MVP 项和 15 个跨领域/运行时行为均有实现契约。
- 新增 XCTest：截图排除、每事件最多 5 张精选、路线/城市统计、顶层字段重算、照片引用匹配、扫描后保留地点/精选/移除/收藏、合并与拆分跨扫描不复现、原图不再检测时保留已确认旅行；已写入工程，待 macOS 执行。
- 敏感模式扫描：未发现 `try!`、`as!`、`URLSession`、AppSecret、TODO 或 FIXME。
- `pnpm run check`：通过；原微信小程序 lint、测试、构建预检通过，11 条 ignore 规则合法，估算主包 0.49 MB。
- 隐私清单：通过；`@AppStorage` 对应 `UserDefaults/CA92.1`，App 容器文件大小统计对应 `FileTimestamp/C617.1`。
- 运行时加固：授权成功后退出等待状态并启动扫描；扫描/PhotoKit 图片请求支持取消；照片库变化去抖；退后台取消、回前台恢复；删除索引先等待旧扫描结束；清缓存使用世代号阻止旧请求写回；Vision 深度理解最多 100 个事件。

## 尚需 macOS 执行

当前 Windows 环境没有 Swift、Xcode、iOS SDK 或模拟器，不能执行官方 Swift 编译、XCTest、签名和真机 PhotoKit 验收。`ios/scripts/verify-on-macos.sh` 已提供完整命令。此项是发布前阻塞，而不是已通过检查。
