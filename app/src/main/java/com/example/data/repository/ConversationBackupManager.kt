package com.example.data.repository

import com.example.data.model.ConversationEntity
import com.example.data.model.MessageEntity
import com.example.data.model.MessageRole
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConversationBackupManager {
    private const val BACKUP_VERSION = 1

    suspend fun exportToJson(
        conversations: List<ConversationEntity>,
        messageProvider: suspend (String) -> List<MessageEntity>
    ): String {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("exportDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))
        root.put("conversationCount", conversations.size)

        val convArray = JSONArray()
        for (conv in conversations) {
            val convObj = JSONObject()
            convObj.put("id", conv.id)
            convObj.put("title", conv.title)
            convObj.put("modelId", conv.modelId ?: JSONObject.NULL)
            convObj.put("createdAt", conv.createdAt)
            convObj.put("updatedAt", conv.updatedAt)

            val messages = messageProvider(conv.id)
            val msgArray = JSONArray()
            for (msg in messages) {
                val msgObj = JSONObject()
                msgObj.put("id", msg.id)
                msgObj.put("conversationId", msg.conversationId)
                msgObj.put("role", msg.role.name)
                msgObj.put("content", msg.content)
                msgObj.put("timestamp", msg.timestamp)
                msgObj.put("tokensCount", msg.tokensCount)
                msgObj.put("tokensPerSecond", msg.tokensPerSecond)
                msgObj.put("promptEvalMs", msg.promptEvalMs)
                msgObj.put("durationMs", msg.durationMs)
                msgArray.put(msgObj)
            }
            convObj.put("messages", msgArray)
            convArray.put(convObj)
        }
        root.put("conversations", convArray)

        return root.toString(2)
    }

    fun parseFromJson(jsonString: String): Pair<List<ConversationEntity>, List<MessageEntity>> {
        val root = JSONObject(jsonString)
        val convList = mutableListOf<ConversationEntity>()
        val msgList = mutableListOf<MessageEntity>()

        val convArray = root.getJSONArray("conversations")
        for (i in 0 until convArray.length()) {
            val convObj = convArray.getJSONObject(i)
            val conv = ConversationEntity(
                id = convObj.getString("id"),
                title = convObj.getString("title"),
                modelId = if (convObj.isNull("modelId")) null else convObj.getString("modelId"),
                createdAt = convObj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = convObj.optLong("updatedAt", System.currentTimeMillis())
            )
            convList.add(conv)

            val msgArray = convObj.optJSONArray("messages") ?: JSONArray()
            for (j in 0 until msgArray.length()) {
                val msgObj = msgArray.getJSONObject(j)
                val roleStr = msgObj.optString("role", MessageRole.USER.name)
                val role = try {
                    MessageRole.valueOf(roleStr)
                } catch (e: Exception) {
                    MessageRole.USER
                }

                val msg = MessageEntity(
                    id = msgObj.getString("id"),
                    conversationId = msgObj.getString("conversationId"),
                    role = role,
                    content = msgObj.getString("content"),
                    timestamp = msgObj.optLong("timestamp", System.currentTimeMillis()),
                    tokensCount = msgObj.optInt("tokensCount", 0),
                    tokensPerSecond = msgObj.optDouble("tokensPerSecond", 0.0),
                    promptEvalMs = msgObj.optLong("promptEvalMs", 0L),
                    durationMs = msgObj.optLong("durationMs", 0L)
                )
                msgList.add(msg)
            }
        }
        return Pair(convList, msgList)
    }
}
