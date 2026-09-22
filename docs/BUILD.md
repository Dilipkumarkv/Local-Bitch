# Build & Toolchain Specifications

## 1. Environment & SDK Requirements
- **JDK**: OpenJDK 17 or 21
- **Android Gradle Plugin (AGP)**: 8.x / 9.x
- **Gradle**: 8.11+
- **Kotlin**: 2.2.10
- **Compile SDK**: 36 (Android 16 preview / Android 15 compatible)
- **Min SDK**: 24 (Android 7.0+)
- **Target SDK**: 36
- **Architecture**: `arm64-v8a` (production target) / `x86_64` (emulator development)

## 2. Dependencies Justification
Every library included in the project has a strict justification:
1. `androidx.core:core-ktx`: Android platform extensions and compatibility.
2. `androidx.activity:activity-compose`: Compose activity host and edge-to-edge system insets.
3. `androidx.compose.material3:material3`: Modern Material 3 design system components (Scaffold, NavigationBar, Slider, Cards, Dialogs).
4. `androidx.compose.material:material-icons-extended`: Navigation and UI icons (Memory, Chat, Settings, Play, Stop, Delete, Edit, etc.).
5. `androidx.lifecycle:lifecycle-viewmodel-compose`: MVVM state preservation across lifecycle.
6. `androidx.room:room-runtime`, `androidx.room:room-ktx`: Offline SQLite database for conversations, messages, and model metadata.
7. `androidx.datastore:datastore-preferences`: Type-safe settings persistence.
8. `org.jetbrains.kotlinx:kotlinx-coroutines-android`: Asynchronous streaming and native background dispatching.

No unnecessary dependencies (no networking libraries, no cloud SDKs, no audio/speech libraries, no analytics).

## 3. Compilation Commands
- **Standard Build Verification**:
  ```bash
  gradle :app:assembleDebug
  ```
- **Unit Tests Execution**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Lint Execution**:
  ```bash
  gradle :app:lintDebug
  ```

## 4. Native Library Build Integration (llama.cpp)
For compiling `libllama.so` with Android NDK:
```bash
cmake -B build-android \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-28 \
  -DGGML_OPENMP=OFF \
  -DGGML_LLAMAFILE=OFF
cmake --build build-android --target llama -j8
```
Shared libraries are placed into `app/src/main/jniLibs/arm64-v8a/` or packaged within AAR bindings.
