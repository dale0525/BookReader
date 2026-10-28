package io.legado.app.domain.usecase

import android.content.Context
import android.net.Uri
import io.legado.app.data.entities.Book
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.readText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class ImportBookshelfUseCase(
    private val context: Context,
    private val bookRepository: BookRepository,
    private val bookSourceRepository: BookSourceRepository,
) {
    suspend fun import(
        str: String,
        groupId: Long,
        onProgress: suspend (String) -> Unit = {}
    ): Result<Unit> = kotlin.runCatching {
        val text = str.trim()
        when {
            text.isAbsUrl() -> {
                val downloadedText = okHttpClient.newCallResponseBody {
                    url(text)
                }.decompressed().text()
                import(downloadedText, groupId, onProgress).getOrThrow()
            }

            text.isJsonArray() -> {
                importByJson(text, groupId, onProgress)
            }

            else -> {
                throw NoStackTraceException("格式不对")
            }
        }
    }

    suspend fun import(
        uri: Uri,
        groupId: Long,
        onProgress: suspend (String) -> Unit = {}
    ): Result<Unit> = kotlin.runCatching {
        val text = uri.readText(context)
        import(text, groupId, onProgress).getOrThrow()
    }

    private suspend fun importByJson(
        json: String,
        groupId: Long,
        onProgress: suspend (String) -> Unit
    ) {
        onProgress("导入中...")
        // 书单只有书名和作者，判断不了类型。文字源排在前面先搜，命中就不必再碰
        // 漫画源与视频源（它们对文字书命中率为 0，却要花 2~3 秒）；仍未命中时
        // 继续搜剩下的源，覆盖率与全部源一致。
        val textSourceUrls = bookSourceRepository.getAllTextEnabledPart()
            .mapTo(HashSet()) { it.bookSourceUrl }
        val bookSourceParts = bookSourceRepository.getAllEnabledPart()
            .sortedBy { it.bookSourceUrl !in textSourceUrls }
        val semaphore = Semaphore(IMPORT_CONCURRENCY)
        val books = GSON.fromJsonArray<Map<String, String?>>(json).getOrThrow()

        withContext(Dispatchers.IO) {
            // 每本书各自走完自己的源序列；书与书之间并发，受 semaphore 限制。
            coroutineScope {
                books.map { bookInfo ->
                    async {
                        val name = bookInfo["name"] ?: ""
                        val author = bookInfo["author"] ?: ""
                        // 简繁归一后再判「已在书架」：书单是简体、库里存的是站点返回的字形，
                        // 精确比对会把同一本书判成没导入过，重搜一遍再插一行。
                        if (name.isEmpty() || bookRepository.getBookByAnyScript(name, author) != null) {
                            return@async
                        }
                        semaphore.withPermit {
                            var foundBook: Book? = null
                            for (s in bookSourceParts) {
                                ensureActive()
                                val source = s.getBookSource() ?: continue
                                foundBook =
                                    WebBook.preciseSearchAwait(source, name, author).getOrNull()
                                if (foundBook != null) break
                            }
                            if (foundBook != null) {
                                val book = foundBook
                                if (groupId > 0) {
                                    book.group = groupId
                                }
                                if (bookRepository.getBook(book.bookUrl) != null) {
                                    bookRepository.update(book)
                                } else {
                                    bookRepository.insert(book)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    context.toastOnUi("没有搜索到<$name>$author")
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private companion object {
        /** 同时处理的书籍数。每本书内部仍按源顺序串行搜索。 */
        const val IMPORT_CONCURRENCY = 10
    }
}
