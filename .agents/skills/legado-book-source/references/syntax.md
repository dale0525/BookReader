# 规则语法速查

只在拿不准用哪种语法时查。App 自身文档 `docs/dev/syntax.md`、`docs/dev/rule.md`、`docs/dev/url-options.md` 是权威版本。

## 选哪套语法

| 场景 | 用什么 |
| --- | --- |
| 常规 CSS 定位 | 默认规则 `tag.a.0@text`、`class.list@tag.li`，或 `@css:.list li@text` |
| 按兄弟位置筛 | `:nth-child()`、`:nth-of-type()`、`:first-of-type()`、`:last-of-type()` |
| 按文本筛 | `:contains(文字)`、`:containsOwn(文字)`、`:matches(正则)` |
| 按结构筛 | `:has(子选择器)`、`:not(选择器)` |
| 结果集取第 n 个 | 默认规则的索引语法 `tag.a.0`、`dd.x[0]`、`[0:3]`（`@css:` 段里无效） |
| 接口返回 JSON | `$.data.list[*]`、`@json:$.x` |
| 需要计算 | `<js>…</js>` 或 `@js:…` 作最后一段 |

## 规则标志

| 标志 | 语法 | 省略条件 |
| --- | --- | --- |
| 默认（CSS） | 直接写 | 总是可省 |
| `@css:` | `@css:选择器@动作` | 可省，与默认等价 |
| `@xpath:` | `@xpath:表达式` | 以 `//` 开头时可省 |
| `@json:` | `@json:表达式` | 以 `$.` 或 `$[` 开头时可省 |
| `@js:` | `@js:代码` | 不可省 |
| `<js>` | `<js>代码</js>` | 不可省 |
| 正则 | `:表达式` | 不可省；只在 `bookList`/`chapterList` 这类列表字段生效 |

规则串里可以混排多段，按出现顺序依次执行，前一段的输出是后一段的输入：

```
tag.dl@tag.dd.0@text##作者：##
@css:.list li@text@js:result.trim()
h3 a@text##^\[[^\]]*\]##
```

## 默认规则的段结构

用 `.` 分三段：`类型.名称.位置`。

| 类型 | 含义 |
| --- | --- |
| `class` | 按 class 名取 |
| `tag` | 按标签名取 |
| `id` | 按 id 取 |
| `children` | 取子元素 |
| `text` | 取含指定自身文本的元素 |

位置从 0 开始，负数倒数：`tag.a.0`、`tag.a.-1`。

段与段之间用 `@` 连接，最后一段是取值动作：

| 动作 | 结果 |
| --- | --- |
| `text` | 元素及其子元素的全部文本 |
| `ownText` | 只取自己的直接文本节点（不含子元素） |
| `textNodes` | 自己的直接文本节点，多行拼接 |
| `html` | 元素的 **outerHTML**，已删掉 script/style |
| `all` | 元素的 **outerHTML**，原样不动 |
| `href` / `src` / 任意属性名 | 该属性值 |

`html` 与 `all` 都是 outerHTML，差别只有一处：`html` 会先删 script/style。实测对同一个 `<dd>`：

```
@html → <dd class="book_other">\n 最新章节：<a href="…">第二百一十三章…</a>\n</dd>
@all  → 同上（若元素里有 <script>/<style>，@all 会带出来，@html 不会）
```

想要"只要内部内容"，用 `@html` 之后再 `##^<dd[^>]*>|</dd>$##` 去掉外壳，或者直接用 `@text`。

## 结果集索引

索引接在**选择器段**后面（`@` 之前），不能接在动作后面：

| 写法 | 含义 |
| --- | --- |
| `tag.dd.1@text` | 第 2 个 dd 的文本 |
| `tag.dd[1]@text` | 同上 |
| `tag.dd.-1@text` | 最后一个 dd |
| `tag.dd[0:3]@text` | 前 3 个 |
| `tag.dd[2:5]@text` | 第 3 到第 5 个 |
| `tag.dd[0:10:2]@text` | 隔一个取一个 |
| `tag.dd[!0:2]@text` | 排除第 1、3 个 |
| `tag.dd[-1:0]@text` | 反转 |

`tag.dd@text.1`、`tag.dd@text[1]` 这两种写法**取不到值**，索引要往前挪。

