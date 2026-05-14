package com.example.medvoiceafrica

// ═══════════════════════════════════════════════════════════════════
// DosageFunctionCalling.kt
// Orchestration du calcul de dosage pédiatrique/adulte
// Étape A : Détection d'intention → Étape B : Extraction entités
// Étape C : Calcul local → Étape D : Réponse structurée UI
// ═══════════════════════════════════════════════════════════════════

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Paramètres extraits de la demande utilisateur ────────────────
data class DosageParams(
    val medicineName: String,
    val patientWeightKg: Double?,      // null si non précisé
    val patientAgeYears: Double?,      // null si non précisé
    val patientAgeMonths: Double?,     // pour les nourrissons
    val frequencyPerDay: Int,          // défaut 3
    val routeOfAdmin: String = "oral"  // oral | injectable | rectal
)

// ── Résultat du calcul de dosage ─────────────────────────────────
data class DosageResult(
    val medicineName: String,
    val dosePerTake: String,          // ex: "250 mg" ou "5 ml"
    val frequencyPerDay: Int,
    val durationDays: Int,
    val specialInstructions: String,
    val warningMessage: String,
    val source: DosageSource
)

enum class DosageSource {
    LOCAL_PROTOCOL,   // Calculé depuis la table de protocoles
    LLM_GEMINI,       // Calculé par Gemini (online)
    LLM_LLAMA,        // Calculé par Llama (offline)
    INSUFFICIENT_DATA // Données insuffisantes
}

// ── Table de protocoles de dosage OMS / référentiels Bénin ────────
// Dose en mg/kg/jour — clé = nom normalisé de la molécule
private data class DosageProtocol(
    val mgPerKgPerDay: Double,
    val maxDoseAdultMg: Double,
    val defaultFreqPerDay: Int,
    val defaultDurationDays: Int,
    val minAgeMonths: Int = 0,
    val instructionsFr: String,
    val instructionsEn: String,
    val warningFr: String = "",
    val warningEn: String = ""
)

