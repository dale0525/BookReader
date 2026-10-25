package io.legado.app.ui.book.video

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.AppLog
import io.legado.app.constant.Status
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.BookRepository
import io.legado.app.help.book.getBookSource
import io.legado.app.help.config.AppConfig
import io.legado.app.help.book.update
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.SourceCallBack
import io.legado.app.model.webBook.WebBook
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 视频播放页的数据层。
 *
 * 与有声书不同，这里不接管播放器生命周期：ExoPlayer 由界面持有并随界面销毁释放，
 * ViewModel 只负责「这一集该播哪个地址」——把书源正文规则解析出的直链交给界面。
 */
class VideoPlayViewModel(
    private val application: Application,
    private val bookRepository: BookRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<VideoPlayEffect>(extraBufferCapacity = 4)
    val effects = _effects.asSharedFlow()

    private var book: Book? = null
    private var bookSource: BookSource? = null
    private var chapters: List<BookChapter> = emptyList()
    private var initialized = false

    /** 正在解析的集号，避免快速切集时旧请求覆盖新集。 */
    private var loadingIndex: Int? = null

    fun init(bookUrl: String) {
        if (initialized || bookUrl.isBlank()) return
        initialized = true
        viewModelScope.launch {
            val loaded = bookRepository.getBook(bookUrl)
            if (loaded == null) {
                _uiState.update { it.copy(errorMessage = "书籍不存在") }
                return@launch
            }
            book = loaded
            bookSource = loaded.getBookSource()
            _uiState.update {
                it.copy(
                    bookUrl = loaded.bookUrl,
                    bookName = loaded.name,
                    author = loaded.author,
                    kind = loaded.kind,
                    intro = loaded.intro.orEmpty(),
                    coverUrl = loaded.getDisplayCover(),
                    chapterIndex = loaded.durChapterIndex,
                )
            }
            loadChapters(loaded)
        }
    }

    private suspend fun loadChapters(book: Book) {
        val source = bookSource ?: return
        // 目录解析会走网络与 JS 引擎，必须离开主线程
        val result = withContext(Dispatchers.IO) { WebBook.getChapterListAwait(source, book) }
        val list = result.getOrElse { error ->
            AppLog.put("获取视频目录出错\n$error", error)
            _uiState.update { it.copy(errorMessage = "获取剧集列表失败") }
            return
        }
        chapters = list
        val target = book.durChapterIndex.coerceIn(0, list.lastIndex.coerceAtLeast(0))
        _uiState.update {
            it.copy(
                chapters = list.map { chapter ->
                    VideoEpisodeUi(
                        index = chapter.index,
                        title = chapter.title,
                        url = chapter.url,
                    )
                }.toImmutableList(),
                chapterIndex = target,
                chapterTitle = list.getOrNull(target)?.title.orEmpty(),
            )
        }
        if (list.isNotEmpty()) {
            loadPlayUrl(target)
        }
    }

    /**
     * 解析某一集的播放地址。视频源的正文规则返回的就是播放直链，
     * 因此这里复用正文链路而不另开一套规则。
     */
    private fun loadPlayUrl(index: Int) {
        val source = bookSource ?: return
        val currentBook = book ?: return
        val chapter = chapters.getOrNull(index) ?: return
        loadingIndex = index
        _uiState.update {
            it.copy(
                chapterIndex = index,
                chapterTitle = chapter.title,
                isLoadingUrl = true,
                playUrl = "",
                seekRequest = null,
                playCommand = null,
                position = 0L,
                duration = 0L,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val url = kotlin.runCatching {
                withContext(Dispatchers.IO) {
                    WebBook.getContentAwait(source, currentBook, chapter, needSave = false)
                }
            }.getOrElse { error ->
                AppLog.put("解析视频地址出错\n$error", error)
                ""
            }
            if (loadingIndex != index) return@launch
            loadingIndex = null
            _uiState.update {
                it.copy(
                    isLoadingUrl = false,
                    playUrl = url.trim(),
                    mediaHeaders = mediaHeadersOf(source),
                    errorMessage = if (url.isBlank()) "这一集没有拿到播放地址" else null,
                )
            }
            saveProgress(index)
        }
    }

    /**
     * 取书源声明的请求头里，媒体请求需要的那部分。
     *
     * 只挑 Referer/Origin/User-Agent：这三项是站点校验媒体防盗链时真正看的东西，
     * 而 `qm-params`、`sign` 这类按接口签发的头带给 CDN 既无用也可能引起歧义。
     */
    private fun mediaHeadersOf(source: BookSource): Map<String, String> {
        val all = source.getHeaderMap(AppConfig.userAgent) // 书源没写 UA 时用 App 默认 UA
        return buildMap {
            listOf("Referer", "Origin", "User-Agent").forEach { key ->
                all.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
                    ?.let { put(it.key, it.value) }
            }
        }
    }

    fun onIntent(intent: VideoPlayIntent) {
        when (intent) {
            is VideoPlayIntent.Init -> init(intent.bookUrl)
            VideoPlayIntent.TogglePlay ->
                _uiState.update { it.copy(playCommand = !it.isPlaying) }
            is VideoPlayIntent.SeekTo ->
                _uiState.update {
                    it.copy(seekRequest = intent.positionMs, position = intent.positionMs)
                }

            VideoPlayIntent.SeekHandled -> _uiState.update { it.copy(seekRequest = null) }
            VideoPlayIntent.PlayCommandHandled ->
                _uiState.update { it.copy(playCommand = null) }
            VideoPlayIntent.PreviousChapter -> stepChapter(-1)
            VideoPlayIntent.NextChapter -> stepChapter(1)
            is VideoPlayIntent.SelectChapter -> loadPlayUrl(intent.index)
            is VideoPlayIntent.SetSpeed -> _uiState.update { it.copy(speed = intent.value) }
            VideoPlayIntent.ToggleMute -> _uiState.update { it.copy(isMuted = !it.isMuted) }
            VideoPlayIntent.FastForwardStart ->
                _uiState.update { it.copy(isFastForwarding = true) }

            VideoPlayIntent.FastForwardEnd ->
                _uiState.update { it.copy(isFastForwarding = false) }

            is VideoPlayIntent.SeekBy -> {
                // 以播放器当前回灌的位置为基准算目标，避免连点双击时基于陈旧值累加。
                val target = (_uiState.value.position + intent.deltaMs)
                    .coerceIn(0L, _uiState.value.duration.coerceAtLeast(0L))
                _uiState.update { it.copy(seekRequest = target, position = target) }
            }

            VideoPlayIntent.Retry -> loadPlayUrl(_uiState.value.chapterIndex)
            VideoPlayIntent.BackPressed -> _effects.tryEmit(VideoPlayEffect.Finish)
        }
    }

    private fun stepChapter(delta: Int) {
        val target = _uiState.value.chapterIndex + delta
        if (target !in chapters.indices) return
        loadPlayUrl(target)
    }

    /** 界面回调：播放器已就绪并开始播放。 */
    fun onPlaybackStateChanged(isPlaying: Boolean) {
        _uiState.update {
            it.copy(
                status = if (isPlaying) Status.PLAY
                else Status.PAUSE
            )
        }
    }

    fun onProgress(position: Long, duration: Long) {
        _uiState.update { it.copy(position = position, duration = duration) }
    }

    fun onPlaybackEnded() {
        val next = _uiState.value.chapterIndex + 1
        if (next in chapters.indices) {
            loadPlayUrl(next)
        } else {
            _uiState.update { it.copy(status = Status.STOP) }
        }
    }

    fun onPlaybackError(message: String?) {
        _uiState.update { it.copy(errorMessage = message ?: "播放失败") }
    }

    private fun saveProgress(index: Int) {
        val currentBook = book ?: return
        Coroutine.async<Unit> {
            currentBook.durChapterIndex = index
            currentBook.durChapterTitle = chapters.getOrNull(index)?.title
            currentBook.durChapterTime = System.currentTimeMillis()
            currentBook.lastCheckCount = 0
            currentBook.update()
            SourceCallBack.callBackBook(
                SourceCallBack.SAVE_READ,
                bookSource,
                currentBook,
                chapters.getOrNull(index),
            )
        }
    }
}
