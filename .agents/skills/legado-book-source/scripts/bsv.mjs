#!/usr/bin/env node
// 书源静态检查与 App 端四链路验证。
//
// 用法:
//   node bsv.mjs lint    <file.json>
//   node bsv.mjs doctor  [--port 1122]
//   node bsv.mjs install <file.json> [--port 1122]
//   node bsv.mjs verify  --source <url> (--key <kw> | --book-url <u> | --toc-url <u> | --content-url <u>) [--port 1122] [--timeout 180]
//   node bsv.mjs cleanup --source <url> [--port 1122]
//
// 退出码: 0 通过, 1 校验失败/链路失败, 2 用法或环境错误。

import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const RULE_KEYS = ["ruleSearch", "ruleBookInfo", "ruleToc", "ruleContent", "ruleExplore"];
// jsoup 1.16.2 的 QueryParser 里没有这些伪类，命中即抛 SelectorParseException，整条链路中断。
const JQUERY_UNSUPPORTED = /:(first|last|text|visible|hidden|odd|even)(?![\w-])/;
// jsoup 有 :eq/:lt/:gt，但语义是"在同级兄弟中的下标"，不是 jQuery 的"结果集第 n 个"。
const JQUERY_MISLEADING = /:(eq|lt|gt)\(/;

const argv = process.argv.slice(2);
const cmd = argv[0];
const opts = {};
const positional = [];
for (let i = 1; i < argv.length; i++) {
  const a = argv[i];
  if (a.startsWith("--")) {
    const k = a.slice(2);
    const v = argv[i + 1] && !argv[i + 1].startsWith("--") ? argv[++i] : "true";
    opts[k] = v;
  } else positional.push(a);
}
const port = Number(opts.port || 1122);
const base = `http://127.0.0.1:${port}`;

function die(msg, code = 2) {
  console.error(msg);
  process.exit(code);
}

// ---------- lint ----------

function loadSources(file) {
  const raw = readFileSync(resolve(file), "utf8");
  let data;
  try {
    data = JSON.parse(raw);
  } catch (e) {
    return { errors: [`JSON 解析失败: ${e.message}`], sources: [] };
  }
  if (Array.isArray(data)) return { errors: [], sources: data };
  return {
    errors: ["顶层必须是数组 [{...}]，当前是对象或标量。阅读导入要求 JSON 数组。"],
    sources: [],
  };
}

function walkRules(obj, path, out) {
  if (obj == null) return;
  if (typeof obj === "string") {
    out.push([path, obj]);
  } else if (Array.isArray(obj)) {
    obj.forEach((v, i) => walkRules(v, `${path}[${i}]`, out));
  } else if (typeof obj === "object") {
    for (const [k, v] of Object.entries(obj)) walkRules(v, path ? `${path}.${k}` : k, out);
  }
}

function lintSource(src, index) {
  const errors = [];
  const warns = [];
  const tag = `[${index}] ${src.bookSourceName || "(未命名)"}`;

  if (!src.bookSourceUrl || typeof src.bookSourceUrl !== "string") {
    errors.push(`${tag}: bookSourceUrl 缺失，App 会拒绝导入`);
  } else if (!/^https?:\/\//i.test(src.bookSourceUrl)) {
    errors.push(`${tag}: bookSourceUrl 必须以 http:// 或 https:// 开头`);
  }
  if (!src.bookSourceName || typeof src.bookSourceName !== "string") {
    errors.push(`${tag}: bookSourceName 缺失，App 会拒绝导入`);
  }
  for (const k of RULE_KEYS) {
    if (k in src && (typeof src[k] !== "object" || src[k] === null)) {
      errors.push(`${tag}: ${k} 必须是对象`);
    }
  }
  if (!src.ruleContent?.content) {
    warns.push(`${tag}: ruleContent.content 为空，正文链路必然失败`);
  }
  if (!src.searchUrl) {
    warns.push(`${tag}: searchUrl 为空，只能按详情页链接验证`);
  }
  if (src.searchUrl && /;post=|;get=/.test(src.searchUrl)) {
    errors.push(
      `${tag}: searchUrl 使用了 ;post= / ;get= 旧语法，本分支只认 URL 后接 ,{JSON} 的 UrlOption`
    );
  }

  const strings = [];
  for (const k of RULE_KEYS) if (src[k]) walkRules(src[k], k, strings);
  if (src.searchUrl) strings.push(["searchUrl", src.searchUrl]);
  if (src.exploreUrl) strings.push(["exploreUrl", String(src.exploreUrl)]);

  for (const [path, value] of strings) {
    // JS 片段里的冒号不是选择器，先摘掉再判伪类，避免把 JS 字符串里的 :first 误报。
    const selectorPart = value.replace(/<js>[\s\S]*?<\/js>/gi, "").replace(/@js:[\s\S]*$/i, "");
    if (JQUERY_UNSUPPORTED.test(selectorPart)) {
      errors.push(
        `${tag}: ${path} 含 ${selectorPart.match(JQUERY_UNSUPPORTED)[0]}，jsoup 1.16.2 无此伪类，会抛 SelectorParseException 中断整条链路；改用索引语法（如 tag.a.0、[0]）或 :nth-child()/:has()/:contains()`
      );
    }
    if (JQUERY_MISLEADING.test(selectorPart)) {
      warns.push(
        `${tag}: ${path} 含 ${selectorPart.match(JQUERY_MISLEADING)[0]}…)，jsoup 里 :eq/:lt/:gt 是按兄弟节点下标筛，不是结果集下标；要取第 n 个用 tag.a.0 或 [0]`
      );
    }
    // 索引写到动作后面取不到值，是静默失败，只能靠静态检查提前拦。
    if (/@(text|html|ownText|textNodes|all|href|src)[.[]\s*\d/.test(selectorPart)) {
      warns.push(
        `${tag}: ${path} 把索引写在动作后面（@text.0 / @text[0]），取不到值；索引要接在选择器段后，如 tag.a.0@text`
      );
    }
    // @css: 段里 .0 / [0] 会被当成 CSS 语法，同样静默取空。
    if (/@css:[^@]*[.[]\s*\d/i.test(selectorPart)) {
      warns.push(
        `${tag}: ${path} 在 @css: 段里写了 .0 / [0] 形式的索引，会被当成 CSS 语法而取不到值；改用 :nth-child() 或换成默认规则`
      );
    }
    // @css: 是整个字段的前缀，只剥一次；连接符右边的 @css: 会被当成选择器，实测抛异常。
    if (/(\|\||&&|%%)@css:/i.test(selectorPart)) {
      errors.push(
        `${tag}: ${path} 在连接符右边又写了 @css:，只有字段开头的 @css: 会被识别，后面的会当成 CSS 选择器的一部分并抛异常；后续段只写选择器`
      );
    }
    for (const m of value.matchAll(/##([^#]*)##/g)) {
      const pattern = m[1];
      if (!pattern) continue;
      try {
        new RegExp(pattern);
      } catch (e) {
        errors.push(`${tag}: ${path} 的 ## 正则 /${pattern}/ 无法编译: ${e.message}`);
      }
    }
  }

  // 列表倒置的 "-" 前缀只在 bookList / chapterList 上生效。
  for (const [rulePath, ruleObj] of [
    ["ruleSearch", src.ruleSearch],
    ["ruleToc", src.ruleToc],
  ]) {
    if (!ruleObj) continue;
    for (const [field, v] of Object.entries(ruleObj)) {
      if (typeof v === "string" && v.startsWith("-") && field !== "bookList" && field !== "chapterList") {
        errors.push(`${tag}: ${rulePath}.${field} 以 - 开头，倒置只在 bookList/chapterList 上生效`);
      }
    }
  }

  return { errors, warns };
}

function lint(sources) {
  const errors = [];
  const warns = [];
  const seen = new Map();
  sources.forEach((src, i) => {
    const r = lintSource(src, i);
    errors.push(...r.errors);
    warns.push(...r.warns);
    if (src.bookSourceUrl) {
      if (seen.has(src.bookSourceUrl)) {
        errors.push(
          `[${i}] ${src.bookSourceName}: bookSourceUrl 与 [${seen.get(src.bookSourceUrl)}] 重复，插入时按 REPLACE 覆盖`
        );
      } else seen.set(src.bookSourceUrl, i);
    }
  });
  return { errors, warns };
}

// ---------- app http ----------

async function httpJson(path, init) {
  const res = await fetch(base + path, init);
  const text = await res.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    body = { raw: text.slice(0, 400) };
  }
  return { status: res.status, body };
}

async function install(sources) {
  const r = await httpJson("/saveBookSources", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(sources),
  });
  return r;
}

async function cleanup(sourceUrl) {
  const all = await httpJson("/getBookSources");
  const list = Array.isArray(all.body?.data) ? all.body.data : [];
  const hit = list.filter((s) => s.bookSourceUrl === sourceUrl);
  if (!hit.length) return { status: 200, body: { note: "未找到该源，无需清理" } };
  return httpJson("/deleteBookSources", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(hit),
  });
}

// ---------- ws 调试 ----------

const MARK = {
  searchStart: "︾开始解析搜索页",
  searchDone: "︽搜索页解析完成",
  exploreStart: "︾开始解析发现页",
  exploreDone: "︽发现页解析完成",
  exploreEmpty: "︽未获取到书籍",
  detailStart: "︾开始解析详情页",
  detailDone: "︽详情页解析完成",
  detailSkip: "≡已获取目录链接,跳过详情页",
  tocStart: "︾开始解析目录页",
  tocDone: "︽目录页解析完成",
  contentStart: "︾开始解析正文页",
  contentDone: "︽正文页解析完成",
};

function stripTime(line) {
  return line.replace(/^\[\d{2}:\d{2}\.\d{3}\]\s?/, "");
}

function debugKey() {
  if (opts.key) return opts.key;
  if (opts["book-url"]) return opts["book-url"];
  if (opts["toc-url"]) return "++" + opts["toc-url"];
  if (opts["content-url"]) return "--" + opts["content-url"];
  return null;
}

function runDebug(tag, key, timeoutSec) {
  return new Promise((resolveRun) => {
    const frames = [];
    const ws = new WebSocket(`ws://127.0.0.1:${port + 1}/bookSourceDebug`);
    let settled = false;
    const finish = (reason) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try {
        ws.close();
      } catch {}
      resolveRun({ frames, reason });
    };
    const timer = setTimeout(() => finish("timeout"), timeoutSec * 1000);
    ws.onopen = () => ws.send(JSON.stringify({ tag, key }));
    ws.onmessage = (e) => frames.push(String(e.data));
    ws.onclose = () => finish("closed");
    ws.onerror = (e) => finish("error:" + (e.message || e.type));
  });
}

