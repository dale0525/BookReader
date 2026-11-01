package io.legado.app.ui.widget.components.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme

/**
 * 播放器通用的进度条：拖动时轨道加粗、滑块变大，松手提交进度；点一下也能直接跳过去。
 *
 * 之前轨道只有 2dp、没有滑块，触碰区只有 18dp 高，手指既难压中、压中了也看不出抓在哪。
 * 现在轨道常态 4dp、拖动时 8dp，末端带一个圆点，触碰区抬到 40dp——
 * 视觉上仍然是一条细进度条，但可点面积按手指的尺寸给。
 */
@Composable
fun PlayerProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val rangeLength = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    var isDragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var previewFraction by remember(valueRange) {
        mutableFloatStateOf(((value - valueRange.start) / rangeLength).coerceIn(0f, 1f))
    }
    LaunchedEffect(value, valueRange) {
        if (!isDragging) {
            previewFraction = ((value - valueRange.start) / rangeLength).coerceIn(0f, 1f)
        }
    }
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 4.dp,
        animationSpec = tween(durationMillis = 150),
        label = "player_progress_track_height",
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (isDragging) 9.dp else 6.dp,
        animationSpec = tween(durationMillis = 150),
        label = "player_progress_thumb_radius",
    )
    val activeColor = LegadoTheme.colorScheme.onSurface
    val inactiveColor = LegadoTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(valueRange, widthPx) {
                fun updatePreview(x: Float) {
                    previewFraction = (x / widthPx).coerceIn(0f, 1f)
                }
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        updatePreview(offset.x)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChange(valueRange.start + previewFraction * rangeLength)
                    },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        updatePreview(change.position.x)
                    },
                )
            }
            // 最常见的用法是「点一下跳到那里」。只支持拖动等于逼用户精确滑到目标，
            // 而进度条上一像素对应的时长可能有十几秒。
            .pointerInput(valueRange, widthPx) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / widthPx).coerceIn(0f, 1f)
                    previewFraction = fraction
                    onValueChange(valueRange.start + fraction * rangeLength)
                }
            },
    ) {
        val centerY = size.height / 2f
        val progressX = size.width * previewFraction
        val trackPx = trackHeight.toPx()
        val thumbPx = thumbRadius.toPx()
        // 滑块圆心不能压到两端之外，否则起点/终点看起来被截掉一截。
        val thumbX = progressX.coerceIn(thumbPx, size.width - thumbPx)
        drawLine(
            color = inactiveColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = trackPx,
            cap = StrokeCap.Round,
        )
        if (progressX > 0f) {
            drawLine(
                color = activeColor,
                start = Offset(0f, centerY),
                end = Offset(progressX, centerY),
                strokeWidth = trackPx,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(color = activeColor, radius = thumbPx, center = Offset(thumbX, centerY))
    }
}
