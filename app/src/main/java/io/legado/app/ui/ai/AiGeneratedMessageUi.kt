package io.legado.app.ui.ai

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiMessagePart
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 一段已生成（或正在流式生成）的 AI 回复，供摘要、选中清洗、改写三个阅读器面板共用。
 */
@Stable
data class AiGeneratedMessageUi(
    val id: String,
    val role: String,
    val parts: ImmutableList<AiMessagePart> = persistentListOf(),
    val content: String,
    val reasoning: String?,
    val toolTrace: String?,
    val createdAt: Long,
    val thinkingDuration: Int = 0,
)
