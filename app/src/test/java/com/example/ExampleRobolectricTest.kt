package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GgufMetadata
import com.example.data.model.GenerationParameters
import com.example.data.model.MessageEntity
import com.example.data.model.MessageRole
import com.example.engine.ChatTemplateHelper
import com.example.engine.GgufParser
import com.example.engine.MemorySafetyHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Local GGUF", appName)
    }

    @Test
    fun `test memory safety snapshot formatting`() {
        val formatted = MemorySafetyHelper.formatBytes(1024L * 1024L * 512L) // 512MB
        assertEquals("512 MB", formatted)

        val formattedGb = MemorySafetyHelper.formatBytes(1024L * 1024L * 1024L * 3L) // 3GB
        assertEquals("3.0 GB", formattedGb)
    }

    @Test
    fun `test gguf header parser rejection of invalid stream`() {
        val invalidBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val stream = ByteArrayInputStream(invalidBytes)
        val result = GgufParser.parse(stream, invalidBytes.size.toLong())
        assertTrue(result.isFailure)
    }

    @Test
    fun `test gguf header parser valid magic`() {
        val bout = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0x46554747) // "GGUF"
        header.putInt(3)          // Version 3
        header.putLong(10L)       // Tensor count
        header.putLong(0L)        // KV count
        bout.write(header.array())

        val stream = ByteArrayInputStream(bout.toByteArray())
        val result = GgufParser.parse(stream, bout.size().toLong())
        assertTrue(result.isSuccess)
        val meta = result.getOrThrow()
        assertEquals("GGUF", meta.magic)
        assertEquals(3, meta.version)
        assertEquals(10L, meta.tensorCount)
    }

    @Test
    fun `test chat template formatting`() {
        val messages = listOf(
            com.example.data.model.MessageEntity(
                id = "m1",
                conversationId = "c1",
                role = com.example.data.model.MessageRole.USER,
                content = "Tell me a joke"
            )
        )
        val prompt = com.example.engine.ChatTemplateHelper.formatPrompt(messages, architecture = "qwen2")
        assertTrue(prompt.contains("<|im_start|>user\nTell me a joke<|im_end|>"))
        assertTrue(prompt.contains("<|im_start|>assistant"))
    }

    @Test
    fun `test gguf parser handles kv pairs without crashing`() {
        val bout = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0x46554747) // GGUF
        header.putInt(3)          // v3
        header.putLong(5L)        // 5 tensors
        header.putLong(1L)        // 1 KV pair
        bout.write(header.array())

        // Key: "general.architecture"
        val keyBytes = "general.architecture".toByteArray(Charsets.UTF_8)
        val keyLenBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(keyBytes.size.toLong())
        bout.write(keyLenBuf.array())
        bout.write(keyBytes)

        // Type: STRING (8)
        val typeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(8)
        bout.write(typeBuf.array())

        // Value: "llama"
        val valBytes = "llama".toByteArray(Charsets.UTF_8)
        val valLenBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(valBytes.size.toLong())
        bout.write(valLenBuf.array())
        bout.write(valBytes)

        val stream = ByteArrayInputStream(bout.toByteArray())
        val result = GgufParser.parse(stream, bout.size().toLong())
        assertTrue(result.isSuccess)
        val meta = result.getOrThrow()
        assertEquals("llama", meta.architecture)
    }

    @Test
    fun `test generation stats calculation`() {
        val stats = com.example.data.model.GenerationStats(
            promptTokens = 12,
            generatedTokens = 48,
            promptEvalMs = 120L,
            generationMs = 1600L,
            tokensPerSecond = 30.0,
            contextTokensUsed = 60
        )
        assertEquals(48, stats.generatedTokens)
        assertEquals(30.0, stats.tokensPerSecond, 0.01)
        assertEquals(60, stats.contextTokensUsed)
    }

    @Test
    fun `test default generation parameters constraints`() {
        val params = com.example.data.model.GenerationParameters(
            temperature = 0.7f,
            maxTokens = 512,
            contextSize = 2048,
            topP = 0.9f,
            topK = 40,
            repeatPenalty = 1.1f,
            threads = 4
        )
        assertEquals(0.7f, params.temperature, 0.001f)
        assertEquals(512, params.maxTokens)
        assertEquals(2048, params.contextSize)
        assertEquals(0.9f, params.topP, 0.001f)
        assertEquals(40, params.topK)
        assertEquals(1.1f, params.repeatPenalty, 0.001f)
        assertEquals(4, params.threads)
    }

    @Test
    fun `test failure matrix - truncated GGUF rejected`() {
        val truncatedBytes = byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00) // only 6 bytes, header requires >= 24
        val stream = ByteArrayInputStream(truncatedBytes)
        val result = GgufParser.parse(stream, truncatedBytes.size.toLong())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("too small") == true)
    }

    @Test
    fun `test failure matrix - invalid GGUF magic rejected`() {
        val fakeBytes = ByteArray(32) { 0x00 }
        val stream = ByteArrayInputStream(fakeBytes)
        val result = GgufParser.parse(stream, fakeBytes.size.toLong())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid GGUF header magic") == true)
    }

    @Test
    fun `test failure matrix - unsupported GGUF version rejected`() {
        val bout = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0x46554747) // GGUF
        header.putInt(99)         // Unsupported version 99
        header.putLong(0L)
        header.putLong(0L)
        bout.write(header.array())

        val stream = ByteArrayInputStream(bout.toByteArray())
        val result = GgufParser.parse(stream, bout.size().toLong())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Unsupported GGUF version") == true)
    }

    @Test
    fun `test failure matrix - memory safety threshold detects high memory usage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hugeFileSize = 1024L * 1024L * 1024L * 64L // 64 GB model
        val isRisky = MemorySafetyHelper.isMemoryRisky(hugeFileSize, context)
        assertTrue("64GB model on phone must trigger memory risk", isRisky)
    }

    @Test
    fun `test system prompt presets defined and non-empty`() {
        val presets = GenerationParameters.PRESETS
        assertEquals(5, presets.size)
        assertTrue(presets.any { it.id == "general" })
        assertTrue(presets.any { it.id == "coding" })
        assertTrue(presets.any { it.id == "reasoner" })
        assertTrue(presets.any { it.id == "writer" })
        assertTrue(presets.any { it.id == "summarizer" })

        presets.forEach { preset ->
            assertTrue("Preset prompt must not be blank", preset.prompt.isNotBlank())
            assertTrue("Preset title must not be blank", preset.title.isNotBlank())
            assertTrue("Preset description must not be blank", preset.description.isNotBlank())
        }
    }

    @Test
    fun `test dynamic system prompt formatting in ChatML and Llama3`() {
        val customPrompt = "You are a specialized code reviewer assistant."
        val messages = listOf(
            MessageEntity(
                id = "m1",
                conversationId = "c1",
                role = MessageRole.USER,
                content = "Check this Kotlin code",
                timestamp = System.currentTimeMillis()
            )
        )

        // ChatML formatting with custom prompt
        val chatmlPrompt = ChatTemplateHelper.formatPrompt(
            messages = messages,
            architecture = "qwen2",
            systemPrompt = customPrompt
        )
        assertTrue("ChatML must contain custom system prompt", chatmlPrompt.contains(customPrompt))
        assertTrue("ChatML must contain user message", chatmlPrompt.contains("Check this Kotlin code"))
        assertTrue("ChatML must terminate with assistant turn", chatmlPrompt.endsWith("<|im_start|>assistant\n"))

        // Llama3 formatting with custom prompt
        val llama3Prompt = ChatTemplateHelper.formatPrompt(
            messages = messages,
            architecture = "llama",
            systemPrompt = customPrompt
        )
        assertTrue("Llama3 must contain custom system prompt", llama3Prompt.contains(customPrompt))
        assertTrue("Llama3 must contain system header", llama3Prompt.contains("<|start_header_id|>system<|end_header_id|>"))
        assertTrue("Llama3 must end with assistant header", llama3Prompt.endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n"))
    }

    @Test
    fun `test explicit system message in conversation overrides default system prompt`() {
        val defaultPrompt = "Default system prompt"
        val explicitPrompt = "Explicit system message in session"
        val messages = listOf(
            MessageEntity(
                id = "m0",
                conversationId = "c1",
                role = MessageRole.SYSTEM,
                content = explicitPrompt,
                timestamp = System.currentTimeMillis()
            ),
            MessageEntity(
                id = "m1",
                conversationId = "c1",
                role = MessageRole.USER,
                content = "Hello world",
                timestamp = System.currentTimeMillis()
            )
        )

        val formatted = ChatTemplateHelper.formatPrompt(
            messages = messages,
            architecture = "qwen2",
            systemPrompt = defaultPrompt
        )
        assertTrue("Formatted prompt should include the explicit system message", formatted.contains(explicitPrompt))
        assertFalse("Formatted prompt should not duplicate or use default prompt when explicit exists", formatted.contains(defaultPrompt))
    }
}
