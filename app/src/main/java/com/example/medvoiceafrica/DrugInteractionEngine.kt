package com.example.medvoiceafrica

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

// ── Résultat d'une vérification d'interaction ────────────────────
data class InteractionResult(
    val hasInteraction: Boolean,
    val severity: InteractionSeverity,
    val message: String,
    val source: InteractionSource
)

enum class InteractionSeverity {
    ROUGE,    // Contre-indication absolue — déclenche TriageAlert
    JAUNE,    // Précaution — avertissement fort
    VERT,     // Compatible — aucun problème connu
    UNKNOWN   // Pas d'info suffisante
}

enum class InteractionSource {
    LOCAL_TABLE,    // Réponse immédiate depuis la table locale
    GEMINI_API,     // Analyse Gemini (online)
    LLAMA_LOCAL,    // Analyse Llama (offline, modèle chargé)
    FALLBACK        // Données insuffisantes
}

// ── Table locale d'interactions (Niveau 1) ───────────────────────
// Clé : paire normalisée de molécules (ordre alphabétique)
// Valeur : InteractionSeverity + message explicatif
private data class InteractionRule(
    val severity: InteractionSeverity,
    val messageFr: String,
    val messageEn: String
)

private val INTERACTION_TABLE: Map<Pair<String, String>, InteractionRule> = buildMap {
    // ── Contre-indications absolues (ROUGE) ───────────────────────
    fun rouge(fr: String, en: String) = InteractionRule(InteractionSeverity.ROUGE, fr, en)
    fun jaune(fr: String, en: String) = InteractionRule(InteractionSeverity.JAUNE, fr, en)
    fun vert(fr: String, en: String)  = InteractionRule(InteractionSeverity.VERT, fr, en)

    put(key("aspirine", "anticoagulant"),    rouge("Aspirine + anticoagulant : risque hémorragique majeur. NE PAS COMBINER.", "Aspirin + anticoagulant: major bleeding risk. DO NOT COMBINE."))
    put(key("aspirine", "warfarine"),         rouge("Aspirine + warfarine : hémorragie potentiellement mortelle.", "Aspirin + warfarin: potentially fatal hemorrhage."))
    put(key("aspirine", "héparine"),          rouge("Aspirine + héparine : risque hémorragique grave.", "Aspirin + heparin: severe bleeding risk."))
    put(key("ibuprofène", "anticoagulant"),   rouge("Ibuprofène + anticoagulant : contre-indication absolue.", "Ibuprofen + anticoagulant: absolute contraindication."))
    put(key("ibuprofène", "warfarine"),       rouge("Ibuprofène + warfarine : hémorragie digestive possible.", "Ibuprofen + warfarin: possible GI hemorrhage."))
    put(key("metformine", "alcool"),          rouge("Metformine + alcool : risque d'acidose lactique grave.", "Metformin + alcohol: risk of severe lactic acidosis."))
    put(key("imaoh", "antidépresseur"),       rouge("IMAO + antidépresseur : syndrome sérotoninergique fatal possible.", "MAOI + antidepressant: potentially fatal serotonin syndrome."))
    put(key("tramadol", "imaoh"),             rouge("Tramadol + IMAO : syndrome sérotoninergique grave.", "Tramadol + MAOI: severe serotonin syndrome."))
    put(key("chloroquine", "quinine"),        rouge("Chloroquine + quinine : allongement QT, risque cardiaque grave.", "Chloroquine + quinine: QT prolongation, severe cardiac risk."))
    put(key("artemether", "quinolone"),       rouge("Artémether + quinolone : arythmie cardiaque possible.", "Artemether + quinolone: possible cardiac arrhythmia."))
    put(key("cotrimoxazole", "warfarine"),    rouge("Cotrimoxazole + warfarine : potentialisation hémorragique.", "Cotrimoxazole + warfarin: hemorrhagic potentiation."))

    // ── Précautions / interactions modérées (JAUNE) ───────────────
    put(key("paracétamol", "alcool"),         jaune("Paracétamol + alcool : risque de toxicité hépatique. Éviter.", "Paracetamol + alcohol: hepatotoxicity risk. Avoid."))
    put(key("amoxicilline", "contraceptif"),  jaune("Amoxicilline peut réduire l'efficacité des contraceptifs oraux.", "Amoxicillin may reduce efficacy of oral contraceptives."))
    put(key("rifampicine", "contraceptif"),   jaune("Rifampicine réduit fortement l'efficacité des contraceptifs.", "Rifampicin significantly reduces contraceptive efficacy."))
    put(key("rifampicine", "antiretroviral"), jaune("Rifampicine + antirétroviraux : ajustement de dose nécessaire.", "Rifampicin + antiretrovirals: dose adjustment required."))
    put(key("metronidazole", "alcool"),       jaune("Métronidazole + alcool : réaction disulfirame (nausées, vomissements).", "Metronidazole + alcohol: disulfiram reaction (nausea, vomiting)."))
    put(key("ciprofloxacine", "calcium"),     jaune("Ciprofloxacine + calcium : absorption réduite. Espacer de 2h.", "Ciprofloxacin + calcium: reduced absorption. Space by 2h."))
    put(key("tétracycline", "lait"),          jaune("Tétracycline + lait/calcium : absorption bloquée. À éviter.", "Tetracycline + milk/calcium: absorption blocked. Avoid."))
    put(key("artémisinine", "pamplemousse"),  jaune("Artémisinine + pamplemousse : absorption modifiée.", "Artemisinin + grapefruit: altered absorption."))
    put(key("amlodipine", "simvastatine"),    jaune("Amlodipine + simvastatine : risque de myopathie à forte dose.", "Amlodipine + simvastatin: myopathy risk at high dose."))
    put(key("furosémide", "aminoside"),       jaune("Furosémide + aminosides : potentialisation de la néphrotoxicité.", "Furosemide + aminoglycosides: potentiated nephrotoxicity."))

    // ── Combinaisons sûres connues (VERT) — pour rassurer ────────
    put(key("paracétamol", "amoxicilline"),   vert("Paracétamol + amoxicilline : combinaison courante sans interaction.", "Paracetamol + amoxicillin: common combination, no interaction."))
    put(key("paracétamol", "ibuprofène"),     jaune("Paracétamol + ibuprofène : alternance possible mais surveiller la dose totale.", "Paracetamol + ibuprofen: alternation possible but monitor total dose."))
    put(key("sro", "zinc"),                   vert("SRO + zinc : combinaison OMS recommandée contre la diarrhée.", "ORS + zinc: WHO-recommended combination for diarrhea."))
    put(key("paracétamol", "metoclopramide"), vert("Paracétamol + métoclopramide : association habituelle sans problème.", "Paracetamol + metoclopramide: usual association, no problem."))
}

