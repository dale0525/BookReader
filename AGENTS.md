# AGENTS.md

面向改动本仓库代码的 agent。产品说明、安装与用法见 [README.md](README.md)。

## 硬门禁

### 测试

- **不主动跑全量测试。** 全量（`testAppDebugUnitTest`，约 1979 项、20+ 分钟）只在用户明确要求时执行。
- 迭代期只跑**单个测试类**，例如：

  ```bash
  pixi run bash -c 'export ANDROID_HOME=$PWD/.android-sdk; export ANDROID_SDK_ROOT=$ANDROID_HOME; \
    ./gradlew :app:testAppDebugUnitTest --tests "io.legado.app.help.source.BuiltInExploreKindsTest" --console=plain'
  ```

  单类约 30–40 秒。`--tests` 可重复传多个类。

- 测试先行仍然适用：改动有可冻结的长期契约时，先写测试。契约是否值得冻结的判据见全局规范。
- 测试必须能复现**用户实际遇到的那条路径**。同一份规则在「全新安装」和「升级已有安装」下行为可能不同，只测前者会漏掉升级路径的缺陷。

### 交付

- 构建：`./gradlew assembleAppDebug -PenableAbiSplits=false`
- 构建产物复制到 WebDAV：`cp app/build/outputs/apk/app/debug/app-app-debug.apk /Volumes/Main/Roaming/APK/`
  - 该目录已由 Finder 挂载，对应 `http://192.168.123.251:20001/dav/Main/Roaming/APK`。
  - 挂载点不存在时先 `open "http://192.168.123.251:20001/dav/Main/"` 让系统重新挂载，不要改用 curl 上传（需要凭据）。
  - 复制后用 `md5 -q` 比对源与目标，确认落盘完整。

## 构建与运行环境

工具链由 pixi 提供，SDK 与 AVD 都在仓库内，不要用系统全局安装。

- `pixi run sdk` — 首次从国内镜像安装 Android SDK 并建 AVD（一次性）。
- `pixi run web` — 起模拟器 → 装调试包 → 打开 web 服务开关 → 打印本机与局域网地址。
- 直接跑 gradle 需要 pixi 注入的环境变量，用
  `pixi run bash -c 'export ANDROID_HOME=$PWD/.android-sdk; export ANDROID_SDK_ROOT=$ANDROID_HOME; ./gradlew …'`。

模块：`:app`、`:modules:book`、`:modules:rhino`、`:baselineprofile`。单元测试只在 `:app` 下。

## 内置书源

`app/src/main/assets/defaultData/bookSources.json` 是随包发布的内置书源（6 个聚合源，单站源已全部并入这 6 个分类）。

- 发现页每个 tab 只由一个**目录源**驱动（站点配置里的 `cat: true`），它提供全部分类与周期榜单；其余站点只参与搜索和正文。新增站点默认不设 `cat`，避免同类列表混进多个站的排版。
- 分类缓存按 `md5(bookSourceUrl + exploreUrl)` 存在 ACache（`cache/explore/`）。内置源的 `exploreUrl` 恒为同一段 JS、改的是它背后的 `jsLib`，所以改 `jsLib` 后必须清缓存（`importDefaultBookSources` 已调用 `clearExploreKindsCache()`），否则覆盖安装后发现页仍显示旧分类。

- 导入由 `DefaultData.importDefaultBookSources()` 完成，在 `upVersion()` 里按 `LocalConfig.needUpBookSources` 触发。
- 书源导入**不能**挂在 `LocalConfig.versionCode < AppConst.appInfo.versionCode` 门禁内：本地构建的 versionCode 恒为 32640（无 `COMMIT_NUMBER` 时的兜底值），覆盖安装时两边相等，整段被跳过，只有删除重装才会执行。这是发现页长期报错的真正成因。
- 升级语义：规则字段用 asset 覆盖，`enabled` / `bookSourceGroup` / `customOrder` / `enabledExplore` 保留用户设置。改了规则或加了源，把 `LocalConfig.needUpBookSources` 的版本号 +1，否则老用户拿不到更新。
- 验证必须模拟**覆盖安装**（`versionCode` 已等于当前版本），不能只测全新安装。`BuiltInBookSourceUpgradeTest` 守这条。
- `exploreUrl` 里的 JS 必须返回 **JSON 字符串**，不能返回 JS 数组对象——`BookSourceExtensions.exploreKinds()` 拿到结果后直接 `.toString()` 再当 JSON 解析，返回数组对象会得到 `org.mozilla.javascript.NativeArray@…`，发现页把它当分类名显示，点进去必然报错。`BuiltInExploreKindsTest` 守这条契约。
- 书源制作用 `.agents/skills/legado-book-source/` 技能；书源说明见 [docs/book-source/README.md](docs/book-source/README.md)。

## 验收

- 改动前先看 `.github/workflows/verify.yml` 里的门禁组合：`testAppDebugUnitTest`、`lintAppDebug`、`verifyConfigArchitecture`、`assembleAppDebug`。这些是 CI 跑的，本地按需取用。
- 界面相关改动要在真实界面上确认，不要只凭单元测试下结论。模拟器软渲染下容易 ANR，卡住时优先换 JVM 测试验证逻辑，界面只做最后冒烟。
- 用 `adb shell input tap` 验证点击时，**不要从 `screencap` 截图里量坐标**：截图预览会被缩放，量出来的点和真实触摸坐标能差 300–400px，表现为「点了没反应」，极易被误判成代码缺陷。坐标只从 `Modifier.onGloballyPositioned { it.positionInWindow() }` 打日志取，验完即删。
- Compose 里整屏浮层会抢走下层按钮的点击。控制层这类「遮罩 + 一排按钮」的结构，把两者放成同一个 `Column` 的兄弟分块，不要用整屏 `Box` 遮罩再靠 `Spacer` 或坐标判定让出底部——让出的高度一旦小于按钮实际高度，按钮就永远点不到。
