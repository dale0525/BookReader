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

`app/src/main/assets/defaultData/bookSources.json` 是随包发布的内置书源：31 个单站源，一个源就是一个站点，发现页不跨源聚合。每源的 `jsLib` 只带自己那一条 `SITES` 记录，规则里取站点配置用 `SITES[0]`。

- 成人分支靠 `bookSourceUrl` 的路径后缀区分（`https://zuidazy.co` 与 `https://zuidazy.co/adult`）。PK 不同就是两条独立记录，但 host 匹配、Cookie、`is18Plus`、`getBaseUrl` 都按 host 走，两边的登录态和请求头互不影响。不要再加别的后缀做分支，主机会被当成两个站。
- 发现页只有 6 个分组：`小说` / `漫画` / `视频` / 各自 ` · 18+`，定义在 `constant/ExploreGroups.kt`。界面上的分组**不读** `bookSourceGroup` 字面值，而是经 `ExploreGroups.canonical()` 归一：分组名含「小说/漫画/视频」按它定档，没写或写了别的词（「短剧」「玄幻」）退回 `bookSourceType`（0 文本 / 2 图片 / 4 视频），含 `18+` 归成人档。新增源即使分组名写错也不会多出第 7 个分组标题。
- 内置源的分组名仍必须原样是那 6 个字符串（含中间空格），`BuiltInExploreKindsTest.everySourceUsesOneOfTheSixGroups` 拦写错的。运行时的归一只是兜底用户自建源，不能用来掩盖内置源的笔误——静默纠正会让人以为改了分组名生效了，实际界面纹丝不动。
- **判断「某个源是不是成人源」只有一条依据：`bookSourceGroup` 含 `18+`**（`AdultContentPolicy.containsMarker`）。不要用 `assets/18PlusList.txt`——它是 208 个 base64 域名的**禁入黑名单**，`SourceHelp.insertBookSource()` 命中就拒绝导入（「…是18+网址,禁止导入.」），实测内置的 10 个成人源一个都不在里面。拿它当成人标记做显示过滤，结果会是「成人源全都不显示、非成人源一个不少」。
- `bookSourceComment` 是发现页卡片的副标题，也是唯一能承载「这个站有多少分类/榜单」的字段。它不在 `bookSource_part` 里，读它必须查 `book_sources`（`BookSourceDao.flowExploreSummaries()`），不要为了显示副标题去拉整行——每行带 200KB+ 的 `jsLib`。同一个投影还要带上 `bookSourceType`，它是归组的依据。
- 分类缓存按 `md5(bookSourceUrl + exploreUrl)` 存在 ACache（`cache/explore/`）。内置源的 `exploreUrl` 恒为同一段 JS、改的是它背后的 `jsLib`，所以改 `jsLib` 后必须清缓存（`importBookSources` 已调用 `clearExploreKindsCache()`），否则覆盖安装后发现页仍显示旧分类。

- 导入由 `DefaultData.importDefaultBookSources()` 完成，在 `upVersion()` 里按 `LocalConfig.needUpBookSources` 触发。
- 规则同时发布在仓库同一路径的 `main` 分支上，`RemoteBookSources.refresh()` 在启动时（12 小时最多一次）静默比对并调用同一个 `DefaultData.importBookSources()`。改规则要 asset 与远端一起改，否则装机用户拿到的和随包的不一致。远端返回空数组、非 2xx 或 HTML 错误页一律不落库——那条路径用户无法预检，出错会直接清空所有人的书源表，`RemoteBookSourcesTest` 守这条。
- 在真机上验证远端这条路径，要先把三道闸门一起拆掉，否则「没复活」只是没发请求：`bookSourceRemoteCheckTime`（12 小时门）、`bookSourceRemoteEtag`（带上去就是 304）、以及「body hash 与上次相同或与随包 asset 相同就直接返回」——远端内容与 asset 一致时永远不写库，用同一份内容验不出任何东西。清完闸门后 `local.xml` 里落下的 `bookSourceRemoteHash` 就是这次真的落了库的证据。
- 书源导入**不能**挂在 `LocalConfig.versionCode < AppConst.appInfo.versionCode` 门禁内：本地构建的 versionCode 恒为 32640（无 `COMMIT_NUMBER` 时的兜底值），覆盖安装时两边相等，整段被跳过，只有删除重装才会执行。这是发现页长期报错的真正成因。
- 升级语义：规则字段用 asset 覆盖，`enabled` / `bookSourceGroup` / `customOrder` / `enabledExplore` 保留用户设置。改了规则或加了源，把 `LocalConfig.needUpBookSources` 的版本号 +1，否则老用户拿不到更新。
- 已下线的源列在 `DefaultData.retiredBookSourceUrls`：升级时按 url 删掉，导入时还要按同一份清单过滤。删除只做一次，而远端清单的版本由发布节奏决定、随时可能比本地旧，不过滤就会被重新写回库里。`BuiltInBookSourceUpgradeTest` 的两个用例分别守删除与过滤。
- 验证必须模拟**覆盖安装**（`versionCode` 已等于当前版本），不能只测全新安装。`BuiltInBookSourceUpgradeTest` 守这条。
- 恢复备份也要按同一套语义覆盖一遍：`Restore.restoreBookSources()` 写完备份里的 `bookSource.json` 后调 `importDefaultBookSources()`。备份里的内置源属于**备份时**的版本，而 `bookSourceVersion` 存在 `SharedPreferences("local")` 里、不在备份的 `config.xml`（只导 `AppConfigStore`）中，升级门禁早已推进过——只写回不覆盖的话，旧规则会永久留在库里，发现页一直停在备份那版，且再没有任何路径会纠正它。`RestoreBuiltInBookSourceTest` 守这条。
- `exploreUrl` 里的 JS 必须返回 **JSON 字符串**，不能返回 JS 数组对象——`BookSourceExtensions.exploreKinds()` 拿到结果后直接 `.toString()` 再当 JSON 解析，返回数组对象会得到 `org.mozilla.javascript.NativeArray@…`，发现页把它当分类名显示，点进去必然报错。`BuiltInExploreKindsTest` 守这条契约。
- `source.get(key)` 在键不存在时返回**空串**而不是 null，`source.put` 的底层键是 `v_${sourceKey}_${key}`（落在 `caches` 表）。判缓存命中不能只写 `!= null`，否则首次读到的空值会被当成命中，表现为「缓存已生效但数据全空」。
- `jsLib` 是共享作用域，里面**没有** `baseUrl` 和 `source`。要在共享函数里用 `source`，得在 `exploreUrl` / `ruleExplore` 的 `<js>` 块里把句柄挂到站点对象上传进去（`s.v=source`），并判断句柄非空再调用。
- 漫画正文图片**不经过** `CoverFetcher`：`CoverInterceptor` 会先把在线页下载成本地文件，再以 `data(File)` 交给解码器。任何需要改写图片字节的处理（如禁漫天堂的切条重排）必须接在 `BookHelp.saveImageFile` 的落盘处，并且要绕开 `skipDecode` 的流式直写分支，否则错位原图会被缓存下来反复显示。
- 书源制作用 `.agents/skills/legado-book-source/` 技能；书源说明见 [docs/book-source/README.md](docs/book-source/README.md)。

