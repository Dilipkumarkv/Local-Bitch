package com.dilipkumarkv.localgguf.engine

enum class InferenceState {
    MODEL_NOT_SELECTED,
    MODEL_IMPORTING,
    MODEL_IMPORT_FAILED,
    MODEL_LOADING,
    MODEL_READY,
    MODEL_LOAD_FAILED,
    GENERATING,
    GENERATION_STOPPED,
    GENERATION_FAILED,
    OUT_OF_MEMORY_RISK,
    NO_STORAGE,
    INVALID_MODEL
}

data class ModelInfo(
    val id: String,
    val name: String,
    val path: String,
    val architecture: String,
    val quantization: String,
    val contextLength: Int,
    val parameterCount: String,
    val fileSize: Long,
    val isNative: Boolean
)
