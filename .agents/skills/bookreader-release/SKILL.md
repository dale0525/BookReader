---
name: bookreader-release
description: 发版时使用——改版本号、打 tag、等 CI 产出签名 APK、核对 GitHub Release。也用于排查「Release 里没有产物」「客户端检查更新找不到新版本」「CI 产出的包装不上或盖不住本地包」这类发版问题。
---

# 发版

正式包只由 CI 产出，本地不打 release 包。整条链路是：改版本号 → 推 `main` → 推同名 tag → CI 构建签名 APK 并发 Release。

## 1. 改版本号

只改 `app/version.properties` 的 `VERSION_PATCH`（`VERSION_SUFFIX=0` 是正式版；非 0 会产出 `-beta.<提交数>` 且标记为 prerelease）。

提交信息固定写 `Bump to X.Y.Z`。

## 2. 打 tag

tag 必须是**不带前缀的纯版本号**（`0.3.10`，不是 `v0.3.10`）：客户端「检查更新」直接用 `versionName` 查 `/releases/tags/{versionName}`，带前缀就查不到。用 annotated tag，消息与提交一致。

```bash
git push origin main
git tag -a X.Y.Z -m "X.Y.Z" && git push origin X.Y.Z
```

推 tag 才会触发 `.github/workflows/release.yml`（触发条件 `tags: '[0-9]*'`）。

## 3. 等 CI

构建约 13–18 分钟。`gh` 的默认仓库是 `upstream`（HapeLee/legado-with-MD3），**每条命令都要带 `-R dale0525/BookReader`**，否则查到的是上游。

```bash
gh run list -R dale0525/BookReader --limit 3
gh release view X.Y.Z -R dale0525/BookReader
```

成功判据：`BookReader-X.Y.Z-universal.apk` 存在、约 27 MB、`prerelease` 为 false。

## CI 里三处不能动

- **`sed -i '/maven.aliyun.com/d' settings.gradle`**：aliyun 镜像面向国内开发机，GitHub runner 上会 502，且 Gradle 一旦判定某仓库失败就整个禁用它。删了这行 CI 会大面积解析失败。
- **签名身份断言**：`RELEASE_STORE_FILE` 缺失时 gradle 静默产出未签名的 release 包（装不上、也无法覆盖安装），所以 workflow 会 `apksigner verify --print-certs` 断言证书 SHA-256 等于 `59a0cd1e8734376ff25b40531241ab87b00bf1fc3f22ba3a63f6ad196996a180`。换密钥库必须同步改这个常量。
- **`versionCode = 40000 + COMMIT_NUMBER`**：偏移量要压过无 `COMMIT_NUMBER` 时的兜底值 32640，否则 CI 包盖不住本地构建的同版本包。

## 已知限制

本地构建的 `versionCode` 恒为 32640，与 CI 包不同。把本地调试包 `install -r` 到装着 CI 正式包的设备上会因降级失败，反之亦然——先 `adb uninstall` 再装，不要为此改构建脚本。

## 交付物

只有 GitHub Release 里的 APK。调试包不归档、不外发，也不再往 WebDAV 镜像一份。
