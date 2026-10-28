package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 九宫格点击区域设置。
 *
 * 必须自成窗口：打开它的入口（阅读器「更多设置」）本身是 miuix 的原生底部弹层窗口，
 * 而独立设置页把它与设置列表并列组合。普通 Compose 层在两种宿主里都会落在窗口之下，
 * 表现为「面板不出现、点了也没反应」，所以这里用全屏 Dialog 承载，顺带把下层的设置界面盖住。
 */
@Composable
fun ClickActionConfigSheet(
    onDismissRequest: () -> Unit,
) {
    val readSettingsRepository: ReadSettingsRepository = koinInject()
    val preferences by readSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = ReadPreferences()
    )
    val scope = rememberCoroutineScope()

    val actions = linkedMapOf(
        -1 to stringResource(R.string.non_action),
        0 to stringResource(R.string.menu),
        1 to stringResource(R.string.next_page),
        2 to stringResource(R.string.prev_page),
        3 to stringResource(R.string.next_chapter),
        4 to stringResource(R.string.previous_chapter),
        5 to stringResource(R.string.read_aloud_prev_paragraph),
        6 to stringResource(R.string.read_aloud_next_paragraph),
        7 to stringResource(R.string.bookmark_add),
        8 to stringResource(R.string.edit_content),
        9 to stringResource(R.string.replace_state_change),
        10 to stringResource(R.string.chapter_list),
        11 to stringResource(R.string.search_content),
        12 to stringResource(R.string.sync_book_progress_t),
        13 to stringResource(R.string.read_aloud_pause_resume),
    )

    var selectingPrefKey by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ProvideAppDensity {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LegadoTheme.colorScheme.background)
                    .clickable(onClick = onDismissRequest),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                ) {
                    Spacer(
                        modifier = Modifier.padding(top = 36.dp)
                    )
                    ClickAreaRow {
                        ClickAreaCell(
                            label = actions[preferences.clickActionTL] ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(3.dp),
                            onClick = { selectingPrefKey = PreferKey.clickActionTL },
                        )
                        ClickAreaCell(
                            label = actions[preferences.clickActionTC] ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(3.dp),
                            onClick = { selectingPrefKey = PreferKey.clickActionTC },
                        )
                        ClickAreaCell(
                            label = actions[preferences.clickActionTR] ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(3.dp),
                            onClick = { selectingPrefKey = PreferKey.clickActionTR },
                        )
                    }
                    ClickAreaRow {
                        ClickAreaCell(
                            label = actions[preferences.clickActionML] ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(3.dp),
                            onClick = { selectingPrefKey = PreferKey.clickActionML },
                        )
                        ClickAreaCell(
                            label = actions[preferences.clickActionMC] ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(3.dp),
                            onClick = { selectingPrefKey = PreferKey.clickActionMC },
                        )
                        ClickAreaCell(
                            label = actions[preferences.clickActionMR] ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(3.dp),
                            onClick = { selectingPrefKey = PreferKey.clickActionMR },
                        )
                    }
                    ClickAreaRow {
                        ClickAreaCell(
                            label = actions[preferences.clickActionBL] ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(3.dp),
                            onClick = { selectingPrefKey = PreferKey.clickActionBL },
                        )
                        ClickAreaCell(
                            label = actions[preferences.clickActionBC] ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(3.dp),
                            onClick = { selectingPrefKey = PreferKey.clickActionBC },
                        )
                        ClickAreaCell(
                            label = actions[preferences.clickActionBR] ?: "",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(3.dp),
                            onClick = { selectingPrefKey = PreferKey.clickActionBR },
                        )
                    }
                }
            }

            // 动作选择弹窗：必须挂在同一窗口内，否则会被下面的九宫格窗口挡住。
            val actionKeys = actions.keys.toList()
            val actionValues = actions.values.toList()
            AppAlertDialog(
                show = selectingPrefKey != null,
                onDismissRequest = { selectingPrefKey = null },
                title = stringResource(R.string.select_action),
                content = {
                    Column {
                        actionValues.forEachIndexed { index, label ->
                            AppText(
                                text = label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val selectedAction = actionKeys[index]
                                        selectingPrefKey?.let { key ->
                                            scope.launch {
                                                readSettingsRepository.setClickAction(
                                                    key,
                                                    selectedAction
                                                )
                                                selectingPrefKey = null
                                            }
                                        }
                                    }
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                style = LegadoTheme.typography.bodyLarge,
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.ClickAreaRow(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun ClickAreaCell(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = LegadoTheme.colorScheme.surfaceContainer
            .copy(alpha = 0.9f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = label,
                style = LegadoTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
