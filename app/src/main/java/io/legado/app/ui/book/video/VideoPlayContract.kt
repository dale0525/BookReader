package io.legado.app.ui.book.video

import androidx.compose.runtime.Stable
import io.legado.app.constant.Status
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** 选集条目：只带播放器需要的字段，避免把 BookChapter 整个搬进 UI 层。 */
@Stable
data class VideoEpisodeUi(
    val index: Int,
    val title: String,
    val url: String,
)

@Stable
data class VideoPlayUiState(
    val bookUrl: String = "",
    val bookName: String = "",
    val author: String = "",
    val kind: String? = null,
    val intro: String = "",
    val coverUrl: String? = null,
    val chapters: ImmutableList<VideoEpisodeUi> = persistentListOf(),
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    /** 当前集解析出的播放地址；为空表示还没解析出来或解析失败。 */
    val playUrl: String = "",
    /**
     * 媒体请求要带的请求头。
     *
     * 部分站点的分片与密钥域名会校验 Referer/Origin，缺任一个直接断连（黄果即如此），
     * 而这些请求由播放器自己发出、不经过书源规则，只能在这里单独交给播放器。
     */
    val mediaHeaders: Map<String, String> = emptyMap(),
    val isLoadingUrl: Boolean = false,
    val status: Int = Status.STOP,
    val position: Long = 0L,
    val duration: Long = 0L,
    /**
     * 待执行的跳转目标；界面消费后由 [VideoPlayIntent.SeekHandled] 清空。
     *
     * 不能复用 [position]：那个字段由播放器每 500ms 回灌，若跳转也写它，
     * 回灌与跳转互相覆盖会形成反馈环路，进度条会原地弹回。
     */
    val seekRequest: Long? = null,
    /**
     * 待执行的一次性播放/暂停指令；界面消费后由 [VideoPlayIntent.PlayCommandHandled] 清空。
     *
     * [status] 是播放器回报的**镜像**，不能反过来驱动播放器：seek 期间播放器会瞬时上报
     * 「未播放」，若把镜像写回 playWhenReady 就会把播放永久停住。
     */
    val playCommand: Boolean? = null,
    val speed: Float = 1f,
    /**
     * 静音状态。播放器音量只由本应用改写，不会被它自己改回去，
     * 所以这里可以直接作为单向指令驱动 volume，不必像播放/暂停那样走一次性命令。
     */
    val isMuted: Boolean = false,
    /** 长按临时 3 倍速快进时为 true；松手回落到 [speed]。 */
    val isFastForwarding: Boolean = false,
    val errorMessage: String? = null,
) {
    val isPlaying: Boolean get() = status == Status.PLAY
    val canPrevious: Boolean get() = chapterIndex > 0
    val canNext: Boolean get() = chapterIndex < chapters.lastIndex
    val hasVideo: Boolean get() = playUrl.isNotBlank()
}

sealed interface VideoPlayIntent {
    data class Init(val bookUrl: String) : VideoPlayIntent
    data object TogglePlay : VideoPlayIntent
    data class SeekTo(val positionMs: Long) : VideoPlayIntent
    /** 界面已把 [VideoPlayUiState.seekRequest] 交给播放器，清掉以免重复跳转。 */
    data object SeekHandled : VideoPlayIntent
    /** 界面已把 [VideoPlayUiState.playCommand] 交给播放器，清掉以免重复下发。 */
    data object PlayCommandHandled : VideoPlayIntent
    data object PreviousChapter : VideoPlayIntent
    data object NextChapter : VideoPlayIntent
    data class SelectChapter(val index: Int) : VideoPlayIntent
    data class SetSpeed(val value: Float) : VideoPlayIntent
    data object ToggleMute : VideoPlayIntent
    /** 长按快进开始/结束；结束由 [FastForwardEnd] 表示。 */
    data object FastForwardStart : VideoPlayIntent
    data object FastForwardEnd : VideoPlayIntent
    /** 双击左右半屏快退/快进，[deltaMs] 为负表示后退。 */
    data class SeekBy(val deltaMs: Long) : VideoPlayIntent
    data object Retry : VideoPlayIntent
    data object BackPressed : VideoPlayIntent
}

sealed interface VideoPlayEffect {
    data object Finish : VideoPlayEffect
}
