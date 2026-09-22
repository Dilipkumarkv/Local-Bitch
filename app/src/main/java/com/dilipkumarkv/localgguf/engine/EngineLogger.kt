package com.dilipkumarkv.localgguf.engine

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val details: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

object EngineLogger {
    private const val MAX_LOGS = 250
    private val idCounter = AtomicLong(1)
    private val logList = ArrayDeque<LogEntry>(MAX_LOGS)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    init {
        i("SYSTEM", "Engine logger initialized. Native diagnostics active.")
    }

    @Synchronized
    fun log(level: LogLevel, tag: String, message: String, details: String? = null) {
        val entry = LogEntry(
            id = idCounter.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            details = details
        )

        // Log to Android Logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, if (details != null) "$message | $details" else message)
            LogLevel.INFO -> Log.i(tag, if (details != null) "$message | $details" else message)
            LogLevel.WARN -> Log.w(tag, if (details != null) "$message | $details" else message)
            LogLevel.ERROR -> Log.e(tag, if (details != null) "$message | $details" else message)
        }

        if (logList.size >= MAX_LOGS) {
            logList.removeFirst()
        }
        logList.addLast(entry)
        _logs.value = logList.toList()
    }

    fun d(tag: String, message: String, details: String? = null) = log(LogLevel.DEBUG, tag, message, details)
    fun i(tag: String, message: String, details: String? = null) = log(LogLevel.INFO, tag, message, details)
    fun w(tag: String, message: String, details: String? = null) = log(LogLevel.WARN, tag, message, details)
    fun e(tag: String, message: String, details: String? = null) = log(LogLevel.ERROR, tag, message, details)

    @Synchronized
    fun clear() {
        logList.clear()
        _logs.value = emptyList()
        i("SYSTEM", "Engine log buffer cleared.")
    }

    fun exportFormattedText(): String {
        val currentLogs = _logs.value
        val sb = StringBuilder()
        sb.append("=== Minimal GGUF LLM Engine Diagnostic Log ===\n")
        sb.append("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        sb.append("Total entries: ${currentLogs.size}\n\n")
        for (entry in currentLogs) {
            sb.append("[${entry.formattedTime}] [${entry.level.name}] [${entry.tag}] ${entry.message}")
            if (!entry.details.isNullOrBlank()) {
                sb.append(" -> ${entry.details}")
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}
