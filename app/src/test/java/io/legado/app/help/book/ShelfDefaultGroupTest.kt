package io.legado.app.help.book

import android.app.Application
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.constant.ExploreGroups
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.BookRepository
import io.legado.app.support.InMemoryAppDatabaseFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
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
 * 六个默认分组（与发现页分类同名），以及「首次入架按源分类归组」。
 *
 * 两个容易踩的点：
 * 1. 内置分组里本来就有同名的「小说」「漫画」（`IdText` / `IdManga`，负数 id 的虚拟分组），
 *    按名字找分组如果不管正负，就会把负数 id 当成成员位写进 `Book.group`，位图直接算错。
 * 2. 「首次入架」不能只看行是否存在：详情页试读会先插一行未上架的书，
 *    之后点「加入书架」走的还是同一个 bookUrl，只看 `has()` 就永远归不了组。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ShelfDefaultGroupTest {

    private lateinit var fixture: InMemoryAppDatabaseFixture
    private lateinit var repository: BookRepository

    private val groupDao get() = fixture.database.bookGroupDao
    private val sourceDao get() = fixture.database.bookSourceDao

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        fixture = InMemoryAppDatabaseFixture(app)
        repository = BookRepository(
            fixture.database.bookDao,
            fixture.database.bookChapterDao,
            fixture.database,
        )
        // 真实库里这两个内置分组恒在，名字与发现页分类撞车
        groupDao.insert(
            BookGroup(
                groupId = BookGroup.IdText,
                groupName = ExploreGroups.NOVEL,
                order = -26,
                show = false,
            ),
            BookGroup(
                groupId = BookGroup.IdManga,
                groupName = ExploreGroups.MANGA,
                order = -25,
                show = false,
            ),
        )
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    private fun source(url: String, group: String?, type: Int) = BookSource(
        bookSourceUrl = url,
        bookSourceName = url,
        bookSourceGroup = group,
        bookSourceType = type,
    )

    private fun userGroups() = groupDao.all.filter { it.groupId > 0 }

    private fun userGroupIdOf(name: String): Long =
        userGroups().single { it.groupName == name }.groupId

    private fun webBook(origin: String, bookUrl: String = "$origin/1") = Book(
        bookUrl = bookUrl,
        origin = origin,
        originName = "源",
        name = "书",
        author = "作者",
        type = BookType.text,
    )

    // ── 默认分组的建组契约 ──

    @Test
    fun `建齐六个默认分组且名字与发现页分类一致`() {
        ensureShelfDefaultGroups()

        val created = userGroups().sortedBy { it.order }
        assertEquals(ExploreGroups.ordered, created.map { it.groupName })
        assertTrue("默认分组必须是正数 id 的用户分组", created.all { it.groupId > 0 })
        assertEquals("分组 id 撞车", created.size, created.map { it.groupId }.distinct().size)
    }

    @Test
    fun `重复建组不会多出第二个同名分组`() {
        ensureShelfDefaultGroups()
        val first = userGroups().associate { it.groupName to it.groupId }

        ensureShelfDefaultGroups()

        assertEquals(first, userGroups().associate { it.groupName to it.groupId })
    }

    @Test
    fun `已有的同名用户分组直接复用`() {
        groupDao.insert(BookGroup(groupId = 5L, groupName = ExploreGroups.NOVEL, order = 0))

        ensureShelfDefaultGroups()

        assertEquals(
            "同名的用户分组被另建了一份",
            listOf(BookGroup.IdText, 5L).sorted(),
            groupDao.all.filter { it.groupName == ExploreGroups.NOVEL }.map { it.groupId }.sorted(),
        )
        assertEquals(6, userGroups().size)
    }

    // ── 首次入架归组 ──

    @Test
    fun `来自漫画源的新书归到漫画分组`() = runBlocking {
        ensureShelfDefaultGroups()
        sourceDao.insert(source("https://m.example.com", ExploreGroups.MANGA, BookSourceType.image))

        val book = webBook("https://m.example.com")
        repository.insert(book)

        assertEquals(userGroupIdOf(ExploreGroups.MANGA), book.group)
    }

    @Test
    fun `成人源按带 18+ 的分类归组`() = runBlocking {
        ensureShelfDefaultGroups()
        sourceDao.insert(
            source("https://v18.example.com", ExploreGroups.VIDEO_ADULT, BookSourceType.video)
        )

        val book = webBook("https://v18.example.com")
        repository.insert(book)

        assertEquals(userGroupIdOf(ExploreGroups.VIDEO_ADULT), book.group)
    }

    @Test
    fun `源分组名写错时按源类型归组`() = runBlocking {
        // 用户自建源常写「短剧」这类词，发现页靠 bookSourceType 兜底，归组也必须同样兜底
        ensureShelfDefaultGroups()
        sourceDao.insert(source("https://d.example.com", "短剧", BookSourceType.video))

        val book = webBook("https://d.example.com")
        repository.insert(book)

        assertEquals(userGroupIdOf(ExploreGroups.VIDEO), book.group)
    }

    @Test
    fun `本地书不归组`() = runBlocking {
        ensureShelfDefaultGroups()
        val book = Book(
            bookUrl = "/sdcard/novel/a.txt",
            origin = BookType.localTag,
            originName = "a.txt",
            name = "本地书",
            author = "",
            type = BookType.local,
        )

        repository.insert(book)

        assertEquals(0L, book.group)
    }

    @Test
    fun `源不存在时不归组`() = runBlocking {
        ensureShelfDefaultGroups()

        val book = webBook("https://unknown.example.com")
        repository.insert(book)

        assertEquals(0L, book.group)
    }

    @Test
    fun `已经有分组的书保持原样`() = runBlocking {
        ensureShelfDefaultGroups()
        groupDao.insert(BookGroup(groupId = 1L, groupName = "我的收藏", order = 0))
        sourceDao.insert(source("https://m.example.com", ExploreGroups.MANGA, BookSourceType.image))

        val book = webBook("https://m.example.com", "https://m.example.com/2")
        book.group = 1L
        repository.insert(book)

        assertEquals(1L, book.group)
    }

    @Test
    fun `已在书架的书被移出分组后不会再次被塞回去`() = runBlocking {
        ensureShelfDefaultGroups()
        sourceDao.insert(source("https://m.example.com", ExploreGroups.MANGA, BookSourceType.image))
        val book = webBook("https://m.example.com", "https://m.example.com/3")
        repository.insert(book)
        assertEquals(userGroupIdOf(ExploreGroups.MANGA), book.group)

        // 用户手动把它移出所有分组，之后保存书籍信息（或同步阅读进度）不该又被塞回去
        book.group = 0L
        book.save()

        assertEquals(0L, book.group)
    }

    @Test
    fun `换源替换已有书时不归组`() = runBlocking {
        // 换源是把新书插进来替掉旧书，旧书本来没有分组说明用户自己移出过。
        // migrateTo 会把 group 一起迁过去，迁移后的 0 不能被当成「首次入架」再补一个。
        ensureShelfDefaultGroups()
        sourceDao.insert(source("https://m.example.com", ExploreGroups.MANGA, BookSourceType.image))

        val book = webBook("https://m.example.com", "https://m.example.com/5")
        repository.insert(book, assignDefaultGroup = false)

        assertEquals(0L, book.group)
    }

    @Test
    fun `详情页试读后再加入书架仍然归组`() = runBlocking {
        ensureShelfDefaultGroups()
        sourceDao.insert(source("https://m.example.com", ExploreGroups.MANGA, BookSourceType.image))
        // 试读：先以「未上架」插一行
        val book = webBook("https://m.example.com", "https://m.example.com/4")
        book.addType(BookType.notShelf)
        book.save()
        assertEquals("未上架的书不该归组", 0L, book.group)

        // 点「加入书架」：同一 bookUrl，去掉未上架标记后再保存
        book.removeType(BookType.notShelf)
        book.save()

        assertEquals(userGroupIdOf(ExploreGroups.MANGA), book.group)
    }
}
