# project.config.json 打包排除配置修复

- 复现：微信开发者工具 2.01.2510290 报 `packOptions.ignore[n] 字段需为 object`。
- 根因：`ignore` 使用了旧式字符串数组，而当前工具要求每项为带 `type` 和 `value` 的对象。
- 修复：目录使用 `{ "type": "folder", "value": "..." }`；文件使用 `{ "type": "file", "value": "..." }`。
- 同步修改：`scripts/build.js` 现在校验并解析当前对象格式。
- 验证：JSON 解析、源码检查和构建预检均需通过；微信开发者工具重新编译后不应再出现该配置错误。
