# 聚合书源

这些书源已作为**内置书源**打进 APK：全新安装首次启动时自动写入书源表，用户无需导入。源码副本在 `app/src/main/assets/defaultData/bookSources.json`（与 `aggregate-sources.json` 内容一致）。

书源列表里只有 6 个条目，背后覆盖 27 个站点。一个书源对应发现页的一个 tab，tab 内的同名分类由客户端跨站合并成一个入口。

| 书源 | 类型 | 站点数 | 分类+榜单入口 |
| --- | --- | --- | --- |
| 小说·常规 | 文本 | 8 | 88 |
| 漫画·常规 | 图片 | 4 | 35 |
| 视频·常规 | 视频 | 9 | 107 |
| 小说·成人 | 文本 | 4 | 60 |
| 漫画·成人 | 图片 | 4 | 47 |
| 视频·成人 | 视频 | 2 | 8 |

单站源（听书、四个短剧）全部并入这 6 个分类，不再单独列出：

- 黄果短剧 → 视频·常规（jsoup 规则，正文取页面里的 `<source src>`）
- 饭果短剧 → 视频·常规（接口型，列表走 `$.result.elements[*].contents[*].duanjuVo`）
- 河果短剧 → 视频·常规（接口型，`kgPP` 解包页面内嵌 JSON，只收有 mp4/m3u8 的集）
- 八叉书库 → 小说·常规 + 小说·成人（同时收录两处，内容偏成人）
- 禁漫天堂 → 漫画·成人（接口型，三处签名现算）

原来的「全站发现」已删除：它把上面 6 个聚合源的站点又平铺了一遍，分类入口与其他 tab 完全重复。

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
- **路由**：搜索结果的 `bookUrl` 是站点真实 URL，详情/目录/正文靠 `siteOf(baseUrl)` 按 host 匹配回站点配置，不需要额外标记。`hostOf()` 只比较主机名、忽略协议，站点在 http/https 间跳转时仍能路由。
- **发现**：`exploreUrl` 把所有站点的分类平铺成入口。分类标题是**裸名**（`玄幻`，不带站点前缀），URL 是站点真实分类页并在末尾拼 `#<站点id>` 作为路由标记；榜单档位额外带 `rank` 字段（`update` / `week` / `month` / `all`）。客户端按标题合并同名分类，点进去由 `ruleExplore` 并发取回各站再合并。
- **发现取列表的容错**：`ruleExplore.bookList` 调 `listFrom(..., 'explore')`，先试站点的 `s` 规则，取不到再逐个试该站的 `alt` 备用规则。同一站点在不同页面用不同 DOM（如分类页与榜单页结构不同）时靠 `alt` 兜住。
- **视频**：maccms 采集接口（`/api.php/provide/vod/`）返回 JSON，走 `kind:"api"` 分支：搜索取 `list`，目录把 `vod_play_url` 按 `$$$`/`#`/`$` 拆成剧集，正文返回 m3u8 直链。

## 视频说明

`bookSourceType=4` 的书源不再走阅读界面，点开后是「详情页 + 全屏播放器」两段：

- **详情页**（`VideoPlayScreen`）：封面、书名、标签、总集数、简介、一个播放按钮、选集网格。不内嵌播放器，也没有常驻控制条。
- **播放器**：点播放按钮或任意一集后以全屏浮层打开，播放控制全在这一层。控制层隐藏时单击任意处唤出；双击左半屏退 10 秒、右半屏进 10 秒；长按 3 倍速。控制层含返回、播放/暂停、可拖动进度条、倍速（1x / 1.25x / 1.5x / 2x / 3x）、全屏、静音。
- 倍速与静音跨集保持；播放中控制层 3 秒自动隐藏。全屏开关走裁切铺满，供横版片源使用；默认等比适配，竖屏片源居中留黑边、不裁切。

播放地址复用正文链路：`ruleContent` 留空时 `WebBook.getContentAwait` 直接返回章节 URL，视频源把播放直链放在 `ruleToc` 的章节 URL 里。这样不必为视频另开一套规则，也不会去下载媒体文件。

