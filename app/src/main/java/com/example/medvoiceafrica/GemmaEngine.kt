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

            val identityAndGreetings = if (lang == "fr") """
1. IDENTITÉ : Tu es MedVoice Africa, une application d'assistance médicale et de santé conçue spécifiquement pour l'Afrique de l'Ouest.
2. SALUTATIONS : Si l'utilisateur te salue simplement (ex: "bonjour", "salut"), réponds poliment en te présentant comme MedVoice Africa et demande comment tu peux aider. Pour ces messages sociaux simples, n'utilise PAS de tag de triage médical complexe, utilise [TRIAGE:VERT].
3. MISSION : Ton but est d'aider les agents de santé et les patients avec des conseils basés sur les protocoles de l'OMS.
""".trimIndent() else """
1. IDENTITY: You are MedVoice Africa, a health and medical support application designed specifically for West Africa.
2. GREETINGS: If the user simply greets you (e.g., "hello", "hi"), respond politely, introduce yourself as MedVoice Africa, and ask how you can help. For these simple social messages, do NOT use complex medical triage tags, use [TRIAGE:VERT].
3. MISSION: Your goal is to help health workers and patients with advice based on WHO protocols.
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
            $identityAndGreetings
            RÈGLES SUPPLÉMENTAIRES :
            1. $extractionRules
            2. Ton message doit être simple, rassurant et rédigé en paragraphes fluides.
            3. Ne mentionne AUCUN chiffre de dosage (mg/ml) dans ton texte, laisse le système s'en charger.
            $safetyRules
        """.trimIndent() else """
            $identityAndGreetings
            ADDITIONAL RULES:
            1. $extractionRules
            2. Your message should be simple, reassuring, and written in fluid paragraphs.
            3. Do NOT mention any dosage numbers (mg/ml) in your text, let the system handle it.
            $safetyRules
        """.trimIndent()
            } else {
                // Version Pro
                if (lang == "fr") """
            $identityAndGreetings
            RÈGLES SUPPLÉMENTAIRES :
            1. $extractionRules
            2. Utilise un langage clinique précis et structuré en paragraphes.
            3. Indique clairement que le dosage final est validé par les protocoles locaux.
            $safetyRules
        """.trimIndent() else """
            $identityAndGreetings
            ADDITIONAL RULES:
            1. $extractionRules
            2. Use precise clinical language structured in paragraphs.
            3. Clearly state that the final dosage is validated by local protocols.
            $safetyRules
        """.trimIndent()
            }
        }



        fun buildMedicalImagePrompt(lang: String, currentMeds: List<String> = emptyList()): String {
            val medsCtx = if (currentMeds.isNotEmpty())
                if (lang == "fr") "\nPatient sous traitement actuel : ${currentMeds.joinToString(", ")}."
                else "\nPatient currently takes: ${currentMeds.joinToString(", ")}."
            else ""

                    return if (lang == "fr") """
        Tu es un assistant médical de terrain pour agents de santé communautaires (ASC) au Bénin.
        Analyse cette image médicale et structure ta réponse OBLIGATOIREMENT ainsi :
        
        TYPE : [Plaie / Rash / Œdème / Brûlure / Radiographie / Médicament / Autre]
        OBSERVATIONS : [Ce que tu vois précisément, sans jargon]
        SIGNES D'ALERTE : [Présents ou absents — infection, nécrose, détresse...]
        TRIAGE : [ROUGE = urgence immédiate / JAUNE = surveiller / VERT = stable]
        ACTION IMMÉDIATE : [Ce que l'ASC doit faire maintenant, en 1-2 phrases simples]
        RÉFÉRER AU CSPS : [Oui immédiat / Oui dans 24h / Non nécessaire]
        $medsCtx
        Protocoles OMS Bénin 2026. Sois précis et concis.
        Termine OBLIGATOIREMENT par le tag : [TRIAGE:ROUGE] ou [TRIAGE:JAUNE] ou [TRIAGE:VERT] selon ta conclusion.
                    """.trimIndent()
                    else """
        You are a field medical assistant for community health workers (CHW) in Benin.
        Analyze this medical image and structure your response STRICTLY as follows:
        
        TYPE: [Wound / Rash / Edema / Burn / X-ray / Medication / Other]
        OBSERVATIONS: [What you see precisely, no medical jargon]
        WARNING SIGNS: [Present or absent — infection, necrosis, distress...]
        TRIAGE: [RED = immediate emergency / YELLOW = monitor / GREEN = stable]
        IMMEDIATE ACTION: [What the CHW must do now, in 1-2 simple sentences]
        REFER TO HEALTH CENTER: [Yes immediate / Yes within 24h / Not necessary]
        $medsCtx
        WHO Benin 2026 protocols. Be precise and concise.
        ALWAYS end with the tag: [TRIAGE:ROUGE] or [TRIAGE:JAUNE] or [TRIAGE:VERT] based on your conclusion.
                    """.trimIndent()
                }



        fun parseTriageLevelFromTag(response: String): TriageLevel {
            val match = Regex("\\[TRIAGE:(\\w+)\\]").find(response)
            return when (match?.groupValues?.get(1)?.uppercase()) {
                "ROUGE" -> TriageLevel.ROUGE; "JAUNE" -> TriageLevel.JAUNE; "VERT" -> TriageLevel.VERT
                else -> when {
                    response.contains("ROUGE", ignoreCase = true) -> TriageLevel.ROUGE
                    response.contains("JAUNE", ignoreCase = true) -> TriageLevel.JAUNE
                    response.contains("VERT", ignoreCase = true)  -> TriageLevel.VERT
                    else -> TriageLevel.UNKNOWN
                }
            }
        }

        fun cleanResponse(raw: String): String {
            var text = raw
            // Nettoyer les blocs think
            text = text.replace(Regex("<thought>.*?</thought>", RegexOption.DOT_MATCHES_ALL), "")
            text = text.replace(Regex("<thinking>.*?</thinking>", RegexOption.DOT_MATCHES_ALL), "")
            text = text.replace(Regex("<\\|think\\|>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            // 🔴 NE PAS effacer [TRIAGE:X] ici — le laisser pour MedOrchestrator
            // Nettoyer seulement les artefacts parasites
            text = text.replace("TRIAGEVERT", "").replace("TRIAGEROUGE", "").replace("TRIAGEJAUNE", "")
            text = text.replace("TRIAGEINFO", "")
            val badPrefixes = listOf("user says", "user input", "role:", "language:", "tone:", "goal:",
                "context:", "greeting:", "constraint:", "thinking:", "reasoning:", "self-correction")
            text = text.lines().filterNot { line ->
                val t = line.trim()
                (t.startsWith("*") || t.startsWith("-")) && badPrefixes.any { t.contains(it, ignoreCase = true) }
            }.joinToString("\n").trim()
            text = text.replace(Regex("\\[TRIAGE:\\w+\\]"), "").trim()
            return text.trimStart('•', '-', '\n', '\r').trim()
        }

        // Nouvelle fonction pour l'affichage UI uniquement (enlève le tag pour l'utilisateur)
        fun cleanResponseForDisplay(raw: String): String {
            return cleanResponse(raw).replace(Regex("\\[TRIAGE:\\w+\\]"), "").trim()
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
            sock.connect(InetSocketAddress("8.8.8.8", 53), 5000)
            sock.close(); true
        } catch (_: Exception) { false }
    }

    private fun detectIfFrench(text: String): Boolean {
        val frenchKeywords = listOf(
            "combien", "donner", "poids", "enfant", "est-ce", "ordonnance", "prendre", "puis-je", "peut-on",
            "médicament", "posologie", "bébé", "dose", "matin", "soir", "jour", "fois"
        )
        val englishKeywords = listOf(
            "how", "give", "weight", "child", "should", "prescription", "take", "can i", "can we",
            "medicine", "dosage", "baby", "dose", "morning", "evening", "day", "times"
        )

        val frCount = frenchKeywords.count { text.contains(it, ignoreCase = true) }
        val enCount = englishKeywords.count { text.contains(it, ignoreCase = true) }

        return if (frCount == enCount) {
            Locale.getDefault().language == "fr"
        } else frCount > enCount
    }

    suspend fun runInference(
        userMessage: String,
        imageBitmap: Bitmap? = null,
        currentMeds: List<String> = emptyList()
    ): Result<String> =
        withContext(Dispatchers.IO) {
            val isInputFr = detectIfFrench(userMessage)
            val lang = if (isInputFr) "fr" else "en"

            // RAG: find relevant protocols BEFORE trying network (needed for offline too)
            val relevantProtocols = ragEngine.findRelevant(userMessage)
            val ragContext = RagEngine.buildContext(relevantProtocols, lang)
            val online = isOnline()

            if (relevantProtocols.isNotEmpty())
                Log.d(TAG, "RAG: injecting ${relevantProtocols.size} protocol(s): ${relevantProtocols.map { it.title }}")


            // Fast offline check
            if (!online) {

                // 1. Essayer LlamaEngine si disponible
                if (LlamaEngine.isReady()) {
                    val llamaResult = LlamaEngine.generateResponse(userMessage, buildSystemPrompt(lang, true), ragContext)
                    return@withContext when (llamaResult) {
                        is LlamaResult.Success -> {
                            val cleaned = cleanResponse(llamaResult.text)
                            chatHistory.add(JSONObject().apply {
                                put("role", "model"); put("parts", JSONArray().apply { put(JSONObject().put("text", cleaned)) })
                            })
                            Result.success(llamaResult.text)
                        }
                        is LlamaResult.Fallback -> Result.success("${llamaResult.offlineText}TRIAGEINFO")
                    }
                }

                // 2. NOUVEAU — Chercher dans OmsProtocolDatabase SQLite directement
                val db = AppDatabase.getInstance(context)
                val lowerQuery = userMessage.lowercase()
                val omsResult = db.omsProtocolDao().searchProtocols("%$lowerQuery%")


                    if (omsResult.isNotEmpty()) {
                        val first = omsResult.first()
                        val inferredTriage = when {
                            first.protocol.contains("urgence", ignoreCase = true) ||
                                    first.protocol.contains("transfert", ignoreCase = true) -> "TRIAGEROUGE"
                            first.protocol.contains("consultation", ignoreCase = true) ||
                                    first.protocol.contains("24h", ignoreCase = true) -> "TRIAGEJAUNE"
                            else -> "TRIAGEVERT"
                        }
                        val intro = if (lang == "fr")
                            "D'après les protocoles OMS locaux :"
                        else
                            "Based on local WHO protocols:"

                        val formatted = omsResult.take(1).filter { it.protocol.length > 50 }.joinToString("\n\n") { proto ->
                            buildString {
                                append("📋 **${proto.title}**\n")
                                proto.protocol
                                    .replace(Regex("\\s+"), " ")
                                    .split(Regex("(?<=[.!?])\\s+"))
                                    .filter { it.length > 10 }
                                    .take(4)
                                    .forEach { append("• $it\n") }
                            }
                        }
                        val omsMsg = "$inferredTriage\n$intro\n\n$formatted"
                        return@withContext Result.success(omsMsg)
                    }



                // Détection salutation hors-ligne
                val isSocialMessage = listOf("bonjour", "bonsoir", "salut", "hello", "hi", "good morning")
                    .any { userMessage.lowercase().contains(it) }
                if (isSocialMessage) {
                    return@withContext Result.success(
                        if (lang == "fr")
                            "Bonjour ! Je suis MedVoice Africa, votre assistant médical. Je fonctionne actuellement en mode hors-ligne. Décrivez un symptôme ou un médicament et je ferai de mon mieux pour vous aider avec les protocoles OMS disponibles localement.\nTRIAGEVERT"
                        else
                            "Hello! I'm MedVoice Africa, your medical assistant. I'm currently running offline. Describe a symptom or medication and I'll help you with locally available WHO protocols.\nTRIAGEVERT"
                    )
                }

                // 3. Fallback RAG JSON existant
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
                return@withContext Result.success(offlineMsg + "TRIAGEINFO")
            }

            // ─── En ligne : maintenant on encode l'image et on appelle Gemini ───
            val effectiveTextPrompt = if (imageBitmap != null) {
                val imagePrompt = buildMedicalImagePrompt(lang, currentMeds)
                if (userMessage.isNotBlank()) "$imagePrompt\n\nUser note: $userMessage" else imagePrompt
            } else userMessage

            val partsArray = JSONArray().apply {
                put(JSONObject().put("text", effectiveTextPrompt))
                if (imageBitmap != null) {
                    put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", encodeBitmapToBase64(imageBitmap))
                        })
                    })
                }
            }
            chatHistory.add(JSONObject().apply { put("role", "user"); put("parts", partsArray) })
            enforceMemoryLimit()

            return@withContext try {
                val url = URL("$BASE_URL?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = if (imageBitmap != null) 120_000 else 90_000

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
                val isNetworkError = e is java.net.UnknownHostException || 
                                     e is java.net.ConnectException || 
                                     e is java.net.SocketTimeoutException || 
                                     e is java.net.SocketException ||
                                     e.message?.contains("Software caused connection abort", ignoreCase = true) == true

                if (isNetworkError) {
                    val msg = if (lang == "fr")
                        "⚠️ **Mode Sécurité Hors-ligne**\n\nL'assistant IA est inaccessible. Je bascule sur les protocoles locaux.${if (ragContext.isNotBlank()) "\n\n$ragContext" else "\n\nVeuillez vérifier votre connexion internet."}"
                    else
                        "⚠️ **Offline Safety Mode**\n\nAI assistant unreachable. Switching to local protocols.${if (ragContext.isNotBlank()) "\n\n$ragContext" else "\n\nPlease check your internet connection."}"
                    Result.success("$msg\n[TRIAGE:INFO]")
                } else Result.failure(Exception("${e.javaClass.simpleName}: ${e.message}"))
            }
        }

    // ── runInferenceForInteraction ──────────────────
    // Appel Gemini one-shot pour interactions et dosages (sans polluer chatHistory)
