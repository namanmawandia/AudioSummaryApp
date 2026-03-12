# Audio Summary App

A sophisticated Android application that captures audio in real-time, automatically transcribes recordings, and generates intelligent AI-powered summaries. Built with modern Android architecture, Kotlin, and cutting-edge technologies for seamless voice recording and processing.

## Overview

Audio Summary App is designed for users who need to quickly capture, transcribe, and summarize audio recordings. Whether you're recording lectures, meetings, interviews, or personal notes, this app handles the heavy lifting—from chunked audio recording to transcription and AI-powered summarization—all in a polished, user-friendly interface.

### Key Features

✨ **Real-Time Audio Chunking**
- Records audio in 30-second chunks with intelligent overlap (2-second) for continuity
- Handles silence detection (10+ seconds of silence triggers end of recording)
- Automatic low-storage warnings (≤50MB available)
- 16 kHz mono PCM format for optimal transcription quality

📝 **Speech-to-Text Transcription**
- Automatic transcription of all audio chunks
- Error handling with retry capabilities
- Stores transcripts locally for offline access
- Support for transcript regeneration

🤖 **AI-Powered Summarization**
- On-demand summary generation from complete transcripts
- Intelligent condensation of key points
- JSON-formatted summaries for structured data
- Error recovery and retry mechanisms

📱 **Beautiful UI with Jetpack Compose**
- Dark theme with modern material design
- Responsive tablet and phone layouts
- Smooth animations and transitions
- Tab-based navigation (Transcript / Summary)

💾 **Local Data Persistence**
- Room database for session metadata
- File-based storage for audio chunks and transcripts
- Session status tracking (RECORDING → COMPLETED)
- Automatic cleanup of stale sessions on app restart

🎨 **Polished User Experience**
- Session history with timestamps
- Real-time amplitude visualization callbacks
- Floating action button for quick recording start
- Detailed session view with status indicators

---

## Screenshots & Demo

### 📸 App Screenshots

#### Dashboard Screen
The home screen displays all recorded sessions with timestamps and duration.

| Screenshot 1 | Screenshot 2 | Screenshot 3 | Screenshots 4 |
|:---:|:---:|:---:|:---:|
| <img width="200" height="700" src="https://github.com/user-attachments/assets/64469b2a-4ca3-4556-a639-3b2c731f5795" />| <img width="200" height="700" src="https://github.com/user-attachments/assets/f3ec800c-11a6-4afd-a186-9abc7b2872dd" />| <img width="200" height="700" src="https://github.com/user-attachments/assets/c70ec6e5-cd64-4dc5-adfa-5a2e50cd0cb2" />| <img width="200" height="700"  src="https://github.com/user-attachments/assets/2a0139ff-f74b-4fdc-af7b-c9d56d67364d" /> |
|*Permission State* | *Empty state when no recordings* | *List of completed sessions* | *Session details preview* |

#### Recording Screen
Real-time recording interface with audio visualization and controls.

| Screenshot 5 | Screenshot 6 | Screenshot 7 | Screenshot 8 |
|:---:|:---:|:---:|:---:| 
|  <img width="200" height="700" src="https://github.com/user-attachments/assets/edc6b6ea-a681-4a5a-b48f-d9160cbadf1e" />| <img width="200" height="700" src="https://github.com/user-attachments/assets/65ba218e-2958-4b46-968f-1a50c6116ec8" /> | <img width="200" height="700" src="https://github.com/user-attachments/assets/eecfa07f-8eac-4608-b686-79cea915bb79" /> | <img width="200" height="700" src="https://github.com/user-attachments/assets/4ae10881-8fcb-41d4-a62e-6e1607d0edfb" /> |
| *Active recording with amplitude* | *Pause feature with incoming calls* | *Change of Audio Device* | *Notification Panel* |

#### Session Detail Screen - Transcript Tab and Summmary Tab
View the complete transcription of your audio recording and AI-generated summary with error handling and retry options.

