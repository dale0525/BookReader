package io.legado.app.help.source

import android.app.Application
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.runBlocking
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
 * 走真实链路：asset 里的 BookSource -> exploreKinds() -> 分类列表。
 * 这条链路包含 jsLib 装载、exploreUrl 求值、toString、JSON 解析，
 * 是发现页报错唯一会经过的路径；比在模拟器上手点快得多，也能定位是哪一步坏。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BuiltInExploreKindsTest {

    private lateinit var sources: List<BookSource>

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val json = RuntimeEnvironment.getApplication().assets
            .open("defaultData/bookSources.json").bufferedReader().use { it.readText() }
        sources = GSON.fromJsonArray<BookSource>(json).getOrThrow()
    }

    @Test
    fun everySourceIsItsOwnSite() {
        // 每个书源只带一个站点，发现页才能按源分栏；两个源共用同一个 bookSourceUrl
        // 在库里根本存不下（主键就是 url）。
        assertEquals(sources.size, sources.map { it.bookSourceUrl }.toSet().size)
        sources.forEach { source ->
            val ids = siteIds(source)
            assertEquals("${source.bookSourceName} 的 jsLib 站点数不是 1: $ids", 1, ids.size)
        }
    }

    @Test
    fun everySourceYieldsRealCategories() = runBlocking {
        sources.forEach { source ->
            val name = source.bookSourceName
            val kinds = source.exploreKinds()
            assertTrue(
                "$name 返回了错误分类: ${kinds.map { it.title }}",
                kinds.none { it.title.startsWith("ERROR:") }
            )
            if (source.bookSourceComment == "仅搜索") {
                // 站点本身没有分类页（如 favcomic），只参与搜索。
                // 卡片上写明「仅搜索」，用户才知道它得用搜索进，而不是发现页里凭空少一个源。
                assertEquals("$name 标了仅搜索却仍有分类", 0, kinds.size)
                return@forEach
            }
            assertTrue("$name 没有任何分类", kinds.isNotEmpty())
            kinds.forEach { kind ->
                assertTrue(
                    "$name 分类「${kind.title}」不是真实分类名",
                    !kind.title.contains("NativeArray")
                )
                assertTrue(
                    "$name 分类「${kind.title}」URL 非法: ${kind.url}",
                    kind.url?.startsWith("http") == true
                )
                // 单站源里分类名不必带站点前缀：发现页已经按源分栏了。
                assertTrue(
                    "$name 分类「${kind.title}」缺少站点路由标记: ${kind.url}",
                    kind.url?.contains('#') == true
                )
                // 分类页必须落在本站域名下，否则点进去会被 siteOf() 判成别的站。
                assertEquals(
                    "$name 分类「${kind.title}」指向了别的站点",
                    hostOf(source.bookSourceUrl),
                    hostOf(kind.url!!.substringBefore('#'))
                )
            }
        }
    }

    @Test
    fun categoriesComeFromTheSourcesOnlySite() = runBlocking {
        sources.filter { it.bookSourceComment != "仅搜索" }.forEach { source ->
            val ids = siteIds(source)
            val kindSites = source.exploreKinds()
                .mapNotNull { it.url?.substringAfter('#', "")?.ifEmpty { null } }
                .toSet()
            assertEquals(
                "${source.bookSourceName} 的分类来自多个站点: $kindSites",
                ids,
                kindSites
            )
        }
    }

    @Test
    fun rankTiersUseKnownKeys() = runBlocking {
        val allowed = setOf("update", "week", "month", "all")
        sources.filter { it.bookSourceComment != "仅搜索" }.forEach { source ->
            source.exploreKinds()
                .filter { it.rank.isNotEmpty() }
                .forEach { kind ->
                    assertTrue(
                        "${source.bookSourceName} 档位「${kind.title}」取值非法: ${kind.rank}",
                        kind.rank in allowed
                    )
                }
        }
    }

    @Test
    fun adultTwinSharesTheHostButNotTheUrl() {
        // 普通/成人两份共用同一个采集接口，只能靠 URL 后缀区分：主键是 bookSourceUrl，
        // 同址两源存不下来；后缀不影响 hostOf/is18Plus/getBaseUrl 的判定。
        val byUrl = sources.associateBy { it.bookSourceUrl }
        val twins = sources.filter { it.bookSourceName.endsWith("·18+") }
        assertEquals(2, twins.size)
        twins.forEach { adult ->
            val plainUrl = adult.bookSourceUrl.substringBeforeLast("/adult")
            val plain = byUrl[plainUrl]
            assertTrue("${adult.bookSourceName} 找不到对应的普通源", plain != null)
            assertEquals(hostOf(plainUrl), hostOf(adult.bookSourceUrl))
            assertEquals(
                "${adult.bookSourceName} 的分组不对",
                "视频 · 18+",
                adult.bookSourceGroup
            )
        }
    }

    private fun siteIds(source: BookSource): Set<String> {
        val lib = source.jsLib ?: ""
        val start = lib.indexOf('[')
        assertTrue("${source.bookSourceName} 的 jsLib 里没有站点表", start >= 0)
        return Regex("\"id\":\"([^\"]+)\"")
            .findAll(lib.substring(start))
            .map { it.groupValues[1] }
            .toSet()
    }

    private fun hostOf(url: String): String =
        Regex("^https?://([^/]+)").find(url)?.groupValues?.get(1) ?: url
}
