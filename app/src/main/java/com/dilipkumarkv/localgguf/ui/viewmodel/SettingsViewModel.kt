package com.dilipkumarkv.localgguf.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dilipkumarkv.localgguf.data.model.GenerationEvent
import com.dilipkumarkv.localgguf.data.model.GenerationParameters
import com.dilipkumarkv.localgguf.data.model.GenerationStats
import com.dilipkumarkv.localgguf.data.model.MessageEntity
import com.dilipkumarkv.localgguf.data.model.MessageRole
import com.dilipkumarkv.localgguf.data.model.ModelEntity
import com.dilipkumarkv.localgguf.data.repository.ChatRepository
import com.dilipkumarkv.localgguf.data.repository.SettingsRepository
import com.dilipkumarkv.localgguf.engine.HardwareHealthSnapshot
import com.dilipkumarkv.localgguf.engine.HardwareMonitor
import com.dilipkumarkv.localgguf.engine.LocalInferenceEngine
import com.dilipkumarkv.localgguf.engine.MemorySafetyHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BenchmarkResult(
    val modelName: String,
    val promptTokens: Int,
    val generatedTokens: Int,
    val promptEvalMs: Long,
    val generationMs: Long,
    val promptSpeedTokPerSec: Double,
    val generationSpeedTokPerSec: Double,
    val timeToFirstTokenMs: Long
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val context: Context,
    private val inferenceEngine: LocalInferenceEngine? = null
) : ViewModel() {

    val parameters: StateFlow<GenerationParameters> = settingsRepository.generationParameters
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            GenerationParameters()
        )

    val themeMode: StateFlow<String> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val selectedProfileId: StateFlow<String> = settingsRepository.selectedProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "balanced")

    val loadedModel: StateFlow<ModelEntity?> = inferenceEngine?.loadedModel
        ?: MutableStateFlow<ModelEntity?>(null).asStateFlow()

    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    private val _benchmarkResult = MutableStateFlow<BenchmarkResult?>(null)
    val benchmarkResult: StateFlow<BenchmarkResult?> = _benchmarkResult.asStateFlow()

    private val _memorySnapshot = MutableStateFlow(MemorySafetyHelper.getMemorySnapshot(context))
    val memorySnapshot: StateFlow<MemorySafetyHelper.MemorySnapshot> = _memorySnapshot.asStateFlow()

    private val _hardwareHealth = MutableStateFlow(HardwareMonitor.getSnapshot(context))
    val hardwareHealth: StateFlow<HardwareHealthSnapshot> = _hardwareHealth.asStateFlow()

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            val profile = GenerationParameters.PROFILES.firstOrNull { it.id == profileId } ?: return@launch
            val updated = profile.toParameters(systemPrompt = parameters.value.systemPrompt)
            settingsRepository.setProfileId(profileId)
            settingsRepository.updateParameters(updated)
        }
    }

    fun runBenchmark() {
        val engine = inferenceEngine ?: return
        val currentModel = loadedModel.value ?: return
        if (_isBenchmarking.value) return

        viewModelScope.launch {
            _isBenchmarking.value = true
            _benchmarkResult.value = null

            val testMessage = MessageEntity(
                id = "benchmark_test",
                conversationId = "bench",
                role = MessageRole.USER,
                content = "Explain how a computer processor works in 40 words.",
                timestamp = System.currentTimeMillis()
            )

            val benchParams = parameters.value.copy(maxTokens = 64)
            val startTime = System.currentTimeMillis()
            var ttft = 0L
            var firstTokenReceived = false
            var finalStats: GenerationStats? = null

            try {
                engine.generate(listOf(testMessage), benchParams).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            if (!firstTokenReceived) {
                                firstTokenReceived = true
                                ttft = System.currentTimeMillis() - startTime
                            }
                        }
                        is GenerationEvent.Completed -> {
                            finalStats = event.stats
                        }
                        is GenerationEvent.Stopped -> {
                            finalStats = event.stats
                        }
                        is GenerationEvent.Error -> {}
                        else -> {}
                    }
                }

                finalStats?.let { stats ->
                    val promptSpeed = if (stats.promptEvalMs > 0) {
                        (stats.promptTokens.toDouble() / stats.promptEvalMs) * 1000.0
                    } else 0.0

                    _benchmarkResult.value = BenchmarkResult(
                        modelName = currentModel.displayName,
                        promptTokens = stats.promptTokens,
                        generatedTokens = stats.generatedTokens,
                        promptEvalMs = stats.promptEvalMs,
                        generationMs = stats.generationMs,
                        promptSpeedTokPerSec = promptSpeed,
                        generationSpeedTokPerSec = stats.tokensPerSecond,
                        timeToFirstTokenMs = if (ttft > 0) ttft else stats.promptEvalMs
                    )
                }
            } catch (_: Exception) {
                // Ignore benchmark failure
            } finally {
                _isBenchmarking.value = false
            }
        }
    }

    fun refreshMemory() {
        _memorySnapshot.value = MemorySafetyHelper.getMemorySnapshot(context)
        _hardwareHealth.value = HardwareMonitor.getSnapshot(context)
    }

    fun updateTemperature(temp: Float) {
        viewModelScope.launch {
            val current = parameters.value
            settingsRepository.setProfileId("custom")
            settingsRepository.updateParameters(current.copy(temperature = temp.coerceIn(0.0f, 2.0f)))
        }
    }

    fun updateMaxTokens(maxTokens: Int) {
        viewModelScope.launch {
            val current = parameters.value
            settingsRepository.setProfileId("custom")
            settingsRepository.updateParameters(current.copy(maxTokens = maxTokens.coerceIn(64, 4096)))
        }
    }

    fun updateContextSize(contextSize: Int) {
        viewModelScope.launch {
            val current = parameters.value
            settingsRepository.setProfileId("custom")
            settingsRepository.updateParameters(current.copy(contextSize = contextSize.coerceIn(512, 8192)))
        }
    }

    fun updateTopP(topP: Float) {
        viewModelScope.launch {
            val current = parameters.value
            settingsRepository.setProfileId("custom")
            settingsRepository.updateParameters(current.copy(topP = topP.coerceIn(0.0f, 1.0f)))
        }
    }

    fun updateTopK(topK: Int) {
        viewModelScope.launch {
            val current = parameters.value
            settingsRepository.setProfileId("custom")
            settingsRepository.updateParameters(current.copy(topK = topK.coerceIn(1, 100)))
        }
    }

    fun updateRepeatPenalty(repeatPenalty: Float) {
        viewModelScope.launch {
            val current = parameters.value
            settingsRepository.setProfileId("custom")
            settingsRepository.updateParameters(current.copy(repeatPenalty = repeatPenalty.coerceIn(1.0f, 2.0f)))
        }
    }

    fun updateThreads(threads: Int) {
        viewModelScope.launch {
            val current = parameters.value
            settingsRepository.setProfileId("custom")
            settingsRepository.updateParameters(current.copy(threads = threads.coerceIn(1, 8)))
        }
    }

    fun updateSystemPrompt(prompt: String) {
        viewModelScope.launch {
            settingsRepository.updateParameters(parameters.value.copy(systemPrompt = prompt))
        }
    }

    fun updateGrammar(type: com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType, gbnf: String?, schemaJson: String?) {
        viewModelScope.launch {
            settingsRepository.updateParameters(
                parameters.value.copy(
                    grammar = gbnf,
                    grammarTypeName = type.name,
                    grammarSchema = schemaJson
                )
            )
        }
    }

    fun clearGrammar() {
        viewModelScope.launch {
            settingsRepository.updateParameters(
                parameters.value.copy(
                    grammar = null,
                    grammarTypeName = "NONE",
                    grammarSchema = null
                )
            )
        }
    }

    fun setThemeMode(theme: String) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(theme)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            val defaultThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
            settingsRepository.setProfileId("balanced")
            settingsRepository.updateParameters(
                GenerationParameters(
                    temperature = 0.7f,
                    maxTokens = 512,
                    contextSize = 2048,
                    topP = 0.9f,
                    topK = 40,
                    repeatPenalty = 1.1f,
                    threads = defaultThreads,
                    systemPrompt = GenerationParameters.DEFAULT_SYSTEM_PROMPT
                )
            )
        }
    }
}
