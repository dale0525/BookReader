package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.ExploreSourceSummary
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.gateway.ExploreBooksGateway
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface ExploreRepository {
    fun getBookshelfItems(): Flow<List<SearchBook>>
    /** 发现页源卡片：只含卡片要显示的字段，不含 jsLib。 */
    fun getExploreSourceSummaries(): Flow<List<ExploreSourceSummary>>
    suspend fun getBookSource(sourceUrl: String): BookSource?
    suspend fun getSourceExploreKinds(sourceUrl: String): List<ExploreKind>
    suspend fun refreshSourceExploreKinds(sourceUrl: String): List<ExploreKind>
    suspend fun deleteSource(sourceUrl: String)
}

class ExploreRepositoryImpl(
    private val appDb: AppDatabase
) : ExploreRepository, ExploreBooksGateway {

    override fun getBookshelfItems(): Flow<List<SearchBook>> {
        return appDb.bookDao.flowBookShelf().map { books ->
            books.filterNot { it.isNotShelf }
                .map {
                    SearchBook(
                        bookUrl = it.bookUrl,
                        name = it.name,
                        author = it.author,
                        originName = it.originName,
                        type = it.type,
                        coverUrl = it.coverUrl,
                        latestChapterTitle = it.latestChapterTitle
                    )
                }
        }
    }

    override fun getExploreSourceSummaries(): Flow<List<ExploreSourceSummary>> {
        return appDb.bookSourceDao.flowExploreSummaries()
    }

    override suspend fun getBookSource(sourceUrl: String): BookSource? {
        return appDb.bookSourceDao.getBookSource(sourceUrl)
    }

    override suspend fun exploreBooks(
        bookSource: BookSource,
        url: String,
        page: Int,
        key: String?
    ): List<SearchBook> {
        return WebBook.exploreBookSuspend(bookSource, url, page, key = key, isSearch = key != null)
    }

    override suspend fun saveSearchBooks(books: List<SearchBook>) {
        if (books.isNotEmpty()) {
            appDb.searchBookDao.insert(books)
        }
    }

    override suspend fun getSourceExploreKinds(sourceUrl: String): List<ExploreKind> = withContext(IO) {
        val source = appDb.bookSourceDao.getBookSource(sourceUrl)
        return@withContext source?.exploreKinds() ?: emptyList()
    }

    override suspend fun refreshSourceExploreKinds(sourceUrl: String): List<ExploreKind> =
        withContext(IO) {
            val source =
                appDb.bookSourceDao.getBookSource(sourceUrl) ?: return@withContext emptyList()
            source.clearExploreKindsCache()
            source.exploreKinds()
        }

    override suspend fun deleteSource(sourceUrl: String) {
        SourceHelp.deleteBookSource(sourceUrl)
    }
}
