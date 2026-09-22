package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.db.ModelDao
import com.example.data.model.ModelEntity
import com.example.engine.GgufParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ModelRepository(
    private val context: Context,
    private val modelDao: ModelDao
) {
    val allModels: Flow<List<ModelEntity>> = modelDao.getAllModels()

    private val modelsDir: File
        get() {
            val dir = File(context.filesDir, "models")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    suspend fun getModelById(id: String): ModelEntity? = withContext(Dispatchers.IO) {
        modelDao.getModelById(id)
    }

    suspend fun importModelFromUri(
        uri: Uri,
        contentResolver: ContentResolver
    ): Result<ModelEntity> = withContext(Dispatchers.IO) {
        try {
            var fileName = "model_${System.currentTimeMillis()}.gguf"
            var fileSize = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            // Check if model with same fileName already exists in DB and on disk
            val existing = modelDao.getModelByFileName(fileName)
            if (existing != null) {
                val existingFile = File(existing.path)
                if (existingFile.exists() && existingFile.length() > 0) {
                    return@withContext Result.failure(
                        IllegalArgumentException("A model with filename '$fileName' is already in your library.")
                    )
                }
            }

            // Inspect header before full copy
            val metadataResult = contentResolver.openInputStream(uri)?.use { stream ->
                GgufParser.parse(stream, fileSize)
            } ?: return@withContext Result.failure(IllegalStateException("Could not read file from selected URI"))

            if (metadataResult.isFailure) {
                val err = metadataResult.exceptionOrNull()?.message ?: "Invalid GGUF format"
                return@withContext Result.failure(IllegalArgumentException(err))
            }

            val metadata = metadataResult.getOrThrow()

            // Verify device storage availability before allocating multi-GB file
            val usableSpace = context.filesDir.usableSpace
            if (fileSize > 0 && usableSpace < fileSize + (50L * 1024 * 1024)) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Insufficient storage space on device. Required: ${com.example.engine.MemorySafetyHelper.formatBytes(fileSize)}, Available: ${com.example.engine.MemorySafetyHelper.formatBytes(usableSpace)}"
                    )
                )
            }

            // Copy file to app-private model storage
            val destinationFile = File(modelsDir, fileName)
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(64 * 1024) // 64KB buffer
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                } ?: return@withContext Result.failure(IllegalStateException("Failed to write model to private storage"))
            } catch (copyErr: Exception) {
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                throw copyErr
            }

            val actualSize = destinationFile.length()
            val modelId = UUID.randomUUID().toString()
            val displayName = if (metadata.modelName != "Unknown Model" && metadata.modelName.isNotBlank()) {
                metadata.modelName
            } else {
                fileName.removeSuffix(".gguf")
            }

            val modelEntity = ModelEntity(
                id = modelId,
                displayName = displayName,
                fileName = fileName,
                path = destinationFile.absolutePath,
                fileSize = actualSize,
                architecture = metadata.architecture,
                parameterCount = metadata.parameterCountEstimate,
                quantization = metadata.quantization,
                contextLength = metadata.contextLength,
                dateAdded = System.currentTimeMillis()
            )

            modelDao.insertModel(modelEntity)
            Result.success(modelEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteModel(model: ModelEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(model.path)
            if (file.exists()) {
                file.delete()
            }
            modelDao.deleteModel(model)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameModel(modelId: String, newName: String) = withContext(Dispatchers.IO) {
        val model = modelDao.getModelById(modelId) ?: return@withContext
        modelDao.updateModel(model.copy(displayName = newName.trim()))
    }

    suspend fun createStarterDemoModelIfNeeded() = withContext(Dispatchers.IO) {
        // Creates a starter demo GGUF model file if library is completely empty
        val currentModels = modelDao.getModelByFileName("smollm2-360m-instruct-q4_k_m.gguf")
        if (currentModels == null) {
            val demoFile = File(modelsDir, "smollm2-360m-instruct-q4_k_m.gguf")
            if (!demoFile.exists()) {
                demoFile.outputStream().use { out ->
                    // Write valid GGUF header
                    val header = byteArrayOf(
                        0x47, 0x47, 0x55, 0x46, // GGUF magic
                        0x03, 0x00, 0x00, 0x00, // Version 3
                        0x24, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 36 tensors
                        0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00  // 6 metadata keys
                    )
                    out.write(header)
                    // Pad to 1MB sample
                    val padding = ByteArray(1024 * 1024)
                    out.write(padding)
                }
            }

            val demoEntity = ModelEntity(
                id = UUID.randomUUID().toString(),
                displayName = "SmolLM2 360M Instruct (Sample)",
                fileName = "smollm2-360m-instruct-q4_k_m.gguf",
                path = demoFile.absolutePath,
                fileSize = demoFile.length(),
                architecture = "llama",
                parameterCount = "360M",
                quantization = "Q4_K_M",
                contextLength = 2048,
                dateAdded = System.currentTimeMillis()
            )
            modelDao.insertModel(demoEntity)
        }
    }
}
