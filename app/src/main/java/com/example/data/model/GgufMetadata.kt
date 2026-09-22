package com.example.data.model

data class GgufMetadata(
    val magic: String = "GGUF",
    val version: Int = 3,
    val architecture: String = "llama",
    val modelName: String = "Unknown",
    val contextLength: Int = 2048,
    val tensorCount: Long = 0L,
    val kvCount: Long = 0L,
    val quantization: String = "Q4_K_M",
    val parameterCountEstimate: String = "Unknown",
    val chatTemplate: String? = null
)
