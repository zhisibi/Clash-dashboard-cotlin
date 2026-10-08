# 咪咪面板（Android 版）

[English](README_en.md) · 简体中文

用 **Kotlin + Jetpack Compose** 编写的原生安卓管理面板，适用于与 mihomo 兼容的代理内核所提供的 `external-controller` RESTful API。界面与功能和 HarmonyOS 版咪咪面板 1.2.0 保持一致（毛玻璃、沉浸光感、主题色、左右滑动切页、下拉刷新、中英文界面）。

> App 本身**不包含代理内核**，也不会启动代理服务或 VPN，需要配合已在运行的后端使用（例如路由器或局域网电脑上的内核，或手机上的代理客户端开放的外部控制器）。

| 项目 | 值 |
| --- | --- |
| 应用名 | 咪咪面板（英文 Mimi Panel） |
| 包名 | `net.zash.clashpanel`（沿用旧版包名与签名，可直接覆盖升级，后端与设置保留） |
| 版本 | 1.2.0（versionCode 1020000） |
| 系统要求 | Android 8.0 及以上（minSdk 26，targetSdk 35）；实时毛玻璃模糊需要 Android 12+ |
| 权限 | 只有 `android.permission.INTERNET` |
| 界面语言 | 简体中文、English（设置 → 语言，可跟随系统） |

## 更新日志

### 1.2.0
- **改名为咪咪面板（Mimi Panel）**：界面、字符串、文档中去掉第三方品牌字样；内部 API 客户端改名为 `CoreApi`。包名 `net.zash.clashpanel` 与签名不变，可以直接覆盖安装 1.1.x，已保存的后端与设置都会保留
- **新图标**：与 HarmonyOS 版相同的原创扁平“小猫咪”头像（蓝紫渐变背景），安卓自适应图标（前景/背景分层，主体在安全区内），并提供 Android 13 主题图标（单色）层
- **主题色**：设置 → 面板 → 外观 → 主题色，7 种预设：星河蓝（默认）、橘黄黄、猫咪蓝、华为红、优雅紫、哔哩粉、小草绿；全局生效、即时切换（按钮、分段选项卡、开关、滑块、单选框、输入光标、流量/内存曲线、选中节点描边、订阅进度条、底栏选中项），下方有“颜色预览”；与浅色/深色/跟随系统叠加，深色模式自动使用稍亮的色调；配色与 HarmonyOS 版一致
- **毛玻璃效果**（设置 → 面板 → 毛玻璃效果）：材质 自定义 / 薄 / 常规 / 厚，模糊强度、卡片不透明度、“列表项也模糊”；作用于卡片、各页顶栏、悬浮底栏；有壁纸时透出壁纸，无壁纸时自动铺一层柔和的主题色渐变背景
  - Android 12+ 使用 `RenderEffect` 实时模糊：背景（壁纸/渐变）和页面内容分别录制到 `GraphicsLayer`，卡片模糊背景、顶栏与底栏模糊从下方滚过的内容；Android 8–11 自动改用更不透明的半透明效果
