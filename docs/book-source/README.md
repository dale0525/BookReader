# 内置书源

这些书源已作为**内置书源**打进 APK：全新安装首次启动时自动写入书源表，用户无需导入。源码副本在 `app/src/main/assets/defaultData/bookSources.json`。

## 一个源就是一个站

内置源共 29 个，每个对应一个真实站点，书源列表里就能直接看到站名。发现页不跨源聚合，一张卡片就是一个站，点进去只看这个站的分类和榜单。

| 分组 | 源 | 类型 | 入口 |
| --- | --- | --- | --- |
| 小说 | 天天看小说、顶点小说、精华书阁、看书网、言情小说阁、台湾小说网、番茄小说 | 文本 | 分类 + 周期榜单 |
| 漫画 | 包子漫画、漫画吧、卡拉漫画、学外漫画 | 图片 | 分类 + 周期榜单 |
| 视频 | 最大资源、非凡资源、魔都资源、如意资源、饭果短剧、河果短剧、番茄短剧 | 视频 | 分类 |
| 小说 · 18+ | 八叉书库、成人小说网、腐文小说、BB成人小说、爱丽丝书屋、UAA小说 | 文本 | 分类 + 周期榜单 |
| 漫画 · 18+ | 开心看漫画、51漫画、favcomic、禁漫天堂 | 图片 | 分类 + 周期榜单 |
| 视频 · 18+ | 黄果短剧 | 视频 | 分类 |

分组只有这 6 档，没有第 7 档。发现页不读 `bookSourceGroup` 的字面值，而是经 `ExploreGroups.canonical()` 归一：分组名写了「小说 / 漫画 / 视频」就按它定档，没写或写成别的词就退回 `bookSourceType`（0 文本 / 2 图片 / 4 视频），含 `18+` 归到成人档。所以新加的源即使分组名写错标点、或者干脆写了「短剧」，也不会在发现页多出一个分组标题。

反过来，内置源的分组名必须是这 6 个字符串之一（含中间的空格），`BuiltInExploreKindsTest.everySourceUsesOneOfTheSixGroups` 会拦下写错的。运行时归一只是兜底用户自己导入的源，不该用来掩盖内置源的笔误——静默纠正会让人以为改了分组名生效了，实际界面纹丝不动。

每张卡片的副标题是 `bookSourceComment`，写着这个站能提供多少分类和榜单（如 `10 个分类 · 2 个榜单`）。没有分类入口的源写 `仅搜索`，卡片仍留在发现页，只是点进去直接给搜索。

18+ 视频档只有黄果短剧一个独立站点。孪生写法（同名常规源加 `/adult` 后缀）会让卡片翻倍而内容完全重复，最大资源、无尽资源那两条 url 留在 `retiredBookSourceUrls` 里，每次导入都按它删、按它滤。

视频档下线的无尽资源、量子资源是另一回事：它们能搜到「持械入宋」，但前者只给一条「全集」播放地址、后者只给一集，目录根本凑不齐，用户点进去看到的就是一集完结。

小说档下线的两个源（飞卢小说、饭饭小说8）也是同理：它们对书架上的 40 本书零命中，搜不到其中任何一本，留着只占卡片，url 同样登记在 `retiredBookSourceUrls`。

八叉书库曾按「零命中」下线过，随后按要求恢复并改判到 `小说 · 18+`：它的站内搜索命中率低（关键词打不中），但目录和正文完整，且站内有成人内容。改分组时旧值 `小说` 登记进了 `DefaultData.legacyBookSourceGroups`，老设备的组名才会跟着刷新。

它的搜索页有一处必须照抄站点的表单参数：`/e/search/index.php` 只认 `show=title,writer,byr&searchget=1` 这套组合，少一个参数就回「没有搜索到相关的内容」。目录分 32 页、每页 58 章，`nextTocUrl` 用 `text.Next@href`（末页的 `Next` 不带链接，翻页自然停）。

封面来源：言情小说阁的分类页自带封面；爱丽丝书屋和台湾小说网的分类页是纯文字列表，站点不提供封面，只能逐本取详情页。后两者用 `source.put` 按书号缓存，同一本书只抓一次（首屏约 40 秒，之后约 3 秒）。分类页本身没有封面的站点在 `SITES` 里标 `coverFromDetail: true`。

