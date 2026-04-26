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
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale

class GemmaEngine(private val context: Context) {

    private var apiKey: String = ""
    private val prefs: SharedPreferences = context.getSharedPreferences("medvoice_prefs", Context.MODE_PRIVATE)
    private val chatHistory = mutableListOf<JSONObject>()
    private val MAX_HISTORY_SIZE = 20
    private val ragEngine = RagEngine(context)

    companion object {
        private const val TAG = "GemmaEngine"
        private const val MODEL = "gemma-4-e2b-it"

        // gemma-4-26b-a4b-it
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

        fun getDeviceLanguage(): String = when (Locale.getDefault().language) { "fr" -> "fr"; "en" -> "en"; else -> "fr" }

        fun buildSystemPrompt(lang: String, isPublicMode: Boolean = true): String {
            val baseRule = "Tu DOIS inclure un tag de triage ([TRIAGE:ROUGE], [TRIAGE:JAUNE] ou [TRIAGE:VERT]) UNIQUEMENT si l'utilisateur décrit des symptômes ou pose une question médicale. Si l'utilisateur dit bonjour, merci, ou pose une question générale, réponds normalement et poliment sans utiliser de tag de triage."

            return if (isPublicMode) {
                // Mode Grand Public (Bobologie autorisée, pas de jargon)
                if (lang == "fr") """
                Tu es MedVoice Africa, un conseiller de santé bienveillant pour le grand public.
                RÈGLES STRICTES :
                1. $baseRule
                2. Tu es autorisé à suggérer UNIQUEMENT des traitements de base en vente libre (ex: Paracétamol pour la fièvre, SRO pour la diarrhée) sans préciser le dosage exact. 
                3. Tu ne dois JAMAIS suggérer d'antibiotiques, d'antipaludiques, ou de traitements lourds.
                4. Conseille TOUJOURS d'aller voir un agent de santé si c'est grave.
                5. Utilise un langage simple, pas de jargon médical.
            """.trimIndent()
                else """
                You are MedVoice Africa, a caring health advisor for the general public...
            """.trimIndent()
            } else {
                // Mode Professionnel (Agent de santé)
                if (lang == "fr") """
                Tu es MedVoice Africa, un assistant médical d'urgence strict pour les professionnels. 
                RÈGLES STRICTES :
                1. $baseRule
                2. Détaille les protocoles de l'OMS de manière clinique et concise.
                3. Donne les dosages précis des médicaments pour guider l'agent de santé.
            """.trimIndent()
                else """
                You are MedVoice Africa, a strict emergency medical assistant for professionals...
            """.trimIndent()
            }
        }

        fun parseTriageLevelFromTag(response: String): TriageLevel {
            val match = Regex("\\[TRIAGE:(\\w+)\\]").find(response)
            return when (match?.groupValues?.get(1)?.uppercase()) {
                "ROUGE" -> TriageLevel.ROUGE; "JAUNE" -> TriageLevel.JAUNE; "VERT" -> TriageLevel.VERT
                else -> when {
                    response.contains("ROUGE", ignoreCase = true) || response.contains("🔴") -> TriageLevel.ROUGE
                    response.contains("JAUNE", ignoreCase = true) || response.contains("🟡") -> TriageLevel.JAUNE
                    response.contains("VERT", ignoreCase = true)  || response.contains("🟢") -> TriageLevel.VERT
                    else -> TriageLevel.UNKNOWN
                }
            }
        }

        fun cleanResponse(raw: String): String {
            var text = raw
            text = text.replace(Regex("<thought>.*?</thought>", RegexOption.DOT_MATCHES_ALL), "")
            text = text.replace(Regex("<thinking>.*?</thinking>", RegexOption.DOT_MATCHES_ALL), "")
            text = text.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            text = text.replace(Regex("\\[TRIAGE:\\w+\\]"), "").trim()
            val badPrefixes = listOf("user says", "user input", "role:", "language:", "tone:", "goal:",
                "context:", "greeting:", "constraint:", "thinking:", "reasoning:", "self-correction")
            text = text.lines().filterNot { line ->
                val t = line.trim()
                (t.startsWith("*") || t.startsWith("-")) && badPrefixes.any { t.contains(it, ignoreCase = true) }
            }.joinToString("\n").trim()
            return text.trimStart('\n', '\r').trim()
        }
    }

