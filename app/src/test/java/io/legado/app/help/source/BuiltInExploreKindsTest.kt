package io.legado.app.help.source

import android.app.Application
import io.legado.app.constant.ExploreGroups
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

    /**
     * 内置源的分组名必须原样就是 6 档之一。
     *
     * 界面侧会把任意分组名归一到 6 档（`ExploreGroups.canonical`），但那只兜用户自己导入的源；
     * 随包发布的内置源是可控的，写错标点就该在测试里拦下，而不是靠运行时静默纠正——
     * 静默纠正会让人以为改了分组名生效了，实际界面纹丝不动。
     */
    @Test
    fun everySourceUsesOneOfTheSixGroups() {
        val groups = sources.map { it.bookSourceGroup }
        assertEquals(
            "内置源用了 6 档之外的分组: ${groups.filterNot { it in ExploreGroups.ordered }.toSet()}",
            emptySet<String>(),
            groups.filterNot { it in ExploreGroups.ordered }.toSet(),
        )
        sources.forEach { source ->
            assertEquals(
                "${source.bookSourceName} 的分组名与 bookSourceType 不一致",
                source.bookSourceGroup,
                ExploreGroups.canonical(source.bookSourceGroup, source.bookSourceType),
            )
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

    /**
     * jsLib 是共享作用域，里面只有 `java`，没有 `source` / `baseUrl`；
     * 要用得写成 `this.source`（`login.apply(this)` 与规则求值都会绑 this）。
     * 裸写 `source.get(...)` 会在真机上抛 EcmaError，登录直接失败。
     */
    @Test
    fun jsLibNeverUsesSourceWithoutThis() {
        val bareCall = Regex("""(?<![\w.$])source\s*[.\[]""")
        val bareBase = Regex("""(?<![\w.$])baseUrl\s*[.\[]""")
        sources.forEach { source ->
            val lib = source.jsLib ?: return@forEach
            // 注释里提到 `source.put` 只是说明文字，剥掉注释再判。
            val code = lib
                .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                .replace(Regex("""(?m)//.*$"""), "")
            listOf("source" to bareCall, "baseUrl" to bareBase).forEach { (word, re) ->
                re.findAll(code).forEach { m ->
                    val ctx = code.substring(maxOf(0, m.range.first - 40), m.range.last + 1)
                    assertTrue(
                        "${source.bookSourceName} 的 jsLib 里裸用了 $word，真机会抛 EcmaError：…$ctx…",
                        false
                    )
                }
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
    fun adultVideoSourcesAreNotUrlTwins() {
        // 18+ 视频档曾经是两条采集接口的 `/adult` 孪生源：与同名常规源共用 host，
        // 只靠 URL 后缀区分主键，内容完全重复、卡片翻倍，已下线。
        // 现在这一档是独立的站（黄果短剧），不该再出现靠后缀拆出来的条目。
        assertTrue(
            "18+ 视频档空了",
            sources.any { it.bookSourceGroup == "视频 · 18+" }
        )
        assertEquals(
            "又出现了靠 URL 后缀拆出的孪生源",
            emptyList<String>(),
            sources.map { it.bookSourceUrl }.filter { it.endsWith("/adult") },
        )
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