private val DOSAGE_PROTOCOLS: Map<String, DosageProtocol> = mapOf(
    "paracétamol" to DosageProtocol(
        mgPerKgPerDay = 60.0, maxDoseAdultMg = 4000.0,
        defaultFreqPerDay = 4, defaultDurationDays = 5,
        instructionsFr = "Prendre avec ou sans nourriture. Espacer les prises d'au moins 4 heures.",
        instructionsEn = "Take with or without food. Space doses at least 4 hours apart.",
        warningFr = "Ne pas dépasser 4g/jour chez l'adulte. Éviter avec l'alcool.",
        warningEn = "Do not exceed 4g/day in adults. Avoid with alcohol."
    ),
    "amoxicilline" to DosageProtocol(
        mgPerKgPerDay = 50.0, maxDoseAdultMg = 3000.0,
        defaultFreqPerDay = 3, defaultDurationDays = 7,
        instructionsFr = "Prendre à heure fixe, de préférence au repas. Compléter le traitement.",
        instructionsEn = "Take at fixed times, preferably with meals. Complete the full course.",
        warningFr = "Vérifier allergie à la pénicilline. Ne pas stopper avant la fin du traitement.",
        warningEn = "Check penicillin allergy. Do not stop before end of treatment."
    ),
    "cotrimoxazole" to DosageProtocol(
        mgPerKgPerDay = 48.0, maxDoseAdultMg = 1920.0,  // dose en SMX
        defaultFreqPerDay = 2, defaultDurationDays = 5,
        minAgeMonths = 6,
        instructionsFr = "Donner avec beaucoup d'eau. Ne pas donner aux nourrissons < 6 mois.",
        instructionsEn = "Give with plenty of water. Do not give to infants < 6 months.",
        warningFr = "Contre-indiqué < 6 mois et en cas de déficit en G6PD.",
        warningEn = "Contraindicated < 6 months and in G6PD deficiency."
    ),
    "ibuprofène" to DosageProtocol(
        mgPerKgPerDay = 30.0, maxDoseAdultMg = 1200.0,
        defaultFreqPerDay = 3, defaultDurationDays = 5,
        minAgeMonths = 3,
        instructionsFr = "Prendre IMPÉRATIVEMENT au repas. Ne pas donner à jeun.",
        instructionsEn = "MUST be taken with food. Do not give on empty stomach.",
        warningFr = "Contre-indiqué < 3 mois, insuffisance rénale, ulcère.",
        warningEn = "Contraindicated < 3 months, renal failure, ulcer."
    ),
    "métronidazole" to DosageProtocol(
        mgPerKgPerDay = 30.0, maxDoseAdultMg = 2400.0,
        defaultFreqPerDay = 3, defaultDurationDays = 7,
        instructionsFr = "Prendre au repas. Éviter absolument l'alcool pendant et 48h après.",
        instructionsEn = "Take with meals. Strictly avoid alcohol during and 48h after.",
        warningFr = "Réaction sévère avec alcool. Ne pas utiliser au 1er trimestre de grossesse.",
        warningEn = "Severe reaction with alcohol. Do not use in 1st trimester of pregnancy."
    ),
    "artémether" to DosageProtocol(
        mgPerKgPerDay = 3.2, maxDoseAdultMg = 80.0,
        defaultFreqPerDay = 1, defaultDurationDays = 3,  // Schéma Coartem simplifié
        minAgeMonths = 12,
        instructionsFr = "Protocole antipaludique OMS : prendre avec un repas ou une boisson lactée.",
        instructionsEn = "WHO antimalarial protocol: take with a meal or a milky drink.",
        warningFr = "Ne pas donner aux nourrissons < 5kg. Vérifier si paludisme confirmé.",
        warningEn = "Do not give to infants < 5kg. Confirm malaria before treating."
    ),
    "chloroquine" to DosageProtocol(
        mgPerKgPerDay = 10.0, maxDoseAdultMg = 600.0,
        defaultFreqPerDay = 1, defaultDurationDays = 3,
        instructionsFr = "Prendre au repas pour réduire les effets gastro-intestinaux.",
        instructionsEn = "Take with meals to reduce GI side effects.",
        warningFr = "Résistances fréquentes en Afrique de l'Ouest. Préférer l'artémisinine.",
        warningEn = "Frequent resistance in West Africa. Prefer artemisinin-based treatment."
    ),
    "zinc" to DosageProtocol(
        mgPerKgPerDay = 0.0,  // Dose fixe selon l'âge
        maxDoseAdultMg = 20.0,
        defaultFreqPerDay = 1, defaultDurationDays = 10,
        instructionsFr = "Nourrisson < 6 mois : 10 mg/jour. Enfant ≥ 6 mois : 20 mg/jour. Diluer dans SRO.",
        instructionsEn = "Infant < 6 months: 10 mg/day. Child ≥ 6 months: 20 mg/day. Dissolve in ORS.",
        warningFr = "Protocole OMS diarrhée : associer obligatoirement aux SRO.",
        warningEn = "WHO diarrhea protocol: must be combined with ORS."
    ),
    "ciprofloxacine" to DosageProtocol(
        mgPerKgPerDay = 20.0, maxDoseAdultMg = 1000.0,
        defaultFreqPerDay = 2, defaultDurationDays = 7,
        minAgeMonths = 216,  // Réservé adulte sauf urgence (18 ans = 216 mois)
        instructionsFr = "Réservé aux adultes. Prendre 2h avant ou après antiacides/calcium.",
        instructionsEn = "Reserved for adults. Take 2h before or after antacids/calcium.",
        warningFr = "Éviter chez l'enfant (croissance osseuse). Photosensibilisant.",
        warningEn = "Avoid in children (bone growth). Photosensitizing."
    ),
    "furosémide" to DosageProtocol(
        mgPerKgPerDay = 2.0, maxDoseAdultMg = 80.0,
        defaultFreqPerDay = 1, defaultDurationDays = 7,
        instructionsFr = "Prendre le matin. Surveiller la kaliémie (banane, orange à recommander).",
        instructionsEn = "Take in the morning. Monitor potassium levels.",
        warningFr = "Risque hypokaliémie. Surveiller la diurèse et la tension.",
        warningEn = "Hypokalemia risk. Monitor urine output and blood pressure."
    ),
)

