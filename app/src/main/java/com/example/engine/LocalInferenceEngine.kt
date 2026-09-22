package com.example.engine

import com.example.data.model.GenerationEvent
import com.example.data.model.GenerationParameters
import com.example.data.model.MessageEntity
import com.example.data.model.ModelEntity
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
