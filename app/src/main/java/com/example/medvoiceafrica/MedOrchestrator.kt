package com.example.medvoiceafrica

import com.google.gson.Gson

data class AiAction(
    val drug: String? = null,
    val weight_kg: Double? = null,
    val age_years: Int? = null,
    val triage: String? = null,
    val interaction_detected: Boolean = false,
    val missing_data: Boolean = false
)

object MedOrchestrator {
    private val gson = Gson()

    fun extractAndProcess(fullResponse: String): Pair<String, DosageResult?> {
        val dataRegex = Regex("""(?is)\[\[DATA\]\](.*?)(?:\[\[/DATA\]\]|$)""")
        val matchResult = dataRegex.find(fullResponse)

        // Si la balise n'existe pas du tout, on renvoie tout le texte
        if (matchResult == null) {
            return Pair(fullResponse.trim(), null)
        }

        // 2. SÉPARATION PROPRE : Le texte humain est ce qui précède la balise d'ouverture
        val humanText = fullResponse.substring(0, matchResult.range.first).trim()
        val rawJsonPart = matchResult.groupValues[1] // Contenu capturé entre les balises

        // 3. NETTOYAGE DU MARKDOWN : On retire les éventuels ```json générés par le LLM
        val cleanJsonPart = rawJsonPart
            .replace(Regex("""(?i)```json"""), "")
            .replace("```", "")
            .trim()

        return try {
            val action = gson.fromJson(cleanJsonPart, AiAction::class.java)

            when {
                // L'IA signale explicitement qu'il manque des données
                action.missing_data -> Pair(humanText, null)  // humanText contient déjà la question de l'IA

                // Interaction détectée → pas de calcul de dosage
                action.interaction_detected -> Pair(humanText, null)

                // Calcul normal
                action.weight_kg != null && action.drug != null ->
                    Pair(humanText, calculate(action.drug, action.weight_kg))

                else -> Pair(humanText, null)
            }
        } catch (e: Exception) {
            Pair(humanText, null)
        }
    }

    // Normalise un nom de medicament pour matcher les cles de MedProtocols
    // Gere les accents, variantes orthographiques, noms commerciaux
    private fun normalizeDrugKey(drug: String): String {
        var s = drug.lowercase().trim()
        // Supprimer les accents courants (cles MedProtocols parfois sans accent)
        s = s.replace("é", "e").replace("è", "e").replace("ê", "e").replace("ë", "e")
            .replace("à", "a").replace("â", "a").replace("ä", "a")
            .replace("î", "i").replace("ï", "i")
            .replace("ô", "o").replace("ö", "o")
            .replace("ù", "u").replace("û", "u").replace("ü", "u")
            .replace("ç", "c")
        return s
    }

    private fun findProtocol(drug: String): MedProtocols.DosageInfo? {
        val raw = drug.lowercase().trim()
        val normalized = normalizeDrugKey(drug)
        // 1. Correspondance exacte (avec accents)
        MedProtocols.protocols[raw]?.let { return it }
        // 2. Correspondance sans accents
        MedProtocols.protocols[normalized]?.let { return it }
        // 3. Recherche partielle : le nom du medicament est contenu dans une cle
        MedProtocols.protocols.entries.firstOrNull { (key, _) ->
            key.contains(raw) || key.contains(normalized) || raw.contains(key) || normalized.contains(key)
        }?.value?.let { return it }
        return null
    }

    private fun calculate(drug: String, weight: Double): DosageResult? {
        // SECURITE 1 : Rejet des poids delirants ou negatifs
        if (weight <= 0.0 || weight > 150.0) {
            return null
        }

        // SECURITE 2 : Alerte specifique pour les tres petits poids
        val warning = if (weight < 3.0) " Poids critique (<3kg). Prudence extreme." else ""

        // CORRECTION BUG : utiliser findProtocol() au lieu de lookup direct
        // L'IA retourne parfois "paracetamol" sans accent alors que la cle est "paracetamol" ou "paracetamol"
        val protocol = MedProtocols.findProtocol(drug) ?: return null

        val rawDose = protocol.mgPerKg * weight

        // SÉCURITÉ 3 : Plafond journalier (On empêche les overdoses si le poids est élevé)
        val safeDose = if (rawDose > protocol.maxPerDayMg) protocol.maxPerDayMg else rawDose
        val dosePerTake = safeDose / protocol.dosesPerDay

        // On renvoie ton objet DosageResult exact pour déclencher ta DosageCard
        return DosageResult(
            medicineName = drug.replaceFirstChar { it.uppercase() },
            dosePerTake = "${"%.1f".format(dosePerTake)} mg",
            frequencyPerDay = protocol.dosesPerDay,
            durationDays = 3,
            specialInstructions = protocol.instruction,
            warningMessage = warning,
            source = DosageSource.LOCAL_PROTOCOL
        )
    }
}