# 网络、编码与登录

App 自身文档 `docs/dev/url-options.md`、`docs/dev/request-headers.md`、`docs/dev/authentication.md` 是权威版本，这里只留写源时最常撞上的部分。

## URL 参数

URL 后接 `,{JSON}`，逗号紧跟在 URL 后面：

```json
"/search.php,{
  \"method\":\"POST\",
  \"body\":\"searchkey={{key}}&searchtype=all\",
  \"charset\":\"gbk\",
  \"headers\":{\"User-Agent\":\"Mozilla/5.0 ...\"}
}"
```

在 JSON 文件里要转义引号。也可以让整段由 JS 生成（`searchUrl` 支持 JS）：

```js
"https://example.com/search," + JSON.stringify({method:"POST", body:"key="+String(key)})
```

`body` 必须是 JS String。变量拼出来的记得 `String()` 强转，否则 Rhino 会传进非字符串类型导致请求体为空。

## 旧语法不存在

`;post=key={{key}}`、`;get=` 这类写法在本分支里没有实现。搜到的老书源这么写就是错的，改成 `,{"method":"POST","body":"..."}`。

## 编码

`charset` 默认 `utf-8`。GBK/GB2312 站点必须显式写 `"charset":"gbk"`，否则中文全是乱码，选择器仍然命中但文本无意义。

判断办法：`curl -s <url> | file -` 或者看响应头 `Content-Type`。也可以直接看页面 `<meta charset=...>`。

## 请求头

`header` 字段是 JSON **字符串**（不是对象）：

```json
"header": "{\"User-Agent\":\"Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36\"}"
```

key 区分大小写，写 `user-agent` 无效。

UA 要写完整。截断的 UA（只有 `Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36`）会被部分站点识别为非浏览器客户端。

需要 Referer 防盗链的站点，在 `header` 里补 `"Referer"`。

## Cookie

| 场景 | 做法 |
| --- | --- |
| 站点靠 Set-Cookie 维持 session | 保持 `enabledCookieJar: true`（默认） |
| 需要预置固定 cookie | `header` 里加 `"Cookie":"k=v; k2=v2"` |
| 需要登录 | 配 `loginUrl`，用户在 App 内 WebView 登录，cookie 自动按主域存 |

登录态按 eTLD+1 归一存储，`www` 子域登录后 `wap`/`m` 子域也能用同一份 cookie。

## 发现页

`exploreUrl` 是 JSON 数组：

```json
[{"title":"热门榜","url":"/rank/hot?page={{page}}"},{"title":"新书榜","url":"/rank/new?page={{page}}"}]
```

对应规则写在 `ruleExplore`，字段与 `ruleSearch` 相同。调试时 key 写 `分类名::url`。

## 验证时的网络限制

`bsv.mjs` 的验证走本机 App，用的是模拟器/真机的网络出口。代理、地区限制、UA 差异都可能让 PC 上能开的页面在 App 里打不开。遇到 `≡获取成功` 之后内容异常，先确认 App 侧网络能到达目标站点，再怀疑规则。
