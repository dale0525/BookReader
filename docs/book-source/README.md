# 聚合书源

这 7 个书源已作为**内置书源**打进 APK：全新安装首次启动时自动写入书源表，用户无需导入。源码副本在 `app/src/main/assets/defaultData/bookSources.json`（与 `aggregate-sources.json` 内容一致，另含仓库原有的 1 个音频源）。

导入阅读后书源列表只多 7 个条目，背后覆盖 18 个站点。

| 书源 | 类型 | 站点数 | 分组 |
| --- | --- | --- | --- |
| 小说·常规 | 文本 | 8 | 小说 |
| 漫画·常规 | 图片 | 5 | 漫画 |
| 视频·常规 | 视频 | 6 | 视频 |
| 小说·成人 | 文本 | 3 | 小说·18+ |
| 漫画·成人 | 图片 | 2 | 漫画·18+ |
| 视频·成人 | 视频 | 2 | 视频·18+ |
| 全站发现 | 文本 | 18 | 发现 |

## 内置机制

`DefaultData.importDefaultBookSources()` 在 `upVersion()` 里按 `LocalConfig.needUpBookSources` 触发一次。规则字段用 asset 里的新值覆盖，`enabled` / `bookSourceGroup` / `customOrder` / `enabledExplore` 保留用户设置——内置源的规则是随包发布的代码资产，不该被旧版本锁死，但用户自己的开关和分组要留住。新增内置源或改规则时把 `bookSourceVersion` 版本号 +1，老用户升级后也会补上。

书源导入**不能**放进 `LocalConfig.versionCode < AppConst.appInfo.versionCode` 那道门禁：本地构建的 versionCode 恒为 32640（无 `COMMIT_NUMBER` 时的兜底值），覆盖安装时两边相等，整段被跳过，只有删除重装才会执行。所以书源导入放在门禁之外，只由自己的 `bookSourceVersion` 计数控制。

构建：

```bash
./gradlew assembleAppDebug -PenableAbiSplits=false
```

## 站点是怎么选出来的

源站来自 yckceo 书源库（`https://www.yckceo.com/yuedu/shuyuan/index.html`）。它的筛选只有 `ver/faxian/sousuo/tu/shengyin` 几个字段，单源规则直链是 `/yuedu/shuyuan/json/id/<id>.json`（顶层数组）。

采集后按下面几道筛：

1. **纯规则候选**：剔除含 `<js>` / `@js:` / `java.` / `$.` 的源，剩 228 个（文本 209、图片 17、音频 1、文件 1）。
2. **域名探活**：409 个唯一 host，183 个返回 200 且正文 > 500 字节。域名停放页（GreatFireVPN、乱码标题）在这一步清掉。
3. **四链路实测**：逐个跑搜索→详情→目录→正文。文本源 108 个里 9 个通过，图片源 17 个里 1 个通过——上游源失效率很高。
4. **补采**：对小说/漫画/影视关键词翻页补 harvest，又补出一批存活站，最终得到 11 个文本源、5 个图片源。

视频源另走一路：yckceo 上 `bookSourceType=4` 只有 4 个，且全部重依赖 `$.*` JSONPath 与自建 jsLib，无法直接并入；改用苹果CMS/maccms 采集接口（`/api.php/provide/vod/`），探活 7 个，全部可用。

## 怎么做到的

一个书源只承载一套规则，站点表存在 `jsLib` 里：

```js
var SITES = [ { id, n, base, kind, search, s:{…}, b:{…}, t:{…}, c:{…}, e:[…] }, … ];
```

- **搜索**：`ruleSearch.bookList` 是一段 JS，逐站抓搜索页、按该站的 `s` 规则取书名/链接，汇总成一个数组返回。单站失败只写日志，不中断其它站。
- **路由**：搜索结果的 `bookUrl` 是站点真实 URL，详情/目录/正文靠 `siteOf(baseUrl)` 按 host 匹配回站点配置，不需要额外标记。
- **发现**：`exploreUrl` 把所有站点的分类平铺成入口，URL 就是站点真实分类页；点进去由 `ruleExplore` 按 host 找到站点再解析。
- **视频**：maccms 采集接口（`/api.php/provide/vod/`）返回 JSON，走 `kind:"api"` 分支：搜索取 `list`，目录把 `vod_play_url` 按 `$$$`/`#`/`$` 拆成剧集，正文返回 m3u8 直链。

## 视频说明

