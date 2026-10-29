# 导入与升级语义

改动导入路径前读这份。每条结论都来自实测，且多数对应一个已经发过版的缺陷。

## 触发点

- 导入由 `DefaultData.importDefaultBookSources()` 完成，在 `upVersion()` 里按 `LocalConfig.needUpBookSources` 触发。
- **书源导入不能挂在 `LocalConfig.versionCode < AppConst.appInfo.versionCode` 门禁内**：本地构建的 versionCode 恒为 32640（无 `COMMIT_NUMBER` 时的兜底值），覆盖安装时两边相等，整段被跳过，只有删除重装才会执行。这是发现页长期报错的真正成因。
- 书架默认分组（`ensureShelfDefaultGroups()`）走同一套思路，用 `LocalConfig.shelfDefaultGroupsCreated` 一次性标记：不挂 versionCode 门禁，也不每次启动重播（重播会让用户删掉的分组复活）。

## 远端刷新

规则同时发布在仓库同路径的 `main` 分支上，`RemoteBookSources.refresh()` 每次冷启动都静默比对并调用同一个 `DefaultData.importBookSources()`，**不设时间冷却**——`If-None-Match` 让「没变化」只花一个 304 的头部，而冷却会把「推完 main 立刻生效」变成「最坏等半天」。

远端返回空数组、非 2xx 或 HTML 错误页一律不落库——那条路径用户无法预检，出错会直接清空所有人的书源表，`RemoteBookSourcesTest` 守这条。

在真机上验证远端这条路径，要先把两道闸门一起拆掉，否则「没复活」只是没发请求：

1. `bookSourceRemoteEtag`（带上去就是 304）。
2. 「body hash 与上次相同或与随包 asset 相同就直接返回」——远端内容与 asset 一致时永远不写库，用同一份内容验不出任何东西。

清完闸门后 `local.xml` 里落下的 `bookSourceRemoteHash` 就是这次真的落了库的证据。

## 字段级升级语义

| 字段 | 语义 |
| --- | --- |
| 规则字段（`searchUrl`、`ruleToc` …） | asset 覆盖 |
| `enabled` / `customOrder` / `enabledExplore` | 保留用户设置 |
| `bookSourceGroup` | 半保留，见下 |

### `bookSourceGroup` 是半保留的

用户自己改过的组名要留着，但**没人动过的旧内置值必须随 asset 刷新**。判据是「库里存的值是否等于 `DefaultData.legacyBookSourceGroups` 里登记的旧内置值」——相等就刷成新值，不等就是用户自己起的，保留。

改内置源分组名时**必须把旧值登记进那份 map 并 +1 版本号**，否则老用户永远停在旧组名：实测 0.2.0 设备上 `黄果短剧` 停在 `视频`，asset 已是 `视频 · 18+`，于是它不带 `18+` 标记，「隐藏成人内容」对它不生效。`BuiltInBookSourceUpgradeTest` 的两个用例分别守「刷新」与「保留用户值」。

## 下线源

已下线的源列在 `DefaultData.retiredBookSourceUrls`。`importBookSources()` 每次进来**先按 url 删一遍，再按同一份清单过滤传入列表**。

两件事都得做：删除只管库里已有的行，过滤管远端清单自带的旧条目（远端版本由发布节奏决定、随时可能比本地旧，不过滤就会写回库里）。

删除放在 `importBookSources()` 而不是只放在升级路径，是因为远端刷新走的也是它：只在升级时删，`bookSourceVersion` 已推进的设备就永远删不掉下线源。`BuiltInBookSourceUpgradeTest` 的四个用例守这几条。

## 恢复备份

`Restore.restoreBookSources()` 写完备份里的 `bookSource.json` 后必须调 `importDefaultBookSources()` 再覆盖一遍。

备份里的内置源属于**备份时**的版本，而 `bookSourceVersion` 存在 `SharedPreferences("local")` 里、不在备份的 `config.xml`（只导 `AppConfigStore`）中，升级门禁早已推进过——只写回不覆盖的话，旧规则会永久留在库里，发现页一直停在备份那版，且再没有任何路径会纠正它。`RestoreBuiltInBookSourceTest` 守这条。

## 发现页分类缓存

分类缓存按 `md5(bookSourceUrl + exploreUrl)` 存在 ACache（`cache/explore/`）。内置源的 `exploreUrl` 恒为同一段 JS、改的是它背后的 `jsLib`，所以改 `jsLib` 后必须清缓存（`importBookSources` 已调用 `clearExploreKindsCache()`），否则覆盖安装后发现页仍显示旧分类。
