
# 🧠 Attention Panner

**Attention Panner** is a mindful Android application designed to interrupt doom-scrolling and regain your focus. It periodically overlays your screen with personal media memories (photos/videos) or wisdom from scripture, using a cryptographically secure random algorithm to ensure every moment is unexpected.

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green?logo=android" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin-purple?logo=kotlin" alt="Language" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-blue?logo=jetpackcompose" alt="UI" />
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License" />
</p>

## ✨ Features

*   **⚡ Blazingly Fast & Lightweight:** Highly optimized using Singleton patterns for ExoPlayer and TTS, Regex pre-compilation, and efficient SQLite indexing. Zero lag overlay appearance.
*   **🎲 True Randomness:** Uses `SecureRandom` to calculate a mathematically fair probability across your entire library of media and verses.
*   **🖼️ Media Overlay:** Displays random photos and videos from your device storage (filters out small icons/thumbnails <16KB).
*   **📖 Wisdom Verses:** Includes a built-in downloader for the King James Version (KJV) Bible (Proverbs, Ecclesiastes, Song of Solomon) and the Wisdom of Sirach.
*   **🗣️ Text-to-Speech (TTS):** automatically reads verses aloud using high-quality neural network voices available on the device.
*   **⏱️ Custom Intervals:** Set your own minimum and maximum time range (e.g., between 1 and 2 minutes) for random interruptions.
*   **🔋 Battery Efficient:** Uses `JobScheduler` logic and aggressive resource cleanup (garbage collection helpers) to run in the background without draining the battery.

## 🛠️ Tech Stack

*   **Language:** Kotlin
*   **UI Toolkit:** Jetpack Compose (Interop with Android Views for Surface rendering)
*   **Video Player:** Media3 (ExoPlayer)
*   **Image Loading:** Coil 3 (AsyncImage with Crossfade)
*   **Database:** SQLite (Custom `SQLiteOpenHelper` with Indexing and VACUUM optimization)
*   **Concurrency:** Kotlin Coroutines & Dispatchers
*   **Permissions:** Runtime Permissions for Storage and System Alert Window (Overlay)

## 🚀 How It Works

1.  **Sync:** On first launch, the app scans your device's `MediaStore` and indexes valid images/videos into a local SQLite database. It also downloads scripture data if the database is empty.
2.  **Service Start:** When you click "Start Service," a foreground service is launched.
3.  **Random Timer:** The app calculates a random delay between your Min and Max minutes using a cryptographically secure generator.
4.  **The Trigger:** When the timer fires, the app calculates a weighted probability based on your total items:
    *   *Total Items = (Total Media Files + Total Verses)*
    *   It picks one index from this massive pool, ensuring a mathematically uniform distribution.
5.  **The Overlay:**
    *   **If Media:** It instantly loads the image (via Coil) or the video (via a pre-warmed ExoPlayer) over whatever app you are currently using.
    *   **If Verse:** It displays a group of 3 consecutive verses for context and reads them aloud via TTS.

## 📦 Installation

### Prerequisites
*   Android Device running **Android 12 (API 31)** or higher.
*   **"Display over other apps"** permission must be granted (the app will prompt you).

### Building from Source
1.  Clone the repository:
    ```bash
    git clone https://github.com/yourusername/attention-panner.git
    ```
2.  Open the project in **Android Studio**.
3.  Sync Gradle files.
4.  Build and Run on your device.

## ⚙️ Configuration

The app requires the following permissions to function correctly:
*   `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` (To access your memories)
*   `SYSTEM_ALERT_WINDOW` (To display the overlay over other apps)
*   `FOREGROUND_SERVICE` (To keep the timer running)
*   `INTERNET` (One-time use to download Bible verses)

## 🤝 Contributing

Contributions are welcome!
1.  Fork the Project
2.  Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3.  Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4.  Push to the Branch (`git push origin feature/AmazingFeature`)
5.  Open a Pull Request

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

---

**Note:** This app is designed to help you, not annoy you. If you need to stop the overlays, simply open the notification shade and stop the service, or close the app from the recents menu.
