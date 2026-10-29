# AGENTS.md

面向改动本仓库代码的 agent。产品说明、安装与用法见 [README.md](README.md)。

## 硬门禁

### 测试

- **不主动跑全量测试。** 全量（`testAppDebugUnitTest`，约 1979 项、20+ 分钟）只在用户明确要求时执行。
- 迭代期只跑**单个测试类**，`--tests` 可重复传多个类，单类约 30–40 秒：

  ```bash
  pixi run bash -c './gradlew :app:testAppDebugUnitTest --tests "io.legado.app.help.source.BuiltInExploreKindsTest" --console=plain'
  ```

- `--tests` 写错包名或类名时 Gradle 不报错、只跑匹配上的类并回 `BUILD SUCCESSFUL`。判定是否真跑过，看 `app/build/test-results/testAppDebugUnitTest/TEST-<全限定类名>.xml` 的 `tests=` 计数；改过测试文件后 XML 可能是上一轮的，先删再跑。
- 测试先行：改动有可冻结的长期契约时先写测试，判据见全局规范。
- 测试必须能复现**用户实际遇到的那条路径**。「全新安装」与「升级已有安装」行为可能不同，只测前者会漏掉升级路径的缺陷。
- Robolectric 里起不了 Koin，凡是碰 `CacheManager` / `AppConst` 等容器的入口都会抛 `ExceptionInInitializerError`（`source.getLoginInfoMap()`、`getLoginInfo()` 都属此类）。这是**测试环境限制，不是缺陷**：绕开它们做断言，别为了让它跑通去改生产代码或补 Koin 初始化。

### 交付

- 构建：`./gradlew assembleAppDebug -PenableAbiSplits=false`。
- 质量门禁组合：`testAppDebugUnitTest`、`lintAppDebug`、`verifyConfigArchitecture`、`assembleAppDebug`。仓库没有 CI 跑这些，本地按需取用。
- 调试包不归档、不外发，只在需要装机验证时 `adb install -r app/build/outputs/apk/app/debug/app-app-debug.apk`。

## 构建与运行环境

工具链由 pixi 提供，SDK 与 AVD 都在仓库内，不要用系统全局安装。

- `pixi run sdk` — 首次从国内镜像安装 Android SDK 并建 AVD（一次性）。
- `pixi run web` — 起模拟器 → 装调试包 → 打开 web 服务开关 → 打印本机与局域网地址。
- `scripts/activate.sh` 已注入 `JAVA_HOME` / `ANDROID_HOME` / `ANDROID_SDK_ROOT` / `ANDROID_AVD_HOME`，`pixi run bash -c './gradlew …'` 直接用，不要再手动 `export`。
- 这些路径一律指向**主检出**（`git rev-parse --git-common-dir` 的上级目录），不是 `$PIXI_PROJECT_ROOT`。若 `JAVA_HOME` 指向某个 worktree 自己的 `.pixi`，会起一个 `javaHome` 绑在该 worktree 上的 daemon；worktree 删掉后主工作区会报 `Directory '…/.worktrees/<name>/.pixi/…' (Current JVM) … does not exist` + `Cannot find a Java installation … languageVersion=21`，且只有 `./gradlew --stop` 加清 `.gradle/configuration-cache` 才能恢复。

模块：`:app`、`:modules:book`、`:modules:rhino`、`:baselineprofile`。单元测试只在 `:app` 下。

## 项目技能

改动前先看有没有对应的技能，有就先加载它再动手——雷区、验收判据与历史成因都在里面。

| 触发 | 技能 |
| --- | --- |
| 给某个站点写或修书源 JSON，排查搜索/详情/目录/正文某条链路不生效 | [`legado-book-source`](.agents/skills/legado-book-source/SKILL.md) |
| 改内置书源资产、导入与升级语义、下线源、书源分组名、成人源判定口径 | [`builtin-book-sources`](.agents/skills/builtin-book-sources/SKILL.md) |
| 改书名/作者的判重与换源匹配 | [`book-identity`](.agents/skills/book-identity/SKILL.md) |
| 改阅读界面的排版、配色、菜单样式的默认值 | [`reader-appearance`](.agents/skills/reader-appearance/SKILL.md) |
| 合并 `upstream`（HapeLee/legado-with-MD3） | [`upstream-sync`](.agents/skills/upstream-sync/SKILL.md) |
| 在模拟器或真机上验证界面改动，或排查「点了没反应」 | [`emulator-ui-verification`](.agents/skills/emulator-ui-verification/SKILL.md) |
| 发版：改版本号、打 tag、等 CI、核对 Release | [`bookreader-release`](.agents/skills/bookreader-release/SKILL.md) |
