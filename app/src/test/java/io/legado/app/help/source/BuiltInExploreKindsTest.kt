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
