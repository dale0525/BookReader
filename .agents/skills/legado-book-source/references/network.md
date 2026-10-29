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

## 表单登录

站点有 JSON 登录接口时，用 `loginUi` + `loginUrl` 让用户在 App 内填账号密码，不必开 WebView：

```json
"loginUi": "[{\"name\":\"账号\",\"type\":\"text\"},{\"name\":\"密码\",\"type\":\"password\"}]",
"loginUrl": "@js:uaaLogin()"
```

- `loginUi` 渲染成表单，值存进 `source.getLoginInfoMap()`（AES 加密落盘），`loginUrl` 执行时可用。
- **`loginUrl` 里必须定义名为 `login` 的函数。** 客户端提交表单后会把 `loginUrl` 与
  `if (typeof login=='function'){ login.apply(this); }` 拼接执行，只定义 `uaaLogin()` 会抛
  `Function login not implements!!!`，而且报错发生在登录按钮的回调里，界面上只显示「登录出错」。
  稳妥写法是定义 `login()` 做一层转发。
- **登录函数要直接写在 `loginUrl` 里，不要写在 `jsLib` 里。** 实测：`jsLib` 是独立原型作用域，
  函数体里看不到 `evalJS` 绑定的 `source`（裸 `source` 与 `this.source` 都是 `undefined`）；
  而 `loginUrl` 那段代码与 bindings 同一作用域，裸 `source` 直接可用。写在 `jsLib` 里、
  `loginUrl` 写 `@js:uaaLogin()` 时，客户端会先跑 `uaaLogin()` 这一行，`this` 还是 `undefined`，
  于是抛 `EcmaError`，界面只显示「登录出错」。`loginUrl` 里也别再定义一层同名 `login` 去转发，
  会遮蔽 `jsLib` 里的同名函数。
- 拿到凭据后调 `source.putLoginHeader(JSON.stringify({...}))` 存进登录请求头；`AnalyzeUrl`
  默认 `hasLoginHeader = true`，之后所有请求自动带上，不必在 `header` 里手写。

## 图片验证码

`java.getVerificationCode(imageUrl)` 弹出客户端通用的验证码对话框（输入框 + 图片，等待 1 分钟），
返回用户输入的字符串。它是**阻塞调用**，只能在后台线程执行，且弹窗是通用组件，任何源都能用。

坑：**验证码与取图请求绑定，每次取图都会刷新答案。** 取图 → 弹窗 → 提交之间不能再调一次取图接口，
否则提交的是上一张图的答案，服务端回「验证码不正确」。取图地址里带时间戳参数也没用——服务端认的是
最后一次下发的那张。

## 发现页

分类地址末尾的 `#id` 是路由标记（多站源用它区分站点），不是 URL 的 query。客户端切 query 时以
`?` 起、以 `#` 止，所以标记不会被发出去。写规则时注意：**不要指望服务端容忍多余参数**——实测
UAA 的接口对 `%23id` 直接回 400，body 只有 120 字节的错误 JSON，列表恒空，界面只显示「没有更多
数据」，而同一份规则走搜索链路（`searchUrl` 不带 `#`）完全正常，极易误判成规则写错。

`exploreUrl` 是 JSON 数组：

```json
[{"title":"热门榜","url":"/rank/hot?page={{page}}"},{"title":"新书榜","url":"/rank/new?page={{page}}"}]
```

对应规则写在 `ruleExplore`，字段与 `ruleSearch` 相同。调试时 key 写 `分类名::url`。

## 区分「路径不存在」与「签名被拒」

这两件事的响应形态不同，判错方向会白试几十个路径：

| 现象 | 含义 |
| --- | --- |
| `404 Page not found`，body 十几字节 | 网关直接回，**路径本身不存在**，换参数没用 |
| `200` 但 body 为 0 字节 | 路径存在、**签名或参数没过**，请求被上游丢弃 |
| `200` 且有 JSON `{"code":...}` | 走到业务逻辑了，读 `message` 补参数 |

同一族接口里往往只有一部分需要签名。实测番茄的 `reading/bookapi/audio/toneinfo/` 裸请求就能拿到数据，同级的 `reading/bookapi/audio/detail/` 却是 200 空体——所以**不要因为一个接口要签名就推断整族都要**，逐个裸测。

更反直觉的一条：**拿错 App 的签名栈去签，会把本来免签的接口签坏**。番茄小说源的 `FV_get` 带的是 `aid=8662`/`novelread` 那套，用它给 `aid=1967` 的 audio 接口加签，返回从 SUCCESS 变成 `{"code":100103,"message":"book_id 无效"}`。加签不是「更保险」，是按 App 身份做匹配。判断方法：同一接口分别裸请求与加签请求，两者不一致就说明签名栈不适用。

## 验证时的网络限制

`bsv.mjs` 的验证走本机 App，用的是模拟器/真机的网络出口。代理、地区限制、UA 差异都可能让 PC 上能开的页面在 App 里打不开。遇到 `≡获取成功` 之后内容异常，先确认 App 侧网络能到达目标站点，再怀疑规则。
