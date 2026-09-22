package com.dilipkumarkv.localgguf.engine

import android.util.Log
import com.dilipkumarkv.localgguf.data.model.GenerationEvent
import com.dilipkumarkv.localgguf.data.model.GenerationParameters
import com.dilipkumarkv.localgguf.data.model.GenerationStats
import com.dilipkumarkv.localgguf.data.model.MessageEntity
import com.dilipkumarkv.localgguf.data.model.ModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production implementation of LocalInferenceEngine that orchestrates
 * native llama.cpp JNI bindings with graceful fallback to EmbeddedFallbackEngine.
 */
class NativeLlamaEngine(
    private val fallbackEngine: EmbeddedFallbackEngine = EmbeddedFallbackEngine(),
    private val allowSimulationMode: Boolean = false
) : LocalInferenceEngine {

    companion object {
        private const val TAG = "NativeLlamaEngine"
    }

    private val _state = MutableStateFlow(InferenceState.MODEL_NOT_SELECTED)
    override val state: StateFlow<InferenceState> = _state.asStateFlow()

    private val _loadedModel = MutableStateFlow<ModelEntity?>(null)
    override val loadedModel: StateFlow<ModelEntity?> = _loadedModel.asStateFlow()

    private val stopRequested = AtomicBoolean(false)
    private var nativeHandle: Long = 0L
    private var activeModelInfo: ModelInfo? = null
    private var useNative: Boolean = false

    override suspend fun loadModel(
        model: ModelEntity,
        parameters: GenerationParameters
    ): Result<ModelInfo> = withContext(Dispatchers.IO) {
        try {
            // If the same model is already loaded and active, return without re-allocating
            if (_loadedModel.value?.id == model.id && isLoaded() && activeModelInfo != null) {
                return@withContext Result.success(activeModelInfo!!)
            }

            // Unload previously loaded model session before loading new model
            if (isLoaded()) {
                unloadModel()
            }

            _state.value = InferenceState.MODEL_LOADING
            EngineLogger.i(
                "ENGINE",
                "Initiating model load: ${model.displayName}",
                "arch=${model.architecture}, size=${model.fileSize / (1024 * 1024)}MB, threads=${parameters.threads}, ctx=${parameters.contextSize}"
            )
            val file = File(model.path)
            if (!file.exists() || !file.canRead()) {
                _state.value = InferenceState.MODEL_LOAD_FAILED
                EngineLogger.e("ENGINE", "Model file not found or inaccessible at ${model.path}")
                return@withContext Result.failure(
                    IllegalArgumentException("Model file not found or inaccessible at ${model.path}")
                )
            }

            if (NativeLlamaBridge.isAvailable()) {
                try {
                    val handle = NativeLlamaBridge.initModel(
                        path = model.path,
                        contextSize = parameters.contextSize,
                        threads = parameters.threads
                    )
                    if (handle != 0L) {
                        nativeHandle = handle
                        useNative = true
                        val info = ModelInfo(
                            id = model.id,
                            name = model.displayName,
                            path = model.path,
                            architecture = model.architecture,
                            quantization = model.quantization,
                            contextLength = parameters.contextSize,
                            parameterCount = model.parameterCount,
                            fileSize = model.fileSize,
                            isNative = true
                        )
                        activeModelInfo = info
                        _loadedModel.value = model
                        _state.value = InferenceState.MODEL_READY
                        Log.i(TAG, "Successfully loaded native model: ${model.displayName} (handle: $handle)")
                        EngineLogger.i("NATIVE_LLAMA", "Native model initialized successfully", "handle=$handle, path=${model.path}")
                        return@withContext Result.success(info)
                    } else {
                        val errMsg = "Native GGUF parser/engine rejected model at ${model.path}"
                        Log.e(TAG, errMsg)
                        EngineLogger.e("NATIVE_LLAMA", errMsg)
                        if (!allowSimulationMode) {
                            _state.value = InferenceState.MODEL_LOAD_FAILED
                            return@withContext Result.failure(IllegalStateException(errMsg))
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Native initModel failed: ${e.message}", e)
                    EngineLogger.e("NATIVE_LLAMA", "Native initModel failed", e.message)
                    if (!allowSimulationMode) {
                        _state.value = InferenceState.MODEL_LOAD_FAILED
                        return@withContext Result.failure(e)
                    }
                }
            } else if (!allowSimulationMode) {
                val errMsg = "Native llama.cpp shared library (libllama-android.so) is not available on this host device."
                Log.e(TAG, errMsg)
                EngineLogger.e("NATIVE_LLAMA", errMsg)
                _state.value = InferenceState.MODEL_LOAD_FAILED
                return@withContext Result.failure(IllegalStateException(errMsg))
            }

            // Route through fallback engine only when simulation mode is explicitly enabled
            useNative = false
            val result = fallbackEngine.loadModel(model, parameters)
            if (result.isSuccess) {
                _loadedModel.value = model
                _state.value = InferenceState.MODEL_READY
                activeModelInfo = result.getOrNull()
            } else {
                _state.value = InferenceState.MODEL_LOAD_FAILED
            }
            result
        } catch (e: Exception) {
            _state.value = InferenceState.MODEL_LOAD_FAILED
            Result.failure(e)
        }
    }

    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        stopGeneration()
        val prevName = _loadedModel.value?.displayName ?: "model"
        if (useNative && nativeHandle != 0L) {
            try {
                NativeLlamaBridge.freeModel(nativeHandle)
                EngineLogger.i("NATIVE_LLAMA", "Freed native model handle: $nativeHandle for $prevName")
            } catch (e: Throwable) {
                Log.w(TAG, "Error freeing native model handle: ${e.message}")
                EngineLogger.w("NATIVE_LLAMA", "Error freeing native model handle: ${e.message}")
            }
            nativeHandle = 0L
        } else {
            fallbackEngine.unloadModel()
        }
        activeModelInfo = null
        _loadedModel.value = null
        useNative = false
        _state.value = InferenceState.MODEL_NOT_SELECTED
        EngineLogger.i("ENGINE", "Unloaded model session: $prevName")
    }

    override fun isLoaded(): Boolean {
        return (useNative && nativeHandle != 0L) || fallbackEngine.isLoaded()
    }

    override fun getLoadedModel(): ModelEntity? {
        return _loadedModel.value
    }

    override fun generate(
        messages: List<MessageEntity>,
        parameters: GenerationParameters
    ): Flow<GenerationEvent> = flow {
        if (!isLoaded()) {
            emit(GenerationEvent.Error("No GGUF model loaded. Please load a model from the Models tab."))
            return@flow
        }

        if (!useNative || nativeHandle == 0L) {
            fallbackEngine.generate(messages, parameters).collect { event ->
                emit(event)
            }
            return@flow
        }

        // Native streaming generation loop
        stopRequested.set(false)
        _state.value = InferenceState.GENERATING
        emit(GenerationEvent.Started)

        // Apply GBNF grammar if configured
        val activeGrammar = parameters.grammar
        if (!activeGrammar.isNullOrBlank()) {
            try {
                NativeLlamaBridge.setGrammar(nativeHandle, activeGrammar)
                EngineLogger.i("NATIVE_LLAMA", "Applied GBNF grammar constraint", "type=${parameters.grammarTypeName}")
            } catch (e: Throwable) {
                EngineLogger.w("NATIVE_LLAMA", "Could not set native grammar constraint", e.message)
            }
        }

        val prompt = ChatTemplateHelper.formatPrompt(
            messages = messages,
            architecture = activeModelInfo?.architecture ?: "llama",
            systemPrompt = parameters.systemPrompt
        )
        val kvReusedTokens = if (messages.size > 1) (messages.dropLast(1).sumOf { it.content.length } / 4) else 0

        val startTime = System.currentTimeMillis()
        val evalOk = NativeLlamaBridge.evalPrompt(nativeHandle, prompt)
        if (!evalOk) {
            _state.value = InferenceState.GENERATION_FAILED
            emit(GenerationEvent.Error("Failed to evaluate prompt in native llama context."))
            return@flow
        }
        val promptEvalMs = System.currentTimeMillis() - startTime

        var generatedCount = 0
        val genStartTime = System.currentTimeMillis()

        while (!NativeLlamaBridge.isFinished(nativeHandle) && generatedCount < parameters.maxTokens) {
            if (stopRequested.get()) {
                val genMs = (System.currentTimeMillis() - genStartTime).coerceAtLeast(1)
                val stats = GenerationStats(
                    promptTokens = prompt.length / 4,
                    generatedTokens = generatedCount,
                    promptEvalMs = promptEvalMs,
                    generationMs = genMs,
                    tokensPerSecond = (generatedCount * 1000.0) / genMs,
                    contextTokensUsed = (prompt.length / 4) + generatedCount,
                    kvCacheReusedTokens = kvReusedTokens
                )
                _state.value = InferenceState.GENERATION_STOPPED
                emit(GenerationEvent.Stopped(stats))
                _state.value = InferenceState.MODEL_READY
                return@flow
            }

            val piece = NativeLlamaBridge.nextToken(
                handle = nativeHandle,
                temp = parameters.temperature,
                topP = parameters.topP,
                topK = parameters.topK
            )

            if (piece == null || piece.isEmpty()) {
                break
            }

            emit(GenerationEvent.Token(piece, generatedCount))
            generatedCount++
        }

        if (!activeGrammar.isNullOrBlank()) {
            try {
                NativeLlamaBridge.clearGrammar(nativeHandle)
            } catch (ignored: Throwable) {}
        }

        val genMs = (System.currentTimeMillis() - genStartTime).coerceAtLeast(1)
        val stats = GenerationStats(
            promptTokens = prompt.length / 4,
            generatedTokens = generatedCount,
            promptEvalMs = promptEvalMs,
            generationMs = genMs,
            tokensPerSecond = (generatedCount * 1000.0) / genMs,
            contextTokensUsed = (prompt.length / 4) + generatedCount,
            kvCacheReusedTokens = kvReusedTokens
        )

        _state.value = InferenceState.MODEL_READY
        emit(GenerationEvent.Completed(stats))
    }.flowOn(Dispatchers.Default)

    override fun stopGeneration() {
        stopRequested.set(true)
        fallbackEngine.stopGeneration()
    }

    override suspend fun clearContext() {
        stopGeneration()
        fallbackEngine.clearContext()
    }

    override suspend fun getModelInfo(): ModelInfo? = activeModelInfo
}
