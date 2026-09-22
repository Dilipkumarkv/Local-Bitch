package com.dilipkumarkv.localgguf.engine

import android.util.Log

object NativeLlamaBridge {
    private const val TAG = "NativeLlamaBridge"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("llama-android")
            isNativeLoaded = true
            Log.i(TAG, "Successfully loaded libllama-android.so")
            EngineLogger.i("NATIVE_BRIDGE", "Successfully loaded libllama-android.so")
        } catch (e: UnsatisfiedLinkError) {
            try {
                System.loadLibrary("llama")
                isNativeLoaded = true
                Log.i(TAG, "Successfully loaded libllama.so")
                EngineLogger.i("NATIVE_BRIDGE", "Successfully loaded libllama.so")
            } catch (e2: UnsatisfiedLinkError) {
                isNativeLoaded = false
                Log.w(TAG, "Native llama library not present on this host ABI: ${e2.message}")
                EngineLogger.w("NATIVE_BRIDGE", "Native llama.cpp library not bundled on this host ABI; switching to integrated fallback engine", e2.message)
            }
        }
    }

    fun isAvailable(): Boolean = isNativeLoaded

    // Native external declarations matching standard llama-android binding
    external fun initModel(path: String, contextSize: Int, threads: Int): Long
    external fun freeModel(handle: Long)
    external fun evalPrompt(handle: Long, prompt: String): Boolean
    external fun setGrammar(handle: Long, grammarStr: String): Boolean
    external fun clearGrammar(handle: Long)
    external fun nextToken(handle: Long, temp: Float, topP: Float, topK: Int): String?
    external fun isFinished(handle: Long): Boolean
    external fun getNativeSystemInfo(): String
}
