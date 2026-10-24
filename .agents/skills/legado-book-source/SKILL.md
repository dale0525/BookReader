---
name: legado-book-source
description: 为任意小说/漫画站点编写、修复、验证阅读（Legado）书源 JSON。当用户要求做一个书源、写搜索/目录/正文规则、排查书源报错或正文为空、或需要用本机 App 调试接口跑通搜索→详情→目录→正文四条链路时使用。产出可直接导入的 JSON 数组。
---

# 阅读书源

写一份能导入、能跑通四链路的书源 JSON。验收只认本机 App 调试接口的实际输出，不认"看着像对"的规则。

## 0. 边界

- 只写规则，不改站点、不绕过反爬。站点要求登录/验证码/加密正文时，按第 6 节收敛，不实现破解。
- 规则语义以本仓库 `docs/dev/` 为准（那是 App 自身的文档）。上游语料是二手的，冲突时以 `docs/dev/` 和实测为准。
- 需要查语法细节再读 `references/`，不要一开始就全读。

## 1. 环境自检

先确认调试通道存在，否则后面所有验证都无从谈起。

```bash
node <skill>/scripts/bsv.mjs doctor --port 1122
```

`http.ok` 与 `ws.ok` 都为 true 才算通，否则退出码 2。不通时依次查：模拟器在线、App 已启动、App 内 Web 服务已开启、`adb forward tcp:1122 tcp:1122` 与 `adb forward tcp:1123 tcp:1123` 都设了。

端口取 App 内 Web 服务设置里的值，默认 `1122`；WebSocket 恒为该端口 +1。改了端口就把 `--port` 一起改。

调试通道不通就停下报告，不要用 curl 抓页面冒充验证。

## 2. 站点勘察

对目标站点逐条确认，每条都要有实际响应作依据：

1. 搜索入口：URL 形态（路径参数还是 query）、GET 还是 POST、关键字参数名、编码。
2. 详情页：书名、作者、简介、封面、目录链接各自的稳定定位点。
3. 目录页：章节列表容器、章节名/链接、是否有分页（分页 URL 怎么变）。
4. 正文页：正文容器、是否有分页（"下一页"链接形态）、是否 `document.writeln`/base64/字体混淆。
5. 编码：`charset` 是否非 UTF-8。

勘察用 curl 或浏览器都行，但只算"页面长什么样"的证据，不算书源可用。

命中以下任一项就按第 6 节收敛，不要继续写规则：正文由 JS 解密、字体 PUA 映射、签名接口、必须登录且无法获得登录态。

## 3. 写规则

产物是**顶层数组**的 JSON 文件，写到当前工作目录下的 `book-sources/<域名>.json`（目录不存在就建）：

```json
[{ "bookSourceUrl": "https://example.com", "bookSourceName": "...", "searchUrl": "...", "ruleSearch": {...}, "ruleBookInfo": {...}, "ruleToc": {...}, "ruleContent": {...} }]
```

### 定位用哪套语法

| 场景 | 用什么 |
| --- | --- |
| 常规 CSS 定位 | 默认规则 `tag.a.0@text`、`class.list@tag.li`，或 `@css:.list li@text` |
| 按兄弟位置筛 | `:nth-child()`、`:nth-of-type()`、`:first-of-type()`、`:last-of-type()` |
| 按文本筛 | `:contains(文字)`、`:containsOwn(文字)`、`:matches(正则)` |
| 按结构筛 | `:has(子选择器)`、`:not(选择器)` |
| 结果集取第 n 个 | 默认规则的索引语法 `tag.a.0`、`dd.x[0]`、`[0:3]`（`@css:` 段里无效） |
| 接口返回 JSON | `$.data.list[*]`、`@json:$.x` |
| 需要计算 | `<js>…</js>` 或 `@js:…` 作最后一段 |

`<js>` 里的 `result` 是上一步的输出。多数字段走 `getString`，多匹配时已被拼成一个 **String**（换行分隔），不是数组；`kind` 走 `getStringList`，拿到的是 **JavaList**。不确定时先 `<js>Object.prototype.toString.call(result)</js>` 打一发。

### 硬性禁令

| 禁令 | 后果 | lint |
| --- | --- | --- |
| `:first` `:last` `:text` `:visible` `:hidden` `:odd` `:even` | 抛 `SelectorParseException`：写在 `name`/`author` 上整条链路中断，写别处字段为空但 `errors` 里留痕 | error |
| `:eq(n)` `:lt(n)` `:gt(n)` | jsoup 里是"同级兄弟下标"，不是结果集下标，静默取错元素 | warn |
| `searchUrl` 用 `;post=key={{key}}` 旧语法 | 本分支没实现，请求参数全丢 | error |
| `-` 前缀加在 `bookList`/`chapterList` 以外的字段 | 被当成 CSS 选择器的一部分，抛解析异常 | error |
| 顶层写成对象 `{...}` | 导入直接失败 | error |

要"结果集第 n 个"用索引语法 `tag.a.0`、`[0]`，不要用 `:eq`。

### 四条链路的必要字段