## 阅读排版与菜单

改阅读界面的默认外观前先分清两个配置来源——它们互不覆盖，只改一处不会生效：

- **排版**（字号、字距、行距、段距、页边距、标题、页眉页脚）走 `ReadBookConfig.config`，即 `if (shareLayout) shareConfig else durConfig`。`shareLayout` 默认 true，所以生效的是 `assets/defaultData/shareReadConfig.json`；`readConfig.json` 里各预设的同名排版字段此时不参与渲染。
- **配色**（背景、正文色）不经过 `config`：`ReadBookViewModel.buildStyleConfig()` 与 `ReadBookRouteScreen` 直接取 `durConfig`，即 `readConfig.json` 中当前选中的那一份预设。

菜单配色是第三套开关，由 `readBarStyle` 决定：0=黑白、1=跟随阅读背景、2=自定义。只有 2 会读 `readMenuBgColor/AccentColor/ContainerColor/TextColor`（见 `ReadBookColorTheme.kt` 的 `rememberCustomReadMenuTheme`）。菜单卡片默认 `readMenuBlurAlpha = 85` 是半透明，要做实心卡片必须设 100，否则正文会透上来。

## 上游同步

`upstream` 指向 `HapeLee/legado-with-MD3`，fork 在 `main` 上累积自己的删除与改造。合并上游时有两处必查：

- **数据库版本号不能沿用上游的。** fork 已把版本号发过版，同一版本号换了 schema，覆盖安装的用户会撞 Room 的 schema 校验。上游的 `AutoMigration` 要顺延到「已发布的最高版本 + 1」，`version` 同步改，然后跑 `:app:kspAppDebugKotlin` 重新生成 `app/schemas/…/<新版本>.json` 并提交。核对方式是拿新旧 schema 的 `entities` 对比，确认只多出预期的那几列。
- **fork 主动删掉的功能会被上游改回来，而且不一定报冲突。** 上游对同一功能的后续提交会以 modify/delete 冲突出现，这部分好处理；真正的坑是同一批改动里与功能无关的钩子（按钮池条目、Intent、Effect、菜单项）会被**自动合并**静默留下，编译期才暴露，或者更糟——编译通过但留下一个点了没反应的入口。合并后按功能名全仓 `grep` 一遍（如 `ai_chat`、`ToggleTranslation`），确认入口、意图、副作用、路由四处都清干净。

## 验收

- 质量门禁组合：`testAppDebugUnitTest`、`lintAppDebug`、`verifyConfigArchitecture`、`assembleAppDebug`。仓库没有 CI 跑这些，本地按需取用。
- 在 worktree 分支上装包验证前先比一次 `AppDatabase` 的 `version`：分支基于旧 `main` 时它可能低于 `main`（如分支 133、`main` 134），`install -r` 覆盖装到用 `main` 构建过的模拟器上会直接崩在 `A migration from 134 to 133 was required but not found`。这是环境残留，不是代码缺陷——先 `adb uninstall` 再干净安装，别去补降级迁移。
- 界面相关改动要在真实界面上确认，不要只凭单元测试下结论。模拟器软渲染下容易 ANR，卡住时优先换 JVM 测试验证逻辑，界面只做最后冒烟。
- 用 `adb shell input tap` 验证点击时，**不要从 `screencap` 截图里量坐标**：截图预览会被缩放，量出来的点和真实触摸坐标能差 300–400px，表现为「点了没反应」，极易被误判成代码缺陷。坐标只从 `Modifier.onGloballyPositioned { it.positionInWindow() }` 打日志取，验完即删。
- Compose 里整屏浮层会抢走下层按钮的点击。控制层这类「遮罩 + 一排按钮」的结构，把两者放成同一个 `Column` 的兄弟分块，不要用整屏 `Box` 遮罩再靠 `Spacer` 或坐标判定让出底部——让出的高度一旦小于按钮实际高度，按钮就永远点不到。
