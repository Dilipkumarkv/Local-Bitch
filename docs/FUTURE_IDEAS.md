# Future Ideas & Architectural Non-Goals

As enforced by the Master Development Prompt (Section 19: Non-Goals Are Enforced), the following capabilities were deliberately excluded from v1 to keep the local GGUF engine small, native, offline-first, and high-performance.

These ideas are recorded here for potential future exploration:

## 1. Hardware & Acceleration
- **GPU Offload (Vulkan / OpenCL)**: Adding Vulkan SPIR-V shader kernels for Adreno and Mali GPUs to accelerate prompt prefill and token generation.
- **NPU Acceleration**: Exploring Android NNAPI / Qualcomm QNN / MediaTek NeuroPilot backends for INT4/INT8 matrix multiplications.

## 2. Advanced Native Features
- **Batch Evaluation**: Configurable prompt batch sizes (`n_batch`, `n_ubatch`) for faster document analysis.
- **Speculative Decoding**: Using a small draft model (e.g. 100M-300M parameters) to accelerate output generation of a larger primary model.
- **Context Caching / KV-Cache Reuse**: Caching common system prompt prefixes to avoid repetitive prompt evaluation across turns.

## 3. UI & Ergonomics
- **Direct Markdown & Code Block Syntax Highlighting**: Formatting fenced code blocks with one-tap copying.
- **Preset Prompt Library**: Curated system prompts (e.g., Code Reviewer, Text Summarizer, Writing Assistant).
- **Benchmarking Tool**: A single-tap native benchmark mode to measure tokens/sec across prompt prefill and token generation under varying thread counts.
