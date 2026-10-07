# Clash 面板（Kotlin 原生版）

一个用 **Kotlin + Jetpack Compose** 编写的原生安卓面板，用来管理 **mihomo / Clash** 的 `external-controller` API。界面和功能参照 Zashboard，但不是 WebView 封装。

> App 本身**不包含 mihomo 内核**，也不会启动代理服务，需要配合已运行的 mihomo / Clash（例如 FlClash、Clash Meta for Android、路由器上的 OpenClash 等）使用。

## 功能

| 页面 | 功能 |
| --- | --- |
| 概览 | 实时上传/下载速度、总流量、活跃连接、内存占用、速度与内存曲线、流量最多的主机、快速切换代理模式 |
| 代理 | 代理组卡片、节点选择、分组/单节点测速、延迟颜色分级、排序、搜索、隐藏不可用节点、显示 GLOBAL 与隐藏组、代理提供商（订阅流量、更新、健康检查） |
| 连接 | 活跃/已关闭连接、搜索与正则过滤、来源 IP 筛选、多字段排序、单条/全部断开、连接详情、暂停刷新 |
| 日志 | 日志级别切换、类型过滤、正则搜索、暂停、清空、复制、导出到“下载”目录 |
| 规则 | 规则列表、单条规则启用/禁用、规则集更新、命中次数 |
| 设置 | 多后端管理与连接测试、代理模式、TUN、局域网、IPv6、各类端口、重载配置、更新 GEO、清空 DNS/FakeIP 缓存、更新/重启内核、主题、自定义壁纸（透明度/暗化/模糊）、测速参数、闪退日志 |

- 实时数据通过 WebSocket 获取：`/traffic`、`/memory`、`/connections`、`/logs`
- 后端返回 404/405 的接口（部分客户端限制了外部控制）会被标记为“当前后端不支持”
- 闪退时自动保存错误日志，可在 **设置 → 闪退日志** 中复制、分享或保存

## 使用

1. 在代理客户端里开启外部控制器（external-controller），记下地址、端口和密钥（secret）
2. 安装 APK，首次打开填写后端地址，例如 `127.0.0.1:9090`
3. 点“测试连接”，成功后保存即可

## 环境要求

- Android 8.0 及以上（minSdk 26，targetSdk 35）
- 构建：JDK 17+、Android SDK 35

## 构建

```bash
./gradlew assembleRelease
```

输出：`app/build/outputs/apk/release/app-release.apk`

### 签名

Release 签名从项目根目录的 `keystore.properties` 读取（已加入 `.gitignore`，不会提交）：

```properties
storeFile=release.keystore
storePassword=你的密码
keyAlias=你的别名
keyPassword=你的密码
```

也可以用环境变量 `KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。都没有时使用 debug 签名。

> 升级安装必须使用同一个签名，否则无法覆盖安装。

### 自动发布 Release

仓库已配置 GitHub Actions（`.github/workflows/release.yml`）：推送 `v*` 标签，或在 Actions 页手动运行 **Release**，即自动构建并发布 APK。

```bash
git tag v1.1.2 && git push origin v1.1.2
```

需在仓库 **Settings → Secrets and variables → Actions** 添加：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | 签名文件 base64（`base64 -w0 release.keystore`） |
| `KEYSTORE_PASSWORD` | 签名库密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

未设置时使用 debug 签名，无法覆盖安装正式版。

## 项目结构

```
app/src/main/java/net/zash/clashpanel/
├── MainActivity.kt        # 入口与底部导航
├── MainViewModel.kt       # 状态与业务逻辑
├── CrashLog.kt            # 闪退日志捕获
├── data/
│   ├── ClashApi.kt        # REST + WebSocket 客户端
│   ├── Models.kt          # 数据模型
│   └── Prefs.kt           # 本地设置
└── ui/                    # 各页面（概览/代理/连接/日志/规则/设置）
```

## 技术栈

Kotlin 2 · Jetpack Compose (Material 3) · OkHttp · kotlinx.serialization · Coroutines · Coil

## 致谢

- [mihomo](https://github.com/MetaCubeX/mihomo)
- [Zashboard](https://github.com/Zephyruso/zashboard)