// Crée une clé normalisée (ordre alphabétique pour éviter les doublons)
private fun key(a: String, b: String): Pair<String, String> =
    if (a < b) Pair(a, b) else Pair(b, a)

// ═══════════════════════════════════════════════════════════════════
// DrugInteractionEngine
// ═══════════════════════════════════════════════════════════════════
object DrugInteractionEngine {

    private const val TAG = "DrugInteractionEngine"

    private fun String.stripAccents(): String {
        val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        return Regex("\\p{InCombiningDiacriticalMarks}+").replace(normalized, "")
    }

    private val DRUG_NAME_MAP = mapOf(
        // Antibiotiques
        "amoxicillin" to "amoxicilline", "amoxil" to "amoxicilline", "clamoxyl" to "amoxicilline",
        "cotrimoxazol" to "cotrimoxazole", "bactrim" to "cotrimoxazole", "septrin" to "cotrimoxazole",
        "metronidazol" to "métronidazole", "flagyl" to "métronidazole",
        "ciprofloxacin" to "ciprofloxacine", "cipro" to "ciprofloxacine",
        "tetracyclin" to "tétracycline", "doxycyclin" to "tétracycline",
        "rifampicin" to "rifampicine", "rifadin" to "rifampicine",
        // Antipaludiques
        "chloroquin" to "chloroquine", "nivaquine" to "chloroquine",
        "artemethe" to "artémether", "coartem" to "artémether",
        "artemisin" to "artémisinine", "artesun" to "artémisinine",
        "quinin" to "quinine",
        // Antidouleurs / AINS
        "acetaminoph" to "paracétamol", "paracetamol" to "paracétamol", "doliprane" to "paracétamol",
        "ibuprofen" to "ibuprofène", "advil" to "ibuprofène", "brufen" to "ibuprofène",
        "aspirin" to "aspirine", "aspégic" to "aspirine",
        // Antidiabétiques
        "metformin" to "metformine", "glucophage" to "metformine",
        // Anticoagulants
        "warfarin" to "warfarine", "coumadin" to "warfarine",
        "heparin" to "héparine",
        // Divers
        "furosemid" to "furosémide", "lasilix" to "furosémide",
        "amlodipine" to "amlodipine", "amlor" to "amlodipine",
        "simvastatin" to "simvastatine", "zocor" to "simvastatine",
        "tramadol" to "tramadol", "contraceptiv" to "contraceptif",
        "anticoagulant" to "anticoagulant", "antiretroviral" to "antiretroviral",
        "antidepressant" to "antidépresseur", "antidepresseur" to "antidépresseur",
        "maoi" to "imaoh", "imao" to "imaoh",
        "quinolone" to "quinolone", "aminoside" to "aminoside",
        "calcium" to "calcium", "alcool" to "alcool", "alcohol" to "alcool",
        "pamplemousse" to "pamplemousse", "grapefruit" to "pamplemousse",
        "lait" to "lait", "milk" to "lait",
        "zinc" to "zinc", "sro" to "sro", "ors" to "sro",
    )

