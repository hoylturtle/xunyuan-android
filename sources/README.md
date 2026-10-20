# 寻源官方来源配置

当前版本2，2026-10-20。共37个来源条目：默认启用9个普通来源，27个历史问题来源默认停用，扩展来源仍受原隐藏开关控制。已有用户升级时同步停用历史问题项一次，保留其他按ID保存的选择。

本次实测旧普通源33个，6个成功得到解析结果，其余有410、403、503、页面改版及环境代理限制。不能把云端403直接视为手机永久不可用。新增 Bitsearch（磁链与独立分页）、爱盼（影视详情的网盘／磁链）、PanSou 公共聚合（多个网盘及已知提取码）。排除了随机关键词仍返回无关资源列表的候选接口。首次聚合查询可能超时，个别词会被上游拒绝。

复核证据：research/source-refresh-audit.json、candidate-source-audit.json、source-cross-check.json、extra-source-audit.json、live-source-refresh.json。9个默认源均已由实际Java搜索引擎取得可解析结果；每次查询、数量和时间详见最后一份报告。捕获的新增解析器响应在 research/probes/，不代表分享长期有效或可下载。

## 客户端更新

安卓0.5.0 / Windows0.3.0起，启动时每天最多自动检查一次，来源页支持手动检查与回退。安卓导入的自定义来源不被官方更新覆盖；可主动切换回官方来源。断网或配置有误时沿用最近有效配置或内置配置，回退会关闭自动更新。不会因更新开启扩展模式。

固定更新地址：

- https://raw.githubusercontent.com/hoylturtle/xunyuan-android/main/sources/catalog.json
- https://github.com/hoylturtle/xunyuan-android/releases/download/source-catalog/sources.json

二者依赖GitHub可达性，失败会保留本机配置。更新不会自行发现新站，也不是每次自动下载安装应用；新增不支持的解析格式仍需应用升级。

## 维护与发布

1. 在正常查询、另一查询、随机无结果查询及分页（支持时）下验证来源。HTTP200不能当作搜索成功，需验证标题相关性和实际资源链接。
2. 修改 catalog.json 并严格增加 version；schema为配置格式版本，engine为所需解析器版本（当前2）。只使用内置解析器，不允许远程脚本。
3. 同步复制至 android/app/src/main/assets/sources.json。运行 python android/test.py 和桌面构建检查。新解析器须包含捕获的真实响应回归测试。
4. 提交后CI验证两个文件一致并发布 source-catalog 附件，保持 --latest=false，不干扰安卓应用更新端点。
5. 每日健康检测只生成 Actions 附件 source-health-report，不因一次超时、验证或地域限制自动禁用来源。

schema变更、engine过高、非法来源、重复ID、非法编码、空清单、超过512KB或较低version均不会替换现用配置。同版本也不会悄悄替换地址。

调研参考：[PanSou](https://github.com/fish2018/pansou) 的公开插件和接口文档；客户端解析与更新逻辑为本项目Java实现。
