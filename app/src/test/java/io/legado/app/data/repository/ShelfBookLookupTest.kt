package io.legado.app.data.repository

import android.app.Application
import io.legado.app.data.entities.Book
import io.legado.app.support.InMemoryAppDatabaseFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 「这本书在不在书架上」必须跨简繁字形判断。
 *
 * 书单里的书名是简体，书架上的书名来自站点、可能是繁体（台湾小说网返回
 * 「誰讓他修仙的！」「最白的烏鴉」）。按字面精确比对会把同一本书判成没导入过，
 * 于是重搜一遍再插一行——实测书架上出现过两行《看見血條的我，選擇打爆世界》。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ShelfBookLookupTest {

    private lateinit var fixture: InMemoryAppDatabaseFixture
    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        fixture = InMemoryAppDatabaseFixture(app)
        repository = BookRepository(fixture.database.bookDao, fixture.database.bookChapterDao, fixture.database)
        runBlocking {
            fixture.database.bookDao.insert(
                Book(
                    bookUrl = "https://www.twkan.cc/book/n6.html",
                    origin = "https://www.twkan.cc",
                    originName = "台湾小说网",
                    name = "誰讓他修仙的！",
                    author = "最白的烏鴉",
                )
            )
        }
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun `simplified query finds traditional shelf book`() = runBlocking {
        val found = repository.getBookByAnyScript("谁让他修仙的！", "最白的乌鸦")

        assertNotNull("简体书名没认出台繁体的同一本书，导入时会再插一行", found)
        assertEquals("https://www.twkan.cc/book/n6.html", found?.bookUrl)
    }

    @Test
    fun `same glyph still matches`() = runBlocking {
        val found = repository.getBookByAnyScript("誰讓他修仙的！", "最白的烏鴉")

        assertNotNull("同字形反而查不到", found)
    }

    @Test
    fun `same title different author is not the same book`() = runBlocking {
        assertNull(
            "作者不同却判成同一本，会把该导入的书跳过",
            repository.getBookByAnyScript("谁让他修仙的！", "别的作者"),
        )
    }

    @Test
    fun `different title is not the same book`() = runBlocking {
        assertNull(
            "不同书名被判成同一本，会把该导入的书跳过",
            repository.getBookByAnyScript("谁让他修车的", "最白的乌鸦"),
        )
    }
}
