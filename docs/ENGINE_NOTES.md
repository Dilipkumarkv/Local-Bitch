# Inference Engine Notes & GGUF Specification

## 1. GGUF Binary Specification
GGUF (GPT-Generated Unified Format) is a binary format designed for fast model loading and memory mapping (`mmap`).

### Header Layout (Little-Endian)
| Offset | Type | Description |
|---|---|---|
| 0x00 | `uint32` | Magic number: `0x46554747` (`GGUF` in ASCII) |
| 0x04 | `uint32` | Format version: `2` or `3` |
| 0x08 | `uint64` | Tensor count (number of weight tensors) |
| 0x10 | `uint64` | Metadata key-value pairs count |

### Key-Value Metadata Types
- `0`: UINT8
- `1`: INT8
- `2`: UINT16
- `3`: INT16
- `4`: UINT32
- `5`: INT32
- `6`: FLOAT32
- `7`: BOOL
- `8`: STRING (length uint64 followed by UTF-8 bytes)
- `9`: ARRAY (type uint32, length uint64, followed by elements)
- `10`: UINT64
- `11`: INT64
- `12`: FLOAT64

### Critical Metadata Keys
- `general.architecture`: Model family (e.g. `llama`, `mistral`, `gemma`, `phi3`, `qwen2`)
- `general.name`: Model name (e.g. `Llama-3.2-1B-Instruct`)
- `general.file_type`: Quantization identifier enum (0=ALL_F32, 2=MOSTLY_Q4_0, 12=MOSTLY_Q4_K_M, etc.)
- `[arch].context_length`: Maximum sequence length (e.g. `llama.context_length`)
- `tokenizer.chat_template`: Jinja-style chat template string used by the model
- `tokenizer.ggml.tokens`: Vocabulary array

---

## 2. Prompt Template Strategies
To ensure output coherence without hardcoding a single prompt style:
1. **ChatML Format** (Used by Qwen, Yi, SmolLM):
   ```text
   <|im_start|>system
   You are a helpful assistant.<|im_end|>
   <|im_start|>user
   {prompt}<|im_end|>
   <|im_start|>assistant
   ```
2. **Llama 3 Format**:
   ```text
   <|begin_of_text|><|start_header_id|>system<|end_header_id|>
   You are a helpful assistant.<|eot_id|><|start_header_id|>user<|end_header_id|>
   {prompt}<|eot_id|><|start_header_id|>assistant<|end_header_id|>
   ```
3. **Standard Instruction Fallback**:
   ```text
   System: You are a helpful assistant.
   User: {prompt}
   Assistant: 
   ```

---

## 3. Streaming and Cancellation
- **Token Generation Loop**:
  Each generation pass runs on `Dispatchers.Default`. The loop evaluates the next token and emits it to a Kotlin `Flow<GenerationEvent>`.
- **Atomic Cancellation**:
  A thread-safe `AtomicBoolean isCancelled` flag is checked on every token cycle. When `stopGeneration()` is called, `isCancelled` is immediately set to `true`, ending the decode loop without crashing or leaking native context state.
- **Latency & Throughput Calculation**:
  - `promptEvalMs`: Time taken from prompt submission to first generated token.
  - `generationMs`: Time spent during the autoregressive token loop.
  - `tokensPerSec`: `tokensGenerated / (generationMs / 1000.0)`.
