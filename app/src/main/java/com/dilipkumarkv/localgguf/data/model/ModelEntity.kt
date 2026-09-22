package com.dilipkumarkv.localgguf.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val fileName: String,
    val path: String,
    val fileSize: Long,
    val architecture: String,
    val parameterCount: String,
    val quantization: String,
    val contextLength: Int,
    val dateAdded: Long = System.currentTimeMillis()
)