| Screenshot 9 | Screenshot 10 | Screenshot 11 | Screenshot 12 |
|:---:|:---:| :---:|:---:|
| <img width="200" height="700" src="https://github.com/user-attachments/assets/6d00a81f-af0c-443d-b501-162365dc4a98" /> | <img width="200" height="700" src="https://github.com/user-attachments/assets/e9c31560-87e5-4062-9071-70ef1b8ed0df"/> | <img width="200" height="700" src="https://github.com/user-attachments/assets/83c6bfd5-5b55-48c6-8369-f24ba89825bb"/> |<img width="200" height="700" src="https://github.com/user-attachments/assets/3b41c7f8-ed62-4337-87bd-c0f2e81c8199"/>|
| *Completed transcript display* | *Transcript processing state* | *AI-powered summary* | *Generation in progress* |

## Architecture & Technologies

### Stack
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material Design 3)
- **Database:** Room ORM
- **Dependency Injection:** Dagger Hilt
- **Concurrency:** Coroutines & Flow
- **Audio:** Android AudioRecord API
- **Build System:** Gradle KTS

### Project Structure

```
app/src/main/java/com/example/audiosummeryapp/
├── AudioSummery.kt                 # Application class with Hilt setup
├── db/
│   ├── RecordingSessionEntity       # Room entity for session metadata
│   ├── RecordingSessionDao          # Data access object with queries
│   └── SessionRepository            # Repository pattern wrapper
├── services/
│   ├── AudioChunkManager            # Low-level audio capture & chunking
│   ├── TranscriptionManager         # Handles speech-to-text processing
│   ├── SummaryManager               # AI summary generation
│   └── RecordingService             # Foreground service lifecycle
├── ui/
│   ├── DashboardViewModel           # Compose state management
│   ├── DashboardScreen              # Home screen with session list
│   ├── SessionDetailScreen          # Detail view with tabs
│   ├── SessionDetailViewModel       # Detail screen logic
│   ├── theme/
│   │   ├── Color.kt                 # Dark theme palette
│   │   ├── Theme.kt                 # Material theme setup
│   │   └── Type.kt                  # Typography system
│   └── MainActivity                 # Activity entry point
└── ... other components
```

---

## Core Implementation Details

### 1. **Audio Recording & Chunking** (`AudioChunkManager`)

The app captures audio using Android's `AudioRecord` API and intelligently chunks it:

```kotlin
// Chunk Configuration
- Sample Rate: 16,000 Hz (optimal for speech recognition)
- Format: PCM 16-bit mono
- Chunk Duration: 30 seconds
- Overlap Duration: 2 seconds (for continuity)
- Silence Threshold: 10 seconds of silence triggers recording stop
```

**Key Implementation:**
- Runs on a coroutine (`Dispatchers.Default`) to avoid blocking the UI
- Calculates RMS (Root Mean Square) amplitude for silence detection
- Prepends overlap buffer to next chunk for seamless transcription
- Saves each chunk as a WAV file in a session folder
- Detects low storage conditions (≤50MB) and notifies the app

### 2. **Database Layer** (`Room + SessionRepository`)

Sessions are tracked in a SQLite database with the following schema:

```kotlin
RecordingSessionEntity {
  id: String                    // Unique session ID (session_YYYYMMDD_HHmmss)
  displayName: String           // User-friendly name (e.g., "Rec · Mar 10, 2:30 PM")
  createdAt: Long              // Timestamp of creation
  durationSecs: Int            // Calculated real duration of audio
  sessionFolderPath: String    // Path to audio chunks folder
  chunkCount: Int              // Number of audio chunks
  status: SessionStatus        // RECORDING or COMPLETED
  transcriptPath: String?      // Path to transcript file
  transcriptError: String?     // Error message if transcription failed
  summaryJson: String?         // Structured summary data
  summaryError: String?        // Error message if summary generation failed
}
```

**DAO Features:**
- Observable queries using Kotlin `Flow` for real-time UI updates
- Specialized update methods for transcript and summary state management
- Automatic cleanup of stale RECORDING sessions on app restart
- One-shot queries for non-flow contexts (suspend functions)

