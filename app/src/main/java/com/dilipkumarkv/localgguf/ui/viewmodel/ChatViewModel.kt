package com.dilipkumarkv.localgguf.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dilipkumarkv.localgguf.data.model.ConversationEntity
import com.dilipkumarkv.localgguf.data.model.GenerationEvent
import com.dilipkumarkv.localgguf.data.model.GenerationParameters
import com.dilipkumarkv.localgguf.data.model.GenerationStats
import com.dilipkumarkv.localgguf.data.model.MessageEntity
import com.dilipkumarkv.localgguf.data.model.MessageRole
import com.dilipkumarkv.localgguf.data.model.ModelEntity
import com.dilipkumarkv.localgguf.data.repository.ChatRepository
import com.dilipkumarkv.localgguf.data.repository.SettingsRepository
import com.dilipkumarkv.localgguf.engine.InferenceState
import com.dilipkumarkv.localgguf.engine.LocalInferenceEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val inferenceEngine: LocalInferenceEngine
) : ViewModel() {

    val allConversations: StateFlow<List<ConversationEntity>> = chatRepository.allConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val parameters: StateFlow<GenerationParameters> = settingsRepository.generationParameters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GenerationParameters())

    val selectedProfileId: StateFlow<String> = settingsRepository.selectedProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "balanced")

    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            val profile = GenerationParameters.PROFILES.firstOrNull { it.id == profileId } ?: return@launch
            val updated = profile.toParameters(systemPrompt = parameters.value.systemPrompt)
            settingsRepository.setProfileId(profileId)
            settingsRepository.updateParameters(updated)
        }
    }

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    val currentMessages: StateFlow<List<MessageEntity>> = _activeConversationId.flatMapLatest { id ->
        if (id != null) {
            chatRepository.getMessages(id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loadedModel: StateFlow<ModelEntity?> = inferenceEngine.loadedModel
    val engineState: StateFlow<InferenceState> = inferenceEngine.state

    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _currentStats = MutableStateFlow<GenerationStats?>(null)
    val currentStats: StateFlow<GenerationStats?> = _currentStats.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _activeGrammarType = MutableStateFlow(com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType.NONE)
    val activeGrammarType: StateFlow<com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType> = _activeGrammarType.asStateFlow()

    private val _activeGrammarGbnf = MutableStateFlow<String?>(null)
    val activeGrammarGbnf: StateFlow<String?> = _activeGrammarGbnf.asStateFlow()

    private val _activeSchemaJson = MutableStateFlow<String?>(null)
    val activeSchemaJson: StateFlow<String?> = _activeSchemaJson.asStateFlow()

    fun setGrammarPreset(preset: com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarPreset) {
        _activeGrammarType.value = preset.type
        _activeGrammarGbnf.value = preset.gbnf
        _activeSchemaJson.value = preset.schemaJson
        viewModelScope.launch {
            settingsRepository.updateParameters(
                parameters.value.copy(
                    grammar = preset.gbnf,
                    grammarTypeName = preset.type.name,
                    grammarSchema = preset.schemaJson
                )
            )
        }
    }

    fun setGrammarType(type: com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType, customGbnf: String? = null, schemaJson: String? = null, choices: List<String>? = null) {
        _activeGrammarType.value = type
        val gbnf = when (type) {
            com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType.NONE -> null
            com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType.JSON_OBJECT -> com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GBNF_JSON_OBJECT
            com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType.JSON_ARRAY -> com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GBNF_JSON_ARRAY
            com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType.BOOLEAN -> com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GBNF_BOOLEAN
            com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType.NUMERIC -> com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GBNF_NUMERIC
            com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType.KEY_VALUE -> com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GBNF_KEY_VALUE
            com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType.CHOICE_ENUM -> com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.createChoiceEnumGbnf(choices ?: listOf("OPTION_A", "OPTION_B"))
            com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType.JSON_SCHEMA -> if (!schemaJson.isNullOrBlank()) com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.convertJsonSchemaToGbnf(schemaJson) else com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GBNF_JSON_OBJECT
            com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType.CUSTOM -> customGbnf
        }
        _activeGrammarGbnf.value = gbnf
        _activeSchemaJson.value = schemaJson

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
        _activeGrammarType.value = com.dilipkumarkv.localgguf.engine.GbnfGrammarHelper.GrammarType.NONE
        _activeGrammarGbnf.value = null
        _activeSchemaJson.value = null
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

    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            val list = chatRepository.allConversations.first()
            if (list.isNotEmpty()) {
                _activeConversationId.value = list.first().id
            } else {
                createNewConversation()
            }
        }
    }

    fun selectConversation(id: String) {
        if (_isGenerating.value) {
            stopGeneration()
        }
        _activeConversationId.value = id
        _streamingContent.value = ""
        _currentStats.value = null
    }

    fun createNewConversation(title: String = "New Chat") {
        viewModelScope.launch {
            if (_isGenerating.value) {
                stopGeneration()
            }
            val conv = chatRepository.createConversation(title = title, modelId = loadedModel.value?.id)
            _activeConversationId.value = conv.id
            _streamingContent.value = ""
            _currentStats.value = null
        }
    }

    fun updateSystemPrompt(prompt: String) {
        viewModelScope.launch {
            settingsRepository.updateParameters(parameters.value.copy(systemPrompt = prompt))
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (_isGenerating.value) return // Reject rapid send attempts while generation is active

        val convId = _activeConversationId.value ?: return
        val model = loadedModel.value
        if (model == null) {
            _errorMessage.value = "Please load a GGUF model from the Models screen before chatting."
            return
        }

        viewModelScope.launch {
            val userMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = MessageRole.USER,
                content = trimmed,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.insertMessage(userMsg)

            // Auto-derive chat title from first message if title is default "New Chat"
            val conversations = chatRepository.allConversations.first()
            val currentConv = conversations.firstOrNull { it.id == convId }
            if (currentConv != null && (currentConv.title == "New Chat" || currentConv.title.startsWith("New Chat"))) {
                val derived = deriveTitle(trimmed)
                chatRepository.updateConversation(currentConv.copy(title = derived))
            }

            startInference(convId)
        }
    }

    private fun deriveTitle(firstMessage: String): String {
        val singleLine = firstMessage.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "New Chat"
        val cleaned = singleLine.replace(Regex("[#*`_\\[\\]()]"), "").trim()
        return if (cleaned.length <= 36) {
            cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else {
            val truncated = cleaned.take(36)
            val lastSpace = truncated.lastIndexOf(' ')
            if (lastSpace > 16) {
                truncated.substring(0, lastSpace) + "…"
            } else {
                truncated + "…"
            }
        }
    }

    private fun startInference(convId: String) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _streamingContent.value = ""
            _currentStats.value = null
            _errorMessage.value = null

            val params = settingsRepository.generationParameters.first()
            val history = chatRepository.getMessagesList(convId)

            val stringBuffer = StringBuilder()
            val startTime = System.currentTimeMillis()

            inferenceEngine.generate(history, params).collect { event ->
                when (event) {
                    is GenerationEvent.Started -> {
                        _streamingContent.value = ""
                    }
                    is GenerationEvent.Token -> {
                        stringBuffer.append(event.text)
                        _streamingContent.value = stringBuffer.toString()
                    }
                    is GenerationEvent.Completed -> {
                        _currentStats.value = event.stats
                        saveAssistantMessage(convId, stringBuffer.toString(), event.stats, startTime)
                        _streamingContent.value = ""
                        _isGenerating.value = false
                    }
                    is GenerationEvent.Stopped -> {
                        _currentStats.value = event.stats
                        if (stringBuffer.isNotEmpty()) {
                            saveAssistantMessage(convId, stringBuffer.toString(), event.stats, startTime)
                        }
                        _streamingContent.value = ""
                        _isGenerating.value = false
                    }
                    is GenerationEvent.Error -> {
                        _errorMessage.value = event.message
                        _isGenerating.value = false
                        _streamingContent.value = ""
                    }
                }
            }
        }
    }

    private suspend fun saveAssistantMessage(
        convId: String,
        content: String,
        stats: GenerationStats,
        startTime: Long
    ) {
        if (content.isBlank()) return
        val assistantMsg = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            role = MessageRole.ASSISTANT,
            content = content.trim(),
            timestamp = System.currentTimeMillis(),
            tokensCount = stats.generatedTokens,
            tokensPerSecond = stats.tokensPerSecond,
            promptEvalMs = stats.promptEvalMs,
            durationMs = System.currentTimeMillis() - startTime
        )
        chatRepository.insertMessage(assistantMsg)
    }

    fun stopGeneration() {
        inferenceEngine.stopGeneration()
        generationJob?.cancel()
        _isGenerating.value = false
    }

    fun regenerateLastResponse() {
        val convId = _activeConversationId.value ?: return
        if (_isGenerating.value) return

        viewModelScope.launch {
            val list = chatRepository.getMessagesList(convId)
            val lastAssistant = list.lastOrNull { it.role == MessageRole.ASSISTANT }
            if (lastAssistant != null) {
                chatRepository.deleteMessage(lastAssistant.id)
            }
            startInference(convId)
        }
    }

    fun editLastUserMessage(newContent: String) {
        val convId = _activeConversationId.value ?: return
        if (_isGenerating.value || newContent.isBlank()) return

        viewModelScope.launch {
            val list = chatRepository.getMessagesList(convId)
            val lastUser = list.lastOrNull { it.role == MessageRole.USER }
            if (lastUser != null) {
                // Remove following assistant message if any
                val lastAssistant = list.lastOrNull { it.role == MessageRole.ASSISTANT && it.timestamp > lastUser.timestamp }
                if (lastAssistant != null) {
                    chatRepository.deleteMessage(lastAssistant.id)
                }
                chatRepository.updateMessage(lastUser.copy(content = newContent.trim(), timestamp = System.currentTimeMillis()))
                startInference(convId)
            }
        }
    }

    fun forkConversationFromMessage(messageId: String) {
        val convId = _activeConversationId.value ?: return
        if (_isGenerating.value) stopGeneration()

        viewModelScope.launch {
            val conversations = chatRepository.allConversations.first()
            val currentConv = conversations.firstOrNull { it.id == convId }
            val baseTitle = currentConv?.title ?: "Chat"
            val newTitle = "$baseTitle (Branch)"

            val newConv = chatRepository.createConversation(
                title = newTitle,
                modelId = currentConv?.modelId ?: loadedModel.value?.id
            )

            val messages = chatRepository.getMessagesList(convId)
            val targetIndex = messages.indexOfFirst { it.id == messageId }
            if (targetIndex >= 0) {
                val branchMessages = messages.subList(0, targetIndex + 1)
                branchMessages.forEach { originalMsg ->
                    val clonedMsg = originalMsg.copy(
                        id = UUID.randomUUID().toString(),
                        conversationId = newConv.id
                    )
                    chatRepository.insertMessage(clonedMsg)
                }
            }

            _activeConversationId.value = newConv.id
            _streamingContent.value = ""
            _currentStats.value = null
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
        }
    }

    fun clearConversation() {
        val convId = _activeConversationId.value ?: return
        if (_isGenerating.value) stopGeneration()
        viewModelScope.launch {
            chatRepository.clearConversationMessages(convId)
            _streamingContent.value = ""
            _currentStats.value = null
        }
    }

    fun deleteConversation(convId: String) {
        if (_isGenerating.value) stopGeneration()
        viewModelScope.launch {
            chatRepository.deleteConversation(convId)
            val remaining = chatRepository.allConversations.first()
            if (remaining.isNotEmpty()) {
                _activeConversationId.value = remaining.first().id
            } else {
                createNewConversation()
            }
        }
    }

    fun renameConversation(convId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            chatRepository.renameConversation(convId, newTitle)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