书源里的 `header` 只作用于规则请求，**不覆盖播放器**。分片和密钥放在独立 CDN 域名上的站点（黄果的 m3u8 指向 `tp*.drleao.cn`）会对裸请求直接断连，必须在播放器这一层再注入一次 `Referer` / `Origin` / `User-Agent`：`VideoPlayViewModel` 把书源 `header` 里这三项挑出来放进 `mediaHeaders`，`PlayerSurface` 用 `DefaultHttpDataSource.Factory().setDefaultRequestProperties()` 喂给 ExoPlayer。规则层能看到地址、却放不出画面的，基本都缺这一步。

短剧源（`book-sources/video-short-drama.json`、`book-sources/maoguo-source.json`）另走一路：四个站点都没有公开采集接口，靠 `jsLib` 解页面里的 `__NEXT_DATA__` 或接口 JSON。河果只放出免费集，付费集的 `chapterVideoVo` 为空，因此 `ruleToc` 会过滤掉没有 `mp4`/`m3u8` 的集。

猫果（qmplaylet）是接口型源，三处都要签名，签名密钥为固定串：

- 搜索 `api-store.qmplaylet.com/api/v1/playlet/search`，`sign = MD5("extend=page=1read_preference=0track_id=<TRACK>wd=<关键词><KEY>")`。
- 详情 `api-read.qmplaylet.com/player/api/v1/playlet/info`，`sign = MD5("playlet_id=<id><KEY>")`。
- 请求头 `qm-params` 是设备信息 JSON 的 Base64 再做一次字符替换表映射，`sign` 是该串的 MD5；两者都必须**每次请求现算**，写死会因过期而返回「验签失败」。

书源规则里的 JS 拿不到 `java.security.MessageDigest`（`java` 绑定的是 `JsExtensions`，且 `RhinoClassShutter` 拦截反射），所以签名用纯 JS 的 MD5 实现，不要用 Java 加密类。

猫果标题带 `<font color=...>` 关键词高亮，`bookList` 必须剥标签后再作为书名，否则搜索页的精确匹配会把条目全部过滤掉。


## 站点

小说·常规：365小说网、玩文学小说网、天天看小说（ttks.tw）、飞卢小说、顶点小说、饭饭小说8、言情小说阁

漫画·常规：包子漫画、漫画吧、卡拉漫画、学外漫画

视频·常规：最大资源、无尽资源、非凡资源、魔都资源、量子资源、如意资源

小说·成人：成人小说网、腐文小说、BB成人小说

漫画·成人：开心看漫画、51漫画、favcomic

视频·成人：最大资源（伦理/写真类目）、无尽资源

卡拉漫画默认 UA 会被拦（只返回 1737 字节的拦截页），所以「漫画·常规」源带了浏览器 UA 的 `header`。

## 验证

每个源都用本机 App 调试接口跑过搜索 → 详情 → 目录 → 正文四链路，并额外验证了发现页入口。最后一次全量结果：

```
novel-normal  ok  search 90   toc 1308  chars 6059     key 剑来
manga-normal  ok  search 33   toc 996   chars 753      key 斗罗大陆
video-normal  ok  search 107  toc 175   chars 70       key 斗罗
novel-adult   ok  search 11   toc 13    chars 11118    key 剑来
manga-adult   ok  search 2    toc 23    chars 8497     key 斗罗大陆
video-adult   ok  search 40   toc 1     chars 73       key 日本
```

搜索条数随关键词变化，不是固定值；目录数与正文字数是站点内容量，可作回归对照。

发现页入口逐条验证过（每个源的每个分类、每个榜单档位各一条，共 288 条，全部 `explore ok`）。
验证脚本串行执行：并行跑会把模拟器上的 App 拖到无响应、调试通道跟着断，结果会全部报失败。

### 发现分类的契约

`exploreUrl` 里的 JS 必须返回 **JSON 字符串**，不能返回 JS 数组对象——`BookSourceExtensions.exploreKinds()`
拿到 JS 结果后直接 `.toString()` 再当 JSON 解析，返回数组对象会得到
`org.mozilla.javascript.NativeArray@…`，发现页把它当分类名显示，点进去必然报错。

`BuiltInExploreKindsTest` 锁住这条契约：在 Rhino 里跑 `exploreAll(java)`，断言结果是能解析的
JSON 数组、每项 `url` 都以 http 开头且带 `#站点id`、分类标题不含 ` · ` 站点前缀、`rank` 取值合法。

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