    fun initialize(): Result<Unit> {
        return try {
            apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank()) {
                Result.failure(IllegalStateException("Clé API manquante"))
            } else {
                loadHistory()
                Log.d(TAG, "GemmaEngine ready (${chatHistory.size} msgs)")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun initRag() { ragEngine.populate(); Log.d(TAG, "RAG ready: ${ragEngine.isReady()}") }

    private fun saveHistory() { prefs.edit().putString("chat_history", JSONArray(chatHistory).toString()).apply() }
    private fun loadHistory() {
        val saved = prefs.getString("chat_history", null) ?: return
        try { val a = JSONArray(saved); chatHistory.clear(); for (i in 0 until a.length()) chatHistory.add(a.getJSONObject(i)) }
        catch (e: Exception) { Log.e(TAG, "Failed to load history: ${e.message}") }
    }
    private fun enforceMemoryLimit() { while (chatHistory.size > MAX_HISTORY_SIZE) chatHistory.removeAt(0) }
    fun clearHistory() { chatHistory.clear(); saveHistory() }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // Fast connectivity check — 3s socket timeout
    private suspend fun isOnline(): Boolean = withContext(Dispatchers.IO) {
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress("generativelanguage.googleapis.com", 443), 3000)
            sock.close(); true
        } catch (_: Exception) { false }
    }

    suspend fun runInference(userMessage: String, imageBitmap: Bitmap? = null): Result<String> =
        withContext(Dispatchers.IO) {
            val lang = getDeviceLanguage()

            // RAG: find relevant protocols BEFORE trying network (needed for offline too)
            val relevantProtocols = ragEngine.findRelevant(userMessage)
            val ragContext = RagEngine.buildContext(relevantProtocols, lang)
            if (relevantProtocols.isNotEmpty())
                Log.d(TAG, "RAG: injecting ${relevantProtocols.size} protocol(s): ${relevantProtocols.map { it.title }}")

            // Build user message parts
            val partsArray = JSONArray().apply {
                put(JSONObject().put("text", userMessage))
                if (imageBitmap != null) put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", "image/jpeg"); put("data", encodeBitmapToBase64(imageBitmap))
                    })
                })
            }
            chatHistory.add(JSONObject().apply { put("role", "user"); put("parts", partsArray) })
            enforceMemoryLimit()

            // Fast offline check
            if (!isOnline()) {
                chatHistory.removeLastOrNull()
                // Gemini recommendation: frame as "Safety Mode" not just an error
                val offlineMsg = if (lang == "fr")
                    "⚠️ **Mode Sécurité Hors-ligne**\n\nL'assistant IA est inaccessible. D'après vos mots-clés, voici les protocoles d'urgence correspondants issus de notre base de données locale. Lisez attentivement la section correspondant à votre niveau de gravité :\n\n${
                        if (ragContext.isNotBlank()) ragContext
                        else "Décrivez un symptôme précis (ex: fièvre, toux, diarrhée) pour accéder aux protocoles OMS hors-ligne."
                    }"
                else
                    "⚠️ **Offline Safety Mode**\n\nAI assistant unreachable. Based on your keywords, here are the emergency protocols from our local database. Read carefully the section matching your severity level:\n\n${
                        if (ragContext.isNotBlank()) ragContext
                        else "Describe a specific symptom (e.g. fever, cough, diarrhea) to access offline WHO protocols."
                    }"
                return@withContext Result.success("$offlineMsg\n[TRIAGE:INFO]")
            }

            return@withContext try {
                val url = URL("$BASE_URL?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 8_000
                conn.readTimeout = 60_000

                val body = JSONObject().apply {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", buildSystemPrompt(lang, true)))
                        })
                    })
                    put("contents", JSONArray(chatHistory))
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.65); put("topK", 40); put("maxOutputTokens", 2048)
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
                val parts = json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                val textBuilder = StringBuilder()
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text") && !part.optBoolean("thought", false)) textBuilder.append(part.getString("text"))
                }
                val rawText = textBuilder.toString().ifBlank {
                    if (lang == "fr") "Je n'ai pas pu générer une réponse." else "Could not generate a response."
                }
                val cleaned = cleanResponse(rawText)
                chatHistory.add(JSONObject().apply {
                    put("role", "model"); put("parts", JSONArray().apply { put(JSONObject().put("text", cleaned)) })
                })
                enforceMemoryLimit(); saveHistory()
                Log.d(TAG, "✅ Response (RAG:${relevantProtocols.size}): ${cleaned.take(80)}")
                Result.success(rawText)

            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.javaClass.simpleName}: ${e.message}")
                chatHistory.removeLastOrNull()
                if (e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException) {
                    val msg = if (lang == "fr")
                        "⚠️ **Mode Sécurité Hors-ligne**\n\nL'assistant IA est inaccessible.${if (ragContext.isNotBlank()) "\n\n$ragContext" else "\n\nVeuillez vous connecter à internet."}"
                    else
                        "⚠️ **Offline Safety Mode**\n\nAI assistant unreachable.${if (ragContext.isNotBlank()) "\n\n$ragContext" else "\n\nPlease connect to the internet."}"
                    Result.success("$msg\n[TRIAGE:INFO]")
                } else Result.failure(Exception("${e.javaClass.simpleName}: ${e.message}"))
            }
        }

    fun parseTriageLevel(response: String) = parseTriageLevelFromTag(response)
    fun isInitialized() = apiKey.isNotBlank()
    fun close() { apiKey = "" }
}

enum class TriageLevel { ROUGE, JAUNE, VERT, UNKNOWN }