    suspend fun checkInteraction(
        scannedText: String,
        scannedBarcode: String,
        currentMeds: List<String>,
        gemmaEngine: GemmaEngine,
        isOnline: Boolean,
        isFr: Boolean
    ): InteractionResult = withContext(Dispatchers.IO) {

        // 1. Extraire le nom de molécule depuis le texte OCR
        val scannedMolecule = extractMoleculeName(scannedText + " " + scannedBarcode)

        if (scannedMolecule.isBlank()) {
            Log.d(TAG, "Impossible d'identifier la molécule dans : $scannedText")
            return@withContext InteractionResult(
                hasInteraction = false,
                severity = InteractionSeverity.UNKNOWN,
                message = if (isFr)
                    "Impossible d'identifier la molécule. Vérifiez manuellement le nom du médicament."
                else
                    "Unable to identify the molecule. Please check the drug name manually.",
                source = InteractionSource.FALLBACK
            )
        }

        Log.d(TAG, "Molécule identifiée : $scannedMolecule | Médicaments actuels : $currentMeds")

        // 2. Niveau 1 — Table locale (immédiat)
        val localResult = checkLocalTable(scannedMolecule, currentMeds, isFr)
        if (localResult != null) {
            Log.d(TAG, "Résultat local : ${localResult.severity}")
            return@withContext localResult
        }

        // 3. Niveau 2 — LLM (Gemini online → Llama offline)
        return@withContext checkWithLLM(scannedMolecule, currentMeds, gemmaEngine, isOnline, isFr)
    }

    // ── Niveau 1 : Table locale ───────────────────────────────────
    fun checkLocalTable(
        scannedMol: String,
        currentMeds: List<String>,
        isFr: Boolean
    ): InteractionResult? {
        val normalizedScan = normalizeDrugName(scannedMol)

        for (med in currentMeds) {
            val normalizedMed = normalizeDrugName(med)
            val tableKey = key(normalizedScan, normalizedMed)
            val rule = INTERACTION_TABLE[tableKey] ?: continue

            Log.d(TAG, "Interaction trouvée dans la table locale : $tableKey → ${rule.severity}")
            return InteractionResult(
                hasInteraction = rule.severity != InteractionSeverity.VERT,
                severity = rule.severity,
                message = if (isFr) rule.messageFr else rule.messageEn,
                source = InteractionSource.LOCAL_TABLE
            )
        }
        return null // Pas trouvé dans la table
    }

    // ── Niveau 2 : LLM ───────────────────────────────────────────
    private suspend fun checkWithLLM(
        scannedMol: String,
        currentMeds: List<String>,
        gemmaEngine: GemmaEngine,
        isOnline: Boolean,
        isFr: Boolean
    ): InteractionResult {
        val medsStr = currentMeds.joinToString(", ").ifBlank {
            return InteractionResult(
                hasInteraction = false,
                severity = InteractionSeverity.UNKNOWN,
                message = if (isFr)
                    "Aucun médicament en cours enregistré. Ajoutez vos médicaments actuels pour une vérification."
                else
                    "No current medications recorded. Add your current medications to enable checking.",
                source = InteractionSource.FALLBACK
            )
        }

        val interactionPrompt = if (isFr)
            """Le patient prend actuellement : $medsStr.
Il s'apprête à prendre : $scannedMol.
Y a-t-il une interaction médicamenteuse dangereuse ?
Réponds UNIQUEMENT avec ce format JSON (rien d'autre) :
{"severity":"ROUGE|JAUNE|VERT","message":"Explication courte en français (max 2 phrases)"}"""
        else
            """The patient currently takes: $medsStr.
They are about to take: $scannedMol.
Is there a dangerous drug interaction?
Reply ONLY with this JSON format (nothing else):
{"severity":"ROUGE|JAUNE|VERT","message":"Short explanation in English (max 2 sentences)"}"""

        return try {
            val llmResponse: String = when {
                isOnline -> {
                    // Gemini API
                    val result = gemmaEngine.runInferenceForInteraction(interactionPrompt, isFr)
                    result.getOrDefault("")
                }
                LlamaEngine.isReady() -> {
                    // Llama local
                    val result = LlamaEngine.generateResponse(
                        prompt = interactionPrompt,
                        systemPrompt = if (isFr)
                            "Tu es un pharmacologue. Réponds UNIQUEMENT en JSON. Ne donne aucune autre information."
                        else
                            "You are a pharmacologist. Reply ONLY in JSON. Give no other information.",
                        ragContext = ""
                    )
                    when (result) {
                        is LlamaResult.Success -> result.text
                        is LlamaResult.Fallback -> ""
                    }
                }
                else -> ""
            }

            parseInteractionResponse(llmResponse, scannedMol, medsStr, isFr, isOnline)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur LLM interaction: ${e.message}")
            InteractionResult(
                hasInteraction = false,
                severity = InteractionSeverity.UNKNOWN,
                message = if (isFr)
                    "Impossible de vérifier l'interaction. Consultez un pharmacien."
                else
                    "Unable to check interaction. Consult a pharmacist.",
                source = InteractionSource.FALLBACK
            )
        }
    }

