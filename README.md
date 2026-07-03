# Hangy

An Android app for hangboard training and real-time force tracking with a **Weiheng WH-C06**
Bluetooth crane scale. All data lives locally.

## Build & test

Requires JDK 17 and an Android SDK with platform 36. Use normal gradle commands:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew spotlessCheck detekt lintDebug
```
