# JS 运行时

引擎是 Rhino v1.8.0，不是 Node/V8。以下都是实测结论。

## `result` 的类型

`result` 是上一步规则的输出。类型取决于字段走的哪个方法：

| 字段 | 方法 | 多匹配时 `result` 是 |
| --- | --- | --- |
| `name` `author` `wordCount` `lastChapter` `intro` `coverUrl` `bookUrl` | `getString` | **String**（多行已拼接） |
| `kind` | `getStringList` | **JavaList** |
| `content` | `getString` | String |
| `chapterName` `chapterUrl` | `getString` | String |

实测（同一个 `dd.book_other` 命中 4 个元素）：

```
dd.book_other@text@js:Object.prototype.toString.call(result)   → [object String]，len=53
@css:dd.book_other@text@js:…                                   → [object String]，len=53
```

所以不要写 `result[0]`——对 String 取 `[0]` 得到的是第一个字符。实测 `result` 为 `来一杯咖啡` 时 `result[0]` 返回 `来`，`result.length` 是 5。要先判断：

```js
var s = (typeof result === 'string') ? result : String(result);
s.split('\n')[1]
```

打类型用这个，比猜快：

```js
<js>Object.prototype.toString.call(result) + ' len=' + result.length</js>
```

## 常用写法

```js
// 取第一个匹配（result 是 String 时）
<js>String(result).split('\n')[0]</js>

// 去空白
<js>String(result).trim()</js>

// 正则提取
<js>(String(result).match(/\d+/) || [''])[0]</js>

// 多段拼装
<js>var a = String(result).split('\n'); a[0] + ' / ' + a[1]</js>
```

## 陷阱

| 写法 | 问题 |
| --- | --- |
| `const` | 无块级作用域。实测 `for(var i=0;i<3;i++){const v=i;o.push(v)}` 得到 `0,0,0`；`let` 正常，得到 `0,2,4`。循环里用 `var` |
| `let` | 可用，但混用 `var`/`let` 容易在同一函数里重复声明，统一用 `var` 更省事 |
| 顶层 `return` | **编译期**报 `invalid return`，整条链路当场中断（不是字段为空）。要提前返回就包一层 IIFE：`(function(){ … return x })()` |
| `result.replace(...)` | `result` 是 String 时可用；是 JavaList 时报 `Cannot find function replace in object` |
| `result[0]` | String 上取到的是**第一个字符**（`来一杯咖啡` → `来`），不是第一个匹配 |
| `result.length` | String 上是字符数，JavaList 上是元素数，语义不同 |
| `page` | **只在 `{{}}` 模板里有，`@js:` 规则体里没有**。`ruleExplore.bookList` 里读 `page` 抛 `ReferenceError: "page" is not defined`，被 catch 住就是静默空列表。要翻页只能在 `exploreUrl` 写 `{{page}}` |

实测对照（同一份规则，只有 JS 不同）：

```
var o=[];for(var i=0;i<3;i++){const v=i;o.push(v)};o.join(',')  → 0,0,0
var o=[];for(var i=0;i<3;i++){let  w=i*2;o.push(w)};o.join(',')  → 0,2,4
if(true){return 'early';} 'late'                                → EvaluatorException: invalid return，链路中断
result.notAFunction()                                           → EcmaError，字段为空，链路继续
(function(){return 'ret:'+s})()                                 → ret:012
```

## 可用对象

| 名字 | 内容 |
| --- | --- |
| `java` | 扩展工具对象，见下表 |
| `baseUrl` | 当前请求的 URL |
| `result` | 上一步结果 |
| `src` | 请求返回的原始源码 |
| `book` | 书籍对象（书名、作者、tocUrl…） |
| `chapter` | 章节对象 |
| `source` | 源对象，`source.put(k,v)` / `source.get(k)` 跨步骤存值（存到 `v_<源key>_<k>` 缓存） |
| `cookie` | Cookie 操作 |
| `cache` | 缓存操作 |
| `title` | 当前章节标题 |
| `nextChapterUrl` | 下一章 URL |
| `rssArticle` | RSS 文章对象 |
| `fromBookInfo` | 是否来自详情页（布尔） |

## `java` 常用方法

| 方法 | 说明 |
| --- | --- |
| `java.ajax(url)` | GET，返回响应体字符串 |
| `java.connect(url)` | 返回 StrResponse，有 `.body()` `.code()` `.headers()` `.raw()` |
| `java.get(url, headers)` / `java.post(url, body, headers)` | 返回 Connection.Response |
| `java.base64Decode(str)` / `java.base64Encode(str)` | Base64 |
| `java.hexDecodeToString(hex)` / `java.hexEncodeToString(str)` | Hex |
| `java.md5Encode(str)` / `java.digestHex(data, alg)` | 摘要 |
| `java.encodeURI(str)` | URL 编码 |
| `java.t2s(str)` / `java.s2t(str)` | 繁简转换 |
| `java.log(msg)` / `java.logType(v)` | 输出日志（在调试面板可见） |
| `java.getCookie(tag)` | 取 cookie |

完整清单见 `docs/dev/js.md`。

## 调试 JS

规则里写 `java.log(...)` 或 `java.logType(...)`，输出会出现在 `bsv.mjs verify` 抓到的日志流里，位置就在该字段的 `┌…`/`└…` 之间：

```
┌获取作者
T=[object String] L=5 [0]=来      ← java.log 的输出
└来一杯咖啡
```

JS 出错时是否中断，取决于字段：

| 字段 | 异常后果 |
| --- | --- |
| `name`、`author`（搜索页和详情页都算） | **未包 try/catch，整条链路当场中断**，`verify` 的 `reason` 变 `closed`、只剩半段 `stages` |
| `kind` `wordCount` `lastChapter` `intro` `coverUrl` `bookUrl` `content` | 被 try/catch 包住，该字段为空、链路继续，但 `verify` 仍会因 `errors` 非空判 `ok: false` |

报错文本两种：

- 运行时错 → `org.mozilla.javascript.EcmaError: TypeError: Cannot find function xxx in object yyy`
- 编译期错（顶层 `return`、语法错）→ `com.script.ScriptException: … EvaluatorException: invalid return`

所以别只看 `stages` 是否都为 `ok`；`errors` 里出现 `EcmaError`/`EvaluatorException` 就是 JS 写错了，即使链路走完。
