package com.dilipkumarkv.localgguf.data.model

data class GenerationStats(
    val promptTokens: Int = 0,
    val generatedTokens: Int = 0,
    val promptEvalMs: Long = 0L,
    val generationMs: Long = 0L,
    val tokensPerSecond: Double = 0.0,
    val contextTokensUsed: Int = 0,
    val kvCacheReusedTokens: Int = 0
)

sealed interface GenerationEvent {
    data object Started : GenerationEvent
    data class Token(val text: String, val index: Int) : GenerationEvent
    data class Completed(val stats: GenerationStats) : GenerationEvent
    data class Stopped(val stats: GenerationStats) : GenerationEvent
    data class Error(val message: String, val cause: Throwable? = null) : GenerationEvent
}
