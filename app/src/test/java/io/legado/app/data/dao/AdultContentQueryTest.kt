package io.legado.app.data.dao

import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.readRecord.ReadRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 「隐藏成人内容」的 SQL 侧。
 *
 * 这里守的是最容易漏的一条：**数量与列表必须同口径**。书架的列表在内存侧按
 * `BookShelfItem.origin` 过滤，而分组书籍数量来自 SQL 的 `COUNT(*)`。数量若照旧统计全部
 * 书籍，文件夹模式就会显示「5 本」却只列出 3 本——等于直接告诉别人有内容被藏起来了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class AdultContentQueryTest {

    private lateinit var db: AppDatabase

    private val adultUrl = "https://adult.example.com"
    private val normalUrl = "https://normal.example.com"

    private val adultBook = "https://adult.example.com/book/1"
    private val normalBook = "https://normal.example.com/book/1"

    private val groupId = 0b100L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        runBlocking {
            db.bookSourceDao.insert(
                BookSource(bookSourceUrl = adultUrl, bookSourceName = "成人源", bookSourceGroup = "小说 · 18+"),
                BookSource(bookSourceUrl = normalUrl, bookSourceName = "常规源", bookSourceGroup = "小说"),
            )
            db.bookGroupDao.insert(BookGroup(groupId = groupId, groupName = "分组"))
            db.bookDao.insert(
                newBook(adultBook, adultUrl, groupId),
                newBook(normalBook, normalUrl, groupId),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun adultSourceUrls_onlyContainsSourcesMarked18Plus() = runBlocking {
        assertEquals(listOf(adultUrl), db.bookSourceDao.flowAdultSourceUrls().first())
    }

    @Test
    fun userGroupCount_dropsAdultBooksWhenHiding() = runBlocking {
        assertEquals(2, db.bookDao.flowUserGroupBookCount(groupId, false).first())
        assertEquals(1, db.bookDao.flowUserGroupBookCount(groupId, true).first())
    }

    @Test
    fun allBookShelfCount_dropsAdultBooksWhenHiding() = runBlocking {
        assertEquals(2, db.bookDao.flowAllBookShelfCount(false).first())
        assertEquals(1, db.bookDao.flowAllBookShelfCount(true).first())
    }

    @Test
    fun systemGroupCounts_dropAdultBooksWhenHiding() = runBlocking {
        val shown = db.bookDao.flowSystemGroupCounts(false).first()
            .associate { it.groupId to it.count }
        val hidden = db.bookDao.flowSystemGroupCounts(true).first()
            .associate { it.groupId to it.count }

        assertEquals(2, shown[BookGroup.IdAll])
        assertEquals(1, hidden[BookGroup.IdAll])
    }

    @Test
    fun countMatchesListLength_soTheShelfNeverShowsMoreThanItLists() = runBlocking {
        // 列表侧的内存过滤等价于「origin 不在成人源集合里」，这里直接用 SQL 复核这个等式
        val listUrls = db.bookDao.flowBookShelfByUserGroup(groupId).first()
            .map { it.bookUrl }
            .filterNot { it.startsWith(adultUrl) }
        val count = db.bookDao.flowUserGroupBookCount(groupId, true).first()

        assertEquals(listUrls.size, count)
        assertEquals(listOf(normalBook), listUrls)
    }

    @Test
    fun bookWhoseSourceWasDeleted_isKept() = runBlocking {
        // 源被删掉后 origin 反查不到分组名，没有证据就不能当成人处理
        db.bookSourceDao.delete(adultUrl)

        assertEquals(2, db.bookDao.flowUserGroupBookCount(groupId, true).first())
        assertEquals(2, db.bookDao.flowUserGroupBookCount(groupId, false).first())
    }

    @Test
    fun localBookWithoutOrigin_isKept() = runBlocking {
        db.bookDao.insert(newBook("loc_book", origin = "loc_book", group = groupId))
        assertEquals(2, db.bookDao.flowUserGroupBookCount(groupId, true).first())
    }

    @Test
    fun totalReadBookCount_dropsAdultBooksWhenHiding() = runBlocking {
        // 首页「Books Read」统计的是读过多少本。最近阅读已经藏掉成人书，
        // 统计若照旧把成人书算进去，等于用数字告诉别人「还有一本被藏起来了」。
        record(adultBook, "书-$adultBook")
        record(normalBook, "书-$normalBook")

        assertEquals(2, db.readRecordDao.observeTotalReadBookCount(false).first())
        assertEquals(1, db.readRecordDao.observeTotalReadBookCount(true).first())
    }

    @Test
    fun totalReadBookCount_matchesRecentListWhenHiding() = runBlocking {
        // 两条路径必须给出同一个答案，否则「读了 1 本」和列表里的 1 张卡能对上，
        // 但哪天两边规则走岔了就没人发现。这里直接互相比对。
        record(adultBook, "书-$adultBook")
        record(normalBook, "书-$normalBook")

        val recent = db.readRecordDao.observeRecentHomeBooks(10).first()
            .filterNot { it.origin in setOf(adultUrl) }
        val count = db.readRecordDao.observeTotalReadBookCount(true).first()

        assertEquals(recent.size, count)
    }

    @Test
    fun totalReadBookCount_keepsRecordWhoseSourceWasDeleted() = runBlocking {
        // 与最近阅读同口径：反查不到来源就当没有证据，保留
        record(adultBook, "书-$adultBook")
        db.bookSourceDao.delete(adultUrl)

        assertEquals(1, db.readRecordDao.observeTotalReadBookCount(true).first())
    }

    private suspend fun record(bookUrl: String, bookName: String) {
        db.readRecordDao.insert(
            ReadRecord(
                deviceId = "dev",
                bookName = bookName,
                bookAuthor = "作者",
                readTime = 60_000L,
                lastRead = System.currentTimeMillis(),
            )
        )
    }

    private fun newBook(url: String, origin: String, group: Long) = SearchBook(
        bookUrl = url,
        origin = origin,
        name = "书-$url",
        author = "作者"
    ).toBook().apply {
        this.group = group
    }
}
