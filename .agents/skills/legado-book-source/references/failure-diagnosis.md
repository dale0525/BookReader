# 失败定位

`bsv.mjs verify` 的输出已经把每条链路的证据摆出来了。按下面的顺序读，不要靠猜。

## 先看阶段字段

| 字段 | 异常值 | 含义与下一步 |
| --- | --- | --- |
| `search.listEmptyFallback` | `true` | `bookList` 完全没命中，App 把搜索页当详情页。改 `bookList` |
| `search.books` | `1` 且上面为 true | 假书，不是搜索成功 |
| `search.listSize` | `0` | 选择器合法但零命中，选择器写错或页面结构变了 |
| `search.listSize` | `null` | 日志里没有 `└列表大小`，说明 `bookList` 规则根本没执行 |
| `search.bookUrl` | `null` | `bookUrl` 规则取不到链接，后续链路会从搜索页起跑 |
| `detail.skipped` | `true` 且 `search.bookUrl` 空 | 搜索没给链接，详情没验到 |
| `detail.name` | `null` | 详情页 `name` 规则错，或详情页根本没打开 |
| `toc.chapters` | `0` / `null` | `chapterList` 没命中 |
| `toc.pages` | `null` | 没有 `nextTocUrl`（不是错误，除非站点有分页） |
| `content.chars` | `0` | 正文规则取不到内容 |
| `errors` | 含 `TocEmptyException` | 目录为空，查 `chapterList` |
| `errors` | 含 `ContentEmptyException` | 正文为空，查 `content` |
| `errors` | 含 `SelectorParseException` | 选择器语法非法，看引号里那个查询串；写在 `name`/`author` 上会中断链路，写别处只是字段为空 |
| `errors` | 含 `EcmaError` | JS 运行时错（如调了不存在的方法），字段为空、链路走完 |
| `errors` | 含 `EvaluatorException` | JS 编译期错（顶层 `return`、语法错），写在 `name`/`author` 上会中断链路 |
| `reason` | `closed` 且 `stages` 只有前半段 | 链路中途抛异常，先看 `errors` 第一条 |
| `reason` | `timeout` | 站点太慢或调试 socket 没连上，先跑 `doctor` |

## 逐字段定位

`verify` 只给阶段结论。要看具体哪个字段空，跑一次并把日志留下来：

```bash
node <skill>/scripts/bsv.mjs verify --source <url> --key <kw> --timeout 180 > report.json
```

日志里的成对标记就是字段值：`┌获取书名` 下一行 `└...` 是取到的值。哪个 `└` 是空，哪个规则就有问题。

单链路排查：

```bash
node <skill>/scripts/bsv.mjs verify --source <url> --book-url <详情页>
node <skill>/scripts/bsv.mjs verify --source <url> --toc-url <目录页>
node <skill>/scripts/bsv.mjs verify --source <url> --content-url <正文页>
```

## 高频原因

| 现象 | 原因 | 修法 |
| --- | --- | --- |
| 列表大小 0，但页面上明明有内容 | `bookList` 选择器多写了层级，或类名带动态后缀 | 用父容器 + 直接子元素，或改 `:has()` 定位 |
| 列表有值，书名全是搜索页标题 | `bookList` 命中的是整页容器 | 收窄到单个条目 |
| 中文乱码 | 站点 GBK，没写 `charset` | `,{"charset":"gbk"}` |
| `≡获取成功` 之后立刻报错 | 拿到的是登录页/验证码页 | 见 `SKILL.md` 第 6 节 |
| 目录只有 1 章 | `chapterList` 命中了分页导航而不是章节 | 收窄选择器；分页另放 `nextTocUrl` |
| 目录章数正确但章节链接是 `#` | `chapterUrl` 取错属性 | 换成 `@href`，或确认章节链接在别的子元素上 |
| 正文只有第一页 | 缺 `nextContentUrl` | 补上，注意判断"下一页"别跨章 |
| 正文里混着导航/广告 | `content` 容器太大 | 收窄到正文容器；用 `##` 正则删尾巴 |
| 详情页 `coverUrl` 是相对路径 | 日志打的是补全前的原值，属正常 | 无需处理，书里存的是绝对地址 |
| `Could not parse query 'x': unexpected token at 'y'` | `x` 里用了不支持的伪类 | 看 `references/selector-traps.md` |
| JS 报 `Cannot find function replace` | `result` 是 JavaList 不是 String | `String(result)` 包一层 |
| JS 报 `has no public instance field or method named "0"` | 对单个 Element 用了 `result[0]` | 加索引要写进选择器段 |

## 不要做的事

- 不要在没跑 `verify` 的情况下根据 curl 结果判断书源可用。
- 不要在 `verify` 失败时靠改 `--key` 换个关键词再试——同一规则换个关键词结果不会变。
- 不要因为某条链路失败就把整份规则重写；先定位到具体字段。