- **沉浸光感**（设置 → 面板 → 沉浸光感）：玻璃边缘高光（朝向光源的边更亮）、斜向柔光、主题色光晕；光感强度、跟随重力感应（重力传感器约 100 ms 低频采样、按 6° 量化，退到后台自动停止）、主题色光晕、流光扫过（切换页面时一道光扫过底栏）
- **全屏沉浸**：内容延伸到状态栏和手势条下方（edge-to-edge，关闭导航栏对比度遮罩），按系统栏实际高度留白；所有页面使用叠放在内容之上的紧凑毛玻璃顶栏
- **悬浮底栏**：贴近手势条的磨砂玻璃底栏；往下浏览时自动收起为左下角的圆形按钮（显示当前页图标），往回滑动或点按即展开；设置 → 面板 → 导航栏 → 自动收起导航栏（默认开启）。“后端连接失败”提示条悬浮在底栏上方
- **左右滑动切换页面**：六个页面放在 `HorizontalPager` 中，与底栏双向同步（点底栏动画切页，滑动时高亮立即跟上），顶栏随页面一起滑动；页面的筛选、搜索等状态在切页和切换语言后保留。页面顺序：概览、代理、连接、日志、规则、设置
- **下拉刷新**：概览、代理（代理组 / 订阅）、连接、日志、规则（规则 / 规则集）下拉即可刷新，语义同 HarmonyOS 1.1.9：概览探测后端并刷新配置、代理与规则；代理、规则重新拉取；连接、日志探测后端后重建实时流；后端连接失败时下拉会重新连接；主题色指示器显示在顶栏下方，请求完成（成功或失败）后才收起，失败时弹出提示；内容不满一屏也能下拉
- **隐私政策与用户协议**：首次启动弹出同意页，同意前不发起任何网络请求，不同意则退出；正文见 `docs/privacy.md`、`docs/agreement.md`（英文 `docs/*_en.md`），已按安卓版修改（SharedPreferences、照片选择器、存储访问框架、INTERNET 权限）
- **设置 → 关于**：应用名称、版本、开发者、兼容接口、隐私政策、用户协议、撤回隐私政策同意（撤回后断开并退出）
- **语言**：设置页顶部“语言 / Language”：跟随系统（默认）、简体中文、English；切换后立即生效、无需重启。全部界面文字走应用内字符串表（`i18n/strings_zh.json` / `strings_en.json`，由 `scripts/gen-i18n.py` 生成 Kotlin 表并检查中英文键一致、代码中用到的键都存在），覆盖所有页面、弹窗、提示、单位与相对时间、闪退日志内容；英文排版：底栏短标签并在空间不足时自动缩小字号、较长分段选项使用紧凑尺寸、毛玻璃“材质”选项单独一行；设置搜索同时匹配中英文关键词；桌面应用名在中文系统显示“咪咪面板”，其他语言显示 “Mimi Panel”
- 日志与闪退日志的“保存”改为系统“另存为”文件选择器（不需要存储权限），日志文件名 `mimi-logs-*.txt`
- 关闭系统自动备份（`allowBackup=false` + `dataExtractionRules`），后端密钥不会被备份到云端；移除 androidx 自动添加的动态广播签名权限，只保留 INTERNET 权限
- 设置主页的闪退日志入口在有未读闪退记录时显示红点

### 1.1.2
- 修复启动闪退；后端表单按钮改为上下排列；新增闪退日志（设置 → 闪退日志，启动时提示，可复制/分享/保存）；R8 不混淆以便阅读堆栈

### 1.1.1
- 兼容限制了外部控制的客户端：后端拒绝 `PATCH /configs`（405）、`PUT /configs`、`/configs/geo`、`/restart`（404）时标记为“当前后端不支持”并说明；更新 GEO 时自动改用 `/upgrade/geo`

### 1.1.0
- 修复“显示 GLOBAL”开关；概览独立为底栏第一个页签；设置 → 面板 新增自定义壁纸（卡片不透明度 / 暗化 / 模糊）

### 1.0.0
- 首个 Kotlin + Jetpack Compose 原生版本

## 功能

| 页面 | 功能 |
| --- | --- |
| 概览 | 实时上传/下载速度、总流量、活跃连接、内存占用、速度与内存曲线、流量最多的主机、快速切换代理模式 |
| 代理 | 代理组卡片（1~3 列）、节点选择、分组/单节点测速、延迟颜色分级、排序、搜索（支持正则）、隐藏不可用节点、显示 GLOBAL 与隐藏组、取消固定、代理提供商（订阅流量/到期、更新、健康检查） |
| 连接 | 活跃/已关闭/全部、搜索与正则过滤、来源 IP 筛选、11 种字段排序、紧凑模式、单条/全部/筛选结果断开、连接详情（可复制）、暂停刷新 |
| 日志 | 日志级别切换、类型过滤、正则搜索、暂停、清空、正序/倒序、复制、另存为文本文件 |
| 规则 | 规则列表、单条规则启用/禁用、规则集更新、命中次数、代理链与延迟 |
| 设置 | 界面语言、多后端管理与连接测试、代理模式、内核日志级别、TUN、局域网、IPv6、各类端口、重载配置、更新 GEO、清空 DNS/FakeIP 缓存、更新/重启内核、主题、主题色、导航栏自动收起、毛玻璃效果、沉浸光感、自定义壁纸、测速参数、保留数量、闪退日志、关于 |

- 实时数据通过 WebSocket 获取：`/traffic`、`/memory`、`/connections`、`/logs`，断线指数退避自动重连（1s → 10s）
- 后端返回 404/405/501 的接口会被标记为“当前后端不支持”，并在设置里说明
- 闪退时自动保存错误日志，下次启动提示；可在 **设置 → 闪退日志** 中复制、分享或另存为