// ═══════════════════════════════════════════════════════════════════
// DosageFunctionCalling — Point d'entrée principal
// ═══════════════════════════════════════════════════════════════════
object DosageFunctionCalling {

    private const val TAG = "DosageFunctionCalling"

    fun detectDosageIntent(userMessage: String): Boolean {
        val msg = userMessage.lowercase()
        val dosageKeywords = listOf(
            // Français & Anglais
            "dose", "dosage", "combien", "comprimé", "comprime",
            "mg", "ml", "pilule", "donner", "prendre", "sirop",
            "posologie", "traitement", "administrer", "enfant de",
            "bébé de", "poids de", "kg", "kilo", "ans", "mois",
            "how many", "how much", "tablet", "syrup", "weight",
            "dosage for", "dose for", "child", "baby", "years old"
        )

        // Mots très courts ou Fon nécessitant une correspondance exacte
        val exactWords = listOf("dodo", "tindo", "navi", "acɔ")

        val containsKeyword = dosageKeywords.any { msg.contains(it) }
        val containsExactWord = exactWords.any { word ->
            Regex("\\b$word\\b").containsMatchIn(msg)
        }

        return containsKeyword || containsExactWord
    }

    // ── Étape B : Extraire les entités du message utilisateur ──────
    fun extractDosageParams(userMessage: String): DosageParams? {
        val msg = userMessage.lowercase()

        // Extraire le poids (ex: "15 kg", "15kg", "15 kilos")
        val weightKg = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:kg|kilo|kilos)").find(msg)
            ?.groupValues?.get(1)?.toDoubleOrNull()

        // Extraire l'âge en années (ex: "3 ans", "3ans", "3 xwè")
        val ageYears = Regex("(\\d+)\\s*(?:ans|an|years?|yr|xwè|xwe)").find(msg)
            ?.groupValues?.get(1)?.toDoubleOrNull()

        // Extraire l'âge en mois (ex: "6 mois", "18 months", "6 sun")
        val ageMonths = Regex("(\\d+)\\s*(?:mois|months?|sun)").find(msg)
            ?.groupValues?.get(1)?.toDoubleOrNull()

        // Extraire le nom du médicament depuis le message
        val medicineName = DrugInteractionEngine.extractMoleculeName(userMessage)

        if (medicineName.isBlank() && weightKg == null && ageYears == null) return null

