# 选择器陷阱

这些结论都来自 App 内实测（jsoup 1.16.2 + `AnalyzeByJSoup`）。写规则时按这里的判据选语法。

## jsoup 支持的伪类（完整清单）

`QueryParser` 里只有这些：

```
:lt  :gt  :eq
:has  :contains  :containsOwn  :matches  :matchesOwn  :not
:nth-child  :nth-last-child  :nth-of-type  :nth-last-of-type
:first-child  :last-child  :first-of-type  :last-of-type
:only-child  :only-of-type  :empty  :root
```

**不在这个清单里的伪类一律抛 `SelectorParseException`**。后果分两种，取决于字段有没有被 App 包 try/catch：

| 写在哪 | 后果 |
| --- | --- |
| `name`、`author` | 整条链路当场中断，`verify` 的 `reason` 变 `closed`，`stages` 只剩半段 |
| 其他字段（`intro`、`kind`、`content`、`chapterList` …） | 异常被吞，该字段为空，链路继续走完，但 `verify` 因 `errors` 非空判 `ok: false` |

两种都会在 `errors` 里留下 `Could not parse query 'xxx': unexpected token at 'yyy'`，`yyy` 前面就是不被支持的部分。

最容易踩的是 jQuery 那一套：

| 写了 | 结果 |
| --- | --- |
| `:first` `:last` | 抛异常 |
| `:text` `:visible` `:hidden` | 抛异常 |
| `:odd` `:even` | 抛异常 |
| `:parent` `:header` `:selected` `:checked` `:animated` | 抛异常 |

替代写法：

| 想要 | 写 |
| --- | --- |
| 第一个元素 | `tag.a.0@text` 或 `tag.a:first-child@text` |
| 最后一个 | `tag.a.-1@text` 或 `tag.a:last-of-type@text` |
| 只取文本节点 | `@text` 或 `@ownText`（动作，不是伪类） |

## `:eq` / `:lt` / `:gt` 不是 jQuery 语义

jsoup 里 `:eq(n)` 就是 `:nth-child()` 的 0 基别名，`:lt`/`:gt` 同理——它们筛的是**在父节点子元素里的位置**，不是"结果集里第 n 个"。

实测同一份 HTML（`dl` 的子元素依次是 `dt`、`dd`(h3)、`dd.book_other`×4）：

| 规则 | 结果 |
| --- | --- |
| `@css:dd.book_other:eq(0)@text` | 空——第 0 个子元素是 `dt` |
| `@css:dd.book_other:lt(1)@text` | 空——只有第 0 个孩子满足位置条件 |
| `@css:dd.book_other:gt(0)@text` | 全部 4 条 |

要"结果集第 n 个"，用索引语法：`dd.book_other.0@text`、`dd.book_other[0]@text`。

`:nth-child()` / `:nth-of-type()` 同样是按兄弟位置数。`dd.book_other:nth-child(3)` 取到的是 `dl` 第 3 个子节点（即第一条 `book_other`），因为第 2 个是 h3 那条 dd，不匹配 `.book_other`。用之前先数清目标在父节点里的实际位置。

## 索引的位置

索引只在**默认规则**的段里生效，`@css:` 段里会被当成 CSS 的一部分而取不到值。

| 写法 | 结果 |
| --- | --- |
| `tag.dd.1@text` | ✅ 第 2 个 dd |
| `tag.dd[1]@text` | ✅ 同上 |
| `tag.dd@text.1` | ❌ 空（索引跑到动作后面） |
| `tag.dd@text[1]` | ❌ 空（同上） |
| `@css:dd.book_other.0@text` | ❌ 空（`.0` 被当 class 名） |
| `@css:dd.book_other[0]@text` | ❌ 空 |

`@css:` 段里要按位置取，只能靠 `:nth-child()` / `:nth-of-type()` 这类 CSS 伪类，或者改用默认规则。

## 整条规则没有取值动作

只写选择器、不写 `@动作` 时，**那串选择器本身会被当成属性名**，不是"默认取文本"。