## 规则怎么更新

规则同时存在两处：

- APK 里的 `app/src/main/assets/defaultData/bookSources.json`，用于首次安装、离线启动、远端取不到时兜底。
- 仓库同一路径的 `main` 分支，装机用户每次启动都静默比对，有变化就覆盖规则。

所以改规则只要把 asset 和远端那份一起 push 到 `main`，用户下次启动就能拿到，不必等发版。远端更新沿用与 asset 完全相同的覆盖语义（见下），不会动用户自己的开关和分组。

远端读取走 `RemoteBookSources`：先试 `raw.githubusercontent.com`，失败再试 jsDelivr 镜像；带 `If-None-Match`，内容摘要与上次相同就跳过写库。空数组、非 2xx、HTML 错误页一律当「这次没更新」，绝不落库——远端出问题不能把用户手里那张可用的书源表清空。`RemoteBookSourcesTest` 守这条。

发版时才需要动 `bookSourceVersion`：asset 的导入由 `LocalConfig.needUpBookSources` 触发一次，改了 asset 里的规则或加了源，把版本号 +1，老用户覆盖安装后才拿得到。

## 内置机制

`DefaultData.importDefaultBookSources()` 在 `upVersion()` 里按 `LocalConfig.needUpBookSources` 触发。规则字段用 asset 里的新值覆盖，`enabled` / `bookSourceGroup` / `customOrder` / `enabledExplore` 保留用户设置——内置源的规则是随包发布的代码资产，不该被旧版本锁死，但用户自己的开关和分组要留住。远端更新走同一个 `importBookSources()`，语义一致。

书源导入**不能**放进 `LocalConfig.versionCode < AppConst.appInfo.versionCode` 那道门禁：本地构建的 versionCode 恒为 32640（无 `COMMIT_NUMBER` 时的兜底值），覆盖安装时两边相等，整段被跳过，只有删除重装才会执行。所以书源导入放在门禁之外，只由自己的 `bookSourceVersion` 计数控制。

分类缓存按 `md5(bookSourceUrl + exploreUrl)` 存在 ACache（`cache/explore/`）。内置源的 `exploreUrl` 恒为同一段 JS、改的是它背后的 `jsLib`，所以改 `jsLib` 后必须清缓存（`importBookSources` 已调用 `clearExploreKindsCache()`），否则覆盖安装后发现页仍显示旧分类。

共享作用域按 `md5(jsLib)` 缓存，远程 jsLib 更是把整段规则按 `md5(url)` 存在 ACache 里且永不过期；`importBookSources` 会调 `SharedJsScope.remove(existing.jsLib)` 清掉，否则改了 asset 也会继续跑旧规则。

`DefaultData.retiredBookSourceUrls` 列出已停止分发的书源：每次导入都先按 url 从库里删掉，再按同一份清单过滤传入的列表。删除管的是库里已有的行，过滤管的是远端清单自带的旧条目——远端版本由发布节奏决定，随时可能比本地旧，不过滤就会写回库里。两件事都放在 `importBookSources()` 里，asset 与远端两条路径共用它，所以对两条路径都生效；只在升级路径里删的话，`bookSourceVersion` 已推进的设备远端刷新只能加新源、删不掉下线源。

聚合源挂在 `example.com` 这个占位域名下，没有真实内容，书架上的书不可能靠它读到正文。`BuiltInBookSourceUpgradeTest` 的 `upgradeDeletesRetiredAggregateSources`、`importIgnoresRetiredAggregateSources` 与 `importRemovesRetiredSourcesAlreadyInDb` 守这几条。

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

## 规则长什么样

一个书源只承载一套规则，站点表存在 `jsLib` 里：

```js
var SITES = [ { id, n, base, kind, search, s:{…}, b:{…}, t:{…}, c:{…}, e:[…] }, … ];
```

拆成单站源后每个 `SITES` 只有一项，规则里取站点配置就是 `SITES[0]`。

- **搜索**：`ruleSearch.bookList` 是一段 JS，抓该站的搜索页、按 `s` 规则取书名/链接，返回数组。
- **路由**：搜索结果的 `bookUrl` 是站点真实 URL，详情/目录/正文靠 host 匹配回站点配置。`hostOf()` 只比较主机名、忽略协议，站点在 http/https 间跳转时仍能路由。
- **发现**：`exploreUrl` 恒为 `<js>exploreAll(java)</js>`，把该站的分类和榜单档位列成入口。榜单档位额外带 `rank` 字段（`update` / `week` / `month` / `all`），发现页据此把榜单做成卡片、把普通分类做成 tab。
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

