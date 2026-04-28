package com.example.medvoiceafrica

// ═══════════════════════════════════════════════════════════════════
// 2. BOUTON SMS "TRANSFÉRER LE CAS"
//
// Ce fichier contient :
//   A) SmsTransferHelper  → construit le message + ouvre l'appli SMS
//   B) TransferButton     → composable Compose à insérer dans ChatBubble
//
// DÉPENDANCES dans AndroidManifest.xml — ajouter si absent :
//   <uses-permission android:name="android.permission.SEND_SMS"/>
//   (optionnel — Intent.ACTION_SENDTO n'exige pas la permission,
//    c'est l'appli SMS native qui gère l'envoi)
//
// INTÉGRATION dans ChatBubble (MainActivity.kt, ~ligne 779) :
//   Ajouter après les boutons ⧉ et 🔊 existants :
//
//   if (!message.isUser && message.triageLevel != TriageLevel.UNKNOWN
//       && message.triageLevel != TriageLevel.VERT) {
//       TransferButton(message = message, colors = colors)
//   }
// ═══════════════════════════════════════════════════════════════════

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*

// ── A) SmsTransferHelper ──────────────────────────────────────────
object SmsTransferHelper {

    /**
     * Ouvre l'application SMS native avec un message pré-rempli.
     * Aucune permission requise — c'est l'appli SMS qui envoie.
     *
     * @param context     Contexte Android
     * @param doctorPhone Numéro du médecin référent (ex: "+22997000000")
     * @param triageLevel Niveau de triage du cas
     * @param symptoms    Symptômes principaux détectés
     * @param patientDesc Description rapide du patient
     * @param referredBy  CSPS / Centre de l'agent
     */
    fun openSmsIntent(
        context: Context,
        doctorPhone: String,
        triageLevel: TriageLevel,
        symptoms: String,
        patientDesc: String = "",
        referredBy: String = "CSPS MedVoice"
    ) {
        val triageLabel = when (triageLevel) {
            TriageLevel.ROUGE -> "🔴 ROUGE — Urgence vitale"
            TriageLevel.JAUNE -> "🟡 JAUNE — Consultation 24h"
            TriageLevel.VERT  -> "🟢 VERT — Suivi à domicile"
            else -> "⚪ Non classé"
        }
        val time = SimpleDateFormat("HH'h'mm", Locale.getDefault()).format(Date())
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        val body = buildString {
            appendLine("🚨 MedVoice Africa — ALERTE MÉDICALE")
            appendLine("────────────────────────")
            if (patientDesc.isNotBlank()) appendLine("Patient : $patientDesc")
            appendLine("Triage : $triageLabel")
            appendLine("Symptômes : $symptoms")
            appendLine("────────────────────────")
            appendLine("Référé par : $referredBy")
            appendLine("Date : $date à $time")
            appendLine("────────────────────────")
            append("Généré par MedVoice Africa (Protocoles OMS v2026)")
        }

        try {
            val uri = Uri.parse("smsto:$doctorPhone")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", body)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("SmsTransfer", "Impossible d'ouvrir SMS: ${e.message}")
        }
    }
}

// ── B) TransferButton — Composable Compose ────────────────────────
@Composable
fun TransferButton(
    message: ChatMessage,
    colors: MedVoiceColors
) {
    val context = LocalContext.current
    val isFr = Locale.getDefault().language == "fr"
    var showDialog by remember { mutableStateOf(false) }

    // Numéro du médecin référent — à stocker dans SharedPreferences en prod
    // Pour l'instant hardcodé pour la démo
    val doctorPhone = remember {
        context.getSharedPreferences("medvoice_settings", Context.MODE_PRIVATE)
            .getString("doctor_phone", "+22997000000") ?: "+22997000000"
    }

    // Bouton compact sous la bulle IA
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
            text = if (isFr) "Transférer le cas" else "Transfer case",
            fontSize = 11.sp,
            color = colors.textSecondary,
            fontWeight = FontWeight.Medium
        )
    }

    // Dialog de confirmation avant envoi
    if (showDialog) {
        TransferConfirmDialog(
            message = message,
            colors = colors,
            doctorPhone = doctorPhone,
            onConfirm = { phone, patient, symptoms ->
                showDialog = false
                SmsTransferHelper.openSmsIntent(
                    context = context,
                    doctorPhone = phone,
                    triageLevel = message.triageLevel,
                    symptoms = symptoms,
                    patientDesc = patient,
                    referredBy = context.getSharedPreferences("medvoice_settings", Context.MODE_PRIVATE)
                        .getString("csps_name", "CSPS MedVoice") ?: "CSPS MedVoice"
                )
            },
            onDismiss = { showDialog = false }
        )
    }
}

// ── Dialog de confirmation d'envoi ───────────────────────────────
@Composable
private fun TransferConfirmDialog(
    message: ChatMessage,
    colors: MedVoiceColors,
    doctorPhone: String,
    onConfirm: (phone: String, patient: String, symptoms: String) -> Unit,
    onDismiss: () -> Unit
) {
    val isFr = Locale.getDefault().language == "fr"
    var phone by remember { mutableStateOf(doctorPhone) }
    var patient by remember { mutableStateOf("") }
    var symptoms by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.bgSecondary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Text(
                    text = if (isFr) "Transférer le cas" else "Transfer case",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )

                // Niveau de triage affiché
                val (triageText, triageColor) = when (message.triageLevel) {
                    TriageLevel.ROUGE -> "ROUGE — Urgence vitale" to Color(0xFFE24B4A)
                    TriageLevel.JAUNE -> "JAUNE — Consultation 24h" to Color(0xFFEF9F27)
                    else -> "VERT" to Color(0xFF1D9E75)
                }
                Surface(
                    color = triageColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Triage : $triageText",
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = triageColor
                    )
                }

                // Champs éditables
                listOf(
                    Triple(if (isFr) "Téléphone médecin" else "Doctor phone", phone) { v: String -> phone = v },
                    Triple(if (isFr) "Patient (âge, sexe)" else "Patient (age, sex)", patient) { v: String -> patient = v },
                    Triple(if (isFr) "Symptômes principaux" else "Main symptoms", symptoms) { v: String -> symptoms = v }
                ).forEach { (label, value, onChange) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, fontSize = 11.sp, color = colors.textSecondary)
                        androidx.compose.foundation.text.BasicTextField(
                            value = value,
                            onValueChange = onChange,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = colors.textPrimary, fontSize = 13.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.bgPrimary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }

                // Boutons
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Annuler
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.bgPrimary)
                            .clickable { onDismiss() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isFr) "Annuler" else "Cancel",
                            fontSize = 13.sp, color = colors.textSecondary
                        )
                    }
                    // Envoyer SMS
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF185FA5))
                            .clickable {
                                onConfirm(
                                    phone.ifBlank { doctorPhone },
                                    patient,
                                    symptoms.ifBlank { "Non précisés" }
                                )
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isFr) "Envoyer SMS" else "Send SMS",
                            fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}