package com.dilipkumarkv.localgguf.engine

import com.dilipkumarkv.localgguf.data.model.GgufMetadata
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

object GgufParser {
    private const val GGUF_MAGIC = 0x46554747 // "GGUF" in little-endian

    // GGUF Metadata Value Types
    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    fun parse(file: File): Result<GgufMetadata> {
        return try {
            file.inputStream().use { stream ->
                parse(stream, file.length())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parse(stream: InputStream, fileSize: Long = 0L): Result<GgufMetadata> {
        return try {
            // Read first 32 bytes for header
            val headerBytes = ByteArray(32)
            var bytesRead = 0
            while (bytesRead < 32) {
                val r = stream.read(headerBytes, bytesRead, 32 - bytesRead)
                if (r == -1) break
                bytesRead += r
            }

            if (bytesRead < 24) {
                return Result.failure(IllegalArgumentException("File is too small to be a valid GGUF file"))
            }

            val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buffer.int
            if (magic != GGUF_MAGIC) {
                return Result.failure(IllegalArgumentException("Invalid GGUF header magic. Expected GGUF, found 0x${Integer.toHexString(magic)}"))
            }

            val version = buffer.int
            if (version !in 2..3) {
                return Result.failure(IllegalArgumentException("Unsupported GGUF version: $version (supports v2 and v3)"))
            }

            val tensorCount = buffer.long
            val kvCount = buffer.long

            var architecture = "llama"
            var modelName = "Unknown Model"
            var contextLength = 2048
            var fileType = -1
            var chatTemplate: String? = null

            // Read metadata KV pairs (limit to first 1000 pairs or 512KB to avoid reading huge tensors into memory)
            var pairsRead = 0
            while (pairsRead < kvCount && pairsRead < 1000) {
                val key = readString(stream) ?: break
                val valType = readUint32(stream) ?: break

                when (key) {
                    "general.architecture" -> {
                        architecture = readValueString(stream, valType) ?: architecture
                    }
                    "general.name" -> {
                        modelName = readValueString(stream, valType) ?: modelName
                    }
                    "general.file_type" -> {
                        fileType = readValueInt(stream, valType) ?: -1
                    }
                    "tokenizer.chat_template" -> {
                        chatTemplate = readValueString(stream, valType)
                    }
                    else -> {
                        if (key.endsWith(".context_length")) {
                            contextLength = readValueInt(stream, valType)?.coerceAtLeast(256) ?: contextLength
                        } else {
                            skipValue(stream, valType)
                        }
                    }
                }
                pairsRead++
            }

            val quantization = mapFileTypeToQuant(fileType)
            val paramEstimate = estimateParameterCount(tensorCount, fileSize, quantization)

            val meta = GgufMetadata(
                magic = "GGUF",
                version = version,
                architecture = architecture,
                modelName = modelName,
                contextLength = contextLength,
                tensorCount = tensorCount,
                kvCount = kvCount,
                quantization = quantization,
                parameterCountEstimate = paramEstimate,
                chatTemplate = chatTemplate
            )
            EngineLogger.i("GGUF_PARSER", "Parsed GGUF header successfully", "arch=$architecture, quant=$quantization, ctx=$contextLength, tensors=$tensorCount, kv=$kvCount")
            Result.success(meta)
        } catch (e: Exception) {
            EngineLogger.e("GGUF_PARSER", "Failed to parse GGUF binary header", e.message)
            Result.failure(e)
        }
    }

    private fun readUint32(stream: InputStream): Int? {
        val b = ByteArray(4)
        if (readFully(stream, b) != 4) return null
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readUint64(stream: InputStream): Long? {
        val b = ByteArray(8)
        if (readFully(stream, b) != 8) return null
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readString(stream: InputStream): String? {
        val len = readUint64(stream) ?: return null
        if (len < 0 || len > 65536) return null // Guard against corrupted string length
        val bytes = ByteArray(len.toInt())
        if (readFully(stream, bytes) != len.toInt()) return null
        return String(bytes, Charsets.UTF_8)
    }

    private fun readValueString(stream: InputStream, type: Int): String? {
        return when (type) {
            TYPE_STRING -> readString(stream)
            else -> {
                skipValue(stream, type)
                null
            }
        }
    }

    private fun readValueInt(stream: InputStream, type: Int): Int? {
        return when (type) {
            TYPE_UINT8, TYPE_INT8 -> stream.read().takeIf { it >= 0 }
            TYPE_UINT16, TYPE_INT16 -> {
                val b = ByteArray(2)
                if (readFully(stream, b) == 2) ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).short.toInt() else null
            }
            TYPE_UINT32, TYPE_INT32 -> {
                val b = ByteArray(4)
                if (readFully(stream, b) == 4) ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int else null
            }
            TYPE_UINT64, TYPE_INT64 -> {
                val b = ByteArray(8)
                if (readFully(stream, b) == 8) ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long.toInt() else null
            }
            else -> {
                skipValue(stream, type)
                null
            }
        }
    }

    private fun skipValue(stream: InputStream, type: Int) {
        when (type) {
            TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> stream.skip(1)
            TYPE_UINT16, TYPE_INT16 -> stream.skip(2)
            TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> stream.skip(4)
            TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> stream.skip(8)
            TYPE_STRING -> {
                val len = readUint64(stream) ?: return
                if (len > 0) stream.skip(len)
            }
            TYPE_ARRAY -> {
                val itemType = readUint32(stream) ?: return
                val count = readUint64(stream) ?: return
                for (i in 0 until count.coerceAtMost(5000)) {
                    skipValue(stream, itemType)
                }
            }
        }
    }

    private fun readFully(stream: InputStream, b: ByteArray): Int {
        var n = 0
        while (n < b.size) {
            val count = stream.read(b, n, b.size - n)
            if (count < 0) break
            n += count
        }
        return n
    }

    private fun mapFileTypeToQuant(fileType: Int): String {
        return when (fileType) {
            0 -> "F32"
            1 -> "F16"
            2 -> "Q4_0"
            3 -> "Q4_1"
            7 -> "Q8_0"
            8 -> "Q5_0"
            9 -> "Q5_1"
            10 -> "Q2_K"
            11 -> "Q3_K_S"
            12 -> "Q3_K_M"
            13 -> "Q3_K_L"
            14 -> "Q4_K_S"
            15 -> "Q4_K_M"
            16 -> "Q5_K_S"
            17 -> "Q5_K_M"
            18 -> "Q6_K"
            19 -> "IQ2_XXS"
            20 -> "IQ2_XS"
            21 -> "Q2_K_S"
            22 -> "IQ3_XS"
            23 -> "IQ3_XXS"
            24 -> "IQ1_S"
            25 -> "IQ4_NL"
            26 -> "IQ3_S"
            27 -> "IQ3_M"
            28 -> "IQ2_S"
            29 -> "IQ2_M"
            30 -> "IQ4_XS"
            else -> "Q4_K_M"
        }
    }

    private fun estimateParameterCount(tensorCount: Long, fileSize: Long, quant: String): String {
        if (fileSize <= 0) return if (tensorCount > 0) "~$tensorCount tensors" else "Unknown"
        val bytesPerParam = when {
            quant.startsWith("Q4") -> 0.55
            quant.startsWith("Q5") -> 0.68
            quant.startsWith("Q8") -> 1.05
            quant.startsWith("F16") -> 2.0
            quant.startsWith("IQ2") -> 0.35
            quant.startsWith("IQ3") -> 0.45
            else -> 0.6
        }
        val estimatedParams = (fileSize / bytesPerParam)
        return when {
            estimatedParams >= 1_000_000_000 -> "%.1fB".format(estimatedParams / 1_000_000_000.0)
            estimatedParams >= 1_000_000 -> "%.0fM".format(estimatedParams / 1_000_000.0)
            else -> "${(fileSize / (1024 * 1024))} MB"
        }
    }
}
