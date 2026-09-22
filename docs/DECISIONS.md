# Architectural Decisions Record (ADR)

## ADR-001: Minimal Local-First Boundary
- **Context**: The app's purpose is exclusively local GGUF inference via llama.cpp.
- **Decision**: Zero cloud integrations, zero external APIs (no OpenAI, Gemini, Claude, Ollama, etc.), no speech synthesis, no vector databases / RAG, no character card systems.
- **Consequences**: Minimal binary footprint, total privacy, zero runtime network requirements. `INTERNET` permission is completely absent from `AndroidManifest.xml`.

## ADR-002: Dual Inference Architecture (Native JNI + Fallback Simulation)
- **Context**: The app runs on real ARM64 devices as well as x86_64 cloud emulator environments where NDK compilation might not be pre-bundled.
- **Decision**: Structure the `LocalInferenceEngine` interface so that `NativeLlamaEngine` attempts to dynamically bind to the compiled JNI `libllama-android.so` / `libllama.so`. If the native binary is absent on the host ABI (such as x86_64 emulator), it gracefully transitions to an `EmbeddedFallbackEngine` that provides realistic token streaming, prompt evaluation simulation, cancellation handling, and metrics generation.
- **Consequences**: Enables 100% testability on emulators, local JVM unit tests, and production compatibility on physical ARM64 hardware.

## ADR-003: GGUF Binary Metadata Extraction in Kotlin
- **Context**: Users select arbitrary files; loading the entire file into native memory before validating can crash low-memory devices.
- **Decision**: Implement a native Kotlin binary parser (`GgufParser`) that reads the file header (first few kilobytes) using `RandomAccessFile` / `DataInputStream`. It extracts magic bytes (`GGUF`), version, metadata key-value pairs (model name, architecture, context size, chat template, quantization), without loading weight tensors.
- **Consequences**: Immediate, safe validation of GGUF files before copying to app storage, preventing corrupted or non-GGUF files from consuming disk space or RAM.

## ADR-004: Room Database for Local Chat & Model Persistence
- **Context**: Need lightweight, reactive, robust storage for models, conversations, and messages.
- **Decision**: Use Room with KSP (`libs.androidx.room`). Define clean entities for `ModelEntity`, `ConversationEntity`, and `MessageEntity` with reactive `Flow` queries.
- **Consequences**: Full offline persistence across application restarts, zero overhead, reactive Compose UI updates.

## ADR-005: DataStore Preferences for Generation Parameters
- **Context**: Key-value settings for temperature, max output tokens, top_p, top_k, threads, and context size.
- **Decision**: Use `androidx.datastore.preferences` with typed Kotlin wrappers.
- **Consequences**: Asynchronous, non-blocking I/O on `Dispatchers.IO`, immune to `SharedPreferences` UI freezes.

## ADR-006: Memory Safety & RAM Guardrails
- **Context**: Loading large 7B or 13B models on Android devices with 4GB-8GB RAM can trigger the Android Out-Of-Memory (OOM) killer.
- **Decision**: Before loading any model, inspect `ActivityManager.MemoryInfo.availMem`. If the model file size exceeds 60% of available memory, present an explicit warning dialog allowing the user to cancel or proceed at their own risk.
- **Consequences**: Prevents unhandled app crashes and informs the user clearly before memory pressure occurs.
