# 寻源 · iOS 快捷指令

保留安卓 APP，另提供原生 iPhone 快捷指令。无服务器，不需要 Scriptable 等额外应用。

## 下载与导入

[打开快捷指令发布页面](https://github.com/hoylturtle/xunyuan-android/releases/tag/ios-v0.1.0)，用 iPhone Safari 下载 `Xunyuan.shortcut`，在「文件」中打开，按系统提示添加到「快捷指令」。可以重命名为「寻源」并添加到主屏幕。

发布文件由 macOS 的 Apple Shortcuts `shortcuts sign --mode anyone` 签名。`Xunyuan.unsigned.shortcut` 为开发用原始文件，不应作为可直接安装的成品分发。没有生成 iCloud 分享链接。

## 使用

1. 每次运行先选择成人搜索状态，默认关闭；开启需要确认已满 18 岁，只影响本次运行。
2. 输入关键词，选择全部网盘、百度、夸克、阿里，或磁力。
3. 选择第 1、2 或 3 页。
4. 从结果列表选择资源，再选择打开链接、复制链接与提取码，或仅复制链接。

网盘通过 PanSearch 的公开分享索引返回结果，磁力通过 Nyaa 查询；开启成人搜索时，磁力改用 Sukebei。成人专用来源未以成人关键词进行联网检索验证。默认关闭时使用关键词过滤相关输入及结果，可能漏判或误判。

网盘打开使用 HTTPS 分享链接。**是否唤起对应 APP 由 iOS Universal Links、网盘 APP 及用户的打开偏好决定**，不能像安卓一样强制指定应用包。磁链需要已安装支持磁链的客户端；没有时请使用复制功能。提取码能从资源行识别时，打开前会复制。

快捷指令首次访问站点、打开链接或使用剪贴板时可能要求授权。网站要求验证码、网络失败时，系统可能直接显示动作错误；更换网络或稍后重试。没有搜索历史、收藏或批量并发搜索；不直接搜索私人网盘文件。

## 验证边界

生成器对动作、变量、UTF-16 占位符及控制流进行结构检查，并以真实抓取样本验证网盘和磁力正则。检查结果见构建产物 `test-report.json`。

Apple 签名成功只说明文件通过签名流程，**不等于已在 iPhone 导入并执行成功**。本项目尚未进行 iPhone 真机测试，首次使用请用普通关键词测试。

## 开发与签名

```sh
python3 ios/build_shortcut.py
python3 ios/test_shortcut.py
# 在 macOS 上执行：
shortcuts sign --mode anyone --input ios/dist/Xunyuan.unsigned.shortcut --output ios/dist/Xunyuan.shortcut
```

`.github/workflows/ios-shortcut.yml` 负责在 macOS runner 上生成、签名及发布。iOS 发布使用独立的 `ios-v*` 标签，并设置 `--latest=false`，避免影响安卓应用的 `releases/latest/download/update.json`。

参考：[Apple 快捷指令命令行与签名说明](https://support.apple.com/guide/shortcuts-mac/run-shortcuts-from-the-command-line-apd455c82f02/mac)。
