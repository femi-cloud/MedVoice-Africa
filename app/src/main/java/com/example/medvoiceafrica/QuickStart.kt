package com.example.medvoiceafrica

// ═══════════════════════════════════════════════════════════════════
// 5A. DÉMARRAGE GUIDÉ — 3 boutons de suggestion au démarrage
//
// Composable à insérer dans MedVoiceChatScreen, juste au-dessus
// de la zone de saisie, visible UNIQUEMENT quand messages.size == 1
// (le message de bienvenue).
//
// INTÉGRATION dans MedVoiceChatScreen (MainActivity.kt) :
//   Juste avant "// ── Input bar ─────────────────────────────────"
//   (ligne ~656), ajouter :
//
//   if (messages.size == 1) {
//       QuickStartButtons(
//           colors = colors,
//           onSuggestion = { text ->
//               doSend(text, null, messages, db, activeSessionId,
//                   onSendMessage, { isLoading = it }, { inputText = "" },
//                   onClearPendingImage,
//                   { id -> activeSessionId = id; onSessionCreated(id) }, scope)
//           },
//           onPrefill = { text -> inputText = text }
//       )
//   }
// ═══════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.vector.ImageVector

data class QuickAction(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val action: String,     // texte envoyé directement comme message
    val prefill: String = "" // texte pré-rempli dans le champ (si action == "")
)

@Composable
fun QuickStartButtons(
    colors: MedVoiceColors,
    onSuggestion: (String) -> Unit,  // envoie directement
    onPrefill: (String) -> Unit       // pré-remplit le champ texte
) {
    val isFr = Locale.getDefault().language == "fr"

    val actions = if (isFr) listOf(
        QuickAction(
            icon = Icons.Default.Search,
            title = "Analyser une lésion cutanée",
            subtitle = "Photo + description",
            action = "",
            prefill = "Analyser une lésion cutanée : "
        ),
        QuickAction(
            icon = Icons.Default.Mic,
            title = "Décrire des symptômes",
            subtitle = "Vocal ou texte libre",
            action = "",
            prefill = "Le patient présente les symptômes suivants : "
        ),
        QuickAction(
            icon = Icons.Default.Info,
            title = "Évaluer une fièvre enfant",
            subtitle = "Triage pédiatrique guidé",
            action = "Évaluer la fièvre d'un enfant. Donne-moi le protocole de triage pédiatrique OMS étape par étape."
        )
    ) else listOf(
        QuickAction(
            icon = Icons.Default.Search,
            title = "Analyze skin lesion",
            subtitle = "Photo + description",
            action = "",
            prefill = "Analyze skin lesion: "
        ),
        QuickAction(
            icon = Icons.Default.Mic,
            title = "Describe symptoms",
            subtitle = "Voice or free text",
            action = "",
            prefill = "The patient presents the following symptoms: "
        ),
        QuickAction(
            icon = Icons.Default.Info,
            title = "Evaluate child fever",
            subtitle = "Guided pediatric triage",
            action = "Evaluate a child's fever. Give me the WHO pediatric triage protocol step by step."
        )
    )

    // Couleurs des icônes par action
    val iconBgColors = listOf(
        Color(0xFF3d1a1a), // rouge foncé pour photo
        Color(0xFF0f1f35), // bleu foncé pour micro
        Color(0xFF162614)  // vert foncé pour thermomètre
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        actions.forEachIndexed { i, action ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgSecondary)
                    .clickable {
                        if (action.action.isNotBlank()) {
                            onSuggestion(action.action)
                        } else {
                            onPrefill(action.prefill)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBgColors.getOrElse(i) { colors.bgPrimary }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        action.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Text(
                        action.subtitle,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
                Text("→", fontSize = 14.sp, color = colors.textSecondary)
            }
        }
    }
}