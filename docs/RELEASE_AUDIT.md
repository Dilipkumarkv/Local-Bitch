# Release Audit Report - Local GGUF

## 1. Network & Privacy Audit
- **Network Permissions**: Audited `AndroidManifest.xml`. `android.permission.INTERNET` is **NOT** present.
- **Outbound HTTP/Socket Traffic**: Zero HTTP clients, zero analytics SDKs, zero crash-reporting pings.
- **Data Location**: 100% of chats, models, parameters, and metadata reside strictly within the application's private sandbox directory (`/data/data/com.dilipkumarkv.localgguf/`).

## 2. Memory & Hardware Safety Guardrails
- **Pre-load Heap & System RAM Check**: Every model load evaluates `ActivityManager.MemoryInfo.availMem`.
- **Memory Pressure Threshold**: If `modelFileSize > availableMem * 0.60` or `ActivityManager.MemoryInfo.lowMemory == true`, the app halts and surfaces the `MemoryWarningDialog`.
- **Thread Safety**: User can tune CPU threads (1 to device cores) in the Settings tab.
- **Context Size Limit**: Context window configurable up to 8192, with an explicit UI warning triggered if set above 4096 tokens.

## 3. Storage & File System
- **Scoped Storage & SAF**: Uses `ActivityResultContracts.OpenDocument()` to import `.gguf` files.
- **Copy Mechanism**: Streams into app-private files directory with 64KB buffering to prevent OutOfMemory during import.
- **Duplicate Prevention**: Rejects duplicate imports based on filename and size.
- **Delete Protection**: Deleting a model that is currently loaded in the active engine is strictly blocked until the user unloads it.

## 4. UI & Accessibility Audit
- **Touch Target Sizes**: All buttons, chips, and sliders satisfy the minimum 48dp touch target requirement.
- **Contrast Ratios**: Verified high-contrast dark theme (Deep slate `#0F172A` with indigo `#818CF8` accents) and light theme (Warm crisp off-white `#F8FAFC`).
- **Compose TestTags**: All action buttons, sliders, input fields, and message items have explicit snake_case `testTag` identifiers.