// ── Appel Gemini one-shot pour interactions/dosages — NE pollue PAS chatHistory ──
    suspend fun runInferenceForInteraction(prompt: String, isFr: Boolean): Result<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("No API key"))
            if (!isOnline()) return@withContext Result.failure(Exception("Offline"))
            return@withContext try {
                val url = URL("$BASE_URL?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 20000

                val sysPrompt = if (isFr)
                    "Tu es un pharmacologue expert. Réponds UNIQUEMENT en JSON valide, rien d'autre."
                else
                    "You are an expert pharmacologist. Reply ONLY in valid JSON, nothing else."

                val body = JSONObject().apply {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", sysPrompt))
                        })
                    })
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", prompt))
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 1024)
                        put("temperature", 0.1)
                    })
                }

                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (conn.responseCode != 200)
                    return@withContext Result.failure(Exception("API ${conn.responseCode}"))

                val raw = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(raw)
                val text = json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                Result.success(text.trim())
            } catch (e: Exception) {
                Log.e(TAG, "runInferenceForInteraction error: ${e.message}")
                val isNetworkError = e is java.net.UnknownHostException || 
                                     e is java.net.ConnectException || 
                                     e is java.net.SocketTimeoutException ||
                                     e is java.net.SocketException ||
                                     e.message?.contains("Software caused connection abort", ignoreCase = true) == true
                
                if (isNetworkError) {
                    return@withContext Result.failure(Exception("OFFLINE_OR_NETWORK_ERROR"))
                }
                Result.failure(e)
            }
        }

    fun removeLastMedicationContext(medName: String) {
        chatHistory.removeAll { msg ->
            val text = msg.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text") ?: ""
            text.contains(medName, ignoreCase = true) &&
                    msg.optString("role") == "user"
        }
        saveHistory()
    }

    fun parseTriageLevel(response: String) = parseTriageLevelFromTag(response)
    fun isInitialized() = apiKey.isNotBlank()
    fun close() { apiKey = "" }
}

enum class TriageLevel { ROUGE, JAUNE, VERT, BLEU, UNKNOWN }