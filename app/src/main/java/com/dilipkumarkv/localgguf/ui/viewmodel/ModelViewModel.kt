package com.dilipkumarkv.localgguf.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dilipkumarkv.localgguf.data.model.GenerationParameters
import com.dilipkumarkv.localgguf.data.model.ModelEntity
import com.dilipkumarkv.localgguf.data.repository.ModelRepository
import com.dilipkumarkv.localgguf.data.repository.SettingsRepository
import com.dilipkumarkv.localgguf.engine.InferenceState
import com.dilipkumarkv.localgguf.engine.LocalInferenceEngine
import com.dilipkumarkv.localgguf.engine.MemorySafetyHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModelViewModel(
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository,
    private val inferenceEngine: LocalInferenceEngine,
    private val context: Context
) : ViewModel() {

    val allModels: StateFlow<List<ModelEntity>> = modelRepository.allModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loadedModel: StateFlow<ModelEntity?> = inferenceEngine.loadedModel
    val inferenceState: StateFlow<InferenceState> = inferenceEngine.state

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _memoryWarningModel = MutableStateFlow<ModelEntity?>(null)
    val memoryWarningModel: StateFlow<ModelEntity?> = _memoryWarningModel.asStateFlow()

    init {
        viewModelScope.launch {
            modelRepository.createStarterDemoModelIfNeeded()
        }
    }

    fun importModel(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _isImporting.value = true
            _errorMessage.value = null
            val result = modelRepository.importModelFromUri(uri, contentResolver)
            _isImporting.value = false
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to import GGUF model"
            }
        }
    }

    fun requestLoadModel(model: ModelEntity, bypassWarning: Boolean = false) {
        if (!bypassWarning && MemorySafetyHelper.isMemoryRisky(model.fileSize, context)) {
            _memoryWarningModel.value = model
            return
        }

        viewModelScope.launch {
            _errorMessage.value = null
            val params = settingsRepository.generationParameters.first()
            val result = inferenceEngine.loadModel(model, params)
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Could not load model."
            }
        }
    }

    fun confirmLoadRiskyModel() {
        val model = _memoryWarningModel.value ?: return
        _memoryWarningModel.value = null
        requestLoadModel(model, bypassWarning = true)
    }

    fun dismissMemoryWarning() {
        _memoryWarningModel.value = null
    }

    fun unloadModel() {
        viewModelScope.launch {
            inferenceEngine.unloadModel()
        }
    }

    fun deleteModel(model: ModelEntity) {
        if (inferenceEngine.loadedModel.value?.id == model.id) {
            _errorMessage.value = "Cannot delete a model that is currently loaded. Unload it first."
            return
        }
        viewModelScope.launch {
            val result = modelRepository.deleteModel(model)
            if (result.isFailure) {
                _errorMessage.value = "Failed to delete model file."
            }
        }
    }

    fun renameModel(modelId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            modelRepository.renameModel(modelId, newName)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
