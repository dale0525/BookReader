package io.legado.app.help.source

import io.legado.app.data.entities.BookSource
import io.legado.app.help.DefaultData
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.CancellationException

/**
 * 内置书源的远端副本。
 *
 * 规则就发布在仓库里 asset 的那个路径上，所以改规则只要 push 到 main，
 * 已装机的用户下次启动就会拿到，不必等下一次发版。asset 里的那份同时保留：
 * 首次安装、离线启动、以及远端取不到时都靠它。
 *
 * 两个地址按顺序试：`raw.githubusercontent.com` 在部分网络下不可达，
 * 国内用户直接落到 jsDelivr 镜像。
 */
object RemoteBookSources {

    private const val rawUrl =
        "https://raw.githubusercontent.com/dale0525/BookReader/main/" +
                "app/src/main/assets/defaultData/bookSources.json"

    private const val jsDelivrUrl =
        "https://cdn.jsdelivr.net/gh/dale0525/BookReader@main/" +
                "app/src/main/assets/defaultData/bookSources.json"

    private val urls = listOf(rawUrl, jsDelivrUrl)

    /**
     * 比对并应用远端规则。返回是否真的改了本地书源表。
     *
     * 每次冷启动都查，不设时间冷却：`If-None-Match` 让「没变化」只花一个 304 的头部，
     * 而冷却会让「推完 main 立刻生效」这个承诺变成「最坏等半天」——规则失效时用户
     * 读不到正文，那点延迟是实打实的损失。
     *
     * 全程静默：离线、被墙、返回体异常都只是「这次没更新」，不弹提示、不影响本地已有规则。
     */
    suspend fun refresh(): Boolean {
        for (url in urls) {
            val changed = try {
                fetch(url)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 这个地址取不到就换下一个；全都失败时保持现状。
                continue
            }
            return changed
        }
        return false
    }

    private suspend fun fetch(url: String): Boolean {
        val cachedEtag = LocalConfig.bookSourceRemoteEtag.takeIf {
            LocalConfig.bookSourceRemoteUrl == url
        }
        val response = okHttpClient.newCallResponse {
            url(url)
            // 上次就是从这个地址拿到的，才能带条件请求；换了地址缓存对不上。
            cachedEtag?.let { header("If-None-Match", it) }
        }
        response.use {
            val body = it.body.string()
            val sources = parseRemoteSources(it.code, body) ?: return false
            val hash = MD5Utils.md5Encode(body)
            LocalConfig.bookSourceRemoteUrl = url
            LocalConfig.bookSourceRemoteEtag = it.header("ETag")
            // 内容没变就不重写数据库：jsDelivr 对分支引用缓存较久，会反复返回同一份 200；
            // 全新安装时远端与随包那份通常一致，也没必要写一遍。
            if (hash == LocalConfig.bookSourceRemoteHash) return false
            if (hash == DefaultData.bookSourcesHash) {
                LocalConfig.bookSourceRemoteHash = hash
                return false
            }
            DefaultData.importBookSources(sources)
            LocalConfig.bookSourceRemoteHash = hash
            return true
        }
    }
}

/**
 * 决定远端这次响应能不能落库。null 表示「这次什么都不做」。
 *
 * 空数组和解析失败必须当成无更新：远端改坏一个字符、或 CDN 返回一页错误页，
 * 都不能把用户手里那张可用的书源表清空。
 */
internal fun parseRemoteSources(statusCode: Int, body: String?): List<BookSource>? {
    if (statusCode == 304) return null
    if (statusCode !in 200..299) return null
    return GSON.fromJsonArray<BookSource>(body).getOrNull()?.takeIf { it.isNotEmpty() }
}