        return DosageParams(
            medicineName = medicineName,
            patientWeightKg = weightKg,
            patientAgeYears = ageYears,
            patientAgeMonths = ageMonths,
            frequencyPerDay = 3  // défaut OMS
        )
    }


    // ── Étape C : Calcul local ou LLM ────────────────────────────
    suspend fun calculateDosage(
        params: DosageParams,
        gemmaEngine: GemmaEngine,
        isOnline: Boolean,
        isFr: Boolean,
        currentMeds: List<String> = emptyList()
    ): DosageResult = withContext(Dispatchers.IO) {

        // ── ÉTAPE 0 : Validation du poids (Sécurité) ─────────────────────────────────
        if (params.patientWeightKg != null && (params.patientWeightKg < 0.1 || params.patientWeightKg > 250.0)) {
            return@withContext DosageResult(
                medicineName = params.medicineName.replaceFirstChar { it.uppercase() },
                dosePerTake = "VALEUR ABSURDE",
                frequencyPerDay = 0,
                durationDays = 0,
                specialInstructions = if (isFr)
                    "Le poids renseigné (${params.patientWeightKg} kg) semble incorrect. Veuillez vérifier."
                else "The weight provided (${params.patientWeightKg} kg) seems incorrect. Please verify.",
                warningMessage = if (isFr) "Sécurité : poids hors limites." else "Safety: weight out of bounds.",
                source = DosageSource.INSUFFICIENT_DATA
            )
        }

        val medicineKey = params.medicineName.lowercase().trim()
        val protocol = DOSAGE_PROTOCOLS[medicineKey]
            ?: DOSAGE_PROTOCOLS.entries.firstOrNull { (k, _) -> k.contains(medicineKey) || medicineKey.contains(k) }?.value
        val medProtocol = MedProtocols.findProtocol(params.medicineName)

        // ── ÉTAPE 1 : Vérification âge minimum (Ciprofloxacine, Cotrimoxazole, etc.) ──
        if (protocol != null && protocol.minAgeMonths > 0) {
            val ageMonths = params.patientAgeMonths ?: ((params.patientAgeYears ?: 99.0) * 12)
            if (ageMonths < protocol.minAgeMonths) {
                return@withContext DosageResult(
                    medicineName = params.medicineName.replaceFirstChar { it.uppercase() },
                    dosePerTake = "INTERDIT",
                    frequencyPerDay = 0,
                    durationDays = 0,
                    specialInstructions = if (isFr)
                        "${params.medicineName.replaceFirstChar { it.uppercase() }} est contre-indiqué avant ${protocol.minAgeMonths} mois. Risque de toxicité sur la croissance."
                    else
                        "${params.medicineName.replaceFirstChar { it.uppercase() }} is contraindicated under ${protocol.minAgeMonths} months. Risk of growth toxicity.",
                    warningMessage = if (isFr) "Alerte rouge : âge insuffisant." else "Red alert: age too low.",
                    source = DosageSource.LOCAL_PROTOCOL
                )
            }
        }

        // ── ÉTAPE 2 : Vérification interactions médicamenteuses ──────────────────────
        var interactionWarning = ""
        var interactionTriage = InteractionSeverity.UNKNOWN
        if (currentMeds.isNotEmpty()) {
            val interaction = DrugInteractionEngine.checkLocalTable(params.medicineName, currentMeds, isFr)
            if (interaction != null) {
                interactionWarning = interaction.message
                interactionTriage = interaction.severity
                
                if (interaction.severity == InteractionSeverity.ROUGE) {
                    return@withContext DosageResult(
                        medicineName = params.medicineName.replaceFirstChar { it.uppercase() },
                        dosePerTake = "STOP",
                        frequencyPerDay = 0,
                        durationDays = 0,
                        specialInstructions = interaction.message,
                        warningMessage = if (isFr) "Interaction dangereuse avec traitement actuel." else "Dangerous interaction with current treatment.",
                        source = DosageSource.LOCAL_PROTOCOL
                    )
                }
            }
        }

        // ── ÉTAPE 3 : Poids manquant (seulement après avoir écarté les dangers) ──────
        if (params.patientWeightKg == null && params.medicineName.lowercase() != "zinc") {
            return@withContext DosageResult(
                medicineName = params.medicineName.replaceFirstChar { it.uppercase() },
                dosePerTake = "Inconnu",
                frequencyPerDay = 0,
                durationDays = 0,
                specialInstructions = if (interactionWarning.isNotBlank()) interactionWarning 
                    else if (isFr) "Médicament identifié. Précisez le poids pour calculer la dose (ex: 15kg)."
                    else "Drug identified. Please provide weight to calculate dose (e.g. 15kg).",
                warningMessage = if (interactionTriage == InteractionSeverity.JAUNE) "Interaction possible avec traitement en cours." else "",
                source = DosageSource.INSUFFICIENT_DATA
            )
        }

        // ── ÉTAPE 4 : Calcul normal ───────────────────────────────────────────────────
        if (protocol != null) return@withContext calculateFromProtocol(params, protocol, isFr)
        if (medProtocol != null) {
            val weight = params.patientWeightKg ?: 0.0
            val rawDose = medProtocol.mgPerKg * weight
            val safeDose = if (rawDose > medProtocol.maxPerDayMg && medProtocol.maxPerDayMg > 0) medProtocol.maxPerDayMg else rawDose
            val dosePerTake = if (medProtocol.dosesPerDay > 0) safeDose / medProtocol.dosesPerDay else safeDose
            return@withContext DosageResult(
                medicineName = params.medicineName.replaceFirstChar { it.uppercase() },
                dosePerTake = "${dosePerTake.toInt()} mg",
                frequencyPerDay = medProtocol.dosesPerDay,
                durationDays = 5,
                specialInstructions = medProtocol.instruction,
                warningMessage = "",
                source = DosageSource.LOCAL_PROTOCOL
            )
        }

        return@withContext calculateWithLLM(params, gemmaEngine, isOnline, isFr, currentMeds)

    }

    // ── Étape C.1 : Calcul depuis le protocole OMS local ─────────
    private fun calculateFromProtocol(
        params: DosageParams,
        protocol: DosageProtocol,
        isFr: Boolean
    ): DosageResult {
        val weight = params.patientWeightKg

        // Cas spécial zinc (dose fixe selon âge)
        if (params.medicineName.lowercase() == "zinc") {
            val ageProvided = params.patientAgeMonths != null || params.patientAgeYears != null
            val ageMonths = params.patientAgeMonths ?: ((params.patientAgeYears ?: 1.0) * 12)
            
            val ageWarning = if (!ageProvided) {
                if (isFr) " Âge non précisé, dose ≥ 6 mois par défaut." 
                else " Age not specified, defaulting to ≥ 6 months dose."
            } else ""

            return DosageResult(
                medicineName = "Zinc",
                dosePerTake = if (ageMonths < 6) "10 mg" else "20 mg",
                frequencyPerDay = 1,
                durationDays = 10,
                specialInstructions = if (isFr) protocol.instructionsFr else protocol.instructionsEn,
                warningMessage = (if (isFr) protocol.warningFr else protocol.warningEn) + ageWarning,
                source = DosageSource.LOCAL_PROTOCOL
            )
        }

        if (weight == null) {
            return DosageResult(
                medicineName = params.medicineName.replaceFirstChar { it.uppercase() },
                dosePerTake = "Inconnu",
                frequencyPerDay = 0,
                durationDays = 0,
                specialInstructions = if (isFr)
                    "Poids manquant : Je ne peux pas calculer la dose sans le poids."
                else "Missing weight: Cannot calculate dose without weight.",
                warningMessage = "",
                source = DosageSource.INSUFFICIENT_DATA
            )
        }

        // Dose fixe zinc (mgPerKgPerDay = 0.0)
        if (protocol.mgPerKgPerDay == 0.0) {
            val fixedDose = if ((params.patientAgeMonths ?: 99.0) < 6.0) 10 else 20
            return DosageResult(
                medicineName = params.medicineName.replaceFirstChar { it.uppercase() },
                dosePerTake = "$fixedDose mg",
                frequencyPerDay = protocol.defaultFreqPerDay,
                durationDays = protocol.defaultDurationDays,
                specialInstructions = if (isFr) protocol.instructionsFr else protocol.instructionsEn,
                warningMessage = if (isFr) protocol.warningFr else protocol.warningEn,
                source = DosageSource.LOCAL_PROTOCOL
            )
        }
        // Calcul dose totale journalière
        val totalDayDoseMg = (weight * protocol.mgPerKgPerDay).coerceAtMost(protocol.maxDoseAdultMg)
        val dosePerTakeMg = totalDayDoseMg / protocol.defaultFreqPerDay

        // Vérification âge minimum
        val ageMonths = params.patientAgeMonths ?: ((params.patientAgeYears ?: 99.0) * 12)
        val ageWarning = if (ageMonths < protocol.minAgeMonths && protocol.minAgeMonths > 0) {
            if (isFr) "Ce médicament est déconseillé avant ${protocol.minAgeMonths} mois. Consultez un médecin. " else "Not recommended under ${protocol.minAgeMonths} months. Consult a doctor. "
        } else ""

        return DosageResult(
            medicineName = params.medicineName.replaceFirstChar { it.uppercase() },
            dosePerTake = "${dosePerTakeMg.toInt()} mg",
            frequencyPerDay = protocol.defaultFreqPerDay,
            durationDays = protocol.defaultDurationDays,
            specialInstructions = if (isFr) protocol.instructionsFr else protocol.instructionsEn,
            warningMessage = ageWarning + (if (isFr) protocol.warningFr else protocol.warningEn),
            source = DosageSource.LOCAL_PROTOCOL
        )
    }

    // ── Étape C.2 : Calcul via LLM ────────────────────────────────
    private suspend fun calculateWithLLM(
        params: DosageParams,
        gemmaEngine: GemmaEngine,
        isOnline: Boolean,
        isFr: Boolean,
        currentMeds: List<String> = emptyList()
    ): DosageResult {
        val medsInfo = buildString {
            if (params.medicineName.isNotBlank()) append("Médicament : ${params.medicineName}. ")
            if (params.patientWeightKg != null) append("Poids : ${params.patientWeightKg} kg. ")
            if (params.patientAgeYears != null) append("Âge : ${params.patientAgeYears} ans. ")
            if (params.patientAgeMonths != null) append("Âge : ${params.patientAgeMonths} mois. ")
            if (currentMeds.isNotEmpty()) {
                append(if (isFr) "NOTE : Le patient prend déjà : ${currentMeds.joinToString(", ")}. "
                else "NOTE: Patient is already taking: ${currentMeds.joinToString(", ")}. ")
            }
        }

        val dosagePrompt = if (isFr)
            """$medsInfo
Calcule la posologie selon les recommandations OMS. Vérifie les interactions avec les traitements actuels.
Réponds UNIQUEMENT avec ce JSON (rien d'autre) :
{"dose_per_take":"Xmg ou Xml","frequency_per_day":N,"duration_days":N,"instructions":"paragraphe d'instructions","warning":"avertissement si interaction ou danger"}"""
        else
            """$medsInfo
Calculate dosage per WHO recommendations. Check interactions with current treatments.
Reply ONLY with this JSON (nothing else):
{"dose_per_take":"Xmg or Xml","frequency_per_day":N,"duration_days":N,"instructions":"instruction paragraph","warning":"warning if interaction or danger"}"""

        return try {
            val res = when {
                isOnline -> gemmaEngine.runInferenceForInteraction(dosagePrompt, isFr)
                else -> Result.failure(Exception("OFFLINE_OR_NETWORK_ERROR"))
            }

            if (res.isFailure) {
                val err = res.exceptionOrNull()?.message ?: ""
                if (err == "OFFLINE_OR_NETWORK_ERROR" || err.contains("Socket") || err.contains("abort")) {
                    return DosageResult(
                        medicineName = params.medicineName.replaceFirstChar { it.uppercase() },
                        dosePerTake = "Connexion requise",
                        frequencyPerDay = 0,
                        durationDays = 0,
                        specialInstructions = if (isFr)
                            "Je ne peux pas calculer cette dose sans connexion internet pour consulter les bases de données sécurisées."
                        else "Cannot calculate this dose without an internet connection to consult secure databases.",
                        warningMessage = if (isFr) "Mode hors-ligne" else "Offline mode",
                        source = DosageSource.INSUFFICIENT_DATA
                    )
                }
            }

            val rawResponse = res.getOrDefault("")
            
            // Fallback Llama si Gemini a échoué mais qu'on a Llama
            val finalRaw = if (rawResponse.isBlank() && LlamaEngine.isReady()) {
                when (val r = LlamaEngine.generateResponse(
                    prompt = dosagePrompt,
                    systemPrompt = if (isFr) "Tu es un médecin. Réponds UNIQUEMENT en JSON." else "You are a doctor. Reply ONLY in JSON.",
                    ragContext = ""
                )) {
                    is LlamaResult.Success -> r.text
                    else -> ""
                }
            } else rawResponse

            parseDosageResponse(finalRaw, params, isFr, isOnline)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur LLM dosage: ${e.message}")
            DosageResult(
                medicineName = params.medicineName.replaceFirstChar { it.uppercase() },
                dosePerTake = "Inconnu",
                frequencyPerDay = 0,
                durationDays = 0,
                specialInstructions = if (isFr)
                    "Erreur lors du calcul via l'IA. Veuillez vérifier votre connexion."
                else "AI calculation error. Please check your connection.",
                warningMessage = "",
                source = DosageSource.INSUFFICIENT_DATA
            )
        }
    }

    // ── Parse la réponse JSON du LLM ─────────────────────────────
    private fun parseDosageResponse(
        raw: String,
        params: DosageParams,
        isFr: Boolean,
        isOnline: Boolean
    ): DosageResult {
        return try {
            // Nettoyage pour ne garder que le bloc JSON
            val json = Regex("""\{.*\}""", RegexOption.DOT_MATCHES_ALL).find(raw)?.value ?: ""

            // Regex améliorée : elle cherche la clé, puis capture tout entre guillemets après les ":"
            fun extract(key: String): String? {
                return Regex("""\"$key\"\s*:\s*\"?([^\"\n]+)\"?""").find(json)?.groupValues?.get(1)
                    ?.trim()
            }

            val dose = extract("dose_per_take") ?: "?"

            val finalDose = if (dose == "?" || dose.isBlank()) {
                // L'IA n'a pas pu calculer → on tente le protocole local
                val localProtocol = MedProtocols.findProtocol(params.medicineName)
                val weight = params.patientWeightKg
                if (localProtocol != null && weight != null && weight > 0) {
                    val rawVal = localProtocol.mgPerKg * weight
                    val safe = if (localProtocol.maxPerDayMg > 0 && rawVal > localProtocol.maxPerDayMg)
                        localProtocol.maxPerDayMg else rawVal
                    "${(safe / localProtocol.dosesPerDay).toInt()} mg"
                } else throw Exception("Insufficient data for parsing")
            } else dose

            DosageResult(
                medicineName = params.medicineName.replaceFirstChar { it.uppercase() },
                dosePerTake = finalDose,
                frequencyPerDay = extract("frequency_per_day")?.toIntOrNull() ?: 3,
                durationDays = extract("duration_days")?.toIntOrNull() ?: 5,
                specialInstructions = extract("instructions") ?: "",
                warningMessage = extract("warning") ?: "",
                source = if (isOnline) DosageSource.LLM_GEMINI else DosageSource.LLM_LLAMA
            )
        } catch (_: Exception) {
            return DosageResult(
                medicineName = params.medicineName.replaceFirstChar { it.uppercase() },
                dosePerTake = "Inconnu",
                frequencyPerDay = 0,
                durationDays = 0,
                specialInstructions = if (isFr)
                    "Poids ou médicament non reconnu. Précisez le poids (ex: 15kg)."
                else "Weight or drug not recognized. Please specify weight (e.g. 15kg).",
                warningMessage = "",
                source = DosageSource.INSUFFICIENT_DATA
            )
        }
    }

}