`bookSourceType=4` 在本仓库 APK 里没有视频播放器渲染路径，导入后行为和文本源一致：能看到搜索结果、剧集列表和 m3u8 地址，但不会在阅读界面里播放。这两个条目是**语义存档**，不是可播放的阅读体验。要真播放需要客户端支持。

## 站点

小说·常规：365小说网、玩文学小说网、天天看小说（ttks.tw / ttk.tw）、飞卢小说、顶点小说、饭饭小说8、言情小说阁

漫画·常规：包子漫画、漫画吧、卡拉漫画、喜漫漫画、学外漫画

视频·常规：最大资源、无尽资源、非凡资源、魔都资源、量子资源、如意资源

小说·成人：成人小说网、腐文小说、BB成人小说

漫画·成人：开心看漫画、51漫画

视频·成人：最大资源（伦理/写真类目）、无尽资源

## 验证

每个源都用本机 App 调试接口跑过搜索 → 详情 → 目录 → 正文四链路，并额外验证了发现页入口。最后一次全量结果：

```
novel-normal  ok  search 90   toc 1308  chars 6059     key 剑来
manga-normal  ok  search 33   toc 996   chars 753      key 斗罗大陆
video-normal  ok  search 107  toc 175   chars 70       key 斗罗
novel-adult   ok  search 11   toc 13    chars 11118    key 剑来
manga-adult   ok  search 2    toc 23    chars 8497     key 斗罗大陆
video-adult   ok  search 40   toc 1     chars 73       key 日本
all           ok  search 113  toc 1308  chars 6059     key 剑来
```

搜索条数随关键词变化，不是固定值；目录数与正文字数是站点内容量，可作回归对照。

发现页入口（`--key '<分类名>::<分类URL>'`）也已逐组验证：

```
novel-normal  玄幻      explore 10  toc 2368  chars 5283
manga-normal  日本漫画   explore 40  toc 216   chars 2564
video-normal  动作片     explore 20  toc 1     chars 73
video-adult   日本伦理   explore 20  toc 8     chars 73
all           玄幻      explore 10  toc 2368  chars 5283
```

### 发现分类的契约

`exploreUrl` 里的 JS 必须返回 **JSON 字符串**，不能返回 JS 数组对象——`BookSourceExtensions.exploreKinds()`
拿到 JS 结果后直接 `.toString()` 再当 JSON 解析，返回数组对象会得到
`org.mozilla.javascript.NativeArray@…`，发现页把它当分类名显示，点进去必然报错。

`BuiltInExploreKindsTest` 锁住这条契约：在 Rhino 里跑 `exploreAll(java)`，断言结果是能解析的
JSON 数组、每项 `url` 都以 http 开头。

`BuiltInBookSourceUpgradeTest` 锁住另一条：模拟**覆盖安装**（`versionCode` 已等于当前版本）后
规则仍被更新。只测全新安装会漏掉升级路径——发现页那个 `NativeArray@…` 在装过旧包的设备上
一直存在，就是因为旧规则从未被替换。

分类入口还要求 host 与站点一致（上游有 `ttk.tw → cn.ttkan.co` 这种跨站分类，实测 403），
且不含 `<a,b>` 分页语法。实测不通的入口已在生成时剔除：ttks.tw 全部分类（403）、
腐文小说的 `/sort/N_{{page}}/`（404，正确形式是 `/sort/N-{{page}}.html`）。

复跑：

```bash
node .agents/skills/legado-book-source/scripts/bsv.mjs verify \
  --source https://agg.example.com/novel-normal --key 剑来
```

`bookSourceUrl` 用 `https://agg.example.com/*` 只是占位标识，不会被真正请求——所有抓取都发生在 `jsLib` 里指向的真实站点上。

## 已知限制

- **18+ 组偏薄**：成人小说 3 站、成人漫画 2 站、成人视频 2 路接口。上游可用的就这么多，没有硬凑。
- **发现入口只给已验证的分类**：各站 `exploreUrl` 里很多是 `<js>` 或含分页占位，不能直接平铺；只保留了实测能打开的几组。其余站点仍可通过搜索使用。
- **漫画正文字数是 HTML 长度**，不是图片张数；漫画正文本来就是 `<img>` 标签。

## 维护

站点表在 `jsLib` 开头，改站点只需改那一段 JSON。某站挂了：把它的 `search` 删掉（或整条删掉），其它站不受影响。
