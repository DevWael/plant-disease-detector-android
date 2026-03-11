# Plant Disease Detector — Native Android 🌿

A native Android application for detecting plant diseases using AI. Built with **Kotlin** and **Jetpack Compose**, powered by **Google AI** (Gemini/Gemma models).

## Features

- 📸 **Camera & Gallery** — Capture or select plant leaf images
- 🤖 **AI Analysis** — Send images to Google AI for disease diagnosis
- 🌍 **Bilingual** — Full English & Arabic support with RTL
- 📋 **Scan History** — Save and review past analyses with Room DB
- 🔐 **Secure Storage** — API key stored with EncryptedSharedPreferences
- ⚙️ **Settings** — Model selection, language switch, API key management
- 🎯 **Onboarding** — First-launch setup wizard

## Tech Stack

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin |
| **UI** | Jetpack Compose + Material 3 |
| **Architecture** | MVVM-like with Compose state |
| **Database** | Room |
| **Preferences** | DataStore + EncryptedSharedPreferences |
| **Camera** | CameraX + Activity Result API |
| **Networking** | OkHttp + Kotlinx Serialization |
| **Image Loading** | Coil |
| **AI Backend** | Google AI Studio API |

## Project Structure

```
app/src/main/java/com/bbioon/plantdisease/
├── MainActivity.kt
├── PlantDiseaseApp.kt
├── data/
│   ├── local/          # Room DB, DAO, PreferencesManager
│   ├── model/          # Data models (AnalysisResult, ScanRecord)
│   └── remote/         # Google AI API service
├── ui/
│   ├── components/     # Reusable composables (cards, overlays)
│   ├── navigation/     # Navigation graph + bottom nav
│   ├── screens/        # Onboarding, Scanner, History, Settings, Detail
│   └── theme/          # Colors, Typography, Theme
└── util/               # Locale helper
```

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17
- Android SDK 35 (compile) / min SDK 26

### Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/DevWael/plant-disease-detector-android.git
   cd plant-disease-detector-android
   ```

2. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

3. Install on device/emulator:
   ```bash
   ./gradlew installDebug
   ```

4. Launch the app and complete the onboarding:
   - Enter your **Google AI Studio API key** ([Get one here](https://aistudio.google.com/app/apikey))
   - Select your preferred language
   - Tap **Get Started**

## APK Size

The native Kotlin build produces an APK significantly smaller than the equivalent React Native (Expo) version:

| Version | Debug APK |
|---------|-----------|
| React Native (Expo) | ~97 MB |
| Native Kotlin | ~20 MB |

## License

MIT

## Author

**Ahmad Wael** — [bbioon.com](https://www.bbioon.com)
