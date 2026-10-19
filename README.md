# 寻源

Android 手机直连的磁力与公开网盘分享搜索工具。无需自建中转服务器，无账号、广告 SDK 或统计 SDK。

## 下载与使用

[下载发布版本](https://github.com/hoylturtle/xunyuan-android/releases/latest) · Android 8.0 及以上。

- 聚合磁力、百度网盘、夸克、阿里云盘等来源，支持分类、分页和链接去重。
- 点击网盘结果优先调用对应网盘应用，未安装或应用无法处理链接时使用网页。提取码自动复制。
- 磁力结果支持复制、解析详情页磁链及调用外部下载器。
- 来源逐个开关，展示查询失败原因；默认启用 6 个经过云端检索验证的普通来源。
- 本机收藏与历史；收藏可导入、导出备份。
- 扩展模式默认关闭：连续点击顶部「寻源」名称6次，确认已满18岁后启用；再连点6次关闭。相邻点击间隔最多1.5秒。关闭时取消查询、清空当前结果，并隐藏识别出的成人收藏、历史与扩展来源。每次启动恢复普通模式，分类可能漏判或误判。

## 0.3.1 界面更新

隐藏式扩展模式入口、图标导航、快捷搜索、关键词清空、清晰的结果卡片、折叠备用来源、分组设置，以及搜索结果返回时的滚动位置保留。

iOS 提供独立的「寻源」与「寻源 Plus」捷径，详见 [iOS 使用说明](ios/README.md)。

## Windows 单文件便携版

[下载 Windows EXE](https://github.com/hoylturtle/xunyuan-android/releases/download/windows-v0.1.2/Xunyuan-Windows-0.1.2.exe) · Windows 10/11 x64，单文件双击运行，无需安装 Java。

支持磁力及百度、夸克、迅雷、115、阿里、其他网盘结果筛选，提取码显示和「只看有提取码」筛选。来源未提供提取码时明确标注；迅雷、115 的搜索结果取决于索引是否收录。详见 [Windows 说明](desktop/README.md)。

## 在线更新

「设置 → 检查应用更新」读取 GitHub Releases 最新版本的 `update.json`。启动时也会每天自动检查一次，可关闭自动检查。不会静默安装。

更新流程：发现更高的 `versionCode` → 用户确认下载 → HTTPS 下载 → 校验文件大小和 SHA-256 → 校验应用包名、版本及签名与已安装应用一致 → 交给安卓系统安装。第一次安装更新时，系统可能要求允许本应用安装未知来源应用。更新文件只通过临时只读 URI 授权交给安装器。

更新地址：`https://github.com/hoylturtle/xunyuan-android/releases/latest/download/update.json`。需要能访问 GitHub；无网络或访问失败时可稍后重试，或手动下载安装包。

## 当前验证范围

- 204 项解析、URL、应用路由及更新元数据检查通过。
- 真实页面样本：7 个来源、159 条解析结果；实时检索验证了 6 个默认来源、网盘下一页，以及 BT 详情页取磁链。
- APK 编译、DEX、v2/v3 签名与对齐检查通过。
- Android 11 模拟器已完成安装、搜索、收藏、来源与设置页检查，并取得真实截图；查询 ubuntu 返回48条结果。实体手机、外部网盘 APP 跳转及更新安装流程仍需验证。
- 网盘结果来自公开分享索引，不搜索用户私人文件。未逐个检查分享是否过期、内容是否准确或下载权限是否可用。
- 配置共 34 个来源；多数旧地址已不可用，默认停用。成人专用来源未通过成人关键词检索验证。

## 构建与测试

工程位于 `android/`。使用 JDK 17+、Android SDK platform 35 和 build-tools 35.0.0：

```sh
export JAVA_HOME=/path/to/jdk
export ANDROID_SDK_ROOT=/path/to/android-sdk
python3 android/test.py
python3 android/build_apk.py
python3 scripts/make_update.py
```

产物：`android/build/Xunyuan-0.3.0.apk` 和 `android/build/update.json`。

也可在 Android Studio 打开 `android/`，使用 Gradle 8.9+。Gradle 默认的 debug 签名与已发布 APK 签名不同；发布更新必须使用同一签名。

为兼容此前个人测试版本，内部应用 ID 和签名保持不变，安装名和界面名称均为「寻源」。`android/signing/` 为本地签名存放位置，已排除在 Git 之外。新环境若没有签名会生成开发签名，这种安装包不能覆盖正式发布的签名版本。不要把私钥提交到源码仓库。

## GitHub Actions 发布

1. 在仓库 Actions Secrets 中配置 `ANDROID_SIGNING_KEY_B64`，内容为原签名文件的 Base64，必须沿用同一私钥。
2. 在 `AndroidManifest.xml` 与 `app/build.gradle` 中同步递增版本号和版本代码。
3. 推送代码，再推送与版本名一致的 `v版本号` 标签。例如：`v0.2.1`。
4. 工作流运行回归检查、构建签名 APK，并生成含下载地址、大小及 SHA-256 的 `update.json`，一起发布到 Releases。

更新描述可在 Release 中编辑。应用内显示的更新描述取自 `update.json` 的 `notes` 字段，可修改 `scripts/make_update.py` 的默认值或发布时传入 `--notes`。

工作流不把签名私钥作为产物上传，PR 构建不读取签名 Secret。

没有签名 Secret 时，普通分支仍可运行测试并生成临时开发签名的 CI 构建，不能用它覆盖已发布版本。标签发布会明确失败，避免发布一个签名不兼容的更新。首个发布版本可以由维护者在本地使用原签名构建后上传。

也可以沿用本地签名构建，把新的 APK、`update.json` 和发布说明放进 `release-bootstrap/` 后推送。发布工作流会核对 APK 的大小、SHA-256 及仓库下载地址，再上传到相应版本的 Release。这个方式不需要把私钥交给 GitHub。首个 0.2.0 版本通过此流程发布。

## 项目结构

- `MainActivity.java`：界面、搜索和收藏状态。
- `SearchEngine.java` / `Source.java` / `Result.java`：直连、解析、分页、去重及分类。
- `CloudApps.java`：网盘 APP 路由。
- `AppUpdates.java` / `UpdateInfo.java` / `UpdateProvider.java`：更新检查、下载校验及系统安装。
- `android/app/src/main/assets/sources.json`：来源配置。
- `android/tests/`、`research/probes/`：回归测试及实际页面样本。
- `.github/workflows/android.yml`：构建和发布。

## 来源与开源依赖

本项目为独立 Java 实现。站点和分页信息参考旧 BTSOU 规则版本 `20260818`，原规则地址为 `http://www.pc936.com/u/UpData/BTSou/ResPool.txt`；没有采用其屏蔽词和广告词表，也不打包原程序。

调研参考：[TorrentSearch](https://github.com/prajwalch/TorrentSearch)、[PanSou](https://github.com/fish2018/pansou)、[qBittorrent 搜索插件](https://github.com/qbittorrent/search-plugins/wiki/How-to-write-a-search-plugin)。未打包这些项目的实现代码。

HTML 解析依赖 [jsoup 1.18.3](https://jsoup.org/)，MIT 许可见 `android/licenses/`。桌面测试使用 JSON-java，不打包进 APK。二进制依赖的 SHA-256 见 `android/SHA256SUMS.dependencies`。

联网用于搜索和更新；安装权限用于确认后的应用升级。历史、收藏、开关只保存在本机；搜索关键词直接发给启用的第三方来源。卸载前请导出收藏备份。