## 各站的额外条件

卡拉漫画默认 UA 会被拦（只返回 1737 字节的拦截页），所以「卡拉漫画」源带了浏览器 UA 的 `header`。

精华书阁的目录是分页的：`/{id}/` 只给前 20 章，翻页在 `<span class="right"><a href="/{id}/index_2.html">下一页</a>`，`ruleToc.nextTocUrl` 必须写 `text.下一页@href`，否则目录只有 20 章、正文永远停在第 20 章。正文页里夹着「阅读提示：…」「☆免费小说阅读…☆」「本章未完，请点击下一页继续阅读》》」三种站点广告，`c.content` 用 `##` 一次性删掉。

看书网的目录同样分页，但页码不是链接而是文本 `1/14`（`class.pages` 里第一个 `li` 的 `a`），所以 `nextTocUrl` 用 `<js>` 读 `result` 里的 `n/m`、按 `baseUrl` 拼出 `index_<n+1>.html`，取不到或已到末页就返回空串。正文分页在同一章内（`第(n/m)页`），靠 `nextContentUrl` 走 `下一章` 链接把整章拼起来。

天天看小说的正文里每章都插一行换着写法、换着字体的广告（`天天看小说`+数学字母域名的各种变体），纯 `##` 正则只能删掉其中一部分；`c.content` 改用 `<js>` 调 `contentOf()` 取到正文后按行过滤，命中「天天看小说」或私用区/数学字母/圈号字符的行整行丢弃。

台湾小说网的榜单页和全本页**必须带页码段**：`/ranking/hits/`、`/full/` 直接返回 `401`、body 为 0 字节，`/ranking/hits/1.html`、`/full/1.html` 才是 `200`。所以 `rk` 里的 url 一律写成 `{{page}}.html`，不能省。主域用 `https://www.twkan.cc`：裸 `twkan.com` 403，`twkan.cc` 会 301 跳到带 `www` 的地址。

UAA小说是官方 App 的 JSON 接口（不是网页），三个限制都是服务端按 `viewId` 算的，写规则时必须知道：

- 每个 `viewId` 终身 10 章，**不按天重置**。匿名只读免费章（通常 4–5 章），登录后 10 章内的会员章节也能读全文。
- 请求过快返回 `code=478`，此时 `java.getVerificationCode()` 弹图片验证码，答对后 `code` 归 0；但额度用完时验证码只能把 478 降级回 `code=1`，不会补额度。
- 两种错误码要分开提示：`code=1` 是会员墙/额度耗尽，`code=478` 是限流。混在一起用户分不清。

登录走 `loginUi` + `loginUrl`（账号密码换 JWT 存进 `putLoginHeader`），不用 WebView。

番茄小说的三条链路落在不同接口上，正文还有一层字形混淆：

- 搜索必须走 App 签名接口 `/reading/bookapi/search/page/v`。网页版 `/search/{kw}` 的 `searchBookList` 恒为空，服务端只渲染空壳。签名复用番茄短剧那套 `FV_get` + `A551`，所以两个源的 `jsLib` 都带完整签名栈。
- 目录和正文走网页端免签接口：`/api/reader/directory/detail?bookId=` 一次给全量 `chapterListWithVolume`（带卷名），正文取 reader 页内嵌的 `chapterData.content`。
- 正文约一半的字被换成私用区码位，靠哈希为 `dc027189e0ba4cd` 的字体渲染。客户端拿不到字体，只能靠 `jsLib` 里的 `FQ_MAP` 逐字还原。
- 发现页走 `api-lf.fanqiesdk.com` 的榜单接口，**不要用网页端 `/rank/0_1_{id}`**：那个页面的 `book_list` 由客户端 JS 填充，服务端渲染出来是 `[]`，规则解析到空数组不报错，界面只显示「没有更多数据」，和「分类本来没书」完全分不开。`side_type` 选榜单、`type` 选频道（1 男频 / 0 女频）、`offset` 真能翻页；实测 `side_type` 10/11/12 各能翻到 120 本，13/15 翻到第 3 页会回到开头，没有采用。该接口返回的书名不带私用区字符，比解析榜单页 HTML 干净。

