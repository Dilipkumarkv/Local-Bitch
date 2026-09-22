# Known Limitations

## 1. System Memory & Model Size
- On devices with 4GB or less of physical RAM, models larger than 1.5GB (e.g. 7B models at Q4) may experience memory pressure and background termination by Android's Low Memory Killer (LMK).
- Recommended model sizes:
  - 4GB RAM devices: 0.5B to 1.5B parameter models (e.g. SmolLM2 360M, Qwen2.5 0.5B, Gemma-2 2B at Q4_K_M).
  - 6GB-8GB RAM devices: 1.5B to 3B parameter models (e.g. Llama-3.2 1B, Llama-3.2 3B Q4_K_M).
  - 12GB+ RAM devices: up to 7B-8B parameter models.

## 2. Token Generation Throughput
- In mobile CPU inference, token generation speed depends on core cluster frequency, ARM NEON/KleidiAI instruction sets, and thermal throttling.
- Sustained generation over prolonged sessions will naturally throttle CPU clocks to prevent device heating.

## 3. Supported Quantization Formats
- Supported: `Q4_0`, `Q4_K_M`, `Q4_K_S`, `Q5_K_M`, `Q8_0`, `IQ3_M`, `IQ4_NL`, `F16`.
- Note: Non-GGUF formats (such as raw `.bin`, `.safetensors`, `.onnx`) are not supported and will be rejected by the GGUF binary header parser.
