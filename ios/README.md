# 寻源 · iOS 快捷指令双版本

安卓 APP 继续保留，iPhone 另提供两个独立的原生快捷指令。无需自建服务器，不需要 Scriptable 等额外应用。

| 名称与文件 | 搜索范围 |
|---|---|
| **寻源.shortcut** | 普通版，不含成人来源或开启入口，过滤已识别的成人关键词和结果。 |
| **寻源 Plus.shortcut** | 扩展版，可搜索成人来源，运行前确认已满18岁；也可查询公开网盘索引。 |

两份文件独立导入。普通版依赖关键词过滤，不能保证识别所有未标注的成人内容；Plus 不启用这类过滤。

## 导入与使用

[打开双版本发布页](https://github.com/hoylturtle/xunyuan-android/releases/tag/ios-v0.2.0)。在 iPhone Safari 下载所需文件，在「文件」中打开，按提示添加到「快捷指令」，也可添加到主屏幕。

1. 输入关键词。
2. 选择全部网盘、百度、夸克、阿里，或磁力来源。
3. 选择第1、2或3页。
4. 在结果列表选择资源，打开链接或复制链接／提取码。

普通版磁力来源为 Nyaa，Plus 磁力来源为 Sukebei。网盘通过 PanSearch 的公开分享索引获取链接，不搜索私人网盘文件。Sukebei 尚未通过成人关键词的联网可用性测试。

网盘使用 HTTPS 分享链接打开，能否唤起 APP 由 iOS Universal Links、网盘应用和用户偏好决定，不能像安卓一样强制指定应用。磁链需要支持它的客户端，没有时请复制使用。识别到提取码时，打开前会复制。

首次访问来源、打开链接或使用剪贴板时，iOS 可能要求授权。遇到验证码或网络失败时，可能直接显示系统动作错误；请更换网络或稍后重试。此版本没有搜索历史、收藏或并发聚合功能。

## 签名与验证

发布前通过 RoutineHub HubSign 签名，并用 shortcut-sign 校验签名、提取工作流，逐项核对普通版168个动作、Plus版155个动作与源文件一致。只向签名服务发送公开工作流，不发送用户关键词、Apple ID 或网盘账号。

结构检查覆盖变量、UTF-16占位符、控制流、版本分离，以及真实页面样本解析。**尚未在 iPhone 真机导入并执行**，签名校验不等于真机功能验证。首次使用请用普通关键词测试。

## 开发

```sh
python3 ios/build_shortcut.py
python3 ios/test_shortcut.py
python3 ios/sign_via_hubsign.py
python3 ios/verify_signed.py
```

有已登录 iCloud 的 Mac 时，也可使用官方签名：

```sh
shortcuts sign --mode anyone --input ios/dist/Xunyuan.unsigned.shortcut --output ios/prebuilt/寻源.shortcut
shortcuts sign --mode anyone --input ios/dist/Xunyuan-Plus.unsigned.shortcut --output 'ios/prebuilt/寻源 Plus.shortcut'
```

GitHub 的 macOS runner 未登录 iCloud，不能直接使用官方签名。本次采用 [Cherri 的 HubSign 流程](https://github.com/electrikmilk/cherri/blob/main/signing.go)，再用 [shortcut-sign](https://github.com/0xilis/shortcut-sign) 验证签名及动作一致性。修改动作后必须重新签名，否则发布检查会拒绝上传。

iOS 使用独立 `ios-v*` 标签和 `--latest=false`，不影响安卓应用的在线更新地址。未生成 iCloud 分享链接；`*.unsigned.shortcut` 仅供开发，不是可安装成品。

[Apple 的命令行与签名说明](https://support.apple.com/guide/shortcuts-mac/run-shortcuts-from-the-command-line-apd455c82f02/mac)。
