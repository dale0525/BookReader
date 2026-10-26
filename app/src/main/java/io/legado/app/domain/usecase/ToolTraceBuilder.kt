package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiToolApprovalState
import io.legado.app.domain.model.AiToolCall

/**
 * Accumulates streaming tool call deltas into complete [AiMessagePart.Tool] parts.
 * Thread-safe for use within a single coroutine context.
 */
class ToolTraceBuilder {
    private val calls = linkedMapOf<String, ToolCallTrace>()
    private val indexKeys = mutableMapOf<Int, String>()

    fun beginResponse() {
        indexKeys.clear()
    }

    fun append(event: AiStreamEvent.ToolCallDelta): String {
        val eventId = event.id?.takeIf { it.isNotBlank() }
        if (eventId != null && event.index != null) {
            indexKeys[event.index] = eventId
        }
        val baseId = eventId
            ?: event.index?.let { indexKeys[it] ?: "tool_index_$it" }
            ?: "tool_${calls.size + 1}"
        val id = if (eventId == null && calls[baseId]?.result != null) {
            "${baseId}_${calls.size + 1}"
        } else {
            baseId
        }
        val call = calls.getOrPut(id) { ToolCallTrace(id = id, rawType = event.rawType) }
        event.name?.takeIf { it.isNotBlank() }?.let { call.name = it }
        event.argumentsDelta?.takeIf { it.isNotEmpty() }?.let { call.arguments.append(it) }
        if (call.rawType.isBlank()) call.rawType = event.rawType
        return toString()
    }

    fun appendResult(id: String, result: String): String {
        calls[id]?.result = result
        return toString()
    }

    fun pendingToolCalls(): List<AiToolCall> {
        return calls.values.filter { it.result == null }.mapNotNull { call ->
            val name = call.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AiToolCall(
                id = call.id,
                name = name,
                arguments = call.arguments.toString().ifBlank { "{}" }
            )
        }
    }

    fun toParts(): List<AiMessagePart> {
        val parts = mutableListOf<AiMessagePart>()
        calls.values.forEach { call ->
            val name = call.name.takeIf { it.isNotBlank() } ?: return@forEach
            parts += AiMessagePart.Tool(
                toolCallId = call.id,
                toolName = name,
                input = call.arguments.toString().ifBlank { "{}" },
                output = call.result ?: "",
                rawType = call.rawType.ifBlank { "tool_call" },
                approvalState = if (call.result == null) {
                    AiToolApprovalState.PENDING
                } else {
                    AiToolApprovalState.AUTO
                }
            )
        }
        return parts
    }

    override fun toString(): String {
        return calls.values.joinToString("\n\n") { call ->
            buildString {
                append("Tool: ")
                append(call.name.ifBlank { call.rawType.ifBlank { call.id } })
                append('\n')
                append("ID: ")
                append(call.id)
                if (call.arguments.isNotBlank()) {
                    append('\n')
                    append(call.arguments)
                }
                call.result?.takeIf { it.isNotBlank() }?.let {
                    append('\n')
                    append("Result: ")
                    append(it.take(2000))
                }
            }
        }
    }
}

internal data class ToolCallTrace(
    val id: String,
    var rawType: String,
    var name: String = "",
    val arguments: StringBuilder = StringBuilder(),
    var result: String? = null
)

/** 工具输出超过 [MAX_TOOL_OUTPUT_CHARS] 就截断，避免把上下文撑爆。 */
internal const val MAX_TOOL_OUTPUT_CHARS = 8_000

internal fun String.truncateToolOutput(): String {
    if (length <= MAX_TOOL_OUTPUT_CHARS) return this
    return take(MAX_TOOL_OUTPUT_CHARS) +
        "\n\n[...truncated from ${length} chars]"
}
