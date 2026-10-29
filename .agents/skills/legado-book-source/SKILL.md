---
name: legado-book-source
description: 为任意小说/漫画站点编写、修复、验证阅读（Legado）书源 JSON。当用户要求做一个书源、写搜索/目录/正文规则、排查书源报错或正文为空、或需要用本机 App 调试接口跑通搜索→详情→目录→正文四条链路时使用。产出可直接导入的 JSON 数组。
---

# 阅读书源

写一份能导入、能跑通四链路的书源 JSON。验收只认本机 App 调试接口的实际输出，不认"看着像对"的规则。

## 0. 边界

- 只写规则，不改站点、不绕过反爬。站点要求登录/验证码/加密正文时，按第 6 节收敛，不实现破解。
- 规则语义以本仓库 `docs/dev/` 为准（那是 App 自身的文档）。上游语料是二手的，冲突时以 `docs/dev/` 和实测为准。
- 本仓库内置书源的整体说明见 `docs/book-source/README.md`；内置资产本身的改动走 `builtin-book-sources` 技能。
- 本仓库自己的客户端行为（`jsLib` 作用域、`#id` 路由标记、缓存空串）见 `../builtin-book-sources/references/app-runtime-contracts.md`。

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

选哪套定位语法、索引怎么写、清洗怎么写，查 `references/syntax.md`；选择器取到空或取错元素，查 `references/selector-traps.md`。

`<js>` 里的 `result` 是上一步的输出。多数字段走 `getString`，多匹配时已被拼成一个 **String**（换行分隔），不是数组；`kind` 走 `getStringList`，拿到的是 **JavaList**。不确定时先 `<js>Object.prototype.toString.call(result)</js>` 打一发。

### 硬性禁令

- 不用 `:first` `:last` `:text` `:visible` `:hidden` `:odd` `:even`——jsoup 里不存在，抛 `SelectorParseException`。
- 不用 `:eq(n)` `:lt(n)` `:gt(n)` 取"结果集第 n 个"——那是同级兄弟下标。用索引语法 `tag.a.0`、`[0]`。
- `searchUrl` 不用 `;post=key={{key}}` 旧语法（本分支未实现，参数全丢），改 `,{"method":"POST","body":"..."}`。
- `-` 前缀只用在 `bookList` / `chapterList` 上。
- 顶层必须是数组，写成对象导入直接失败。

各条的实测后果与 `lint` 等级见 `references/selector-traps.md` 与 `references/network.md`。

### 四条链路的必要字段

搜索要 `bookList` 命中列表容器且 `name`/`bookUrl` 非空，详情要 `name`，目录要 `chapterList`+`chapterName`+`chapterUrl`（有分页补 `nextTocUrl`），正文要 `content`（有分页补 `nextContentUrl`）。各字段的取值语义、分页串行与并发的区别、跨章判断见 `references/syntax.md`。

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

通过的唯一判据是 `ok: true` 且 `search`/`detail`/`toc`/`content` 四段都在、都为 `ok`。任何一段缺失、为 `false`，或 `errors` 非空，都算未通过——逐字段定位查 `references/failure-diagnosis.md`，只看输出里的阶段字段和 `errors`，不要猜。

- 只验单条链路：`--book-url` / `--toc-url` / `--content-url`，分别从详情、目录、正文入口起跑。
- 验发现页：`--key '分类名::<发现页 URL>'`，输出里是 `explore` 段而不是 `search` 段（`explore.books` 是抓到的书籍数），后续三段照常跑。
- 搜索被登录墙/验证码挡住但后三条正常：用 `--book-url <真实详情页>` 验证，并在交付说明里写清搜索链路未验证。这是诚实的降级，不是通过。

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
| `references/syntax.md` | 拿不准某条规则该用哪种语法、索引或清洗怎么写 |
| `references/selector-traps.md` | 选择器取到空或取错元素 |
| `references/js-runtime.md` | 要写 `<js>`、不确定 `result` 类型或可用 API |
| `references/network.md` | POST、自定义 header、charset、cookie、登录、发现页 |
| `references/failure-diagnosis.md` | `verify` 报错，要定位到具体字段 |
| `references/upstream-notes.md` | 想知道某个说法出自哪份上游文档 |
| `references/corpus/` | 查上游原文（两套完整语料，含样例书源） |