索引只在默认规则段里生效。`@css:dd.0@text`、`@css:dd[0]@text` 都取不到值——那段整体是 CSS 选择器，`.0` 被当 class 名、`[0]` 被当属性选择器。`@css:` 段要按位置取只能用 `:nth-child()` 之类。

不给索引时，动作作用在每个匹配元素上：`tag.dd@text` 返回所有 dd 的文本，多行拼接。

## 正则替换

```
规则##匹配##替换##
规则##匹配##
```

| 形态 | 行为 |
| --- | --- |
| `##匹配##替换##` | 全局替换，所有匹配都换成替换内容 |
| `##匹配##` | 全局删除匹配 |

**`##` 出现三次以上就变成"只取第一个匹配"**：解析时按 `##` 切段，段数超过 4 就置 `replaceFirst`，此时返回值只有第一个匹配的替换文本，整串其余内容全丢。

实测：`作者：来一杯咖啡##作##A###` 得到 `A`；`@css:#intro@text##^简介：\s*####【展开】【收起】##`（想连做两次清理）得到空串。

要多处清理就合成一条带 `|` 的正则：

```
@css:#intro_pc@text##简介：\s*|【展开】【收起】|您要是觉得[\s\S]*##
```

正则按 Java 语法写，JSON 里 `\d`、`\[`、`\.` 都要写成 `\\d`、`\\[`、`\\.`。

### 常见字段的清洗写法

| 目标 | 写法 |
| --- | --- |
| 书名带 `[武侠]` 前缀 | `h3 a@text##^\[[^\]]*\]##` |
| 简介去 `简介：`、`【展开】【收起】`、推广尾巴 | `@css:#intro_pc@text##简介：\s*\|【展开】【收起】\|您要是觉得[\s\S]*##` |
| 站点给的是 `//cdn.x.com/a.jpg` 这类协议相对地址 | `@css:img@src##^//##https://##` |
| 正文去分页标记 | `@css:article@html##第\(\d+/\d+\)页##` |

## 连接符

| 符号 | 语义 | 实测 |
| --- | --- | --- |
| `\|\|` | 取第一个非空结果，短路——后面的段不再求值 | 第一段命中就不看第二段 |
| `&&` | 各段结果**顺序拼接**，换行连接 | 段1 的 4 行在前，段2 的行接在后面 |
| `%%` | 各段结果**按行号交替**，换行连接 | `i=0` 取段1第0行、段2第0行；`i=1` 取段1第1行…… |

`%%` 按 `results[0].indices` 遍历，所以最终行数由**第一段**的行数决定；后面段比它长就丢弃多出来的部分。

走 `getString` 的字段（`name` `author` `intro` `content` …）多行结果会被合成**一个 String**（换行分隔）；走 `getStringList` 的字段（`kind`、`chapterList`）拿到的是**列表**，再由调用方决定怎么拼（`kind` 在搜索页按逗号拼）。

### 混用 `@css:` 会炸

`@css:` 是**整个字段**的前缀，只被剥掉一次。连接符切开后的每一段不会重新判断模式：

| 写法 | 结果 |
| --- | --- |
| `@css:A@text \|\| @css:B@text` | ❌ 第二段的 `@css:` 被当选择器，抛 `Could not parse query '@css:B'` |
| `@css:A@text \|\| B@text` | ✅ 每段都是"CSS 选择器 + @动作"，后续段不写 `@css:` |
| `A@text \|\| @css:B@text` | ⚠️ `\|\|` 短路时侥幸不报错；第一段为空才走到第二段，此时 `@css:` 被吃掉当 `css:B` 查询 → 报错 |
| `A@text && @css:B@text` | ❌ `&&` 必然求值第二段，报 `Could not parse query 'css:B'` |

结论：一个字段内要么全程 `@css:`（后续段只写选择器），要么全程默认规则。**不要在连接符右边写 `@css:`**，`bsv.mjs lint` 会拦。

### JS 里的 `&&` / `||` / `%%`

`<js>` / `@js:` 段内部的这些符号不会被当连接符，可以正常用逻辑运算和字符串拼接：

