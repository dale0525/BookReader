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

    private lateinit var aggregates: List<BookSource>

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val json = RuntimeEnvironment.getApplication().assets
            .open("defaultData/bookSources.json").bufferedReader().use { it.readText() }
        aggregates = GSON.fromJsonArray<BookSource>(json).getOrThrow()
            .filter { it.bookSourceUrl.startsWith("https://agg.example.com/") }
    }

    @Test
    fun everyAggregateSourceYieldsRealCategories() = runBlocking {
        assertEquals(6, aggregates.size)
        aggregates.forEach { source ->
            val kinds = source.exploreKinds()
            val name = source.bookSourceName
            assertTrue(
                "$name 返回了错误分类: ${kinds.map { it.title }}",
                kinds.none { it.title.startsWith("ERROR:") }
            )
            assertTrue(
                "$name 没有任何分类: ${kinds.map { it.title }}",
                kinds.isNotEmpty()
            )
            kinds.forEach { kind ->
                assertTrue(
                    "$name 分类「${kind.title}」不是真实分类名",
                    !kind.title.contains("NativeArray")
                )
                assertTrue(
                    "$name 分类「${kind.title}」URL 非法: ${kind.url}",
                    kind.url?.startsWith("http") == true
                )
                // 裸分类名才能跨站合并；带站点前缀会让同名分类拆成多个入口。
                assertTrue(
                    "$name 分类「${kind.title}」仍带站点前缀，无法跨站合并",
                    !kind.title.contains(" · ")
                )
                // #站点id 是 exploreAll 的路由标记，缺了它 siteOf() 只能靠域名猜。
                assertTrue(
                    "$name 分类「${kind.title}」缺少站点路由标记: ${kind.url}",
                    kind.url?.contains('#') == true
                )
            }
        }
    }

    @Test
    fun categoriesComeFromASingleCatalogSource() = runBlocking {
        // 发现页只由一个目录源驱动：它出分类和榜单，列表尽量完整。
        // 其余站点仍参与搜索与正文，但不再往发现页塞分类——否则同名分类会混进
        // 多个站的排版，缺章和取不到列表的问题也会一起暴露在首屏。
        aggregates.forEach { source ->
            val kinds = source.exploreKinds()
            val catalogSites = kinds
                .mapNotNull { it.url?.substringAfter('#', "")?.ifEmpty { null } }
                .toSet()
            val name = source.bookSourceName
            assertTrue("$name 没有分类入口", catalogSites.isNotEmpty())
            // 漫画·常规是唯一例外：分类走包子漫画，本周/本月/总榜只有漫画吧提供。
            val allowed = if (name == "漫画·常规") setOf("m1", "m2") else catalogSites
            assertEquals(
                "$name 的分类来自多个站点，发现页会混入不同排版: $catalogSites",
                allowed,
                catalogSites
            )
            if (name == "漫画·常规") {
                // 例外仅限榜单：普通分类必须全部来自 m1，否则同名分类会出现两个入口。
                val plainFromM2 = kinds
                    .filter { it.rank.isEmpty() }
                    .count { it.url?.endsWith("#m2") == true }
                assertEquals("漫画·常规 的普通分类混入了 m2", 0, plainFromM2)
            }
        }
    }

    @Test
    fun rankTiersUseKnownKeys() = runBlocking {
        val allowed = setOf("update", "week", "month", "all")
        aggregates.forEach { source ->
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
}
