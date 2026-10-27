package io.legado.app.data.repository

import android.app.Application
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.support.InMemoryAppDatabaseFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 换源结果缓存读取：简体书名必须能取回繁体站点的结果。
 *
 * 换源界面第二次打开时不重新搜索，直接读上一次落库的结果（[SearchRepositoryImpl.findChangeSourceBooks]）；
 * 这里若按书名精确匹配，台湾小说网这类返回繁体书名的源在重进界面后就整条消失。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ChangeSourceCachedBooksTest {
    private lateinit var fixture: InMemoryAppDatabaseFixture
    private val db get() = fixture.database
    private val sourceUrl = "https://www.twkan.cc"

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        fixture = InMemoryAppDatabaseFixture(app)
    }

    @After fun tearDown() {
        fixture.close()
    }

    private fun seed() = runBlocking {
        db.bookSourceDao.insert(
            BookSource(
                bookSourceUrl = sourceUrl,
                bookSourceName = "台湾小说网",
                bookSourceGroup = "小说",
            )
        )
        db.searchBookDao.insert(
            SearchBook(
                bookUrl = "$sourceUrl/book/n6.html",
                origin = sourceUrl,
                originName = "台湾小说网",
                name = "劍來",
                author = "烽火戲諸侯",
            )
        )
    }

    @Test fun `simplified name finds cached traditional result`() = runBlocking {
        seed()

        val books = SearchRepositoryImpl(db).findChangeSourceBooks("剑来", "")

        assertEquals(1, books.size)
        assertEquals("劍來", books.first().name)
    }

    @Test fun `author filter accepts cross script author`() = runBlocking {
        seed()

        val books = SearchRepositoryImpl(db).findChangeSourceBooks("剑来", "烽火戏诸侯")

        assertEquals(1, books.size)
    }

    @Test fun `another book stays out of the cached result`() = runBlocking {
        seed()

        assertEquals(0, SearchRepositoryImpl(db).findChangeSourceBooks("剑仙", "").size)
    }

    /**
     * 台湾站点常见的字形差异不止「简→繁」一档，`裡面`/`裏面` 这类同是繁体但写法不同，
     * 查库时的候选字形漏掉一种，那条缓存结果就搜不到。
     */
    @Test fun `taiwan glyph variant is found by its simplified form`() = runBlocking {
        db.bookSourceDao.insert(
            BookSource(
                bookSourceUrl = sourceUrl,
                bookSourceName = "台湾小说网",
                bookSourceGroup = "小说",
            )
        )
        db.searchBookDao.insert(
            SearchBook(
                bookUrl = "$sourceUrl/book/n7.html",
                origin = sourceUrl,
                originName = "台湾小说网",
                name = "裡面",
                author = "某甲",
            )
        )

        assertEquals(1, SearchRepositoryImpl(db).findChangeSourceBooks("里面", "").size)
    }
}