`FQ_MAP` 是**位置敏感**的：第 i 个字符就是码位 `0xe3e8 + i` 的真字。该字体在 `0xe3e8`–`0xe55b` 之间有 10 个码位没有字形，表里必须留空字符占位；把空位删掉会让后面全部错位，而且不抛异常，只会安静地把整章正文换成别的字。`FanqieNovelPuaMapTest` 冻结这三条（表长、空位、首尾码位）。

字体换了之后表会整体失效，表现为正文出现方块或错字。换字体时按「渲染字形 → 与 Source Han Sans SC Normal 同款字体做位图匹配 → 按码位顺序重拼」重建，并用测试里的首尾码位标定。

付费章节（`needPay=1`）拿不到正文，规则只覆盖免费章。

听书（音频）接不了，实测结论如下，不要重复试：

- `reading/bookapi/audio/toneinfo/?aid=1967&book_id=` 免签可用，能给音色清单（`tts_tones` 七档 AI 音色、`audio_tones` 真人音频书的 `abook_id`、`related_audio_bookids`），但只是元数据。
- 音频本身拿不到：`audio/abook/{info,detail,catalog,item,chapter,group,album}/v`、`audio/album/*`、`audio/book/*`、`audio/track/*`、`audio/play`、`audio/url` 全部 `404 Page not found`（网关级，路径不存在）；`audio/detail/` 对任何 aid 都是 `200` 空体；`audio/tts/*`、`audio/synthesis`、`audio/text` 全部 404。
- 网页端 reader 页里 `fmReadEnable` 对多数书是 `false`、`phoneUrl` 为空，没有可用入口。
- 第三方下载器也不用番茄的音频：`Tomato-Novel-Downloader` 的有声书导出走 `edge_tts`（微软 Edge 朗读），与番茄接口无关。要「听书」应配 TTS 引擎，不要指望抓番茄音频。

## 验证

每个源都用本机 App 调试接口跑过搜索 → 详情 → 目录 → 正文四链路，并额外验证了发现页入口。抽样结果：

```
台湾小说网    ok  search 12   toc 2033  chars 3701     key 斩神
包子漫画      ok  search 33   toc 996   chars 753      key 斗罗大陆
最大资源      ok  search 107  toc 175   chars 70       key 斗罗
成人小说网    ok  search 11   toc 13    chars 11118    key 剑来
禁漫天堂      ok  search 2    toc 23    chars 8497     key 斗罗大陆
黄果短剧      ok  search 20   toc 10    chars 190      key 总裁
UAA小说       ok  search 20   toc 463   chars 3330     key 都市
八叉书库      ok  search 1    toc 1587  chars 4842     key 玄浑道章
番茄小说      ok  search 6    toc 1496  chars 2540     key 十日终焉
```

番茄短剧的画质由 `FV_playData` 从播放接口返回的四档里挑：实测只有 360p / 480p / 540p / 720p，全是 bytevc1（标准 H.265），没有 1080p；`auto_definition` 恒为 `360p`，那是平台自己的默认值，不能当「最高档」用。规则未设置 `hgq` 时取接口给出的最高档，设置后按 `definition` 精确匹配。不要为「起播快」把默认档位写死成低清——解密发生在点播放之后（`VideoPlayViewModel.loadPlayUrl()` 调正文规则期间 `isLoadingUrl = true`，播放器已在转圈），低清换来的时间用户看不见。`FanqieDramaQualityTest` 守这条。

搜索条数随关键词变化，不是固定值；目录数与正文字数是站点内容量，可作回归对照。

书架那 40 本书的覆盖是逐本验过的：39 本至少一个内置源能搜到，每本取中后段 3 章比对正文，均无缺失、无乱码、无站点插入的替换内容。唯一搜不到的是《重生后的妻管严生活》，yckceo 上可用的常规小说源全部没有这本。

判定「正文合格」时排除的是站点自己加的东西，不是原文：站点广告行、`阅读提示：…`、`本章未完，请点击下一页继续阅读》》`、`第(n/m)页`。**正文里出现 `███` 或 `□□` 这类字符不算站点换文**——它出现在源站 HTML 里，是站点对敏感词的自我屏蔽（如看书网《谁让他修仙的！》第 551 章「这气息是███！」），同书的另一个源给的是完整原文，换源即可读到。