```
…@js:String(result)||'fallback'        → 正常返回 result
…@js:(String(result).length>0)&&'ok'   → ok
…@js:'a'+'%%'+'b'                      → a%%b
```

## 列表倒置

`bookList` 和 `chapterList` 的值以 `-` 开头时，整个列表反转。这是这两个字段独有的前缀，写在别的字段上会被当成选择器的一部分而报错。`+` 开头表示不反转（默认行为）。

倒置在日志里看不到：`┌获取书名`、`◇章节名称:` 都是在解析循环里逐条打的，反转发生在这之后。所以 `verify` 报的 `search.name` 和 `toc.firstChapter` 永远是**反转前**的第一条，别拿它判断倒置有没有生效。

## 分页字段

| 字段 | 归属 | 返回值语义 |
| --- | --- | --- |
| `nextTocUrl` | `ruleToc` | 返回单个 URL 会串行翻页，返回数组会并发 |
| `nextContentUrl` | `ruleContent` | 同上 |

判断正文"下一页"时要排除跨章链接（形如"下一章"），否则会把下一章正文拼进本章。

## URL 参数

URL 后接 `,{JSON}`：

| 字段 | 说明 |
| --- | --- |
| `method` | `GET`（默认）/`POST`/`HEAD` |
| `body` | POST 体，必须是 JS String |
| `charset` | 响应编码，默认 utf-8 |
| `headers` | 请求头对象，key 区分大小写 |
| `webView` | 用 WebView 加载（布尔或延迟毫秒数） |
| `webJs` | WebView 加载完后执行的 JS |
| `js` | 解析 URL 时执行的 JS，返回值替换 URL |
| `bodyJs` | 拿到响应后执行的 JS，返回值替换 body |
| `type` | 文件类型（文件类源用） |
| `retry` | 重试次数，默认 0 |
| `dnsIp` | 指定域名解析到的 IP |
| `serverID` | 书源服务器 id |
| `webViewDelayTime` | WebView 等页面加载完的额外延迟（毫秒） |

`proxy` **不是** UrlOption 字段，它写在 `headers` 里（`{"headers":{"proxy":"http://..."}}`），App 会把它取出来单独用作代理、并从请求头里删掉。

逗号的识别规则是 `\s*,\s*(?=\{)`——逗号后面必须紧跟 `{`，否则整段被当成 URL 的一部分。

## 模板变量

`{{…}}` 里的内容**当 JS 表达式求值**，不是简单的字符串替换。可用变量 `page`、`key`、`baseUrl`、`java`、`cookie`、`cache`、`source`、`book`、`result`。

| 写法 | 结果 |
| --- | --- |
| `{{key}}` | 搜索关键字 |
| `{{page}}` | 页码（从 1 开始） |
| `{{(page-1)*20}}` | 可做算术 |
| `{{2*3+1}}` | `7`（证明是 JS 求值） |
| `{{'abc'.toUpperCase()}}` | `ABC`（可调字符串方法） |
| `{{page - 1 == 0 ? "" : page}}` | 首页不带页码 |

实测 `searchUrl` 写成 `/search.php?q={{key}}&probe={{2*3+1}}&p2={{'abc'.toUpperCase()}}`，日志里的请求 URL 是 `/search.php?q=剑来&probe=7&p2=ABC`。

`<,{{page}}>` 是另一种简写，走的是**页码模式**：`<a,b,c>` 按页码取第 n 项（`page` 超过项数就取最后一项）。

替换顺序是：先把 `{{}}` 当 JS 求值，再处理 `<a,b,c>` 页码模式，最后才切 `,{JSON}` 的 UrlOption。所以 `<,{{page}}>` 实际是先变成 `<,1>`，再按逗号切成 `["", "1"]`、取第 1 项 → 空字符串。

实测：发现页 URL 写 `/list1/<,{{page}}>.html`，第 1 页请求的确实是 `https://www.xbiquge.info/list1/.html`（该地址在本测试站点上是 404，只用于确认替换结果）。

相对路径自动基于 `bookSourceUrl` 拼接。

## 发现 URL

`exploreUrl` 是 JSON 数组，每项 `{"title": "分类名", "url": "带{{page}}的地址"}`。发现页用 `ruleExplore`，字段与 `ruleSearch` 相同。调试时 key 写成 `分类名::url`。
