package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import androidx.room.withTransaction
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.GroupBookCount
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.ShelfBookSummary
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.utils.ChineseUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BookRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val appDb: AppDatabase,
) {

    private companion object {
        const val SQLITE_MAX_BIND_PARAMETERS = 900
    }

    fun flowBook(bookUrl: String): Flow<Book?> {
        return bookDao.flowGetBook(bookUrl)
    }

    fun flowChapters(bookUrl: String): Flow<List<BookChapter>> {
        return bookChapterDao.getChapterListFlow(bookUrl)
    }

    fun getAllBooks(): Flow<List<Book>> {
        return bookDao.flowAll()
    }

    suspend fun getBookCoverByNameAndAuthor(bookName: String, bookAuthor: String): String? {
        return withContext(Dispatchers.IO) {
            bookDao.getBook(bookName, bookAuthor)?.getDisplayCover()
        }
    }

    suspend fun getChapterTitle(bookName: String, bookAuthor: String, chapterIndex: Int): String? {
        return withContext(Dispatchers.IO) {
            val book = bookDao.getBook(bookName, bookAuthor)
            val bookUrl = book?.bookUrl
            if (bookUrl.isNullOrEmpty()) return@withContext null

            bookChapterDao.getChapterTitleByUrlAndIndex(bookUrl, chapterIndex)
        }
    }

    suspend fun getBook(bookUrl: String): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.getBook(bookUrl)
        }
    }

    suspend fun getBook(name: String, author: String): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.getBook(name, author)
        }
    }

    /**
     * 按书名与作者找书架上的书，书名做简繁归一。
     *
     * 书单里的书名是简体，书架上的书名来自站点、可能是繁体（台湾小说网给「誰讓他修仙的！」），
     * 精确比对会把同一本书判成没导入过，于是重新搜一遍再插一行。
     *
     * 字形候选只负责把行捞出来，判等仍走 [ChineseUtils.sameText]；作者同样归一，
     * 但库里的作者常带「作者：」之外的写法差异，因此只在作者非空时比对。
     */
    suspend fun getBookByAnyScript(name: String, author: String): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.getBook(name, author) ?: bookDao.getBooksByNames(ChineseUtils.variants(name))
                .firstOrNull {
                    ChineseUtils.sameText(it.name, name) &&
                        (author.isEmpty() || ChineseUtils.sameText(it.author, author))
                }
        }
    }

    /**
     * 批量取书，用于把跨分组选中的 bookUrl 解析成实体。
     * 分批是因为 `IN (:bookUrls)` 会展开成绑定参数，单次上限由 SQLite 决定（默认 999）。
     */
    suspend fun getBooksByUrls(bookUrls: Set<String>): List<Book> = withContext(Dispatchers.IO) {
        if (bookUrls.isEmpty()) return@withContext emptyList()
        if (bookUrls.size <= SQLITE_MAX_BIND_PARAMETERS) {
            return@withContext bookDao.getBooksByUrls(bookUrls)
        }
        bookUrls.chunked(SQLITE_MAX_BIND_PARAMETERS).flatMap { chunk ->
            bookDao.getBooksByUrls(chunk.toSet())
        }
    }

    suspend fun getShelfBookSummaries(): List<ShelfBookSummary> {
        return withContext(Dispatchers.IO) {
            bookDao.getShelfBookSummaries()
        }
    }

    fun flowBookShelfByGroup(groupId: Long): Flow<List<BookShelfItem>> {
        return bookDao.flowBookShelfByGroup(groupId)
    }

    fun flowSystemGroupCounts(hideAdult: Boolean = false): Flow<List<GroupBookCount>> {
        return bookDao.flowSystemGroupCounts(hideAdult)
    }

    fun flowAllBookShelfCount(hideAdult: Boolean = false): Flow<Int> {
        return bookDao.flowAllBookShelfCount(hideAdult)
    }

    fun flowUserGroupBookCount(groupId: Long, hideAdult: Boolean = false): Flow<Int> {
        return bookDao.flowUserGroupBookCount(groupId, hideAdult)
    }

    fun flowGroupPreview(groupId: Long): Flow<List<BookShelfItem>> {
        return bookDao.flowGroupPreview(groupId)
    }

    suspend fun getChapter(bookUrl: String, index: Int): BookChapter? {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapter(bookUrl, index)
        }
    }

    suspend fun getChapterCount(bookUrl: String): Int {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapterCount(bookUrl)
        }
    }

    suspend fun getVolumeCount(bookUrl: String): Int {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getVolumeCount(bookUrl)
        }
    }

    suspend fun getChapters(bookUrl: String): List<BookChapter> {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapterList(bookUrl)
        }
    }

    suspend fun getChapters(bookUrl: String, start: Int, end: Int): List<BookChapter> {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapterList(bookUrl, start, end)
        }
    }

    suspend fun update(vararg book: Book) {
        withContext(Dispatchers.IO) {
            bookDao.update(*book)
        }
    }

    /** 只改本书的记录开关，保留阅读器并发保存的进度和其他字段。 */
    suspend fun setReadRecordDisabled(bookUrl: String, disabled: Boolean): Book? =
        withContext(Dispatchers.IO) {
            appDb.withTransaction {
                bookDao.setReadRecordDisabled(bookUrl, disabled)
                bookDao.getBook(bookUrl)
            }
        }

    suspend fun getMinOrder(): Int {
        return withContext(Dispatchers.IO) {
            bookDao.minOrder
        }
    }

    suspend fun insert(book: Book) {
        withContext(Dispatchers.IO) {
            bookDao.insert(book)
        }
    }

    suspend fun insertChapters(vararg chapters: BookChapter) {
        withContext(Dispatchers.IO) {
            bookChapterDao.insert(*chapters)
        }
    }

    suspend fun getHasUpdateBooks(): List<Book> {
        return withContext(Dispatchers.IO) {
            bookDao.hasUpdateBooks
        }
    }

    suspend fun getAll(): List<Book> {
        return withContext(Dispatchers.IO) {
            bookDao.getAll()
        }
    }

    suspend fun getLastReadBook(): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.lastReadBook
        }
    }

    suspend fun replace(oldBook: Book, newBook: Book) {
        withContext(Dispatchers.IO) {
            bookDao.replace(oldBook, newBook)
        }
    }

    suspend fun delete(vararg book: Book) {
        withContext(Dispatchers.IO) {
            bookDao.delete(*book)
        }
    }

    suspend fun deleteChaptersByBook(bookUrl: String) {
        withContext(Dispatchers.IO) {
            bookChapterDao.delByBook(bookUrl)
        }
    }

    suspend fun replaceChaptersAndUpdateBook(book: Book, chapters: List<BookChapter>) {
        withContext(Dispatchers.IO) {
            appDb.runInTransaction {
                bookChapterDao.delByBook(book.bookUrl)
                bookChapterDao.insert(*chapters.toTypedArray())
                bookDao.update(book)
            }
        }
    }

}
