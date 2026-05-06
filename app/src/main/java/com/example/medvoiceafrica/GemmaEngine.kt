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
        private const val MODEL = "gemma-4-26b-a4b-it"

        // gemma-4-26b-a4b-it
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

        fun getDeviceLanguage(): String = when (Locale.getDefault().language) { "fr" -> "fr"; "en" -> "en"; else -> "fr" }

        fun buildSystemPrompt(lang: String, isPublicMode: Boolean = true): String {
            val jsonStructure = """
    {
      "drug": "nom_du_medicament",
      "weight_kg": 0.0,
      "age_years": 0,
      "triage": "ROUGE|JAUNE|VERT",
      "interaction_detected": false
    }
    """.trimIndent()

            // ── NOUVELLES RÈGLES DE SÉCURITÉ ──
            val safetyRules = if (lang == "fr") """
4. SÉCURITÉ INTERACTIONS : Vérifie systématiquement les traitements en cours mentionnés par l'utilisateur. 
   - Si un risque d'interaction existe (ex: Aspirine + Anticoagulant, ou deux médicaments de même classe), passe impérativement en [TRIAGE:ROUGE], explique le danger en détail dans un paragraphe clair, et mets "interaction_detected": true dans le JSON.
5. FORMAT : Réponds toujours par un paragraphe bien structuré et fluide. Évite les listes à puces si possible.
6. RÈGLE D'OR DU POIDS (Adulte & Enfant) : 
   - Si l'utilisateur demande une dose mais que le poids (weight_kg) est absent :
     a) NE GÉNÈRE PAS le bloc JSON [[DATA]].
     b) Réponds par un paragraphe demandant poliment le poids pour garantir la sécurité.
""".trimIndent() else """
4. INTERACTION SAFETY: Systematically check the current treatments mentioned by the user.
   - If an interaction risk exists, use [TRIAGE:ROUGE], explain the danger in a clear paragraph, and set "interaction_detected": true in the JSON.
5. FORMAT: Always respond with a well-structured, fluid paragraph. Avoid bullet points if possible.
6. WEIGHT RULE: If a dose is requested but weight is missing:
   a) DO NOT generate the [[DATA]] block.
   b) Respond with a paragraph politely asking for the weight.
""".trimIndent()

            val extractionRules = if (lang == "fr") """
    - NE CALCULE JAMAIS DE DOSAGE TOI-MÊME. Ton rôle est d'extraire les données pour le système local.
    - Tu DOIS inclure un tag de triage ([TRIAGE:ROUGE|JAUNE|VERT]) au tout début de ta réponse.
    - SI ET SEULEMENT SI toutes les données (nom molécule, poids, absence d'interaction) sont réunies, termine ton message par le bloc JSON entre [[DATA]] et [[/DATA]].
    - Structure du JSON : $jsonStructure
    """.trimIndent() else """
    - NEVER CALCULATE DOSAGE YOURSELF. Your role is to extract data for the local system.
    - You MUST include a triage tag ([TRIAGE:ROUGE|JAUNE|VERT]) at the very beginning.
    - IF AND ONLY IF all data (drug name, weight, no interaction) are present, end your message with the JSON block between [[DATA]] and [[/DATA]].
    - JSON Structure: $jsonStructure
    """.trimIndent()

            return if (isPublicMode) {
                if (lang == "fr") """
            Tu es MedVoice Africa, un conseiller de santé expert et prudent.
            RÈGLES :
            1. $extractionRules
            2. Ton message doit être simple, rassurant et rédigé en paragraphes fluides.
            3. Ne mentionne AUCUN chiffre de dosage (mg/ml) dans ton texte, laisse le système s'en charger.
            $safetyRules
        """.trimIndent() else """
            You are MedVoice Africa, an expert and cautious health advisor.
            RULES:
            1. $extractionRules
            2. Your message should be simple, reassuring, and written in fluid paragraphs.
            3. Do NOT mention any dosage numbers (mg/ml) in your text, let the system handle it.
            $safetyRules
        """.trimIndent()
            } else {
                // Version Pro
                if (lang == "fr") """
            Tu es MedVoice Africa, assistant expert pour professionnels de santé.
            RÈGLES :
            1. $extractionRules
            2. Utilise un langage clinique précis et structuré en paragraphes.
            3. Indique clairement que le dosage final est validé par les protocoles locaux.
            $safetyRules
        """.trimIndent() else """
            You are MedVoice Africa, an expert assistant for healthcare professionals.
            RULES:
            1. $extractionRules
            2. Use precise clinical language structured in paragraphs.
            3. Clearly state that the final dosage is validated by local protocols.
            $safetyRules
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

        // Génère un titre court via Gemini, SANS polluer le chatHistory
        // Appelée UNE SEULE FOIS à la création d'une session (depuis MainActivity)
        suspend fun generateTitleOnly(prompt: String): String = withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank()) return@withContext ""
            return@withContext try {
                val url = URL("$BASE_URL?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5_000
                conn.readTimeout = 10_000

                val body = JSONObject().apply {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text",
                                "Tu es un générateur de titres médicaux. Réponds UNIQUEMENT avec un titre de 3 à 5 mots, sans guillemets, sans ponctuation finale, rien d'autre."
                            ))
                        })
                    })
                    // Appel one-shot : pas de chatHistory, juste le prompt
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply { put(JSONObject().put("text", prompt)) })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 20)   // Titre = 3-5 mots, 20 tokens max
                        put("temperature", 0.3)       // Moins créatif = plus précis
                    })
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                if (conn.responseCode != 200) return@withContext ""

                val raw = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(raw)
                json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .lines().firstOrNull()?.trim()
                    ?.replace(Regex("[\"'*#\\[\\]]"), "")
                    ?.take(50)
                    ?: ""
            } catch (_: Exception) {
                "" // MainActivity fera le fallback keywords
            }
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

    private fun detectIfFrench(text: String): Boolean {
        val frenchKeywords = listOf("combien", "donner", "poids", "enfant", "est-ce", "ordonnance", "prendre", "puis-je", "peut-on")
        val englishKeywords = listOf("how", "give", "weight", "child", "should", "prescription", "take", "can i", "can we")

        val frCount = frenchKeywords.count { text.contains(it, ignoreCase = true) }
        val enCount = englishKeywords.count { text.contains(it, ignoreCase = true) }

        return if (frCount == 0 && enCount == 0) {
            Locale.getDefault().language == "fr" // Fallback
        } else frCount >= enCount
    }

    suspend fun runInference(userMessage: String, imageBitmap: Bitmap? = null): Result<String> =
        withContext(Dispatchers.IO) {
            val isInputFr = detectIfFrench(userMessage)
            val lang = if (isInputFr) "fr" else "en"

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
                    "**Mode Sécurité Hors-ligne**\n\nL'assistant IA est inaccessible. D'après vos mots-clés, voici les protocoles d'urgence correspondants issus de notre base de données locale. Lisez attentivement la section correspondant à votre niveau de gravité :\n\n${
                        if (ragContext.isNotBlank()) ragContext
                        else "Décrivez un symptôme précis (ex: fièvre, toux, diarrhée) pour accéder aux protocoles OMS hors-ligne."
                    }"
                else
                    "**Offline Safety Mode**\n\nAI assistant unreachable. Based on your keywords, here are the emergency protocols from our local database. Read carefully the section matching your severity level:\n\n${
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
                val candidates = json.getJSONArray("candidates")
                if (candidates.length() == 0) return@withContext Result.failure(Exception("No candidates"))
                
                val candidate = candidates.getJSONObject(0)
                val parts = candidate.getJSONObject("content").getJSONArray("parts")
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

    // ── runInferenceForInteraction ──────────────────
    // Appel Gemini one-shot pour interactions et dosages (sans polluer chatHistory)
    suspend fun runInferenceForInteraction(prompt: String, isFr: Boolean): Result<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("No API key"))
            if (!isOnline()) return@withContext Result.failure(Exception("Offline"))
            return@withContext try {
                val url = URL("$BASE_URL?key=$apiKey")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 8_000
                conn.readTimeout = 20_000
                val sysPrompt = if (isFr)
                    "Tu es un pharmacologue expert. Reponds UNIQUEMENT en JSON valide, rien d'autre."
                else
                    "You are an expert pharmacologist. Reply ONLY in valid JSON, nothing else."
                val body = org.json.JSONObject().apply {
                    put("system_instruction", org.json.JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(org.json.JSONObject().put("text", sysPrompt))
                        })
                    })
                    put("contents", org.json.JSONArray().apply {
                        put(org.json.JSONObject().apply {
                            put("role", "user")
                            put("parts", org.json.JSONArray().apply {
                                put(org.json.JSONObject().put("text", prompt))
                            })
                        })
                    })
                    put("generationConfig", org.json.JSONObject().apply {
                        put("maxOutputTokens", 200)
                        put("temperature", 0.1) // Tres deterministe pour les donnees medicales
                    })
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (conn.responseCode != 200) return@withContext Result.failure(Exception("API ${conn.responseCode}"))
                val raw = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = org.json.JSONObject(raw)
                val text = json.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text")
                Result.success(text.trim())
            } catch (e: Exception) {
                Log.e(TAG, "runInferenceForInteraction error: ${e.message}")
                Result.failure(e)
            }
        }

    fun parseTriageLevel(response: String) = parseTriageLevelFromTag(response)
    fun isInitialized() = apiKey.isNotBlank()
    fun close() { apiKey = "" }
}

enum class TriageLevel { ROUGE, JAUNE, VERT, BLEU, UNKNOWN }