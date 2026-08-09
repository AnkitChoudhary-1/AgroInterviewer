# AgroInterviewer

An AI-powered Android interview coach that conducts real technical interviews using voice. You speak, the AI listens, asks dynamic technical questions, scores your answers, and gives you a full breakdown at the end.

Built for the **Android Engineers × Agora Conversational AI Hackathon**.

---

## What It Does

- **Live Voice Session**: Connects to a real-time voice channel powered by Agora RTC.
- **Dynamic AI Question Generation**: Fresh, non-repeating technical questions generated per session via Groq (`llama-3.3-70b-versatile`) or OpenAI (`gpt-4o-mini`).
- **Zero-Latency Start & Fallback**: Starts Question 1 instantly using local question banks while fetching fresh AI-generated questions in parallel.
- **Chat & Voice Options**: Speak your answers directly or toggle the collapsible text input field via a bottom bar chat button.
- **Evaluation & Feedback**: Transcribes and evaluates candidate responses on technical accuracy (1–10 scale) with constructive feedback.
- **Network Resilience & Auto-Reconnect**: Handles network drops gracefully and reconnects automatically without losing progress.

---

## Technology Stack

### **Android App**
- **Architecture**: Kotlin, Jetpack Compose (Just a Prototype Ui), MVVM + Repository pattern.
- **Dependency Injection**: Hilt.
- **Networking**: Retrofit 2 + OkHttp 4 with custom `X-API-Key` authentication interceptor.
- **Database & Storage**: Room Database for offline session persistence and Android DataStore.
- **Real-Time Voice**: Agora RTC Engine (Broadcaster profile, auto audio capture, volume indication).
- **Speech Engine**: Android SpeechRecognizer & TextToSpeech (TTS) integration with TTS utterance tracking.

### **Backend Server** (`server/`)
- **Runtime**: Node.js + Express.js listening on port `3000` (bound to `0.0.0.0` for physical device access over Wi-Fi).
- **Agora Integration**: Dynamic RTC Token generation using `agora-token` package.
- **AI Providers**: Groq API (`llama-3.3-70b-versatile`) as primary high-speed inference engine, with OpenAI fallback.
- **Resilient AI Pipelines**: Strict JSON mode enforcement (`response_format: { type: 'json_object' }`), custom JSON sanitizer, and local fallback mode when Agora Cloud Agent APIs return 404.
- **API Security**: Shared secret header validation (`X-API-Key`) across all non-health endpoints.

---

## Project Structure

```
AgroInterviewer/
├── app/                        # Android app
│   └── src/main/java/com/agro/interviewer/
│       ├── data/               # Repositories, Room DB, Retrofit DTOs & Services
│       ├── di/                 # Hilt Modules (AppModule)
│       ├── domain/             # Domain Models (Question, Session, VoiceState)
│       ├── presentation/       # Compose Screens & ViewModels
│       │   ├── home/           # Dashboard & past session history
│       │   ├── config/         # Topic & difficulty setup screen
│       │   ├── voice/          # Live interview & voice UI screen
│       │   └── results/        # Score breakdown screen
│       └── utils/              # SpeechManager, NetworkMonitor, TranscriptAnalyzer
│
└── server/                     # Express.js Node.js Backend
    ├── src/index.js            # Main server file (Agora Token, Questions, Evaluate)
    ├── package.json
    └── .env.example
```

---

## Getting Started

### Prerequisites

- **Android Studio** (Hedgehog or newer) & **JDK 21**
- **Node.js** 18+
- **Agora Developer Account**: App ID & App Certificate from [Console.agora.io](https://console.agora.io)
- **Groq API Key** (Free) or **OpenAI API Key**

---

### 1. Server Setup

Create a `server/.env` file inside the `server/` directory:

```env
PORT=3000
INTERNAL_API_KEY=agro_secret_key_2026

AGORA_APP_ID=your_agora_app_id
AGORA_APP_CERTIFICATE=your_agora_app_certificate
AGORA_CUSTOMER_ID=your_customer_id
AGORA_CUSTOMER_SECRET=your_customer_secret

GROQ_API_KEY=your_groq_api_key
OPENAI_API_KEY=your_openai_api_key
```

Run the Node.js server:

```bash
cd server
npm install
npm start
```

*The server will start on port `3000` listening on `0.0.0.0`.*

---

### 2. Configure Android App

Update `local.properties` in the project root:

```properties
AGORA_APP_ID=your_agora_app_id
INTERNAL_API_KEY=agro_secret_key_2026

# For Android Emulator:
# BACKEND_BASE_URL=http://10.0.2.2:3000/

# For Physical Phone on local Wi-Fi:
BACKEND_BASE_URL=http://172.27.130.227:3000/
```

---

### 3. Build & Run

- Open the project in **Android Studio**.
- Select your target device (Physical Phone or Android Emulator).
- Build and run (`assembleDebug` or `assembleRelease`).

---

## API Endpoints

All requests (except `/api/health`) require `X-API-Key: agro_secret_key_2026` header.

| Method | Endpoint | Description |
|--------|----------|-------------|
| **GET** | `/api/health` | Health check & AI provider status |
| **POST** | `/api/agora/token` | Generates Agora RTC token for channel |
| **POST** | `/api/agent/start` | Starts AI agent (with local fallback mode) |
| **POST** | `/api/agent/stop` | Stops AI agent |
| **POST** | `/api/questions/generate` | Generates technical questions via Groq/OpenAI |
| **POST** | `/api/answers/evaluate` | Evaluates answer correctness & feedback |
| **POST** | `/api/ai/ask` | Mid-interview candidate query helper |

---

## License

MIT License. Built for the Android Engineers Agora Hackathon.
