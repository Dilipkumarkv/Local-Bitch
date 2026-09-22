package com.dilipkumarkv.localgguf.data.repository

import com.dilipkumarkv.localgguf.data.db.ConversationDao
import com.dilipkumarkv.localgguf.data.db.MessageDao
import com.dilipkumarkv.localgguf.data.model.ConversationEntity
import com.dilipkumarkv.localgguf.data.model.MessageEntity
import com.dilipkumarkv.localgguf.data.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    val allConversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationId)

    suspend fun getMessagesList(conversationId: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        messageDao.getMessagesListForConversation(conversationId)
    }

    suspend fun getConversation(conversationId: String): ConversationEntity? = withContext(Dispatchers.IO) {
        conversationDao.getConversationById(conversationId)
    }

    suspend fun createConversation(
        title: String = "New Chat",
        modelId: String? = null
    ): ConversationEntity = withContext(Dispatchers.IO) {
        val conversation = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            modelId = modelId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        conversationDao.insertConversation(conversation)
        conversation
    }

    suspend fun updateConversation(conversation: ConversationEntity) = withContext(Dispatchers.IO) {
        conversationDao.updateConversation(conversation.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun renameConversation(conversationId: String, newTitle: String) = withContext(Dispatchers.IO) {
        val conv = conversationDao.getConversationById(conversationId) ?: return@withContext
        conversationDao.updateConversation(conv.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessagesForConversation(conversationId)
        conversationDao.deleteConversationById(conversationId)
    }

    suspend fun clearConversationMessages(conversationId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessagesForConversation(conversationId)
    }

    suspend fun insertMessage(message: MessageEntity) = withContext(Dispatchers.IO) {
        messageDao.insertMessage(message)
        // Update conversation timestamp
        val conv = conversationDao.getConversationById(message.conversationId)
        if (conv != null) {
            // Auto derive title from first user message if still default
            val newTitle = if (conv.title == "New Chat" && message.content.isNotBlank()) {
                val line = message.content.lines().firstOrNull()?.trim() ?: "Chat"
                if (line.length > 30) line.take(30) + "..." else line
            } else {
                conv.title
            }
            conversationDao.updateConversation(conv.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun updateMessage(message: MessageEntity) = withContext(Dispatchers.IO) {
        messageDao.updateMessage(message)
    }

    suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessageById(messageId)
    }
}