`AnalyzeByJSoup.getResultList` 按 `@` 切成段，最后一段交给 `getResultLast`；它只认 `text` / `ownText` / `textNodes` / `html` / `all` 这几个动作，其余一律走 `element.attr(最后一段)`。所以 `.booktitle` 这种写法取的是根元素的 `attr(".booktitle")`——恒为空串，且**不报错**。

实测（台湾小说网详情页）：`coverUrl: .bookcover img.thumbnail@src` 正常出图，同一次解析里 `name: .booktitle`、`author: .booktag a.red`、`intro: .bookintro`、`lastChapter: a.bookchapter` 全部返回空，`verify` 的 `detail` 段四项都是 `""`，`errors` 里干干净净。

症状是「选择器明明能命中，字段却全空」，很容易误判成选择器写错或站点改版。补上 `@text` 即可：

| 写法 | 结果 |
| --- | --- |
| `.booktitle` | ❌ 空（被当成 `attr(".booktitle")`） |
| `.booktitle@text` | ✅ 文本 |
| `class.booktitle` | ❌ 空（同上，`class.` 只是取值方式，仍缺动作） |
| `class.booktitle@text` | ✅ 文本 |

判据：**除 JS / 正则段外，每条规则都该有 `@` 动作**。用 `class.` / `tag.` / `id.` 前缀写法时也一样，前缀只决定怎么选元素，不决定取什么。

## `@css:` 与默认规则的差别

两者最终都走 `Element.select()`，**伪类支持完全一样**（`:contains`、`:has`、`:nth-child` 都可用）。差别在段结构：

- 默认规则按 `.` 拆段，`tag.a.0` 里的 `.0` 是索引；`[n]` 也是索引。
- `@css:` 后面整段都是 CSS 选择器，`@` 之后才是动作。所以 `.0` 会被当成 class 名，`[0]` 会被当成属性选择器，两者都取不到东西。

需要索引就换默认规则，或改用 `:nth-child()`。

## 列表为空 vs 选择器报错

| 现象 | 含义 |
| --- | --- |
| `└列表大小:0` | 选择器合法但没命中，改选择器 |
| `└Could not parse query 'xxx': unexpected token at 'yyy'` | 伪类/语法不被支持，看 `yyy` 前面是什么 |
| `└列表为空,按详情页解析` | `bookList` 完全没命中，App 退化成把搜索页当详情页 |

第三种最危险：日志里 `◇书籍总数:1`、书名是搜索页标题，看起来像成功。`bsv.mjs verify` 会把它标成 `search.listEmptyFallback: true` 且 `search.ok: false`。

## 相对链接

`@href`、`@src` 拿到的是页面里的原值，可能是 `/images/x.jpg` 这种相对路径。App 会在 `bookUrl`、`chapterUrl`、`coverUrl` 上自动基于当前页地址补全，**不需要自己拼**。

一个容易看错的地方：详情页的日志打的是补全**前**的原值。

```
搜索页 ┌获取封面链接 → └https://www.xbiquge.info/images/45/45525/45525s.jpg   （打的是补全后的）
详情页 ┌获取封面链接 → └/images/45/45525/45525s.jpg                          （打的是补全前的）
```

所以 `verify` 报的 `detail.coverUrl` 是相对路径**不代表书源有问题**，存进书里的已经是绝对地址。真正要看的是封面能不能显示，别拿这条日志当缺陷。

只有当站点给的路径既不是绝对地址也不是以 `/` 开头的正常相对路径（比如 `//cdn.x.com/a.jpg` 之外的畸形值）时才需要自己用 `##` 补全。

## 文本里混了子元素

`@text` 取的是元素及其所有子元素的文本，会粘在一起（`作者：来一杯咖啡`）。要只要自己那层用 `@ownText`，或者先用子选择器定位到 span 再取。

`@html` 会先删掉 script/style 再输出，正文里内嵌的脚本不会带出来。
