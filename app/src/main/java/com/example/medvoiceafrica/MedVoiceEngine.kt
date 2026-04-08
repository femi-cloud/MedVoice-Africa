package com.example.medvoiceafrica

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MedVoiceEngine(private val context: Context) {

    private var llmInference: LlmInference? = null
    private val tag = "MedVoiceEngine"
    var isReady = false
        private set

    private val systemInstruction = """
        You are MedVoice Africa, a specialized medical assistant...
        (PASTE YOUR FULL PROMPT HERE)
    """.trimIndent()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            // 1. Define the exact target file path
            val targetFile = File(context.filesDir, "decoder_model_merged_q4.onnx")

            // 2. Direct Copy: We go straight for the file in the assets/onnx folder
            // IMPORTANT: The path must match your assets folder structure EXACTLY
            FileUtils.copySingleFile(context, "onnx/decoder_model_merged_q4.onnx", targetFile)

            // 3. Check if the copy actually worked before trying to load it
            if (!targetFile.exists()) {
                Log.e(tag, "CRITICAL ERROR: File was not copied! Check your assets folder structure.")
                return@withContext
            }

            // 4. Initialize the engine
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(targetFile.absolutePath)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isReady = true
            Log.d(tag, "Engine initialized successfully!")
        } catch (e: Exception) {
            Log.e(tag, "Initialization failed: ${e.message}")
            isReady = false
        }
    }

    suspend fun generateResponse(userInput: String): String = withContext(Dispatchers.IO) {
        if (!isReady) return@withContext "Error: Engine not ready."
        val fullPrompt = "$systemInstruction\nUser: $userInput\nAssistant:"
        try {
            llmInference?.generateResponse(fullPrompt) ?: "Error: Model not initialized."
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun close() {
        llmInference?.close()
    }
}