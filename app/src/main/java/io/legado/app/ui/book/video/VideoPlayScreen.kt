package io.legado.app.ui.book.video

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import io.legado.app.ui.widget.components.player.AnimatedPlayPauseButton
import io.legado.app.ui.widget.components.player.PlayerProgressSlider
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.delay

/** 双击单侧跳转的步长。 */
private const val SEEK_STEP_MS = 10_000L

/** 长按快进的倍率。 */
private const val FAST_FORWARD_SPEED = 3f

/** 播放中控制层自动隐藏的等待时间。 */
private const val CONTROLS_AUTO_HIDE_MS = 3_000L

/** 控制层可选的播放速度。 */
private val SPEED_OPTIONS = listOf(1f, 1.25f, 1.5f, 2f, 3f)

/**
 * 视频详情页。
 *
 * 这一屏只有封面、播放按钮和选集：播放控制全部在 [FullscreenPlayer] 里，
 * 点播放按钮才拉起播放器，避免详情页上同时挂着播放器与一整套控制条。
 */
@Composable
fun VideoPlayScreen(
    state: VideoPlayUiState,
    onIntent: (VideoPlayIntent) -> Unit,
    onBack: () -> Unit,
    onPlaybackStateChanged: (Boolean) -> Unit = {},
    onProgress: (Long, Long) -> Unit = { _, _ -> },
    onPlaybackEnded: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var playerVisible by remember { mutableStateOf(false) }
    BackHandler(enabled = playerVisible) { playerVisible = false }

    if (playerVisible) {
        FullscreenPlayer(
            state = state,
            onIntent = onIntent,
            onExit = { playerVisible = false },
            onPlaybackStateChanged = onPlaybackStateChanged,
            onProgress = onProgress,
            onPlaybackEnded = onPlaybackEnded,
            modifier = modifier,
        )
        return
    }

    AppScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VideoTopBar(
                title = state.bookName,
                onBack = onBack,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            VideoHero(
                state = state,
                onPlay = { playerVisible = true },
            )
            EpisodeSection(
                state = state,
                onPlayEpisode = { index ->
                    onIntent(VideoPlayIntent.SelectChapter(index))
                    playerVisible = true
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** 详情页头部：封面、书名、元信息与唯一的播放入口。 */
@Composable
private fun VideoHero(
    state: VideoPlayUiState,
    onPlay: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            BookCoverImage(
                name = state.bookName,
                author = state.author,
                path = state.coverUrl,
                bookUrl = state.bookUrl,
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(3f / 4f),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppText(
                    text = state.bookName,
                    style = LegadoTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    state.author.takeIf { it.isNotBlank() },
                    state.kind?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    AppText(
                        text = meta,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.chapters.isNotEmpty()) {
                    AppText(
                        text = stringResource(R.string.video_episode_total, state.chapters.size),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.intro.isNotBlank()) {
            AppText(
                text = state.intro,
                modifier = Modifier.padding(top = 12.dp),
                style = LegadoTheme.typography.bodyMedium,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        MediumTonalButton(
            onClick = onPlay,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            enabled = state.chapters.isNotEmpty() && !state.isLoadingUrl,
            icon = Icons.Default.PlayArrow,
            text = stringResource(R.string.video_play),
            contentDescription = stringResource(R.string.video_play),
        )

        state.errorMessage?.let { message ->
            AppText(
                text = message,
                modifier = Modifier.padding(top = 8.dp),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun VideoTopBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediumPlainButton(
            onClick = onBack,
            icon = AppIcons.Back,
            contentDescription = stringResource(R.string.back),
        )
        AppText(
            text = title,
            modifier = Modifier.weight(1f),
            style = LegadoTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 全屏播放层。
 *
 * 画面铺满整屏，控制层浮在画面上、播放 3 秒后自动隐藏。
 * 手势：控制层隐藏时单击任意处唤出；双击左/右半屏 ±10 秒；长按 3 倍速。
 */
@Composable
private fun FullscreenPlayer(
    state: VideoPlayUiState,
    onIntent: (VideoPlayIntent) -> Unit,
    onExit: () -> Unit,
    onPlaybackStateChanged: (Boolean) -> Unit,
    onProgress: (Long, Long) -> Unit,
    onPlaybackEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var controlsVisible by remember { mutableStateOf(true) }
    // 横版视频在竖屏里上下留黑边，用裁切铺满整屏；退出全屏恢复原样。
    var fillScreen by remember { mutableStateOf(false) }

    // 播放中控制层自动隐藏；暂停时保持可见，否则用户找不到继续按钮。
    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    val activity = context as? Activity
    DisposableEffect(fillScreen, activity) {
        val original = activity?.requestedOrientation
        if (fillScreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (fillScreen) {
                activity?.requestedOrientation =
                    original ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PlayerSurface(
            state = state,
            onIntent = onIntent,
            onPlaybackStateChanged = onPlaybackStateChanged,
            onProgress = onProgress,
            onPlaybackEnded = onPlaybackEnded,
            fillScreen = fillScreen,
            modifier = Modifier.fillMaxSize(),
        )

        // 控制层隐藏时接管整个画面：单击唤出控制层，双击左右半屏跳转，长按 3 倍速。
        if (!controlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = true },
                            onDoubleTap = { offset ->
                                val delta = if (offset.x > size.width / 2f) {
                                    SEEK_STEP_MS
                                } else {
                                    -SEEK_STEP_MS
                                }
                                onIntent(VideoPlayIntent.SeekBy(delta))
                            },
                            onLongPress = { onIntent(VideoPlayIntent.FastForwardStart) },
                            // onLongPress 只在触发那一刻回调一次，收尾靠释放回调。
                            onPress = {
                                val released = tryAwaitRelease()
                                if (released) onIntent(VideoPlayIntent.FastForwardEnd)
                            },
                        )
                    },
            )
        }

        // 控制层：画面区（遮罩 + 中央播放键）与底部控制条放进同一个 Column，
        // 两者是上下分块、永不重叠的兄弟节点。
        // 之前把遮罩做成整屏浮层，再用 Spacer 让出底部——一旦让出的高度小于控制条实际高度，
        // 遮罩就会盖住速度/全屏/静音，点击被它吞掉，按钮看起来像没接线。
        if (controlsVisible) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { controlsVisible = false })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedPlayPauseButton(
                        isPlaying = state.isPlaying,
                        contentDescription = stringResource(R.string.play_pause),
                        onClick = { onIntent(VideoPlayIntent.TogglePlay) },
                        isLoading = state.isLoadingUrl,
                        modifier = Modifier.size(72.dp),
                    )
                }
                PlayerBottomBar(
                    state = state,
                    onIntent = onIntent,
                    fillScreen = fillScreen,
                    onToggleFillScreen = { fillScreen = !fillScreen },
                )
            }
        }

        if (controlsVisible) {
            PlayerTopBar(
                title = state.chapterTitle.ifBlank { state.bookName },
                onBack = onExit,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        if (state.isFastForwarding) {
            AppText(
                text = stringResource(R.string.video_fast_forwarding, FAST_FORWARD_SPEED.toInt()),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 56.dp)
                    .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = LegadoTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediumPlainButton(
            onClick = onBack,
            icon = AppIcons.Back,
            contentDescription = stringResource(R.string.back),
            tint = Color.White,
        )
        AppText(
            text = title,
            modifier = Modifier.weight(1f),
            style = LegadoTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 控制层底部：进度条、速度、全屏、静音。 */
@Composable
private fun PlayerBottomBar(
    state: VideoPlayUiState,
    onIntent: (VideoPlayIntent) -> Unit,
    fillScreen: Boolean,
    onToggleFillScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppText(
                text = formatDuration(state.position),
                style = LegadoTheme.typography.labelMedium,
                color = Color.White,
            )
            PlayerProgressSlider(
                value = state.position.toFloat(),
                onValueChange = { onIntent(VideoPlayIntent.SeekTo(it.toLong())) },
                valueRange = 0f..state.duration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.weight(1f),
            )
            AppText(
                text = formatDuration(state.duration),
                style = LegadoTheme.typography.labelMedium,
                color = Color.White,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpeedButton(
                speed = state.speed,
                onSelect = { onIntent(VideoPlayIntent.SetSpeed(it)) },
            )
            Spacer(modifier = Modifier.width(8.dp))
            MediumPlainButton(
                onClick = onToggleFillScreen,
                icon = if (fillScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = stringResource(
                    if (fillScreen) R.string.video_exit_fullscreen else R.string.video_fullscreen
                ),
                tint = Color.White,
            )
            Spacer(modifier = Modifier.width(8.dp))
            MediumPlainButton(
                onClick = { onIntent(VideoPlayIntent.ToggleMute) },
                icon = if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff
                else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(
                    if (state.isMuted) R.string.video_unmute else R.string.video_mute
                ),
                tint = Color.White,
            )
        }
    }
}

/** 速度控制：点一下在预设档位间轮转，省掉一层弹窗。 */
@Composable
private fun SpeedButton(
    speed: Float,
    onSelect: (Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .clickable {
                val index = SPEED_OPTIONS.indexOfFirst { it == speed }
                val next = if (index < 0) SPEED_OPTIONS.first()
                else SPEED_OPTIONS[(index + 1) % SPEED_OPTIONS.size]
                onSelect(next)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = stringResource(R.string.video_speed, speed),
            style = LegadoTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

/**
 * 播放器画面本体。
 *
 * [fillScreen] 为 true 时裁切铺满整屏（横版视频用），否则按原始比例居中。
 */
@Composable
private fun PlayerSurface(
    state: VideoPlayUiState,
    onIntent: (VideoPlayIntent) -> Unit,
    onPlaybackStateChanged: (Boolean) -> Unit,
    onProgress: (Long, Long) -> Unit,
    onPlaybackEnded: () -> Unit,
    fillScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 硬解失败的片源会重建成纯软解播放器：setEnableDecoderFallback 只在解码器初始化失败时切换，
    // 而部分设备是初始化成功、喂数据时才失败，那一路只能靠重建播放器兜住。
    var softwareDecode by remember { mutableStateOf(false) }
    val mediaHeaders = state.mediaHeaders
    val exoPlayer = remember(softwareDecode, mediaHeaders) {
        val renderersFactory = if (softwareDecode) {
            DefaultRenderersFactory(context).setMediaCodecSelector { mimeType, secure, tunneling ->
                MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling)
                    .filter { it.softwareOnly }
            }
        } else {
            DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        }
        // 媒体请求由播放器自己发出，不经过书源规则；站点校验 Referer/Origin 时必须在这里带上，
        // 否则分片与密钥会被直接断连（表现是能读出时长但画面全黑、进度不动）。
        val httpFactory = DefaultHttpDataSource.Factory().apply {
            if (mediaHeaders.isNotEmpty()) setDefaultRequestProperties(mediaHeaders)
            setAllowCrossProtocolRedirects(true)
        }
        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))
            )
            .build()
            .apply { playWhenReady = true }
    }

    // 播放器的真实状态与进度必须回灌到 UI 状态，否则界面停在初始的 00:00 / 暂停图标。
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlaybackStateChanged(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                onProgress(exoPlayer.currentPosition, exoPlayer.duration)
                if (playbackState == Player.STATE_ENDED) onPlaybackEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                // 硬解崩了就用软解重来一次；已经是软解还失败就不再重试，避免无限重建。
                if (!softwareDecode) softwareDecode = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, state.playUrl) {
        if (state.playUrl.isBlank()) return@LaunchedEffect
        while (true) {
            onProgress(exoPlayer.currentPosition, exoPlayer.duration)
            delay(500)
        }
    }

    LaunchedEffect(state.playUrl, exoPlayer) {
        if (state.playUrl.isBlank()) {
            exoPlayer.stop()
            return@LaunchedEffect
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(state.playUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // 播放/暂停只接受一次性指令。state.isPlaying 是播放器的镜像，
    // 用它驱动 playWhenReady 会在 seek 期间被瞬时「未播放」覆盖，导致播放停住。
    LaunchedEffect(exoPlayer, state.playCommand) {
        val command = state.playCommand ?: return@LaunchedEffect
        exoPlayer.playWhenReady = command
        onIntent(VideoPlayIntent.PlayCommandHandled)
    }

    // 跳转请求是单向的：界面把它交给播放器后立刻清掉，避免每次进度回灌都重复 seek。
    LaunchedEffect(exoPlayer, state.seekRequest) {
        val target = state.seekRequest ?: return@LaunchedEffect
        exoPlayer.seekTo(target)
        onIntent(VideoPlayIntent.SeekHandled)
    }

    // 长按期间用 3 倍速，松手回到用户选的倍速。
    LaunchedEffect(exoPlayer, state.speed, state.isFastForwarding) {
        exoPlayer.setPlaybackSpeed(
            if (state.isFastForwarding) FAST_FORWARD_SPEED else state.speed
        )
    }

    LaunchedEffect(exoPlayer, state.isMuted) {
        exoPlayer.volume = if (state.isMuted) 0f else 1f
    }

    Box(modifier = modifier) {
        if (state.hasVideo) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) view.player = exoPlayer
                    view.resizeMode = if (fillScreen) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
            )
        }
        if (state.isLoadingUrl) {
            AppText(
                text = stringResource(R.string.loading),
                color = Color.White,
                style = LegadoTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EpisodeSection(
    state: VideoPlayUiState,
    onPlayEpisode: (Int) -> Unit,
) {
    if (state.chapters.isEmpty()) return
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppText(
                text = stringResource(R.string.video_episodes),
                style = LegadoTheme.typography.titleSmall,
            )
            AppText(
                text = stringResource(R.string.video_episode_total, state.chapters.size),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier
                .fillMaxWidth()
                .height(episodeGridHeight(state.chapters.size)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false,
        ) {
            items(state.chapters, key = { it.index }) { episode ->
                EpisodeCell(
                    episode = episode,
                    selected = episode.index == state.chapterIndex,
                    onClick = { onPlayEpisode(episode.index) },
                )
            }
        }
    }
}

/** 网格固定 6 列，按行数算高度，避免嵌在滚动列里互相争抢滚动。 */
private fun episodeGridHeight(count: Int): androidx.compose.ui.unit.Dp {
    val rows = (count + 5) / 6
    return (rows * 44 + (rows - 1) * 8 + 8).dp
}

@Composable
private fun EpisodeCell(
    episode: VideoEpisodeUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextCard(
        modifier = Modifier.fillMaxWidth(),
        text = episode.title,
        onClick = onClick,
        backgroundColor = if (selected) LegadoTheme.colorScheme.primary
        else LegadoTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) LegadoTheme.colorScheme.onPrimary
        else LegadoTheme.colorScheme.onSurface,
        cornerRadius = 8.dp,
        horizontalPadding = 4.dp,
        verticalPadding = 10.dp,
        textStyle = LegadoTheme.typography.labelMedium,
    )
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