    // ── Parse la réponse JSON du LLM ─────────────────────────────
    private fun parseInteractionResponse(
        raw: String,
        scannedMol: String,
        currentMeds: String,
        isFr: Boolean,
        isOnline: Boolean
    ): InteractionResult {
        return try {
            // Extraire le JSON même si le LLM a ajouté du texte autour
            val jsonMatch = Regex("""\{[^}]+\}""").find(raw)?.value ?: ""
            val severityMatch = Regex("\"severity\"\\s*:\\s*\"(ROUGE|JAUNE|VERT)\"").find(jsonMatch)?.groupValues?.get(1)
            val messageMatch = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(jsonMatch)?.groupValues?.get(1)

            val severity = when (severityMatch) {
                "ROUGE" -> InteractionSeverity.ROUGE
                "JAUNE" -> InteractionSeverity.JAUNE
                "VERT"  -> InteractionSeverity.VERT
                else    -> InteractionSeverity.UNKNOWN
            }
            val message = messageMatch ?: if (isFr)
                "Résultat incertain. Consultez un professionnel de santé."
            else
                "Uncertain result. Consult a healthcare professional."

            InteractionResult(
                hasInteraction = severity == InteractionSeverity.ROUGE || severity == InteractionSeverity.JAUNE,
                severity = severity,
                message = message,
                source = if (isOnline) InteractionSource.GEMINI_API else InteractionSource.LLAMA_LOCAL
            )
        } catch (_: Exception) {
            InteractionResult(
                hasInteraction = false,
                severity = InteractionSeverity.UNKNOWN,
                message = if (isFr) "Vérification manuelle recommandée." else "Manual verification recommended.",
                source = InteractionSource.FALLBACK
            )
        }
    }

    // ── Extraction du nom de molécule depuis le texte OCR ─────────
    fun extractMoleculeName(ocrText: String): String {
        // 1. Nettoyage de base
        val lowerText = ocrText.lowercase().trim()

        // 2. Normalisation (on enlève les accents : "paracétamol" -> "paracetamol")
        val normalizedText = lowerText.stripAccents()

        // 3. Suppression des unités et bruits courants pour ne pas polluer l'extraction
        val cleanedForSearch = normalizedText
            .replace(Regex("[0-9]+\\s*(mg|ml|g|mcg|ug|%|cp|gel|tablet|capsule|comprime|suppositoire)"), " ")
            .replace(Regex("[^a-z\\s-]"), " ") // On ne garde que les lettres simples a-z
            .trim()

        // 4. Recherche dans la DRUG_NAME_MAP
        // On compare le texte SANS ACCENTS avec les clés SANS ACCENTS
        for ((key, normalizedMolecule) in DRUG_NAME_MAP) {
            if (cleanedForSearch.contains(key.stripAccents())) {
                return normalizedMolecule // Renvoie le nom "propre" avec accents pour le protocole
            }
        }

        // 5. Fallback intelligent : filtrer les mots "bruit" pour éviter le bug "enfant"
        val noiseWords = listOf("enfant", "bebe", "poids", "kilos", "matin", "midi", "soir", "donner")

        return cleanedForSearch.split(Regex("\\s+"))
            .filter { it.length > 4 } // Doit être assez long
            .filter { it !in noiseWords } // Ne doit pas être un mot de structure
            .firstOrNull() ?: ""
    }

    // ── Normalisation d'un nom de médicament ─────────────────────
    fun normalizeDrugName(name: String): String {
        val normalizedInput = name.lowercase().trim().stripAccents()
        // Cherche d'abord une correspondance directe dans le dictionnaire
        for ((key, normalized) in DRUG_NAME_MAP) {
            if (normalizedInput.contains(key.stripAccents()) || key.stripAccents().contains(normalizedInput)) return normalized
        }
        return normalizedInput
    }
}