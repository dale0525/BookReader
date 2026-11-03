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

## 目标依赖方向

```text
app host / composition root
        ↓
feature implementation → feature API
        ↓
domain / model / platform contracts
        ↓
data abstractions
        ↓
platform implementations (Android/JVM/iOS/...)
```

- 应用宿主负责组装 Feature 实现、DI、平台生命周期和导航运行时。
- Feature 之间通过稳定契约或导航 API 协作，不直接依赖其他 Feature 的实现。
- `commonMain` 只容纳经过依赖审计、能被至少一个非 Android 目标编译验证的代码；移动目录不等于完成跨平台迁移。
- Android `Context`、Room 实体/DAO、Service、Broadcast、Activity Result、Cronet、文件
  URI、通知、媒体会话、成熟阅读器渲染和现有 Rhino 集成默认留在平台侧，通过窄接口进入共享层。
- 平台能力不可用时必须显式建模为 capability/unsupported，不得用静默空实现伪造跨平台支持。

## Compose 屏幕约束

- 新屏幕使用 Compose；不要新增 XML/View Activity/Fragment。
- Android Compose 新屏幕采用 UDF/MVI：`@Stable XxxUiState`、`XxxIntent`、`XxxEffect`；ViewModel 直接继承
  `ViewModel`，私有 `MutableStateFlow` / `MutableSharedFlow(extraBufferCapacity = 16)`，对外只暴露只读
  Flow，并由单一 `onIntent` 分发用户动作。共享 Feature 的状态宿主按 KMP skill 单独决策。
- Screen/Content 保持无业务逻辑、无 DAO/网络/存储直连；ViewModel 通过 Gateway/Repository/UseCase
  访问业务能力。
- 所有 UiState 与 UI item 数据类标注 `@Stable`。Compose 渲染边界中的集合使用
  `kotlinx.collections.immutable`；内部计算和数据层不机械替换集合类型。
- 导航、权限、文件选择和其他宿主动作通过回调或 Effect 处理。新目的地优先由 `MainActivity` 的 Navigation
  3 图组装。
- 使用项目的 `AppScaffold`、主题、top bar、dialog/sheet、列表和设置组件。正确处理 edge-to-edge 与
  predictive back。
- 所有交互控件必须具有稳定、可本地化的无障碍名称和可执行语义；动画文字隐藏时仍须保留名称，菜单关闭区域须说明用途，
  排序须暴露当前位置。修复无障碍问题时验证语义树与动作，并记录尚未完成的屏幕阅读器实测。
- Activity 仅在遗留 `Intent`/result 兼容确有需要时作为薄宿主保留。

具体文件形态和审查清单由 Compose migration/review skill 维护，本文件不复制完整模板。

## 数据与设置边界

- UI（包括 ViewModel）不得新增 DAO、`appDb.*Dao`、网络客户端或旧偏好 API 直连；历史债务由
  `verifyConfigArchitecture` 的基线棘轮冻结并逐步下调。
- 普通设置 Gateway 通过 `update { current -> current.copy(...) }` 修改状态；关联字段在一次
  `copy(...)` 中原子提交。
- 不引入 `*SettingsUpdate` 分发类型或 `updateAll`。
- `ReadStyleMutation`、`ThemePackageSettingsGateway.applyAndAwait`、`ThemeStateTransaction`、
  `AppUiConfigurationGateway` 等专用 API 保持现有形态。
- Koin 中 Gateway 接口到 Repository 实现保持显式绑定；不要用构造函数绑定掩盖接口归属。普通
  Repository/UseCase/ViewModel 可继续使用项目既有 `singleOf` / `viewModelOf` 约定。
- 新的共享领域契约不得暴露 `Context`、`File`、`Uri`、Room 类型、Android resource id 或 JVM-only
  stream；用领域值、`ByteArray`/抽象 source-sink、序列化模型或平台接口表达。

## KMP/CMP 迁移纪律

- 先写行为/契约测试和依赖清单，再移动代码；一个 PR 只完成一个可说明的边界变化。
- 优先抽取稳定模型、纯函数、规则解析契约和 UseCase；网络、数据库、JS 引擎、服务、阅读器渲染最后通过平台适配器处理。
- `expect/actual` 只用于真正的平台原语。可用普通接口 + DI 表达的能力，不使用 `expect/actual`。
- 平台实现由 Koin 和应用 composition root 组装。
- 不因 KMP 目标同时替换数据库、网络、DI、导航和 UI 框架。每次只改变一个主要风险维度。
- Feature `api/impl` 只有形成真实 Gradle 边界并存在跨 Feature/宿主调用时才创建，不能只建同名文件夹。
- 首个非 Android 目标是架构证明，不承诺功能齐平；目标顺序由 capability matrix 和产品需求决定。

