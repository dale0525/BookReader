---
name: builtin-book-sources
description: 改动随包内置的书源（app/src/main/assets/defaultData/bookSources.json）、书源导入与升级语义、下线源清单、发现页六档分组名、或「隐藏成人内容」的成人源判定口径时使用。也用于排查「改了 asset 但装机用户没生效」「发现页分类恒空」「成人源没被隐藏」这类升级路径问题。
---

# 内置书源

`app/src/main/assets/defaultData/bookSources.json` 随包发布：30 个单站源，一个源就是一个站点，发现页不跨源聚合。每源的 `jsLib` 只带自己那一条 `SITES` 记录，规则里取站点配置用 `SITES[0]`。

单条书源规则怎么写，用 `legado-book-source` 技能；本技能只管**内置**这一层的资产、导入与分组。

## 分组只有六档

发现页只有 6 个分组：`小说` / `漫画` / `视频` / 各自 ` · 18+`，定义在 `constant/ExploreGroups.kt`。

- 界面上的分组**不读** `bookSourceGroup` 字面值，而是经 `ExploreGroups.canonical()` 归一：分组名含「小说/漫画/视频」按它定档，没写或写了别的词（「短剧」「玄幻」）退回 `bookSourceType`（0 文本 / 2 图片 / 4 视频），含 `18+` 归成人档。新增源即使分组名写错也不会多出第 7 个分组标题。
- 内置源的分组名仍必须原样是那 6 个字符串（含中间空格），`BuiltInExploreKindsTest.everySourceUsesOneOfTheSixGroups` 拦写错的。运行时的归一是兜底用户自建源，不能用来掩盖内置源的笔误——静默纠正会让人以为改了分组名生效了，实际界面纹丝不动。
- 6 档都是一个 host 一条记录，不靠 `bookSourceUrl` 的路径后缀拆分支。孪生写法（同名常规源加 `/adult` 后缀）会让卡片翻倍而内容完全重复，这类 url 列在 `retiredBookSourceUrls`。

## 成人源判定

**唯一依据：`bookSourceGroup` 含 `AdultContentPolicy.MARKERS` 里的任一标记**（`18+`、`成人`、`🔞`、`R18`、`R-18`，裸 `18` 故意不算，否则「2018 书单」这类年份会被整组误藏）。

- Kotlin 侧走 `containsMarker`，SQL 侧拼 `SQL_GROUP_MATCHES_ADULT`（`instr(lower(…)) > 0` 的 OR 链），两处由同一份 `MARKERS` 生成，**改一处必须同时改另一处**。三处 `@Query` 引用它：`BookDao.ADULT_BOOK_FILTER`、`BookSourceDao.flowAdultSourceUrls`、`ReadRecordDao` 最近阅读计数。
- 不要用 `assets/18PlusList.txt`：它是 208 个 base64 域名的**禁入黑名单**，`SourceHelp.insertBookSource()` 命中就拒绝导入（「…是18+网址,禁止导入.」），实测内置的 11 个成人源一个都不在里面。拿它当成人标记做显示过滤，结果会是「成人源全都不显示、非成人源一个不少」。

## 改动 asset 后的必做动作

1. 改了规则或加了源 → `LocalConfig.needUpBookSources` 的版本号 +1（现为 22），否则老用户拿不到更新。
2. 改了内置源的分组名 → 把旧值登记进 `DefaultData.legacyBookSourceGroups`，同样 +1 版本号。
3. asset 与仓库 `main` 分支同路径的那份**一起改**：远端刷新每次冷启动都跑，只改一边会让装机用户拿到的和随包的不一致。

## 参考文件

| 文件 | 什么时候读 |
| --- | --- |
| `references/import-and-upgrade.md` | 改导入、升级、下线、恢复备份这几条路径前；排查「改了 asset 老用户没生效」 |
| `references/app-runtime-contracts.md` | 改内置源的 `jsLib` / `exploreUrl` / 漫画正文规则前；排查「分类显示成 NativeArray」「缓存命中但数据全空」 |

## 验收

- 单类测试：`BuiltInExploreKindsTest`（分组名与分类可求值）、`BuiltInBookSourceUpgradeTest`（覆盖安装语义）、`RemoteBookSourcesTest`（远端坏响应不落库）、`RestoreBuiltInBookSourceTest`（恢复备份后仍覆盖）、`FanqieNovelExploreTest` / `FanqieDramaQualityTest`（具体源的接口契约）。
- 验证必须模拟**覆盖安装**（`versionCode` 已等于当前版本），不能只测全新安装。
