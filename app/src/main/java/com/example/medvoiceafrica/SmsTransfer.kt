package com.example.medvoiceafrica

// ═══════════════════════════════════════════════════════════════════
// SmsTransfer.kt — Transfert automatisé du cas (SMS + WhatsApp)
//   - Pré-remplissage automatique depuis DosageResult + DosageParams
//   - Aperçu du message formaté dans la Dialog avant envoi
//   - Bouton WhatsApp avec fallback si non installé
//   - Message structuré pro (OMS, triage, posologie)
// ═══════════════════════════════════════════════════════════════════

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════════════
// A) SmsTransferHelper — Génération du message + intents
// ═══════════════════════════════════════════════════════════════════
object SmsTransferHelper {

    // ── Génère le message structuré complet ──────────────────────
    fun buildMessage(
        triageLevel: TriageLevel,
        patientDesc: String,
        symptoms: String,
        dosageResult: DosageResult?,
        dosageParams: DosageParams?,
        referredBy: String = "CSPS MedVoice"
    ): String {
        val triageLabel = when (triageLevel) {
            TriageLevel.ROUGE -> "ROUGE — Urgence vitale"
            TriageLevel.JAUNE -> "JAUNE — Consultation 24h"
            TriageLevel.VERT  -> "VERT — Suivi a domicile"
            else -> "Non classe"
        }
        val triageIcon = when (triageLevel) {
            TriageLevel.ROUGE -> "🔴"
            TriageLevel.JAUNE -> "🟡"
            TriageLevel.VERT  -> "🟢"
            else -> "⚪"
        }
        val time = SimpleDateFormat("HH'h'mm", Locale.getDefault()).format(Date())
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        return buildString {
            appendLine("📱 RAPPORT MEDVOICE AFRICA")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("$triageIcon Triage : $triageLabel")
            appendLine()

            // Patient
            if (patientDesc.isNotBlank()) {
                appendLine("👤 Patient : $patientDesc")
            }

            // Symptômes
            if (symptoms.isNotBlank()) {
                appendLine("🩺 Symptome : $symptoms")
            }

            // Posologie calculée
            if (dosageResult != null) {
                appendLine()
                appendLine("💊 Posologie conseillee :")
                appendLine("   Medicament : ${dosageResult.medicineName}")
                appendLine("   Dose/prise : ${dosageResult.dosePerTake}")
                appendLine("   Frequence  : ${dosageResult.frequencyPerDay}x/jour")
                appendLine("   Duree      : ${dosageResult.durationDays} jours")
                if (dosageResult.specialInstructions.isNotBlank()) {
                    appendLine("   Consigne   : ${dosageResult.specialInstructions}")
                }
                if (dosageResult.warningMessage.isNotBlank()) {
                    appendLine("   ⚠️ Alerte : ${dosageResult.warningMessage}")
                }
                val sourceLabel = when (dosageResult.source) {
                    DosageSource.LOCAL_PROTOCOL -> "Protocole OMS local"
                    DosageSource.LLM_GEMINI    -> "Calcul Gemini AI"
                    DosageSource.LLM_LLAMA     -> "Calcul IA locale"
                    DosageSource.INSUFFICIENT_DATA -> "Donnees incompletes"
                }
                appendLine("   Source     : $sourceLabel")
            }

            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("Refere par : $referredBy")
            appendLine("Date : $date a $time")
            append("MedVoice Africa — Protocoles OMS v2026")
        }
    }

