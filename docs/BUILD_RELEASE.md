# Build & Release Instructions

## 1. Prerequisites
- Android Studio Ladybug (2024.2.1+) or command-line Gradle 8.11+
- Android SDK 36 (compileSdk = 36, minSdk = 24)
- JDK 17 or JDK 21

## 2. Debug Build
To build and install the debug APK:
```bash
gradle :app:assembleDebug
```
The resulting APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

## 3. Unit Tests & Verification
To execute local Robolectric tests:
```bash
gradle :app:testDebugUnitTest
```

To run Roborazzi screenshot verification:
```bash
gradle :app:verifyRoborazziDebug
```

## 4. Release Build
To assemble a signed release AAB/APK:
```bash
gradle :app:assembleRelease
```
Ensure signing configurations and keystores are configured according to your deployment pipeline.