function parseReport(tag, key, frames, reason) {
  const lines = [];
  for (const f of frames) for (const l of String(f).split("\n")) lines.push(l);
  const plain = lines.map(stripTime);

  // 按阶段切段，避免"获取书名"这种在搜索页和详情页都会出现的标题串段。
  const bounds = [];
  for (let i = 0; i < plain.length; i++) {
    for (const [name, mark] of Object.entries(MARK)) {
      if (mark.startsWith("︾") && plain[i].includes(mark)) bounds.push({ i, name });
    }
  }
  const segment = (name) => {
    const start = bounds.find((b) => b.name === name);
    if (!start) return null;
    const next = bounds.find((b) => b.i > start.i);
    return plain.slice(start.i, next ? next.i : plain.length);
  };

  const numIn = (lines, re) => {
    for (const l of lines || plain) {
      const m = l.match(re);
      if (m) return Number(m[1]);
    }
    return null;
  };
  // "┌标题" 之后紧跟的 "└值"，在给定段内查找
  const afterIn = (lines, header) => {
    if (!lines) return null;
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].startsWith(header)) {
        const next = lines[i + 1];
        if (next == null) return null;
        const v = next.startsWith("└") ? next.slice(1) : next;
        return v.trim();
      }
    }
    return null;
  };

  const has = (m) => plain.some((l) => l.includes(m));
  const hasIn = (lines, m) => !!lines && lines.some((l) => l.includes(m));
  const errors = [];
  for (let i = 0; i < plain.length; i++) {
    const l = plain[i];
    if (/^at [a-zA-Z_$][\w.$]*\(/.test(l)) continue;
    if (/Exception|Error:|Caused by:/.test(l) && !l.startsWith("└")) errors.push(l.trim());
  }
  // └ 后紧跟的报错文本（字段解析抛异常时就在 └ 里）
  for (let i = 0; i < plain.length; i++) {
    if (plain[i].startsWith("└")) {
      const v = plain[i].slice(1);
      if (/Could not parse query|Exception|is not defined|Cannot read|TypeError/.test(v)) {
        errors.push(v.trim());
      }
    }
  }

  const content = (() => {
    for (let i = 0; i < plain.length; i++) {
      if (plain[i].startsWith("┌获取正文内容")) {
        const rest = [];
        for (let j = i + 1; j < plain.length; j++) {
          if (/^[┌︽︾◇≡⇒]/.test(plain[j])) break;
          rest.push(plain[j]);
        }
        if (rest[0] === "└") rest.shift();
        return rest.join("\n").trim();
      }
    }
    return "";
  })();

  const stages = {};

  // 发现页入口：`分类名::url`。发现页跑完会接着走详情→目录→正文，阶段标记与搜索链路相同。
  const exploreSeg = segment("exploreStart");
  if (exploreSeg) {
    const books = numIn(exploreSeg, /◇书籍总数:(\d+)/);
    stages.explore = {
      ok: hasIn(exploreSeg, MARK.exploreDone) && (books ?? 0) > 0,
      listSize: numIn(exploreSeg, /└列表大小:(\d+)/),
      books,
      empty: hasIn(exploreSeg, MARK.exploreEmpty),
      name: afterIn(exploreSeg, "┌获取书名"),
      bookUrl: afterIn(exploreSeg, "┌获取详情页链接"),
    };
  }

  const searchSeg = segment("searchStart");
  if (searchSeg) {
    const books = numIn(searchSeg, /◇书籍总数:(\d+)/);
    const fallback = hasIn(searchSeg, "└列表为空,按详情页解析");
    stages.search = {
      // 列表为空时 App 会退化成"按详情页解析"，凑出 1 本假书。这不是搜索通过。
      ok: hasIn(searchSeg, MARK.searchDone) && (books ?? 0) > 0 && !fallback,
      listSize: numIn(searchSeg, /└列表大小:(\d+)/),
      books,
      listEmptyFallback: fallback,
      name: afterIn(searchSeg, "┌获取书名"),
      bookUrl: afterIn(searchSeg, "┌获取详情页链接"),
    };
  }

  const detailSeg = segment("detailStart");
  if (detailSeg || has(MARK.detailSkip)) {
    const skipped = !detailSeg && has(MARK.detailSkip);
    // 搜索页/发现页给出的入口链接，作为"跳过详情页"时的通过依据。
    const entryUrl = stages.search?.bookUrl ?? stages.explore?.bookUrl;
    stages.detail = {
      // 入口没给出详情页链接时跳过详情页，说明 bookUrl 规则没取到值，不能算通过。
      ok: skipped ? !!entryUrl : hasIn(detailSeg, MARK.detailDone),
      skipped,
      name: afterIn(detailSeg, "┌获取书名"),
      author: afterIn(detailSeg, "┌获取作者"),
      intro: afterIn(detailSeg, "┌获取简介"),
      coverUrl: afterIn(detailSeg, "┌获取封面链接"),
      lastChapter: afterIn(detailSeg, "┌获取最新章节"),
    };
  }

  const tocSeg = segment("tocStart");
  if (tocSeg) {
    const chapters = numIn(tocSeg, /◇目录总数:(\d+)/);
    const inline = (label) => {
      const line = (tocSeg || []).find((l) => l.startsWith(label));
      return line ? line.slice(label.length).trim() : null;
    };
    stages.toc = {
      ok: hasIn(tocSeg, MARK.tocDone) && (chapters ?? 0) > 0,
      chapters,
      pages: numIn(tocSeg, /◇目录总页数:(\d+)/),
      firstChapter: inline("◇章节名称:"),
      firstChapterUrl: inline("◇章节链接:"),
    };
  }

  const contentSeg = segment("contentStart");
  if (contentSeg) {
    stages.content = {
      ok: hasIn(contentSeg, MARK.contentDone) && content.length > 0,
      pages: numIn(contentSeg, /◇本章总页数:(\d+)/),
      chars: content.length,
      preview: content.slice(0, 80),
    };
  }

  const ok = Object.keys(stages).length > 0 && Object.values(stages).every((s) => s.ok) && errors.length === 0;
  return { source: tag, key, reason, ok, stages, errors: [...new Set(errors)].slice(0, 6) };
}

