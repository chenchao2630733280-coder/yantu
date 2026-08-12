# 后端实施报告

当前已批准范围为离线、本机优先 MVP，没有远程 API、账号、RBAC 或共享数据，因此没有为“架构完整”虚构后端。`services/photo-adapter.js` 是生产接入边界：目前真实调用 `wx.chooseMedia`，检测函数返回确定性本地演示结果。真实 EXIF/GPS/AI 是明确外部阻塞项。
