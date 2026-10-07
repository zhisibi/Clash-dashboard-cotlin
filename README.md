# Clash 面板（Kotlin 原生版）

一个用 **Kotlin + Jetpack Compose** 原生实现的 Android 面板，通过 mihomo / Clash 的 `external-controller` API 管理代理内核。界面和功能参考 [Zashboard](https://github.com/Zephyruso/zashboard)，但不是 WebView 封装。

> 本应用 **不包含** mihomo 内核，也不会启动代理服务。请先在代理客户端（或路由器、服务器上的 mihomo）里开启外部控制器。

## 功能

| 页面 | 功能 |
| --- | --- |
| 概览 | 实时上传/下载速度、总流量、活跃连接、内存占用、速度和内存曲线、流量最多的主机、快速切换代理模式 |
| 代理 | 代理组卡片、节点选择、分组/单节点测速、延迟颜色分级、排序、搜索、隐藏不可用节点、显示 GLOBAL 和隐藏代理组、代理提供商（订阅流量、更新、健康检查） |
| 连接 | 活跃/已关闭/全部连接、搜索与正则过滤、来源 IP 筛选、多字段排序、单条/全部断开、连接详情、暂停实时更新 |
| 日志 | 日志级别切换、类型过滤、正则搜索、暂停、清空、复制、导出到“下载”目录 |
| 规则 | 规则列表、单条规则启用/禁用、规则集更新、命中次数 |
| 设置 | 多后端管理（地址、端口、二级路径、密钥、备注）、连接测试、代理模式、TUN、局域网、IPv6、各类端口、重载配置、更新 GEO、清空 DNS/FakeIP 缓存、更新/重启内核、主题、壁纸（透明度/暗化/模糊）、测速参数、卡片布局、保留数量、闪退日志 |

其他：
- 通过 WebSocket 实时订阅 `/traffic`、`/memory`、`/connections`、`/logs`
- 后端拒绝的接口（HTTP 404/405，例如部分客户端只开放了只读 API）会标记为“当前后端不支持”
- 应用闪退时自动记录堆栈，下次启动弹窗提示，可在“设置 > 闪退日志”中复制、分享或保存
- 支持明文 HTTP 后端（局域网 / 本机）

## 使用

1. 在代理客户端或 mihomo 配置中开启外部控制器，例如：
   ```yaml
   external-controller: 127.0.0.1:9090
   secret: "your-secret"
   ```
2. 安装 APK，首次打开时填写后端地址、端口和密钥。
3. 点击“测试连接”，成功后“保存并连接”。

## 环境要求

- Android 8.0 及以上（minSdk 26，targetSdk 35）
- 构建：JDK 17+、Android SDK 35

## 构建

```bash
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/app-release.apk
```

### 签名

Release 签名从项目根目录的 `keystore.properties`（已加入 .gitignore）读取，也可用环境变量 `KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。都没有时使用 debug 签名。

```properties
storeFile=release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

> 升级安装需要使用同一个签名证书，否则需要先卸载旧版。

### 测试

`app/src/test/.../ApiTest.kt` 是针对真实 mihomo 的 API 集成测试，需要本地运行一个 mihomo（默认 `127.0.0.1:19090`，密钥 `test123`）：

```bash
./gradlew testReleaseUnitTest
```

## 项目结构

```
app/src/main/java/net/zash/clashpanel/
├── ClashPanelApp / CrashLog.kt   应用入口与闪退日志
├── MainActivity.kt               底部导航与壁纸
├── MainViewModel.kt              状态、WebSocket、所有操作
├── data/
│   ├── ClashApi.kt               REST + WebSocket 客户端（OkHttp）
│   ├── Models.kt                 数据模型（kotlinx.serialization）
│   └── Prefs.kt                  本地设置
└── ui/                           各页面（Compose / Material 3）
```

## 技术栈

Kotlin 2 · Jetpack Compose（Material 3）· OkHttp · kotlinx.serialization · Coroutines · Coil

## 更新记录

- **1.1.2** 修复启动闪退；编辑后端布局调整；新增闪退日志
- **1.1.1** 标记后端不支持的接口；GEO 更新增加备用接口
- **1.1.0** 概览独立为底栏第一页；新增壁纸；修复“显示 GLOBAL”开关
- **1.0.0** 首个原生版本
