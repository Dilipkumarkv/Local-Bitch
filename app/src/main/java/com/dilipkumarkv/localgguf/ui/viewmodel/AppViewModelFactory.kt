package com.dilipkumarkv.localgguf.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dilipkumarkv.localgguf.data.db.AppDatabase
import com.dilipkumarkv.localgguf.data.repository.ChatRepository
import com.dilipkumarkv.localgguf.data.repository.ModelRepository
import com.dilipkumarkv.localgguf.data.repository.SettingsRepository
import com.dilipkumarkv.localgguf.engine.LocalInferenceEngine
import com.dilipkumarkv.localgguf.engine.NativeLlamaEngine

class AppViewModelFactory(
    private val context: Context,
    private val inferenceEngine: LocalInferenceEngine = NativeLlamaEngine()
) : ViewModelProvider.Factory {

    private val database = AppDatabase.getInstance(context)
    private val modelRepository = ModelRepository(context, database.modelDao())
    private val chatRepository = ChatRepository(database.conversationDao(), database.messageDao())
    private val settingsRepository = SettingsRepository(context)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ModelViewModel::class.java) -> {
                ModelViewModel(modelRepository, settingsRepository, inferenceEngine, context) as T
            }
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> {
                ChatViewModel(chatRepository, settingsRepository, inferenceEngine) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(settingsRepository, context, inferenceEngine) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
