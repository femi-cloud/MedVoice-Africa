# 🩺 MedVoice Africa

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

> **An offline-first, multimodal AI medical assistant powered by Gemma 4, designed to bridge the healthcare gap in rural areas.**

## 🌍 The Problem
In rural areas, the "Medical Desert" creates a fatal gap between symptom onset and professional care. Lack of connectivity and trained specialists leads to dangerous delays and incorrect treatments.

## 💡 The Solution
**MedVoice Africa** is an edge-AI agent that works anywhere, even in airplane mode. It provides:
- **Multimodal Triage:** Visual analysis of skin/eyes + Voice analysis of symptoms.
- **Pharmacy Guard:** Scan medicine packaging for authenticity and check for deadly drug interactions.
- **Offline Intelligence:** Full access to WHO protocols via local RAG (Retrieval-Augmented Generation).
- **Linguistic Inclusion:** Voice interaction in **French, English, and Fon**.

## 🚀 Tech Stack
- **LLM:** Gemma 4 (2B/4B) optimized with **LiteRT (Google AI Edge)**.
- **Multimodality:** MediaPipe / ONNX Runtime for Vision & Audio.
- **STT/TTS:** Whisper.cpp & Piper (Local-first).
- **Knowledge Base:** SQLite-vec for offline RAG.
- **Platform:** Android (Kotlin & Jetpack Compose).

## 🛠️ Installation & Setup
> **Note:** Due to file size limits, the model weights are not hosted on GitHub.

1. Clone this repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/MedVoice-Africa.git
