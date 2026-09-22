# System Architecture: Minimal Android GGUF Local LLM Client

## 1. Overview & High-Level Architecture

The Minimal Android GGUF Local LLM Client is a focused, 100% offline, native Android application engineered strictly for:
1. Importing downloaded `.gguf` models into private storage.
2. Reading and verifying GGUF metadata directly on-device.
3. Loading a single model at a time into memory with device RAM guardrails.
4. Executing streaming local inference on device CPU/hardware.
5. Managing local chat history and inference parameters without external cloud dependencies.

```
+---------------------------------------------------------------+
|                       Presentation Layer                      |
|   Jetpack Compose UI (Material 3)                             |
|   - ModelsScreen    - ChatScreen    - SettingsScreen          |
+-------------------------------+-------------------------------+
                                | StateFlow / Events
+-------------------------------v-------------------------------+
|                         ViewModel Layer                       |
|   - ModelViewModel  - ChatViewModel  - SettingsViewModel      |
+-------------------------------+-------------------------------+
                                | Coroutines / Domain Calls
+-------------------------------v-------------------------------+
|                    Application / Domain Layer                 |
|   - ModelManager: Orchestrates model files & metadata         |
|   - GgufParser: Direct binary GGUF reader (Kotlin)            |
|   - LocalInferenceEngine: Contract for LLM inference          |
|   - ChatSession: Active conversation session                  |
+-------------------------------+-------------------------------+
                                |
        +-----------------------+-----------------------+
        |                                               |
+-------v-----------------------+       +---------------v---------------+
|        Data Persistence       |       |        Inference Engine       |
|  - Room Local SQLite Database |       |  - NativeLlamaEngine (JNI)    |
|    * ModelEntity / ModelDao   |       |    * libllama-android.so      |
|    * ConversationDao          |       |    * KleidiAI / ARM Neon      |
|    * MessageDao               |       |  - EmbeddedFallbackEngine     |
|  - DataStore Preferences      |       |    * Guarantees testability   |
|    * Inference parameters     |       |    * Offline mock validation  |
+-------------------------------+       +-------------------------------+
```

---

## 2. Component Breakdown

### 2.1 Model Management & GGUF Parsing
- **`GgufParser`**: Reads binary metadata from the GGUF file directly from `File` or `InputStream` without loading model weights into heap memory. Reads:
  - Magic header `GGUF` (0x46554747)
  - Version (v2 or v3)
  - Tensor count and Key-Value (KV) count
  - KV metadata: `general.architecture`, `general.name`, `llama.context_length`, `tokenizer.ggml.model`, `tokenizer.chat_template`
  - Quantization types (`Q4_K_M`, `Q4_0`, `Q8_0`, `F16`, etc.)
- **`ModelManager`**: Handles copying models from Android Storage Access Framework (SAF) URI to `context.filesDir/models/`. Compares file name and length to avoid duplicates. Enforces single-model-loaded constraint.

### 2.2 Local Inference Engine Abstraction
```kotlin
interface LocalInferenceEngine {
    suspend fun loadModel(modelPath: String, params: GenerationParameters): Result<ModelInfo>
    suspend fun unloadModel()
    fun isLoaded(): Boolean
    fun getLoadedModelPath(): String?
    fun generate(
        messages: List<ChatMessage>,
        parameters: GenerationParameters
    ): Flow<GenerationEvent>
    fun stopGeneration()
    suspend fun clearContext()
    suspend fun getModelInfo(): ModelInfo?
}
```

Events streamed over `Flow<GenerationEvent>`:
- `GenerationEvent.Started`: Emitted when prompt evaluation begins.
- `GenerationEvent.Token(val text: String, val tokenIndex: Int)`: Emitted for every generated token.
- `GenerationEvent.Completed(val stats: GenerationStats)`: Emitted on completion with tokens/sec, prompt eval ms, generation ms.
- `GenerationEvent.Stopped(val stats: GenerationStats)`: Emitted when user cancels generation.
- `GenerationEvent.Error(val message: String, val cause: Throwable?)`: Human-readable error.