// ---------- main ----------

async function main() {
  if (cmd === "lint") {
    const file = positional[0];
    if (!file) die("用法: bsv.mjs lint <file.json>");
    const { errors: parseErrors, sources } = loadSources(file);
    const { errors, warns } = lint(sources);
    const all = [...parseErrors, ...errors];
    for (const w of warns) console.error("warn  " + w);
    for (const e of all) console.error("error " + e);
    if (!all.length) {
      console.log(`OK ${sources.length} 个书源通过静态检查${warns.length ? `，${warns.length} 条警告` : ""}`);
      process.exit(0);
    }
    console.error(`FAIL ${all.length} 个错误`);
    process.exit(1);
  }

  if (cmd === "doctor") {
    const out = { http: null, ws: null, wsPort: port + 1 };
    try {
      const r = await httpJson("/getBookSources");
      out.http = { ok: r.status === 200, status: r.status };
    } catch (e) {
      out.http = { ok: false, error: e.message };
    }
    out.ws = await new Promise((res) => {
      const ws = new WebSocket(`ws://127.0.0.1:${port + 1}/bookSourceDebug`);
      const t = setTimeout(() => {
        try { ws.close(); } catch {}
        res({ ok: false, error: "连接超时" });
      }, 5000);
      ws.onopen = () => {
        clearTimeout(t);
        ws.close();
        res({ ok: true });
      };
      ws.onerror = (e) => {
        clearTimeout(t);
        res({ ok: false, error: e.message || "连接失败" });
      };
    });
    out.hint = `需要 adb forward tcp:${port} tcp:${port} 与 adb forward tcp:${port + 1} tcp:${port + 1}；端口来自 App 内 Web 服务设置（默认 ${port}），WebSocket 恒为端口 +1`;
    console.log(JSON.stringify(out, null, 2));
    process.exit(out.http.ok && out.ws.ok ? 0 : 2);
  }

  if (cmd === "install") {
    const file = positional[0];
    if (!file) die("用法: bsv.mjs install <file.json>");
    const { errors: parseErrors, sources } = loadSources(file);
    const { errors } = lint(sources);
    const all = [...parseErrors, ...errors];
    if (all.length) {
      for (const e of all) console.error("error " + e);
      die("静态检查未通过，拒绝安装", 1);
    }
    const r = await install(sources);
    console.log(JSON.stringify(r.body, null, 2));
    process.exit(r.status === 200 && !r.body?.errorMsg ? 0 : 1);
  }

  if (cmd === "cleanup") {
    const tag = opts.source;
    if (!tag) die("用法: bsv.mjs cleanup --source <bookSourceUrl>");
    const r = await cleanup(tag);
    console.log(JSON.stringify(r.body, null, 2));
    process.exit(r.status === 200 ? 0 : 1);
  }

  if (cmd === "verify") {
    const tag = opts.source;
    const key = debugKey();
    if (!tag || !key) {
      die("用法: bsv.mjs verify --source <url> (--key <kw> | --book-url <u> | --toc-url <u> | --content-url <u>)");
    }
    // 源不在库里时调试 socket 不会回任何帧，会一直挂到超时。先查一次，直接说清原因。
    const installed = await httpJson("/getBookSource?url=" + encodeURIComponent(tag));
    if (installed.body?.errorMsg) {
      die(`书源未安装: ${tag}\n${installed.body.errorMsg}\n先跑 bsv.mjs install <file.json>`, 1);
    }
    const timeoutSec = Number(opts.timeout || 180);
    const { frames, reason } = await runDebug(tag, key, timeoutSec);
    const report = parseReport(tag, key, frames, reason);
    console.log(JSON.stringify(report, null, 2));
    process.exit(report.ok ? 0 : 1);
  }

  die("未知命令。可用: lint / doctor / install / verify / cleanup");
}

main().catch((e) => die(e.stack || String(e), 2));
