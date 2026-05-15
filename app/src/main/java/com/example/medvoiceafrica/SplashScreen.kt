package com.example.medvoiceafrica

// ═══════════════════════════════════════════════════════════════════
// SplashScreen.kt — Écran de démarrage MedVoice Africa
// Affiché UNE seule fois (SharedPreferences "splash_accepted")
// Contient : logo, disclaimer médical, bouton "J'accepte & Continuer"
// ═══════════════════════════════════════════════════════════════════

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun MedVoiceSplashScreen(
    onAccepted: () -> Unit   // callback → déclenche le vrai app
) {
    val context = LocalContext.current
    val isFr = Locale.getDefault().language == "fr"

    // Animation d'entrée du logo
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Fade-in initial
        logoAlpha.animateTo(1f, animationSpec = tween(600))

        // 3 pulsations puis stop
        repeat(3) {
            logoAlpha.animateTo(0.7f, animationSpec = tween(900))
            logoAlpha.animateTo(1.0f, animationSpec = tween(900))
        }
        // Reste à 1f — plus d'animation, GPU libéré
    }

    // Fond dégradé sombre
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0D1F1A), Color(0xFF111111))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Spacer(Modifier.height(40.dp))

            // ── Logo ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .alpha(logoAlpha.value)
                    .background(Color(0xFF1D9E75), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🏥", fontSize = 40.sp)
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "MedVoice Africa",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                if (isFr) "Assistant médical de terrain · Gemma 4 Edge AI"
                else "Field medical assistant · Gemma 4 Edge AI",
                fontSize = 12.sp,
                color = Color(0xFF1D9E75),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))
            HorizontalDivider(color = Color(0xFF2A2A2A))
            Spacer(Modifier.height(28.dp))

            // ── Carte Disclaimer ──────────────────────────────────
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1C1C1C),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {

                    // Titre avertissement
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFFE24B4A).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚠", fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (isFr) "Avis Important — Outil d'Aide Médicale"
                            else "Important Notice — Medical Aid Tool",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE24B4A)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Points du disclaimer
                    val points = if (isFr) listOf(
                        "🤖" to "MedVoice Africa est une IA conçue pour aider les agents de santé communautaires — elle ne remplace pas l'avis d'un médecin diplômé.",
                        "💊" to "Les suggestions de dosage sont calculées selon les protocoles OMS/PCIME. Vérifiez toujours avant administration.",
                        "🚨" to "En cas d'urgence vitale, contactez immédiatement un centre de santé ou composez le numéro d'urgence local.",
                        "🔒" to "Les données de consultation restent sur l'appareil. Aucune donnée personnelle n'est transmise sans votre accord.",
                        "⚖️" to "La responsabilité clinique finale appartient à l'agent de santé utilisant l'application."
                    ) else listOf(
                        "🤖" to "MedVoice Africa is an AI designed to assist community health workers — it does not replace a licensed doctor.",
                        "💊" to "Dosage suggestions follow WHO/IMCI protocols. Always verify before administration.",
                        "🚨" to "In case of a life-threatening emergency, immediately contact a health center or call local emergency services.",
                        "🔒" to "Consultation data stays on the device. No personal data is transmitted without your consent.",
                        "⚖️" to "Final clinical responsibility belongs to the health worker using the application."
                    )

                    points.forEach { (emoji, text) ->
                        Row(
                            modifier = Modifier.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(emoji, fontSize = 15.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text,
                                fontSize = 12.sp,
                                color = Color(0xFFCCCCCC),
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF2A2A2A))
                    Spacer(Modifier.height(12.dp))

                    // Clause légale
                    Text(
                        if (isFr)
                            "En utilisant cette application, vous reconnaissez avoir lu et accepté ces conditions d'utilisation."
                        else
                            "By using this application, you acknowledge having read and accepted these terms of use.",
                        fontSize = 10.sp,
                        color = Color(0xFF666666),
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Bouton Accepter ───────────────────────────────────
            Button(
                onClick = {
                    context.getSharedPreferences("medvoice_safety", Context.MODE_PRIVATE)
                        .edit().putBoolean("splash_accepted", true).apply()
                    onAccepted()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D9E75))
            ) {
                Text(
                    if (isFr) "✓  Je comprends et j'accepte — Continuer"
                    else "✓  I understand and accept — Continue",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Version
            Spacer(Modifier.height(16.dp))
            Text(
                "MedVoice Africa v1.0  •  Protocoles OMS/PCIME 2026",
                fontSize = 10.sp,
                color = Color(0xFF444444),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}