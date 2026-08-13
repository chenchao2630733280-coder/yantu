# App Store 发布清单

## 账号与签名

1. 加入 Apple Developer Program。
2. 在 Xcode 的 Signing & Capabilities 中选择 Team。
3. 把 `com.example.ShiLv` 改为团队唯一 Bundle ID。
4. 在 App Store Connect 创建同 Bundle ID 的 App，SKU 可使用 `shilv-ios-001`。

## 隐私与审核说明

- App 使用 PhotoKit 读取用户授权范围内的照片元数据和按需缩略图。
- “所有照片”授权用于自动发现完整旅行；“部分照片”仍可使用，但结果只覆盖被允许的照片。
- 扫描阶段不批量下载 iCloud 原图，只读取 `creationDate`、`location`、本机标识和尺寸。
- 用户确认旅行后，Apple Vision 在本机分析每个事件的一张代表图。
- 确认旅行时，Apple 系统地理编码把有限坐标转换为地点名称；手动纠正地点时会解析用户输入的地点词。没有开发者服务器、账号、广告或跟踪。
- `PrivacyInfo.xcprivacy` 已声明 App 内分享偏好使用 `UserDefaults/CA92.1`，以及缓存统计读取 App 容器文件元数据使用 `FileTimestamp/C617.1`。
- App Store Connect 隐私问卷应按最终发行行为填写。当前代码不把照片、位置、标识或用户内容发送给开发者。

审核备注建议：

> 拾旅需要照片“读取”权限，以便在设备本地根据拍摄时间和照片位置发现旅行。初次进入后点击“允许访问照片”，建议选择“允许访问所有照片”。App 不修改系统照片。若测试账号/设备照片库没有带 GPS 的多日旅行照片，首页可能显示空状态；可导入一组跨两天、距离常驻地 80 公里以上的带位置照片进行验证。

## 归档与上传

1. 执行 `./scripts/verify-on-macos.sh`。
2. 在 Xcode 选择 `Any iOS Device (arm64)`，执行 Product → Archive。
3. Organizer 中运行 Validate App。
4. Distribute App → App Store Connect → Upload。
5. 在 App Store Connect 补充截图、描述、支持网址、隐私政策网址、年龄分级和审核备注。
6. 选择构建版本，提交审核。

## 发布前真机验收

按 `REAL_DEVICE_ACCEPTANCE.md` 逐项记录机型、系统版本、照片数、扫描耗时和结果。

- 首次授权：所有照片、部分照片、拒绝三条路径。
- 5,000/20,000/50,000 张照片库的扫描耗时、内存和中断恢复。
- iCloud 优化存储下，缩略图加载与离线反馈。
- 没有 GPS、只有近郊照片、跨时区旅行、同日往返等边界。
- 确认旅行后 Vision 分类、地理编码失败降级和内容修改持久化。
- 删除本机索引不会删除系统照片。
