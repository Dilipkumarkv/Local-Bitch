package com.dilipkumarkv.localgguf.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dilipkumarkv.localgguf.data.model.GenerationParameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
        val KEY_CONTEXT_SIZE = intPreferencesKey("context_size")
        val KEY_TOP_P = floatPreferencesKey("top_p")
        val KEY_TOP_K = intPreferencesKey("top_k")
        val KEY_REPEAT_PENALTY = floatPreferencesKey("repeat_penalty")
        val KEY_THREADS = intPreferencesKey("threads")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
        val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val KEY_PROFILE_ID = stringPreferencesKey("profile_id")
    }

    val selectedProfileId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PROFILE_ID] ?: "balanced"
    }

    val generationParameters: Flow<GenerationParameters> = context.dataStore.data.map { prefs ->
        val defaultThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
        GenerationParameters(
            temperature = prefs[KEY_TEMPERATURE] ?: 0.7f,
            maxTokens = prefs[KEY_MAX_TOKENS] ?: 512,
            contextSize = prefs[KEY_CONTEXT_SIZE] ?: 2048,
            topP = prefs[KEY_TOP_P] ?: 0.9f,
            topK = prefs[KEY_TOP_K] ?: 40,
            repeatPenalty = prefs[KEY_REPEAT_PENALTY] ?: 1.1f,
            threads = prefs[KEY_THREADS] ?: defaultThreads,
            systemPrompt = prefs[KEY_SYSTEM_PROMPT] ?: GenerationParameters.DEFAULT_SYSTEM_PROMPT
        )
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "SYSTEM"
    }

    val defaultModelId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_MODEL_ID]
    }

    suspend fun updateParameters(parameters: GenerationParameters) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TEMPERATURE] = parameters.temperature
            prefs[KEY_MAX_TOKENS] = parameters.maxTokens
            prefs[KEY_CONTEXT_SIZE] = parameters.contextSize
            prefs[KEY_TOP_P] = parameters.topP
            prefs[KEY_TOP_K] = parameters.topK
            prefs[KEY_REPEAT_PENALTY] = parameters.repeatPenalty
            prefs[KEY_THREADS] = parameters.threads
            prefs[KEY_SYSTEM_PROMPT] = parameters.systemPrompt
        }
    }

    suspend fun setThemeMode(theme: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = theme
        }
    }

    suspend fun setProfileId(profileId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PROFILE_ID] = profileId
        }
    }

    suspend fun setDefaultModelId(modelId: String?) {
        context.dataStore.edit { prefs ->
            if (modelId != null) {
                prefs[KEY_DEFAULT_MODEL_ID] = modelId
            } else {
                prefs.remove(KEY_DEFAULT_MODEL_ID)
            }
        }
    }
}
