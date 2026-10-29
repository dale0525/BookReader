package io.legado.app.help.source

import android.app.Application
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 番茄小说的发现页曾经挂过一次：8 个分类里 7 个点进去恒空。
 *
 * 起因是分类走了网页端的 `/rank/0_1_{id}`，那个页面的 `book_list` 是客户端 JS 填的，
 * 服务端渲染出来是 `[]`。规则解析到空数组不报错，界面只显示「没有更多数据」，
 * 和「这个分类本来就没书」完全分不开。`BuiltInExploreKindsTest` 当时是绿的，
 * 因为它只断言分类能求值、URL 合法，不管分类里有没有内容。
 *
 * 现在的分类改走榜单接口，key 与页码都编码在 URL 里，规则从 `baseUrl` 切出来用。
 * 这里冻结两个位置敏感的前提：URL 里必须有 key 段、必须有页码占位。
 * 少任何一个，`FQ_explore` 会拿到 undefined 去请求，又是一次静默空列表。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FanqieNovelExploreTest {

    private lateinit var source: BookSource
    private lateinit var urls: List<String>

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val json = RuntimeEnvironment.getApplication().assets
            .open("defaultData/bookSources.json").bufferedReader().use { it.readText() }
        source = GSON.fromJsonArray<BookSource>(json).getOrThrow()
            .first { it.bookSourceName == "🍅番茄小说" }
        // exploreUrl 是 JSON 数组字符串；用 GSON 解析，别拼进 JS 源码（引号会撞成语法错误）。
        urls = GSON.fromJsonArray<Map<String, String>>(source.exploreUrl!!)
            .getOrThrow()
            .map { it["url"] ?: error("分类缺 url: $it") }
    }

    /** 每个分类都要有独立的榜单 key，否则 6 个分类会退化成同一个列表。 */
    @Test
    fun everyKindCarriesItsOwnRankKey() {
        val keys = urls.map { it.split("/fqrank/")[1].split("/")[0] }
        assertEquals("分类数不对: ${keys}", 6, keys.size)
        assertEquals("有分类共用了同一个榜单 key: $keys", keys.size, keys.toSet().size)
        keys.forEach { key ->
            assertTrue(
                "榜单 key「$key」格式不对，应为 side_type_type：$key",
                Regex("""^\d+_[01]$""").matches(key)
            )
        }
    }

    /** 页码必须走 {{page}} 模板：@js 规则体里没有 page 变量（实测抛 ReferenceError）。 */
    @Test
    fun everyKindPassesPageThroughTemplate() {
        urls.forEach { url ->
            assertTrue(
                "分类 URL 缺 {{page}} 占位，翻页会永远停在第一页：$url",
                url.contains("{{page}}")
            )
        }
    }

    /** 规则要从 baseUrl 的同一位置切出 key 与页码，URL 结构必须一致。 */
    @Test
    fun bookListRuleParsesTheUrlItIsGiven() {
        val rule = source.getExploreRule().bookList ?: error("ruleExplore.bookList 为空")
        assertTrue("bookList 必须从 baseUrl 取参数: $rule", rule.contains("baseUrl"))
        assertTrue("bookList 必须切 /fqrank/ 段: $rule", rule.contains("/fqrank/"))
        assertTrue("bookList 必须调 FQ_explore: $rule", rule.contains("FQ_explore"))
        // 与 URL 结构对齐：key 与页码是 /fqrank/ 之后的第 1、2 段。
        val probe = "https://fanqienovel.com/fqrank/12_0/3#fq"
        val seg = probe.split("/fqrank/")[1].split("#")[0].split("/")
        assertEquals("URL 结构变了，规则切不出 key", "12_0", seg[0])
        assertEquals("URL 结构变了，规则切不出页码", "3", seg[1])
    }
}
