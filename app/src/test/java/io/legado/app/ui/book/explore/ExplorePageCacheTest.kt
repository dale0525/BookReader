package io.legado.app.ui.book.explore

import io.legado.app.data.entities.SearchBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExplorePageCacheTest {

    private fun book(url: String) = SearchBook(
        bookUrl = url,
        name = "书名",
        author = "作者",
        origin = "https://agg.example.com/novel-adult",
        originName = "小说·成人",
    )

    @Test
    fun `entry survives across view model instances`() {
        // 切走再切回会重建 ViewModel，缓存必须留在进程里，否则每次都要重跑整页抓取。
        ExplorePageCache.put(
            "https://agg.example.com/novel-adult",
            "https://a.example.com/list/1#a5",
            ExplorePageCache.Entry(listOf(book("u1"), book("u2")), nextPage = 2, isEnd = false),
        )

        val cached = ExplorePageCache.get(
            "https://agg.example.com/novel-adult",
            "https://a.example.com/list/1#a5",
        )

        assertNotNull(cached)
        assertEquals(listOf("u1", "u2"), cached!!.books.map { it.bookUrl })
        assertEquals(2, cached.nextPage)
        assertEquals(false, cached.isEnd)
    }

    @Test
    fun `different category or source does not share an entry`() {
        val source = "https://agg.example.com/novel-adult"
        ExplorePageCache.put(
            source,
            "https://a.example.com/list/1#a5",
            ExplorePageCache.Entry(listOf(book("u1")), nextPage = 2, isEnd = false),
        )

        // 同一书源的不同分类必须各自独立，否则切分类会串列表。
        assertNull(ExplorePageCache.get(source, "https://a.example.com/list/2#a5"))
        // 同一分类 URL 挂在另一个书源下也不该命中。
        assertNull(
            ExplorePageCache.get(
                "https://agg.example.com/novel-normal",
                "https://a.example.com/list/1#a5",
            )
        )
    }

    @Test
    fun `refresh drops the entry so the next load refetches`() {
        val source = "https://agg.example.com/novel-adult"
        val url = "https://a.example.com/list/1#a5"
        ExplorePageCache.put(
            source,
            url,
            ExplorePageCache.Entry(listOf(book("u1")), nextPage = 3, isEnd = true),
        )

        ExplorePageCache.remove(source, url)

        assertNull(ExplorePageCache.get(source, url))
    }
}
