package com.example.medvoiceafrica

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class GemmaEngine(private val context: Context) {

    private var apiKey: String = ""
    private val prefs: SharedPreferences =
        context.getSharedPreferences("medvoice_prefs", Context.MODE_PRIVATE)
    private val chatHistory = mutableListOf<JSONObject>()
    private val MAX_HISTORY_SIZE = 20

    // RAG Engine — injecte les protocoles OMS pertinents
    private val ragEngine = RagEngine(context)

    companion object {
        private const val TAG = "GemmaEngine"
        private const val MODEL = "gemma-4-26b-a4b-it"
        private const val BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

        fun getDeviceLanguage(): String = when (Locale.getDefault().language) {
            "fr" -> "fr"
            "en" -> "en"
            else -> "fr"
        }

        fun buildSystemPrompt(lang: String, ragContext: String = ""): String {
            val base = if (lang == "fr") """
Tu es MedVoice Africa, un assistant médical de confiance pour les agents de santé en Afrique rurale, principalement au Bénin.

Règles de conversation :
- Première salutation : présente-toi brièvement, invite à décrire un patient.
- Conversation en cours : réponds naturellement SANS te représenter.
- Question de suivi : tiens compte de tout l'historique.
- Image fournie : analyse médicalement (lésion, yeux, emballage médicament).

Pour des symptômes médicaux : 2-3 paragraphes fluides. Max 200 mots.
Termine TOUJOURS par ce tag exact : [TRIAGE:ROUGE] ou [TRIAGE:JAUNE] ou [TRIAGE:VERT] ou [TRIAGE:INFO].

Réponds directement, sans montrer ton raisonnement interne.
            """.trimIndent() else """
You are MedVoice Africa, a trusted medical assistant for health workers in rural Africa, mainly in Benin.

Conversation rules:
- First greeting: briefly introduce yourself, invite to describe a patient.
- Ongoing: reply naturally WITHOUT re-introducing yourself.
- Follow-up: use full conversation history.
- Image provided: analyze medically (lesion, eyes, medicine packaging).

For medical symptoms: 2-3 clear flowing paragraphs. Max 200 words.
ALWAYS end with: [TRIAGE:ROUGE] or [TRIAGE:JAUNE] or [TRIAGE:VERT] or [TRIAGE:INFO].

Reply directly, never show internal reasoning.
            """.trimIndent()

            // Injecter le contexte RAG si disponible
            return if (ragContext.isNotBlank()) "$base\n\n$ragContext" else base
        }

        fun parseTriageLevelFromTag(response: String): TriageLevel {
            val match = Regex("\\[TRIAGE:(\\w+)\\]").find(response)
            return when (match?.groupValues?.get(1)?.uppercase()) {
                "ROUGE" -> TriageLevel.ROUGE
                "JAUNE" -> TriageLevel.JAUNE
                "VERT"  -> TriageLevel.VERT
                else -> when {
                    response.contains("ROUGE", ignoreCase = true) ||
                            response.contains("🔴") -> TriageLevel.ROUGE
                    response.contains("JAUNE", ignoreCase = true) ||
                            response.contains("🟡") -> TriageLevel.JAUNE
                    response.contains("VERT", ignoreCase = true) ||
                            response.contains("🟢") -> TriageLevel.VERT
                    else -> TriageLevel.UNKNOWN
                }
            }
        }

        fun cleanResponse(raw: String): String {
            var text = raw
            text = text.replace(Regex("<thought>.*?</thought>", RegexOption.DOT_MATCHES_ALL), "")
            text = text.replace(Regex("<thinking>.*?</thinking>", RegexOption.DOT_MATCHES_ALL), "")
            text = text.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            text = text.replace(Regex("<\\|channel\\|>.*?<channel\\|>", RegexOption.DOT_MATCHES_ALL), "")
            text = text.replace(Regex("\\[TRIAGE:\\w+\\]"), "").trim()
            val bad = listOf(
                "user says", "user input", "role:", "language:", "instruction",
                "protocol", "tone:", "goal:", "context:", "greeting:",
                "constraint:", "capabilities:", "self-correction", "thinking:", "reasoning:"
            )
            text = text.lines().filterNot { line ->
                val t = line.trim()
                (t.startsWith("*") || t.startsWith("-")) &&
                        bad.any { t.contains(it, ignoreCase = true) }
            }.joinToString("\n").trim()
            return text.trimStart('\n', '\r').trim()
        }
    }

    fun initialize(): Result<Unit> {
        return try {
            apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank()) {
                return Result.failure(IllegalStateException("Clé API manquante"))
            }
            loadHistory()
            Log.d(TAG, "GemmaEngine ready (${chatHistory.size} msgs in history)")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Initialiser la DB RAG au démarrage
    suspend fun initRag() {
        ragEngine.populate()
        Log.d(TAG, "RAG ready: ${ragEngine.isReady()}")
    }

    private fun saveHistory() {
        prefs.edit().putString("chat_history", JSONArray(chatHistory).toString()).apply()
    }

    private fun loadHistory() {
        val saved = prefs.getString("chat_history", null) ?: return
        try {
            val array = JSONArray(saved)
            chatHistory.clear()
            for (i in 0 until array.length()) chatHistory.add(array.getJSONObject(i))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load history: ${e.message}")
        }
    }

    private fun enforceMemoryLimit() {
        while (chatHistory.size > MAX_HISTORY_SIZE) chatHistory.removeAt(0)
    }

    fun clearHistory() {
        chatHistory.clear()
        saveHistory()
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun runInference(
        userMessage: String,
        imageBitmap: Bitmap? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val lang = getDeviceLanguage()

        // ── 1. Recherche RAG — trouver les protocoles OMS pertinents ──
        val relevantProtocols = ragEngine.findRelevant(userMessage)
        val ragContext = RagEngine.buildContext(relevantProtocols, lang)
        if (relevantProtocols.isNotEmpty()) {
            Log.d(TAG, "RAG: injecting ${relevantProtocols.size} protocol(s): ${relevantProtocols.map { it.title }}")
        }

        // ── 2. Construire le message utilisateur ──────────────────────
        val partsArray = JSONArray().apply {
            put(JSONObject().put("text", userMessage))
            if (imageBitmap != null) {
                put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", encodeBitmapToBase64(imageBitmap))
                    })
                })
            }
        }

        val userTurn = JSONObject().apply {
            put("role", "user")
            put("parts", partsArray)
        }
        chatHistory.add(userTurn)
        enforceMemoryLimit()

        return@withContext try {
            val url = URL("$BASE_URL?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000

            // ── 3. Injecter le contexte RAG dans le system prompt ─────
            val body = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        // Le system prompt contient maintenant les protocoles OMS pertinents
                        put(JSONObject().put("text", buildSystemPrompt(lang, ragContext)))
                    })
                })
                put("contents", JSONArray(chatHistory))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("topK", 40)
                    put("maxOutputTokens", 2048)
                })
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val responseCode = conn.responseCode
            val stream = if (responseCode == 200) conn.inputStream else conn.errorStream
            val rawResponse = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (responseCode != 200) {
                Log.e(TAG, "API error $responseCode: $rawResponse")
                chatHistory.removeLastOrNull()
                return@withContext Result.failure(Exception("Erreur API $responseCode"))
            }

            val json = JSONObject(rawResponse)
            val parts = json.getJSONArray("candidates")
                .getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts")

            val textBuilder = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text") && !part.optBoolean("thought", false)) {
                    textBuilder.append(part.getString("text"))
                }
            }

            val rawText = textBuilder.toString().ifBlank {
                if (lang == "fr") "Je n'ai pas pu générer une réponse."
                else "Could not generate a response."
            }
            val cleaned = cleanResponse(rawText)

            val modelTurn = JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().apply { put(JSONObject().put("text", cleaned)) })
            }
            chatHistory.add(modelTurn)
            enforceMemoryLimit()
            saveHistory()

            Log.d(TAG, "Response OK (RAG:${relevantProtocols.size}, history:${chatHistory.size}): ${cleaned.take(80)}")
            Result.success(rawText) // rawText conserve le tag [TRIAGE:X] pour le parsing
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.javaClass.simpleName}: ${e.message}")
            chatHistory.removeLastOrNull()
            Result.failure(Exception("${e.javaClass.simpleName}: ${e.message}"))
        }
    }

    fun parseTriageLevel(response: String) = parseTriageLevelFromTag(response)
    fun isInitialized() = apiKey.isNotBlank()
    fun close() { apiKey = "" }
}

enum class TriageLevel { ROUGE, JAUNE, VERT, UNKNOWN }