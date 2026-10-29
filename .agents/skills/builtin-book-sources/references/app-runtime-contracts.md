# App 侧运行时契约

内置源的规则跑在 App 自己的运行时里，以下每条都是客户端行为决定的，不是站点特性。改内置源的 `jsLib` / `exploreUrl` / 漫画正文规则前必读。

## `bookSourceComment` 要单独查，不要拉整行

`bookSourceComment` 是发现页卡片的副标题，也是唯一能承载「这个站有多少分类/榜单」的字段。它不在 `bookSource_part` 里，读它必须查 `book_sources`（`BookSourceDao.flowExploreSummaries()`），不要为了显示副标题去拉整行——每行带 200KB+ 的 `jsLib`。同一个投影还要带上 `bookSourceType`，它是归组的依据。

## `exploreUrl` 必须返回 JSON **字符串**

`BookSourceExtensions.exploreKinds()` 拿到 JS 结果后直接 `.toString()` 再当 JSON 解析。返回 JS 数组对象会得到 `org.mozilla.javascript.NativeArray@…`，发现页把它当分类名显示，点进去必然报错。

`BuiltInExploreKindsTest` 守这条契约。

## `source.get(key)` 缺键返回空串，不是 null

`source.put` 的底层键是 `v_${sourceKey}_${key}`（落在 `caches` 表）。判缓存命中不能只写 `!= null`，否则首次读到的空值会被当成命中，表现为「缓存已生效但数据全空」。

## `jsLib` 里没有 `baseUrl` 和 `source`

`jsLib` 是共享作用域，这两个名字只在 `exploreUrl` / `ruleExplore` 的 `<js>` 块里可用。要在共享函数里用 `source`，得在调用处把句柄挂到站点对象上传进去（`s.v=source`），并在共享函数里判断句柄非空再调用。

登录函数同理，必须定义在 `loginUrl` 里——三种写法的实测差别见 `legado-book-source` 技能的 `references/network.md`。

## 漫画正文图片不经过 `CoverFetcher`

`CoverInterceptor` 会先把在线页下载成本地文件，再以 `data(File)` 交给解码器。任何需要改写图片字节的处理（如禁漫天堂的切条重排）必须接在 `BookHelp.saveImageFile` 的落盘处，并且要绕开 `skipDecode` 的流式直写分支，否则错位原图会被缓存下来反复显示。

## 发现页分类地址末尾的 `#id` 是路由标记

不是 query 的一部分。`AnalyzeUrl` 切 query 时以 `?` 起、以 `#` 止，否则标记会被编码成 `%23id` 一起发出去——实测 UAA 的接口对多余参数直接回 400，body 只有 120 字节的错误 JSON，列表恒空、界面只显示「没有更多数据」，而同一份规则走搜索链路（`searchUrl` 不带 `#`）完全正常，极易误判成规则写错。`AnalyzeUrlPagingTest.siteMarkerAfterQueryIsNotSentAsParameter` 守这条。
