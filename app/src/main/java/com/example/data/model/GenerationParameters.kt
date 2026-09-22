package com.example.data.model

data class SystemPromptPreset(
    val id: String,
    val title: String,
    val description: String,
    val prompt: String
)

data class GenerationProfile(
    val id: String,
    val title: String,
    val subtitle: String,
    val temperature: Float,
    val maxTokens: Int,
    val contextSize: Int,
    val topP: Float,
    val topK: Int,
    val repeatPenalty: Float,
    val threadMultiplier: Float
) {
    fun toParameters(systemPrompt: String = GenerationParameters.DEFAULT_SYSTEM_PROMPT): GenerationParameters {
        val maxCores = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        val computedThreads = (maxCores * threadMultiplier).toInt().coerceIn(1, maxCores)
        return GenerationParameters(
            temperature = temperature,
            maxTokens = maxTokens,
            contextSize = contextSize,
            topP = topP,
            topK = topK,
            repeatPenalty = repeatPenalty,
            threads = computedThreads,
            systemPrompt = systemPrompt
        )
    }
}

data class GenerationParameters(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 512,
    val contextSize: Int = 2048,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8),
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = "You are a helpful, concise assistant running locally on Android."

        val PRESETS = listOf(
            SystemPromptPreset(
                id = "general",
                title = "General",
                description = "Helpful and concise assistant",
                prompt = DEFAULT_SYSTEM_PROMPT
            ),
            SystemPromptPreset(
                id = "coding",
                title = "Coding",
                description = "Expert engineer, clean code, no fluff",
                prompt = "You are an expert software engineer. Provide clean, secure, idiomatic code with concise technical explanations."
            ),
            SystemPromptPreset(
                id = "reasoner",
                title = "Reasoner",
                description = "Direct, rigorous step-by-step logic",
                prompt = "You are a rigorous, direct reasoning assistant. Answer questions concisely with step-by-step logic and zero filler."
            ),
            SystemPromptPreset(
                id = "writer",
                title = "Editor",
                description = "Refines grammar, clarity, and style",
                prompt = "You are an articulate writing editor. Refine clarity, tone, and grammar while preserving the original intent."
            ),
            SystemPromptPreset(
                id = "summarizer",
                title = "Summarizer",
                description = "Extracts key insights and bullet points",
                prompt = "You are an efficient summarization assistant. Extract key takeaways, bullet points, and core ideas clearly."
            )
        )

        val PROFILES = listOf(
            GenerationProfile(
                id = "balanced",
                title = "Balanced",
                subtitle = "Optimal latency, RAM, and response quality for everyday use",
                temperature = 0.7f,
                maxTokens = 512,
                contextSize = 2048,
                topP = 0.9f,
                topK = 40,
                repeatPenalty = 1.1f,
                threadMultiplier = 0.5f
            ),
            GenerationProfile(
                id = "turbo",
                title = "Turbo Speed",
                subtitle = "Max CPU threads and 4K context for fastest generation",
                temperature = 0.8f,
                maxTokens = 1024,
                contextSize = 4096,
                topP = 0.9f,
                topK = 40,
                repeatPenalty = 1.1f,
                threadMultiplier = 1.0f
            ),
            GenerationProfile(
                id = "cool",
                title = "Battery Saver",
                subtitle = "Low CPU utilization to prevent thermal throttling and save power",
                temperature = 0.7f,
                maxTokens = 256,
                contextSize = 1024,
                topP = 0.85f,
                topK = 30,
                repeatPenalty = 1.15f,
                threadMultiplier = 0.25f
            ),
            GenerationProfile(
                id = "precise",
                title = "Deterministic",
                subtitle = "Low temperature for code, mathematical logic, and factual rigor",
                temperature = 0.2f,
                maxTokens = 768,
                contextSize = 2048,
                topP = 0.8f,
                topK = 20,
                repeatPenalty = 1.15f,
                threadMultiplier = 0.5f
            ),
            GenerationProfile(
                id = "creative",
                title = "Creative",
                subtitle = "High temperature and broad sampling for brainstorming and stories",
                temperature = 1.0f,
                maxTokens = 768,
                contextSize = 2048,
                topP = 0.95f,
                topK = 60,
                repeatPenalty = 1.08f,
                threadMultiplier = 0.5f
            )
        )
    }
}
