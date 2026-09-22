# Local GGUF - Minimal Android GGUF Local LLM Client

A minimal, native, offline-first Android application designed exclusively for local GGUF model inference using `llama.cpp`.

## Project Philosophy
- **100% Offline & Private**: No cloud APIs, no network permissions, zero telemetry, zero accounts.
- **Focused Scope**: Single-model loading, native token streaming, clean conversation history, and fine-grained local generation controls.
- **Hardware-Aware**: Built for ARM64 with CPU/KleidiAI/NEON optimization and memory safety guardrails.

---

## Phase Status Tracker

| Phase | Description | Status | Summary |
|---|---|---|---|
| **Phase 0** | Recon / Technical Design | **COMPLETE** | Architecture, ADRs, build specs, and engine notes defined in `/docs/`. |
| **Phase 1** | Android Shell | **COMPLETE** | M3 Theme (dark/light), typography, bottom navigation (Models, Chat, Settings). |
| **Phase 2** | Native llama.cpp Integration | **COMPLETE** | Native bridge abstraction (`LocalInferenceEngine`, `NativeLlamaEngine`, `EmbeddedFallbackEngine`). |
| **Phase 3** | Model Library | **COMPLETE** | SAF file picker, binary GGUF header parser, private storage copying, memory validation. |
| **Phase 4** | Chat Engine | **COMPLETE** | Real-time token streaming, cancellation, multi-turn history, metrics banner (tok/s, prefill). |
| **Phase 5** | Persistence | **COMPLETE** | Room SQLite database (`ModelEntity`, `ConversationEntity`, `MessageEntity`), DataStore settings. |
| **Phase 6** | Performance | **COMPLETE** | Memory pressure check (60% RAM threshold), thread selection, context window limits. |
| **Phase 7** | Failure Testing | **COMPLETE** | Human-readable error states, OOM warning dialog, invalid header rejection. |
| **Phase 8** | UI Polish | **COMPLETE** | M3 slate/indigo palette, custom neural adaptive icon, monospace metrics, accessibility testTags. |
| **Phase 9** | Release Audit | **COMPLETE** | `/docs/RELEASE_AUDIT.md`, `/docs/KNOWN_LIMITATIONS.md`, `/docs/BUILD_RELEASE.md`. |
| **Phase 10** | Final Deliverable & Sign-Off | **COMPLETE** | End-to-end verification, headless build hardening, clean documentation, production sign-off. |
| **Phase 11** | Ergonomics & Hardware Diagnostics | **COMPLETE** | Markdown & fenced code formatting, 1-tap copy, conversation export, CPU/ABI & model size guidance. |
| **Phase 12** | Chat Templates & Context Profiling | **COMPLETE** | Multi-architecture chat templates (ChatML, Llama3, Gemma, Mistral, DeepSeek, Phi), real-time context token telemetry. |
| **Phase 13** | Benchmarking Suite & Prompt Starters | **COMPLETE** | 64-token on-device inference benchmark (prefill & gen tok/s, TTFT), zero-tap empty-state prompt starters. |
| **Phase 14** | Multi-Conversation History & Auto-Titles | **COMPLETE** | ModalBottomSheet conversation switcher, auto title derivation from first user prompt without AI inference, delete/switch workflows. |
| **Phase 15** | Persona Presets & System Prompt Engine | **COMPLETE** | Curated persona library (General, Coding, Reasoner, Editor, Summarizer), custom system prompt persistence in DataStore, dynamic template injection. |
| **Phase 16** | Hardware-Aware Generation Profiles & Thermal Diagnostics | **COMPLETE** | Preset inference profiles (Balanced, Turbo, Battery Saver, Deterministic, Creative), KV cache reuse metrics, battery & thermal throttling monitor. |
| **Phase 17** | Engine Diagnostic Telemetry & Open-Source Compliance | **COMPLETE** | Centralized `EngineLogger` ring buffer with StateFlow UI bridge, log level filtering, 1-click debug export, engine lifecycle & parser instrumentation, and interactive open-source license disclosures. |
| **Phase 18** | Model Storage Analytics, Search/Filter & GGUF Inspection | **COMPLETE** | Disk storage breakdown vs free internal space (`StatFs`), multi-field real-time model search, architecture/quantization filter chips, multi-mode sorting (Recent, A-Z, Largest, Smallest), and detailed GGUF metadata inspector bottom sheet. |
| **Phase 19** | Conversation Forking, Message Search & Native Offline TTS Voice | **COMPLETE** | In-chat message keyword search and filtering, 1-tap conversation branching/forking from any past message in history, and native on-device Text-to-Speech (TTS) voice playback with zero network dependencies. |
| **Phase 20** | Full Database JSON Backup/Restore & Context Memory Compression | **COMPLETE** | 1-click JSON backup export & restore merging with share sheet, and active context compression summarizing long conversation turns into consolidated memory. |

---

## Technical Specifications
- **Target OS**: Android 7.0+ (API 24 to API 36)
- **Primary ABI**: `arm64-v8a` (with `x86_64` development/emulator support)
- **UI Framework**: Jetpack Compose (Material Design 3)
- **State Management**: MVVM + Kotlin StateFlow
- **Data Persistence**: AndroidX Room (SQLite) + Preferences DataStore
- **Inference Runtime**: llama.cpp native binding with robust fallback engine
- **Permissions**: Zero network permissions (`android.permission.INTERNET` omitted)