## 代码生成

- 生成器用于编码已稳定的约定，不用于发明架构。至少有两个手工迁移且通过审查的同类样本后，才能固化模板。
- 脚手架必须可预览或 dry-run、拒绝覆盖已有文件、输出最小文件集，并在已有 Feature 上采用追加/AST
  感知修改，禁止覆盖 route graph 或 DI 文件。
- 生成的可提交源码仍接受普通代码审查；真正的编译期生成物放入 `build/generated`，不得手工编辑或提交。
- 模板和生成器变更必须有 fixture/snapshot 或编译验证，并与 convention plugin 的模块类型保持一致。

## 重构审查门禁

每个迁移 PR 至少回答：

- 行为基线是什么，哪些测试或手工路径证明没有回退？
- 依赖边界净变化是什么，是否减少了 Android/JVM 泄漏或历史基线？
- 新模块/接口是否有真实调用方，能否用更小改动完成？
- Android 实现与共享契约的错误、线程、取消、序列化语义是否一致？
- 做了哪些验证，哪些真机、性能、外部服务或其他平台行为尚未验证？
- CI 是否执行了受影响的共享测试、目标编译或 adapter contract test？

减少历史违规时必须同步下调对应 baseline；门禁不接受“先放宽基线再迁移”。详细分级见 KMP/CMP 现代化计划。

## 构建与验证

开发与 CI 使用 JDK 21。按风险选择最小充分验证：

```bash
# Kotlin/Compose 快速编译
./gradlew :app:compileAppDebugKotlin

# 当前主验证集
./gradlew testAppDebugUnitTest lintAppDebug verifyConfigArchitecture assembleAppDebug --continue --no-configuration-cache

# 资源、Manifest、生成绑定或打包变化
./gradlew :app:assembleAppDebug

# Release/R8 相关
./gradlew assembleAppRelease
./gradlew assembleAppNoR8

# Web 前端
pnpm --dir modules/web build
```

KMP 任务名在模块实际创建后才存在；不要假装运行尚未定义的 `commonTest`、目标编译或 API
检查任务。新增模块时把其真实任务加入 CI，并在交付中列出准确命令。

所有文本改动至少运行 `git diff --check`。构建通过不替代架构边界、行为和真机性能复核。

## 重要项目约束

- 不将 jsoup 升级到 1.16.2 以上；新版行为会影响 `AnalyzeByJSoup.kt` 与 JsoupXpath。
- 应用代码仅在两处依赖 Hutool：①作为书源 JS 的运行时加密库保留在 classpath（书源直接调用
  `Packages.cn.hutool.*`）；②`help/crypto/CryptoUtils.kt` 的 base64 **解码**统一走
  `cn.hutool.core.codec.Base64.decode`，以兼容缺 `=` 填充等宽松输入（Kotlin
  `kotlin.io.encoding.Base64` 严格填充会导致部分书源取章名/正文回归）。版本固定 5.8.22 勿升级。
  应用内部加密主体仍用现有 JCA（`javax.crypto`/`java.security`）与 `help/crypto` 路径，KMP 抽取时
  再通过能力契约替换 JVM API。
- 代码 namespace 为 `io.legado.app`，Android `applicationId` 为 `io.legato.kazusa`，不要混用。
- 当前 minSdk 26、target/compile SDK 37；Release 启用 R8 与资源压缩，`noR8` 变体用于排障。
- Rhino 书源/RSS/TTS 规则、Android 服务、Web 服务和阅读器渲染属于高行为风险平台能力，迁移前必须建立兼容测试或
  capability 边界。
- `modules/web` 必须连接应用内 Ktor WebService；开发环境通过 `VITE_API` 指向设备服务地址。
- 发布形态为**单包**：本 fork 不跟随上游拆 `core` + `full` 双包，只有一个 `app` 产品维度，
  产物为 `assembleAppRelease` / `assembleAppNoR8`，同一 Release 只上传一份 APK 与一份 R8 mapping。
  应用内更新仍按 `AppProduct` 解析资产名里的产品标记，但只有单包时不区分产品。

## 交付说明

最终说明应包含：修改的职责范围、关键设计取舍、实际运行的验证与结果、未验证风险。不要把“已生成代码”或“已移动文件”当作完成标准。