- 搜索：`bookList` 必须命中列表容器；`name`、`bookUrl` 不可为空，否则 App 会退化成"把搜索页当详情页"，日志出现 `└列表为空,按详情页解析`，看起来有 1 本书其实是假的。
- 详情：`name` 必填；`tocUrl` 只在目录不在详情页同址时才需要。
- 目录：`chapterList` + `chapterName` + `chapterUrl`；有分页时补 `nextTocUrl`（返回单个 URL 会串行翻页，返回数组会并发）。
- 正文：`content`；正文分页时补 `nextContentUrl`（同样单 URL 串行、数组并发）。判断"下一页"时排除跨章，否则会把下一章正文拼进来。

### 常见字段的清洗

`##` 只有一对匹配/替换。要多处清理就合成一条正则，别叠两个 `##…##`：

| 目标 | 写法 |
| --- | --- |
| 书名带 `[武侠]` 前缀 | `h3 a@text##^\[[^\]]*\]##` |
| 简介去 `简介：`、`【展开】【收起】`、推广尾巴 | `@css:#intro_pc@text##简介：\s*\|【展开】【收起】\|您要是觉得[\s\S]*##` |
| 站点给的是 `//cdn.x.com/a.jpg` 这类协议相对地址 | `@css:img@src##^//##https://##` |
| 正文去分页标记 | `@css:article@html##第\(\d+/\d+\)页##` |

## 4. 静态检查

```bash
node <skill>/scripts/bsv.mjs lint book-sources/<域名>.json
```

有 `error` 就改到 0。`warn` 逐条判断：多半是真的会踩坑，但也可能是你确实要这个行为。

## 5. App 端四链路验证

```bash
node <skill>/scripts/bsv.mjs install book-sources/<域名>.json
node <skill>/scripts/bsv.mjs verify --source <bookSourceUrl> --key <一个真实存在的书名>
```

`verify` 从搜索一路跑到正文，输出每阶段 `ok`、列表大小、书籍总数、目录总数/页数、正文页数与字数、正文预览、错误列表。

判定标准：

- `ok: true` 且 `search`/`detail`/`toc`/`content` 四段都在、都为 `ok` —— 通过。
- `search.listEmptyFallback: true` —— 搜索规则没命中列表，改 `bookList`。
- `detail.skipped: true` 且 `search.bookUrl` 为空 —— 搜索没取到详情页链接。
- `toc.chapters` 为 0 或 `errors` 里有 `TocEmptyException` —— 目录规则错。
- `content.chars` 为 0 或 `errors` 里有 `ContentEmptyException` —— 正文规则错。

只验单条链路时用 `--book-url` / `--toc-url` / `--content-url`，它们分别从详情、目录、正文入口起跑。

验发现页用 `--key '分类名::<发现页 URL>'`，此时输出里是 `explore` 段而不是 `search` 段（`explore.books` 是抓到的书籍数），后续详情/目录/正文照常跑。

搜索被登录墙/验证码挡住但详情、目录、正文正常时，用 `--book-url <一个真实详情页>` 验证后三条，并在交付说明里写清搜索链路未验证。这是诚实的降级，不是通过。

失败时按 `references/failure-diagnosis.md` 的判据定位；只看 `verify` 输出里的 `errors` 和阶段字段，不要猜。

验证通过后清理测试源：

```bash
node <skill>/scripts/bsv.mjs cleanup --source <bookSourceUrl>
```

## 6. 停止条件

出现以下情况停止实现并如实报告，不写规则、不"试试看"：

| 情况 | 报告内容 |
| --- | --- |
| 正文由 JS 解密 / base64 / `document.writeln` 拼装 | 哪个页面、什么手法、为什么不做 |
| 字体 PUA 映射混淆 | 同上 |
| 请求带签名且必须复现站点前端算法 | 同上 |
| 必须登录，且拿不到登录态 | 需要用户提供什么 |
| 搜索入口是验证码/Cloudflare | 哪条链路被挡，其余链路是否仍可验证 |

不要用 WebView 兜底、不要逆向签名、不要实现解码器。

## 7. 交付

- 书源 JSON 落在 `book-sources/<域名>.json`，顶层数组，UTF-8。
- 交付说明只写：书源名、`bookSourceUrl`、验证结果（四链路各自证据）、已知缺陷（如简介带推广尾巴、某字段取到多余文本）、未验证的部分及原因。
- 没有 App 端 `verify` 的 `ok: true` 输出，不许说"已完成"/"可用"。

## 参考文件

按需读，不要预读全部：

| 文件 | 什么时候读 |
| --- | --- |
| `references/syntax.md` | 拿不准某条规则该用哪种语法、连接符怎么写 |
| `references/selector-traps.md` | 选择器取到空或取错元素 |
| `references/js-runtime.md` | 要写 `<js>`、不确定 `result` 类型或可用 API |
| `references/network.md` | POST、自定义 header、charset、cookie、登录 |
| `references/failure-diagnosis.md` | `verify` 报错，要定位到具体字段 |
| `references/upstream-notes.md` | 想知道某个说法出自哪份上游文档 |
| `references/corpus/` | 查上游原文（两套完整语料，含样例书源） |