### 3. **Transcription Processing** (`TranscriptionManager`)

Once a recording is complete:
- Combines all WAV chunks into a unified audio stream
- Sends audio data to a speech-to-text API (external service)
- Writes transcript to a file in the session folder
- Updates the database with transcript path or error state
- Supports retry mechanism on failure

### 4. **Summary Generation** (`SummaryManager`)

After transcription is ready:
- Reads the complete transcript file
- Sends text to an AI summarization service
- Generates a condensed, coherent summary
- Stores summary as JSON for structured access
- Allows on-demand regeneration from the detail screen

### 5. **UI Layer** (Jetpack Compose)

**DashboardScreen:**
- Displays a paginated list of all completed sessions
- Shows session creation date and duration
- Floating action button to start a new recording
- Empty state when no recordings exist

**SessionDetailScreen:**
- Tabbed interface: Transcript | Summary
- Transcript tab loads text from file or shows loading/error state
- Summary tab triggers generation on first visit
- Retry buttons for both transcript and summary
- Status indicators (Green = success, Orange = in-progress, Red = error)

**Theme:**
- Dark background (`#0D0D0D`) for extended use comfort
- Accent red (`#E5484D`) for call-to-action buttons and highlights
- Waveform color blue (`#3A8CFF`) for visualizations
- Responsive text sizes using Material Design 3 tokens

### 6. **Service Architecture** (`RecordingService`)

A foreground service manages:
- Lifecycle of audio recording (start/stop/pause)
- Coordination between `AudioChunkManager`, `TranscriptionManager`, and `SummaryManager`
- Notification updates for user awareness
- Graceful cleanup on service destruction

---

## Getting Started

### Prerequisites
- Android SDK 24 or higher
- Kotlin 1.9+
- Internet connection (for transcription and summarization APIs)
- OpenAI API key

### Build & Run
```bash
# Clone the repository
git clone https://github.com/namanmawandia/AudioSummaryApp

# Navigate to project
cd AudioSummaryApp

# Build and run (requires connected device or emulator)
./gradlew installDebug
```

### Permissions Required
The app requests:
- `android.permission.RECORD_AUDIO` – for capturing microphone input
- `android.permission.INTERNET` – for transcription and summarization APIs
- `android.permission.POST_NOTIFICATIONS` – for foreground service notifications

---

## Usage

1. **Open App** → Navigate to the Recordings tab
2. **Start Recording** → Tap the red microphone FAB
3. **Record Audio** → Speak naturally; app handles chunking automatically
4. **Stop Recording** → Recording auto-stops after 10+ seconds of silence, or tap the stop button
5. **View Transcript** → Navigate to the session detail; transcript appears once processing completes
6. **Generate Summary** → Switch to the Summary tab; AI summary is generated on-demand
7. **Manage Sessions** → List shows all completed recordings with timestamps and durations

---

## Technical Highlights

### Coroutine-Driven Architecture
All I/O operations (database, file, API calls) run on `Dispatchers.IO` or `Dispatchers.Default`, keeping the UI thread responsive.

### Efficient Audio Storage
- PCM format without compression reduces transcription latency
- Session folders organize audio chunks by recording time
- Automatic duration calculation from file metadata

### Reactive UI with Compose
- `StateFlow` auto-unsubscribes when composables leave scope
- `LazyColumn` with diffing reduces recomposition
- Smooth animations for tab switching and content transitions

### Database Consistency
- Room ensures transaction safety during concurrent reads/writes
- Session status enum prevents invalid state transitions
- DAO methods return `Flow` for UI subscriptions or `suspend` for background tasks

---

## License

This project is provided as-is for educational and personal use.

---

## Author

**Naman Mawandia**  
[GitHub: @namanmawandia](https://github.com/namanmawandia)

---

**Built with ❤️ using Kotlin, Jetpack Compose, and modern Android best practices.**
