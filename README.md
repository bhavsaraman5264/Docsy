# Kholo (working name)

India ke phones ke liye tez, offline, no-trap document opener. Android only.

## Setup

- Android Studio (2026.1+), JDK 17, Android SDK platform 37.
- `local.properties` Android Studio khud banata hai.

## Commands

```
./gradlew assembleDebug        # debug APK
./gradlew installDebug         # phone par install
./gradlew check                # Lint, detekt, ktlint, unit tests
./gradlew assembleRelease      # R8 ke saath release APK
node tools/fixtures/generate.mjs   # test documents banao (fixtures/generated)
```

`./gradlew check` pass hue bina koi milestone done nahi.

## Privacy niyam

Manifest me INTERNET aur storage permission nahi hai. Password, user ke details, file ka naam, URI
aur document ka text logs me kabhi nahi jata. Asli documents repo me kabhi commit nahi karne.
