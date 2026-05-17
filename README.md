# 🩺 MedVoice Africa

<div align="center">

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/Platform-Android%2026+-brightgreen.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![Model](https://img.shields.io/badge/LLM-Gemma%204%20-orange.svg)](https://ai.google.dev/gemma)
[![Status](https://img.shields.io/badge/Status-Hackathon%20Build-yellow.svg)]()
[![Model](https://img.shields.io/badge/HuggingFace-Fysna/medvoice_final_v2-yellow)](https://huggingface.co/Fysna/medvoice_final_v2)


> **An offline-first, multimodal AI medical assistant powered by Gemma 4, built to bridge the deadly gap between symptom onset and professional care in rural Africa.**

[Features](#What-it-does) · [Architecture](#%EF%B8%8F-architecture) · [Setup](#%EF%B8%8F-installation--setup)

</div>

---

## 🌍 The Problem

In rural Sub-Saharan Africa, a **"Medical Desert"** creates a fatal gap:

- **1 doctor per 10,000+ people** in remote areas of Benin
- **No connectivity** — 3G/4G coverage drops to near zero outside urban centers
- **Community health workers** act as the first — and often only — line of defense, with minimal training and no decision-support tools
- **Language barrier** — French-only medical tools exclude the majority of rural populations who speak Fon or other local languages

The result: preventable deaths from treatable conditions like malaria, diarrhea, and fever — simply because the right protocol was unavailable at the right moment.

---

## 💡 The Solution

**MedVoice Africa** is an edge-AI agent that runs entirely on a mid-range Android phone, **with or without internet**. It puts a trained medical reasoning engine in the hands of every person within the West African community and every community health worker, anywhere.

```
No WiFi. No server. No problem.
> ⚠️ **Demo note:** The LLM offline module requires 3GB+ free RAM.
> In this demo build, Gemma 4 via Gemini API is used for natural language generation.
> All medical logic (triage, dosage, WHO protocols) runs fully offline.
```

### What it does

| Module | Description |
|---|---|
| 🔴 **Smart Triage** | Automatically classifies symptoms as ROUGE / JAUNE / VERT using WHO protocols |
| 💊 **Dosage Calculator** | Computes weight-based pediatric & adult dosages from 100+ local drug protocols |
| ⚠️ **Pharmacy Guard** | Scans medicine boxes via camera (OCR + barcode) and detects dangerous drug interactions |
| 🌿 **Offline RAG** | Retrieves WHO emergency protocols locally — no internet required |
| 🗣️ **Multilingual Voice** | Understands and responds in **French, English, and Fon** (Beninese language) |
| 📲 **Case Transfer** | Sends structured case summaries to referring doctors via SMS or WhatsApp |
| 📊 **Epidemio Log** | Tracks local consultation patterns for weekly epidemiological reporting |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    MedVoice Africa                       │
│                  Android (Kotlin + Compose)              │
└────────────┬────────────────────────┬───────────────────┘
             │                        │
    ┌────────▼────────┐     ┌─────────▼──────────┐
    │  GemmaEngine    │     │   LlamaEngine       │
    │  (Gemini API)   │     │   (GGUF local)      │
    │  Online path    │     │   Offline path      │
    └────────┬────────┘     └─────────┬──────────┘
             │                        │
             └──────────┬─────────────┘
                        │
           ┌────────────▼────────────────┐
           │       MedOrchestrator        │
           │  Parses [[DATA]] JSON tags   │
           │  Routes to DosageEngine      │
           └────────────┬────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
┌───────▼──────┐ ┌──────▼──────┐ ┌─────▼──────────────┐
│  RagEngine   │ │DosageCalling│ │DrugInteraction      │
│  (SQLite-vec)│ │(100+ drugs) │ │Engine (30+ rules)   │
└──────────────┘ └─────────────┘ └────────────────────┘
```

### Offline-first strategy

```
User message
     │
     ├─ Fon emergency? → Instant JSON response (zero latency)
     │
     ├─ Online?  → Gemini API  ──┐
     │                           ├─ MedOrchestrator → DosageCard
     └─ Offline? → LlamaEngine ──┘
                       │
                  RAM < threshold?
                       └─ RAG-only fallback (WHO protocols)
```

---

## 🚀 Tech Stack

| Layer | Technology |
|---|---|
| **UI** | Jetpack Compose, Material 3, Dark/Light theme |
| **LLM (online)** | Gemma 4 via Gemini API (`gemma-4-26b-a4b-it`) |
| **LLM (offline)** | GGUF model via `llama-kotlin-android` JNI bindings |
| **Vision** | CameraX + ML Kit (OCR + Barcode) |
| **Knowledge Base** | Room DB + SQLite-vec for offline RAG |
| **TTS / STT** | Android native TTS + `RECORD_AUDIO` |
| **Networking** | Raw `HttpURLConnection` (no Retrofit — minimal dependencies) |
| **DI / State** | Compose `remember` + coroutines (no ViewModel overhead) |
| **Language** | Kotlin 2.0, min SDK 26, target SDK 35 |

---

## 📱 Screenshots

> *(Add screenshots here)*

| Triage Chat | Dosage Card | Pharma Scan | Transfer SMS |
|:-----------:|:-----------:|:-----------:|:------------:|
| `ROUGE/JAUNE/VERT` visual alerts | Weight-based pediatric dosage | OCR + barcode drug interaction check | Auto-filled SMS to referring doctor |

---

## 🛠️ Installation & Setup

### Prerequisites

- Android Studio Hedgehog or later
- Android device or emulator with **API 26+**
- A [Google AI Studio](https://aistudio.google.com/) account for the Gemini API key

### 1. Clone the repository

```bash
git clone https://github.com/femi-cloud/MedVoice-Africa.git
cd MedVoice-Africa
```

### 2. Configure your API key

Create or edit `local.properties` at the project root:

```properties
GEMINI_API_KEY=your_api_key_here
```

### 3. (Optional) Enable local offline model

For full offline AI capability, download the GGUF model and place it in your device's Downloads folder:

```
/storage/emulated/0/Download/medvoice_final_v2.gguf
```

The app detects RAM automatically:
-  **≥ 8 GB RAM detected**  → Local GGUF model loads and runs
-  **< 8 GB RAM** → (e.g. Logicom ONIX 2GB) → Automatic fallback to Gemini API or RAG-only mode

### 4. Build and run

```bash
./gradlew assembleDebug
```

Or open the project in Android Studio and press **Run**.

---
## 📲 Quick Install (Recommended)

No Android Studio needed — just install the APK directly on your Android device.

1. Download the latest APK from the [Releases page](https://github.com/femi-cloud/MedVoice-Africa/releases)
2. On your Android device, enable **"Install from unknown sources"**:
   - Settings → Security → Unknown Sources → ✅
   - *(or Settings → Apps → Special app access → Install unknown apps)*
3. Open the downloaded APK and tap **Install**
4. Launch **MedVoice Africa** 🎉

> ⚠️ Requires Android 8.0+ (API 26)

### (Optional) Enable offline AI model

For full offline capability without internet, download the GGUF model:

1. Download [`medvoice_final_v2.gguf`](https://huggingface.co/Fysna/medvoice_final_v2) (~3.4 GB) from HuggingFace
2. Place it in `/storage/emulated/0/Download/`
3. Restart the app — it will detect the model automatically

> Without the model, the app works perfectly via **Gemini API** (internet required)

## 🧠 Key Design Decisions

**Why no ViewModel?**
State is managed directly in Compose with `remember` + coroutines. For a single-screen medical app with real-time audio and camera, this avoids overhead and keeps the lifecycle simple.

**Why raw `HttpURLConnection` instead of Retrofit?**
Minimal dependencies = smaller APK = faster installs in low-bandwidth zones.

**Why [[DATA]] JSON tags instead of structured outputs?**
Gemma 4 does not yet support native function calling in the Android API. Embedding JSON in the response and parsing it locally gives us reliable structured extraction without changing the model.

**Why Fon as a first-class language?**
Fon is spoken by ~2 million people in Benin. Emergency medical instructions in a language the patient and caregiver actually understand can be the difference between life and death.

---

## 📂 Project Structure

```
app/src/main/
├── java/com/example/medvoiceafrica/
│   ├── MainActivity.kt          # Main UI, chat, drawer, triage cards
│   ├── GemmaEngine.kt           # Gemini API calls + system prompt
│   ├── LlamaEngine.kt           # Local GGUF inference via JNI
│   ├── MedOrchestrator.kt       # [[DATA]] parser + dosage router
│   ├── RagEngine.kt             # Offline WHO protocol retrieval
│   ├── DrugInteractionEngine.kt # 30+ interaction rules (Lvl 1) + LLM (Lvl 2)
│   ├── DosageFunctionCalling.kt # 100+ drug protocols + weight-based calc
│   ├── MedProtocols.kt          # Drug database with findProtocol() lookup
│   ├── PharmaScanScreen.kt      # Camera + ML Kit OCR/barcode scanner
│   ├── SmsTransfer.kt           # SMS + WhatsApp case transfer
│   ├── MedicationsDialog.kt     # Current medications management UI
│   ├── StatsScreen.kt           # Epidemiological log + bar charts
│   ├── SettingsActivity.kt      # Settings (API key, doctor phone, CSPS name)
│   ├── NetworkMonitor.kt        # Real-time connectivity flow
│   ├── AppDatabase.kt           # Room DB (sessions, messages, ConsultationLog)
│   └── OmsProtocolDatabase.kt   # Embedded WHO protocols (Benin)
└── assets/
    └── fon.json                 # Emergency phrases in Fon language
```

---

## 🗺️ Roadmap

- [x] Gemini API integration with RAG
- [x] Offline GGUF model via llama.cpp
- [x] Triage system (ROUGE / JAUNE / VERT)
- [x] Weight-based dosage calculator (100+ drugs)
- [x] Drug interaction detection (30+ rules)
- [x] Pharma scan (OCR + barcode via ML Kit)
- [x] Fon language emergency responses
- [x] SMS + WhatsApp case transfer
- [x] Session history + epidemiological log
- [x] Whisper.cpp local STT integration
- [ ] Piper local TTS (Fon voice)
- [ ] Skin/eye condition visual analysis (MediaPipe)
      
---

## ⚙️ Permissions Required

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

---

## 🤝 Contributing

Contributions are welcome, especially:
- Additional drug protocols (African formularies)
- Fon language improvements
- Offline TTS/STT integration

Please open an issue before submitting a large PR.

---

## ⚖️ License

```
Copyright 2026 MedVoice Africa Contributors
Licensed under the Apache License, Version 2.0

```

---

## ⚠️ Medical Disclaimer

MedVoice Africa is a **decision-support tool** for trained community health workers. It is **not** a replacement for professional medical diagnosis or treatment. All dosage recommendations and triage decisions must be validated by a qualified healthcare professional before being acted upon.

---

<div align="center">

**For the Kaggle x Google Deepmind — Gemma 4 Good Hackathon**

*Made with 💗 for the African Community.*


</div>