    // ── Ouvre l'appli SMS native ──────────────────────────────────
    fun openSmsIntent(
        context: Context,
        doctorPhone: String,
        message: String
    ) {
        try {
            val uri = Uri.parse("smsto:$doctorPhone")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", message)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("SmsTransfer", "Impossible d'ouvrir SMS: ${e.message}")
            Toast.makeText(context, "Impossible d'ouvrir l'application SMS", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Ouvre WhatsApp avec le message pré-rempli ─────────────────
    fun sendToWhatsApp(context: Context, phoneNumber: String, message: String) {
        try {
            // Nettoyage du numéro : supprimer espaces et tirets
            val cleanPhone = phoneNumber.replace(Regex("[\\s\\-()]"), "")
            val encoded = URLEncoder.encode(message, "UTF-8")
            val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encoded"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                setPackage("com.whatsapp")
            }

            // Vérifier si WhatsApp Business aussi
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Fallback WhatsApp Business
                val intentBusiness = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(url)
                    setPackage("com.whatsapp.w4b")
                }
                if (intentBusiness.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intentBusiness)
                } else {
                    // Aucune version de WhatsApp — ouvrir dans le navigateur
                    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(fallback)
                    Toast.makeText(context, "WhatsApp non installe — ouverture dans le navigateur", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SmsTransfer", "Erreur WhatsApp: ${e.message}")
            Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Construit le résumé patient depuis DosageParams ──────────
    fun buildPatientSummary(params: DosageParams?): String {
        if (params == null) return "Informations patient non fournies"

        return buildString {
            // Logique de classification précise (fusion des deux versions)
            val isChild = (params.patientWeightKg != null && params.patientWeightKg < 40.0) ||
                    (params.patientAgeYears != null && params.patientAgeYears < 15.0) ||
                    (params.patientAgeMonths != null && params.patientAgeMonths < 180.0)

            append(if (isChild) "Enfant" else "Adulte")

            // Gestion sécurisée du poids pour le rapport médical
            params.patientWeightKg?.let {
                append(", ${it.toInt()} kg")
            } ?: append(", poids inconnu")

            // Gestion précise de l'âge (Années + Mois pour les petits)
            params.patientAgeYears?.let { append(", ${it.toInt()} ans") }
            params.patientAgeMonths?.let { age ->
                if (age < 24) append(", ${age.toInt()} mois")
            }
        }
    }

    // ── Construit le résumé symptômes depuis DosageResult + ChatMessage ──
    fun buildSymptomsSummary(
        dosageResult: DosageResult?,
        dosageParams: DosageParams?,
        messageText: String,
        triageLevel: TriageLevel
    ): String {
        return buildString {
            val drug = dosageResult?.medicineName ?: dosageParams?.medicineName ?: ""
            if (drug.isNotBlank()) append("Demande posologie $drug")
            // Extraire un mot-clé médical du message (fièvre, diarrhée, etc.)
            val keywords = listOf("fièvre", "diarrhée", "toux", "vomiss", "convuls", "douleur",
                "paludisme", "malaria", "infection", "plaie", "blessure",
                "fever", "cough", "diarrhea", "pain", "malaria")
            val found = keywords.firstOrNull { messageText.lowercase().contains(it) }
            if (found != null) {
                if (isNotBlank()) append(" + ")
                append(found.replaceFirstChar { it.uppercase() })
            }
            val triageStr = when (triageLevel) {
                TriageLevel.ROUGE -> "Urgence vitale"
                TriageLevel.JAUNE -> "Consultation 24h"
                TriageLevel.VERT  -> "Surveillance domicile"
                else -> ""
            }
            if (triageStr.isNotBlank()) {
                if (isNotBlank()) append(" | ")
                append(triageStr)
            }
            if (isBlank()) append("Consultation medicale")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// B) TransferButton — Bouton compact sous la bulle IA
// ═══════════════════════════════════════════════════════════════════
@Composable
fun TransferButton(
    message: ChatMessage,
    colors: MedVoiceColors,
    dosageResult: DosageResult? = null,      // ← NOUVEAU : injecté depuis DosageCard
    dosageParams: DosageParams? = null,      // ← NOUVEAU : pour le résumé patient
) {
    val context = LocalContext.current
    val isFr = Locale.getDefault().language == "fr"
    var showDialog by remember { mutableStateOf(false) }

    val doctorPhone = remember {
        context.getSharedPreferences("medvoice_settings", Context.MODE_PRIVATE)
            .getString("doctor_phone", "+22997000000") ?: "+22997000000"
    }
    val cspsName = remember {
        context.getSharedPreferences("medvoice_settings", Context.MODE_PRIVATE)
            .getString("csps_name", "CSPS MedVoice") ?: "CSPS MedVoice"
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when (message.triageLevel) {
                    TriageLevel.ROUGE -> Color(0xFFE24B4A).copy(alpha = 0.12f)
                    TriageLevel.JAUNE -> Color(0xFFEF9F27).copy(alpha = 0.12f)
                    else -> colors.bgSecondary
                }
            )
            .clickable { showDialog = true }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("✉", fontSize = 12.sp, color = colors.textSecondary)
        Text(
            text = if (isFr) "Transferer le cas" else "Transfer case",
            fontSize = 11.sp,
            color = colors.textSecondary,
            fontWeight = FontWeight.Medium
        )
    }

    if (showDialog) {
        TransferConfirmDialog(
            message = message,
            colors = colors,
            doctorPhone = doctorPhone,
            cspsName = cspsName,
            dosageResult = dosageResult,
            dosageParams = dosageParams,
            onDismiss = { showDialog = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// C) TransferConfirmDialog — Dialog avec pré-remplissage + aperçu
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun TransferConfirmDialog(
    message: ChatMessage,
    colors: MedVoiceColors,
    doctorPhone: String,
    cspsName: String,
    dosageResult: DosageResult?,
    dosageParams: DosageParams?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isFr = Locale.getDefault().language == "fr"

    // ── Pré-remplissage automatique ───────────────────────────────
    var phone by remember { mutableStateOf(doctorPhone) }
    var patient by remember {
        mutableStateOf(SmsTransferHelper.buildPatientSummary(dosageParams))
    }
    var symptoms by remember {
        mutableStateOf(
            SmsTransferHelper.buildSymptomsSummary(
                dosageResult = dosageResult,
                dosageParams = dosageParams,
                messageText = message.text,
                triageLevel = message.triageLevel
            )
        )
    }
    var showPreview by remember { mutableStateOf(false) }

    // ── Message généré en temps réel (réactif aux champs) ────────
    val generatedMessage by remember(phone, patient, symptoms) {
        derivedStateOf {
            SmsTransferHelper.buildMessage(
                triageLevel = message.triageLevel,
                patientDesc = patient,
                symptoms = symptoms,
                dosageResult = dosageResult,
                dosageParams = dosageParams,
                referredBy = cspsName
            )
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.bgSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ── Titre ─────────────────────────────────────────
                Text(
                    text = if (isFr) "Transferer le cas" else "Transfer case",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )

                // ── Badge triage ──────────────────────────────────
                val (triageText, triageColor) = when (message.triageLevel) {
                    TriageLevel.ROUGE -> "ROUGE — Urgence vitale" to Color(0xFFE24B4A)
                    TriageLevel.JAUNE -> "JAUNE — Consultation 24h" to Color(0xFFEF9F27)
                    else -> "VERT — Surveillance" to Color(0xFF1D9E75)
                }
                Surface(
                    color = triageColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Triage : $triageText",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = triageColor
                    )
                }

                // ── Posologie intégrée (si disponible) ───────────
                if (dosageResult != null) {
                    Surface(
                        color = colors.bgPrimary,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = if (isFr) "💊 Posologie incluse" else "💊 Dosage included",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                            Text(
                                text = "${dosageResult.medicineName} — ${dosageResult.dosePerTake} × ${dosageResult.frequencyPerDay}/j pendant ${dosageResult.durationDays}j",
                                fontSize = 12.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                }

                // ── Champs éditables ──────────────────────────────
                listOf(
                    Triple(
                        if (isFr) "📞 Telephone medecin" else "📞 Doctor phone",
                        phone
                    ) { v: String -> phone = v },
                    Triple(
                        if (isFr) "👤 Patient (age, poids, sexe)" else "👤 Patient (age, weight, sex)",
                        patient
                    ) { v: String -> patient = v },
                    Triple(
                        if (isFr) "🩺 Symptomes / Motif" else "🩺 Symptoms / Reason",
                        symptoms
                    ) { v: String -> symptoms = v }
                ).forEach { (label, value, onChange) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, fontSize = 11.sp, color = colors.textSecondary)
                        BasicTextField(
                            value = value,
                            onValueChange = onChange,
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.bgPrimary, RoundedCornerShape(8.dp))
                                .border(
                                    width = 1.dp,
                                    color = colors.accent.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                // ── Aperçu du message (toggle) ────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.bgPrimary)
                        .clickable { showPreview = !showPreview }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showPreview)
                            if (isFr) "Masquer l'aperçu" else "Hide preview"
                        else
                            if (isFr) "👁 Voir le message complet" else "👁 Preview full message",
                        fontSize = 12.sp,
                        color = colors.accent,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (showPreview) {
                    Surface(
                        color = Color(0xFF0A0A0A),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = generatedMessage,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 11.sp,
                            color = Color(0xFF00FF88),
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ── Boutons d'envoi ───────────────────────────────
                // Ligne 1 : SMS (pleine largeur)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF185FA5))
                        .clickable {
                            SmsTransferHelper.openSmsIntent(
                                context = context,
                                doctorPhone = phone.ifBlank { doctorPhone },
                                message = generatedMessage
                            )
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isFr) "📱 Envoyer par SMS" else "📱 Send via SMS",
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Ligne 2 : WhatsApp + Annuler
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // WhatsApp
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF25D366))
                            .clickable {
                                SmsTransferHelper.sendToWhatsApp(
                                    context = context,
                                    phoneNumber = phone.ifBlank { doctorPhone },
                                    message = generatedMessage
                                )
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isFr) "WhatsApp" else "WhatsApp",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Annuler
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.bgPrimary)
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isFr) "Annuler" else "Cancel",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}