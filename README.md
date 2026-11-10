# Keybord — Next-Gen Android IME

An ultra-low-latency, privacy-first Android Input Method Editor featuring hybrid on-device Rust intelligence, unlimited clipboard history, fluid spatial gesture glide typing, adaptive vocabulary n-gram modeling, Rambler voice speech-to-text cleanup, and an extensive visual theme engine.

## Building locally

Prerequisites:
- JDK 17
- Android SDK with NDK r25c+
- Rust stable + cargo-ndk
  ```bash
  cargo install cargo-ndk
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
  ```

Build:
  ```bash
  cd rust && cargo ndk -t arm64-v8a -o ../app/src/main/jniLibs build --release
  ./gradlew assembleDebug
  ```

APK location: `app/build/outputs/apk/debug/app-debug.apk`

## Releasing

Push a version tag:
  ```bash
  git tag v1.0.0
  git push origin v1.0.0
  ```

GitHub Actions builds and signs the APK automatically.
The signed APK is attached to the GitHub Release.