顶点小说的搜索是**频率限制**，不是接口抖动：站点对搜索做了 15 秒冷却，间隔不够时它不返回错误，而是回一页结构完整、`<ul>` 为空、正文里夹一句 `<script>alert('搜索间隔15秒,请稍后')</script>` 的页面——看起来和「没有这本书」一模一样。

这个站因此在 `SITES` 记录里多一个 `wait` 字段：`{"mark":"搜索间隔","ms":16000,"times":4}`。`searchAll` 命中 `mark` 就等 `ms` 毫秒重发，最多 `times` 次。没写 `wait` 的站行为完全不变（判据是字段缺失，不是空值）。实测 5 次冷搜索全部命中，平均 1–3 次重试、耗时 19–52 秒。

判据用那句 `搜索间隔` 脚本，不用「列表为空」：真正的零命中页标题是 `搜索"关键词"的结果`、`<ul>` 也是空的，只有这句脚本能把「站点限频」和「真没这本书」分开。

光判出来还不够——**立刻重发一定还是被限频**，冷却按时间算。第一版只加了「空结果重试 4 次」，实测 6 次搜索 0 命中；加上 `Thread.sleep(16000)` 之后 5 次全中。所以 `wait` 必须同时给等待时长，不能只给次数。

发现页入口逐条验证过（每个源的每个分类、每个榜单档位各一条，全部 `explore ok`）。当前 asset 共 389 条入口；台湾小说网新增的 24 条（18 分类 + 6 榜单）全部单独跑过，均返回 12 本。
验证脚本串行执行：并行跑会把模拟器上的 App 拖到无响应、调试通道跟着断，结果会全部报失败。

### 发现分类的契约

`exploreUrl` 里的 JS 必须返回 **JSON 字符串**，不能返回 JS 数组对象——`BookSourceExtensions.exploreKinds()`
拿到 JS 结果后直接 `.toString()` 再当 JSON 解析，返回数组对象会得到
`org.mozilla.javascript.NativeArray@…`，发现页把它当分类名显示，点进去必然报错。

`BuiltInExploreKindsTest` 锁住这条契约：在 Rhino 里对 29 个源逐个跑 `exploreAll(java)`，断言结果是能解析的
JSON 数组、每项 `url` 都以 http 开头、`rank` 取值合法；除标了「仅搜索」的源外，每个源都必须给出非空入口。

`BuiltInBookSourceUpgradeTest` 锁住另一条：模拟**覆盖安装**（`versionCode` 已等于当前版本）后
规则仍被更新，且用户的分组/排序/开关不被覆盖。只测全新安装会漏掉升级路径——发现页那个 `NativeArray@…`
在装过旧包的设备上一直存在，就是因为旧规则从未被替换。

分类入口还要求 host 与站点一致（上游有 `ttk.tw → cn.ttkan.co` 这种跨站分类，实测 403），
且不含 `<a,b>` 分页语法。实测不通的入口已在生成时剔除：ttks.tw 全部分类（403）、
腐文小说的 `/sort/N_{{page}}/`（404，正确形式是 `/sort/N-{{page}}.html`）。

复跑：

```bash
node .agents/skills/legado-book-source/scripts/bsv.mjs verify \
  --source https://zuidazy.co --key 剑来
```

`bookSourceUrl` 就是站点主域，除采集接口类源外都会被真实请求。

## 已知限制

- **18+ 组偏薄**：成人小说 4 站、成人漫画 4 站、成人视频 1 站。上游可用的就这么多，没有硬凑。
- **发现入口只给已验证的分类**：各站 `exploreUrl` 里很多是 `<js>` 或含分页占位，不能直接平铺；只保留了实测能打开的几组。其余站点仍可通过搜索使用。
- **favcomic 只有搜索**：站点分类页没有可用列表结构，发现页卡片标「仅搜索」。
- **漫画正文字数是 HTML 长度**，不是图片张数；漫画正文本来就是 `<img>` 标签。

## 维护

站点表在 `jsLib` 开头，改规则只需改那一段 JSON，然后同时更新 asset 与 `main` 分支上的同名文件。某站挂了：把它从 asset 里删掉（并同步远端），其它源不受影响。
