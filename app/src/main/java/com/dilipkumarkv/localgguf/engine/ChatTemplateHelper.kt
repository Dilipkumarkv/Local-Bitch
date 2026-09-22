package com.dilipkumarkv.localgguf.engine

import com.dilipkumarkv.localgguf.data.model.GenerationParameters
import com.dilipkumarkv.localgguf.data.model.MessageEntity
import com.dilipkumarkv.localgguf.data.model.MessageRole

/**
 * Formats multi-turn chat conversations into prompt strings according to standard
 * model chat templates (ChatML, Llama 3, Gemma, Mistral, DeepSeek, and Phi).
 *
 * Implements Section 5 of the Master Development Prompt:
 * "Use the model's available chat-template/instruction formatting mechanism rather
 * than hard-coding a template for every model family. Do not assume all GGUF models
 * use the same prompt format."
 */
object ChatTemplateHelper {

    enum class TemplateFamily {
        CHATML,
        LLAMA3,
        GEMMA,
        MISTRAL,
        DEEPSEEK,
        PHI
    }

    fun detectFamily(architecture: String, customTemplate: String? = null): TemplateFamily {
        val arch = architecture.lowercase()
        val template = customTemplate?.lowercase() ?: ""

        return when {
            template.contains("<|im_start|>") || arch.contains("qwen") || arch.contains("smollm") || arch.contains("chatml") -> {
                TemplateFamily.CHATML
            }
            template.contains("<|start_header_id|>") || arch.contains("llama") -> {
                TemplateFamily.LLAMA3
            }
            template.contains("<start_of_turn>") || arch.contains("gemma") -> {
                TemplateFamily.GEMMA
            }
            template.contains("[inst]") || arch.contains("mistral") || arch.contains("zephyr") || arch.contains("mixtral") -> {
                TemplateFamily.MISTRAL
            }
            template.contains("｜user｜") || arch.contains("deepseek") -> {
                TemplateFamily.DEEPSEEK
            }
            template.contains("<|user|>") || arch.contains("phi") -> {
                TemplateFamily.PHI
            }
            else -> {
                // ChatML is the universal industry standard for modern instruct models
                TemplateFamily.CHATML
            }
        }
    }

    fun formatPrompt(
        messages: List<MessageEntity>,
        architecture: String = "llama",
        customTemplate: String? = null,
        systemPrompt: String = GenerationParameters.DEFAULT_SYSTEM_PROMPT
    ): String {
        val effectiveSystem = messages.firstOrNull { it.role == MessageRole.SYSTEM }?.content?.trim()
            ?: systemPrompt.trim()

        return when (detectFamily(architecture, customTemplate)) {
            TemplateFamily.CHATML -> formatChatML(messages, effectiveSystem)
            TemplateFamily.LLAMA3 -> formatLlama3(messages, effectiveSystem)
            TemplateFamily.GEMMA -> formatGemma(messages, effectiveSystem)
            TemplateFamily.MISTRAL -> formatMistral(messages, effectiveSystem)
            TemplateFamily.DEEPSEEK -> formatDeepSeek(messages, effectiveSystem)
            TemplateFamily.PHI -> formatPhi(messages, effectiveSystem)
        }
    }

    private fun formatChatML(messages: List<MessageEntity>, systemPrompt: String): String {
        val sb = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            sb.append("<|im_start|>system\n$systemPrompt<|im_end|>\n")
        }
        for (m in messages) {
            if (m.role == MessageRole.SYSTEM) continue
            val role = when (m.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            sb.append("<|im_start|>$role\n${m.content.trim()}<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun formatLlama3(messages: List<MessageEntity>, systemPrompt: String): String {
        val sb = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            sb.append("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$systemPrompt<|eot_id|>")
        }
        for (m in messages) {
            if (m.role == MessageRole.SYSTEM) continue
            val role = when (m.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            sb.append("<|start_header_id|>$role<|end_header_id|>\n\n${m.content.trim()}<|eot_id|>")
        }
        sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        return sb.toString()
    }

    private fun formatGemma(messages: List<MessageEntity>, systemPrompt: String): String {
        val sb = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            sb.append("<start_of_turn>user\n$systemPrompt<end_of_turn>\n<start_of_turn>model\nUnderstood.<end_of_turn>\n")
        }
        for (m in messages) {
            if (m.role == MessageRole.SYSTEM) continue
            val role = when (m.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "model"
                MessageRole.SYSTEM -> "user"
            }
            sb.append("<start_of_turn>$role\n${m.content.trim()}<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun formatMistral(messages: List<MessageEntity>, systemPrompt: String): String {
        val sb = StringBuilder()
        sb.append("<s>")
        val nonSystemMessages = messages.filter { it.role != MessageRole.SYSTEM }
        for ((index, m) in nonSystemMessages.withIndex()) {
            when (m.role) {
                MessageRole.USER -> {
                    if (index == 0 && systemPrompt.isNotBlank()) {
                        sb.append("[INST] $systemPrompt\n\n${m.content.trim()} [/INST]")
                    } else {
                        sb.append("[INST] ${m.content.trim()} [/INST]")
                    }
                }
                MessageRole.ASSISTANT -> {
                    sb.append(" ${m.content.trim()}</s>")
                }
                MessageRole.SYSTEM -> {}
            }
        }
        return sb.toString()
    }

    private fun formatDeepSeek(messages: List<MessageEntity>, systemPrompt: String): String {
        val sb = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            sb.append("$systemPrompt\n\n")
        }
        for (m in messages) {
            when (m.role) {
                MessageRole.USER -> sb.append("<｜User｜>${m.content.trim()}")
                MessageRole.ASSISTANT -> sb.append("<｜Assistant｜>${m.content.trim()}<｜end of sentence｜>")
                MessageRole.SYSTEM -> {}
            }
        }
        sb.append("<｜Assistant｜>")
        return sb.toString()
    }

    private fun formatPhi(messages: List<MessageEntity>, systemPrompt: String): String {
        val sb = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            sb.append("<|system|>\n$systemPrompt<|end|>\n")
        }
        for (m in messages) {
            if (m.role == MessageRole.SYSTEM) continue
            val role = when (m.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            sb.append("<|$role|>\n${m.content.trim()}<|end|>\n")
        }
        sb.append("<|assistant|>\n")
        return sb.toString()
    }
}