## 使用

1. 在代理客户端里开启外部控制器（external-controller），记下地址、端口和密钥（secret）
2. 安装 APK，首次打开阅读并同意隐私政策与用户协议，然后填写后端地址，例如 `192.168.1.1:9090` 或 `127.0.0.1:9090`
3. 点“测试连接”，成功后“保存并连接”

## 构建

需要 JDK 17+ 与 Android SDK 35：

```bash
./gradlew assembleRelease        # 输出 app/build/outputs/apk/release/app-release.apk
./gradlew testReleaseUnitTest    # JVM 单元测试（本机 127.0.0.1:19090 有测试用 mihomo 时会跑联机测试，否则跳过）
```

修改界面文字后运行 `python3 scripts/gen-i18n.py`：从 `i18n/strings_zh.json`、`i18n/strings_en.json` 与 `docs/*.md` 生成 `app/src/main/java/net/zash/clashpanel/i18n/Strings.kt`、`LegalTexts.kt`，并检查键是否一致。隐私政策有实质变更时递增 `i18n/I18n.kt` 中的 `PRIVACY_VERSION`，用户会被要求重新同意。

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

`.github/workflows/release.yml`：推送 `v*` 标签（如 `git tag v1.2.0 && git push origin v1.2.0`），或在 Actions 页手动运行 **Release**，即自动构建并发布 `MimiPanel-Android-<版本>.apk`，说明取自本文“更新日志”中对应版本并附 SHA-256。

需在仓库 **Settings → Secrets and variables → Actions** 添加（首尾空白会被自动去掉）：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | 签名文件 base64（`base64 -w0 release.keystore`） |
| `KEYSTORE_PASSWORD` | 签名库密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码 |

## 项目结构

```
docs/                      # 隐私政策 / 用户协议（中英文）
i18n/                      # 界面字符串表 strings_zh.json / strings_en.json
scripts/gen-i18n.py        # 生成 Kotlin 字符串表与协议正文并检查键
app/src/main/java/net/zash/clashpanel/
├── MainActivity.kt        # 入口、首次同意页、首次设置、横向分页、毛玻璃顶栏、悬浮底栏
├── MainViewModel.kt       # 状态与业务逻辑（下拉刷新、隐私同意、外观设置）
├── CrashLog.kt            # 闪退日志捕获
├── data/                  # CoreApi（REST + WebSocket）、数据模型、本地设置
├── i18n/                  # I18n（语言选择与 t()）、生成的字符串表与协议正文
└── ui/
    ├── Theme.kt           # 浅色/深色配色、7 种主题色
    ├── Glass.kt           # 毛玻璃（RenderEffect 背景模糊）与沉浸光感
    ├── Chrome.kt          # 顶栏、下拉刷新、底栏自动收起、重力感应
    ├── Common.kt          # 通用组件
    └── *Screen(s).kt      # 概览 / 代理 / 连接 / 日志与规则 / 设置 / 闪退日志
```

## 与 HarmonyOS 版的差异

- 毛玻璃用 Compose `GraphicsLayer` + `RenderEffect` 自绘，Android 12 以下为半透明效果；没有 HarmonyOS 的“系统”沉浸材质选项（安卓没有对应的系统材质），对应位置显示本机是否支持实时模糊
- 光感边缘高光用渐变描边近似（HarmonyOS 为四边分别取色），主题色光晕用彩色阴影（Android 9+ 才显示颜色）
- 顶栏会拦截触摸（避免点穿到下方的列表项），因此在顶栏上左右滑动不会翻页，需要在页面内容上滑动（HarmonyOS 版顶栏位于页面内，可以随页面滑动）
- 桌面应用名随系统语言显示，不随应用内语言切换（应用内界面会切换）
- 包名保持 `net.zash.clashpanel` 以便老用户覆盖升级（HarmonyOS 版为上架改成了 `com.zhisibi.mimipanel`）；本地设置文件名仍为 `clash_panel`
- 壁纸模糊需要 Android 12+

## 技术栈

Kotlin 2 · Jetpack Compose (Material 3) · OkHttp · kotlinx.serialization · Coroutines · Coil

## 致谢

- [mihomo](https://github.com/MetaCubeX/mihomo)
- [Zashboard](https://github.com/Zephyruso/zashboard)