### 2.3 Persistence Architecture (Room + DataStore)
- **Room SQLite**:
  - `ModelEntity`: Stored models (`id`, `displayName`, `fileName`, `path`, `fileSize`, `architecture`, `parameterCount`, `quantization`, `contextLength`, `dateAdded`)
  - `ConversationEntity`: Conversations (`id`, `title`, `modelId`, `createdAt`, `updatedAt`)
  - `MessageEntity`: Messages (`id`, `conversationId`, `role`, `content`, `timestamp`, `tokensCount`, `tokensPerSecond`, `durationMs`)
- **DataStore**:
  - `temperature` (default: 0.7f)
  - `maxTokens` (default: 512)
  - `contextSize` (default: 2048)
  - `topP` (default: 0.9f)
  - `topK` (default: 40)
  - `repeatPenalty` (default: 1.1f)
  - `threads` (default: 4)
  - `themeMode` (default: System)

---

## 3. The Phase 0 Gate: Concrete Call Chain

> **"How does one GGUF file become streamed assistant text?"**

1. **Selection & Import**:
   - User picks a `.gguf` file via Android `ActivityResultContracts.GetContent` / `OpenDocument`.
   - `ModelManager.importModel(uri)` opens `contentResolver.openInputStream(uri)`.
   - `GgufParser.parseHeaderAndMetadata(stream)` validates `magic == 0x46554747`, extracts model architecture, quant, context length.
   - Stream is piped in chunks (64KB buffer) to `context.filesDir/models/<filename>.gguf`.
   - Model metadata is saved to Room DB (`modelDao.insertModel(...)`).

2. **Model Loading**:
   - User taps "Load" on the model card in `ModelsScreen`.
   - `ModelViewModel.loadModel(model)` checks `ActivityManager.getMemoryInfo()`. If `model.fileSize > availableMemory * 0.6`, a safety dialog is displayed.
   - On confirmation, `InferenceEngine.loadModel(model.path, params)` is invoked.
   - Previous model is released via `unloadModel()`.
   - Native engine initializes `llama_model_load_from_file(path, model_params)` and creates `llama_context` with configured context size and threads.
   - Model state updates to `MODEL_READY`.

3. **Prompt Formatting**:
   - User submits a prompt in `ChatScreen`.
   - Message is saved as `user` role in Room DB (`messageDao.insert(...)`).
   - `ChatViewModel` passes conversation history (`List<ChatMessage>`) to `InferenceEngine.generate(...)`.
   - The engine formats the prompt using the model's chat template (e.g. ChatML `<|im_start|>user\n...<|im_end|>\n<|im_start|>assistant\n`).

4. **Token Generation & Streaming**:
   - Native engine tokenizes the formatted prompt into token IDs (`llama_tokenize`).
   - Prefill phase: `llama_decode` evaluates prompt tokens in batches.
   - Generation loop starts:
     - While not EOS and `tokens < maxTokens` and `!stopRequested`:
     - Sample next token using temperature, top-k, top-p, repeat penalty.
     - Decode token ID to UTF-8 text piece (`llama_token_to_piece`).
     - Emit `GenerationEvent.Token(piece)`.
     - `ChatViewModel` receives event on `Dispatchers.Main` and appends text to the active streaming assistant message in `StateFlow`.
     - Jetpack Compose UI recomposes the chat bubble smoothly.

5. **Completion or Stop**:
   - If user taps "Stop", `stopGeneration()` flips an `AtomicBoolean`. The loop breaks cleanly on the next iteration.
   - Native context state is maintained for multi-turn chat or reset on `clearContext()`.
   - Stats (token count, tokens/sec, elapsed time) are packaged into `GenerationStats`.
   - Emitted `GenerationEvent.Completed` or `GenerationEvent.Stopped` causes `ChatViewModel` to persist the completed assistant message with its stats to Room DB.
