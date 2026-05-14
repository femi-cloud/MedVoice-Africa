package com.example.medvoiceafrica

import android.content.Context
import com.example.medvoiceafrica.PdfReportEngine.ReportData
import org.json.JSONArray
import java.util.Locale

object ReportDataMapper {

    fun mapToReportData(
        context: Context,
        sessionId: Long,
        sessionTitle: String,
        messages: List<ChatMessage>,
        dosageResult: DosageResult?,
        dosageParams: DosageParams?,
        currentMedications: List<String>,
        isOffline: Boolean,
        agentName: String = ""
    ): ReportData {
        
        // 1. Détermination du triage global
        val triage = dosageResult?.warningMessage?.let { 
            if (it.contains("INTERDIT") || it.contains("STOP") || it.contains("ROUGE")) "ROUGE" else null
        } ?: messages.lastOrNull { !it.isUser }?.triageLevel?.name ?: "VERT"

        // 2. Extraction des symptômes rapportés (généralement le premier message utilisateur)
        val symptoms = messages.firstOrNull { it.isUser }?.text ?: "Non renseigné"

        // 3. Récupération du conseil Fon si applicable
        val conseilFon = findConseilFon(context, symptoms)

        // 4. Extraction des allergies (si présentes dans le texte)
        val allergies = extractAllergies(messages)

        return ReportData(
            consultationId = sessionId,
            timestamp = System.currentTimeMillis(),
            isOffline = isOffline,
            patientName = "Patient MedVoice", // Par défaut si non extrait
            weightKg = dosageParams?.patientWeightKg?.toFloat(),
            ageYears = dosageParams?.patientAgeYears?.toInt(),
            ageMonths = dosageParams?.patientAgeMonths?.toInt(),
            knownAllergies = allergies,
            currentTreatments = currentMedications,
            triage = triage,
            symptomsReported = symptoms,
            sessionTitle = sessionTitle,
            dosageResult = dosageResult,
            drugInteraction = dosageResult?.warningMessage?.takeIf { it.contains("Interaction", ignoreCase = true) },
            interactionSeverity = if (dosageResult?.warningMessage?.contains("STOP") == true) "ROUGE" else "JAUNE",
            conseilFon = conseilFon,
            agentName = agentName
        )
    }

    private fun findConseilFon(context: Context, text: String): String? {
        return try {
            val jsonString = context.assets.open("fon.json").bufferedReader().use { it.readText() }
            val array = JSONArray(jsonString)
            val lowerText = text.lowercase()
            
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val keywords = obj.getJSONArray("keywords")
                for (j in 0 until keywords.length()) {
                    if (lowerText.contains(keywords.getString(j))) {
                        return obj.getString("conseil_fon")
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun extractAllergies(messages: List<ChatMessage>): String? {
        val keywords = listOf("allergie", "allergique", "allergy", "allergic")
        val message = messages.find { msg -> 
            keywords.any { msg.text.lowercase().contains(it) }
        }
        return message?.text?.let { text ->
            // Tentative d'extraction simple : après le mot "allergie"
            val lower = text.lowercase()
            val index = keywords.map { lower.indexOf(it) }.filter { it != -1 }.minOrNull() ?: -1
            if (index != -1) {
                text.substring(index).take(100)
            } else null
        }
    }
}