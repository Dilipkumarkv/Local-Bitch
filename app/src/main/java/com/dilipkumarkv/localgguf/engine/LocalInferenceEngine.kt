package com.dilipkumarkv.localgguf.engine

import com.dilipkumarkv.localgguf.data.model.GenerationEvent
import com.dilipkumarkv.localgguf.data.model.GenerationParameters
import com.dilipkumarkv.localgguf.data.model.MessageEntity
import com.dilipkumarkv.localgguf.data.model.ModelEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface LocalInferenceEngine {
    val state: StateFlow<InferenceState>
    val loadedModel: StateFlow<ModelEntity?>

    suspend fun loadModel(model: ModelEntity, parameters: GenerationParameters): Result<ModelInfo>
    suspend fun unloadModel()
    fun isLoaded(): Boolean
    fun getLoadedModel(): ModelEntity?

    fun generate(
        messages: List<MessageEntity>,
        parameters: GenerationParameters
    ): Flow<GenerationEvent>

    fun stopGeneration()
    suspend fun clearContext()
    suspend fun getModelInfo(): ModelInfo?
}
