package com.example.medvoiceafrica

// ================================================================
// LOCATION: app/src/main/java/com/example/medvoiceafrica/SettingsActivity.kt
// ================================================================

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.util.Locale

class SettingsActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null
    private val voicesState = mutableStateOf<List<Voice>>(emptyList())
    private val ttsReadyState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReadyState.value = true
                // Only show French and English voices — simplified
                val voices = tts?.voices
                    ?.filter { it.locale.language in listOf("fr", "en") && !it.isNetworkConnectionRequired }
                    ?.sortedWith(compareBy({ it.locale.language }, { it.name }))
                    ?: emptyList()
                voicesState.value = voices
            }
        }

        setContent {
            val prefs = getSharedPreferences("medvoice_settings", MODE_PRIVATE)
            var theme by remember { mutableStateOf(prefs.getString("theme", "dark") ?: "dark") }
            var ttsSpeed by remember { mutableStateOf(prefs.getFloat("tts_speed", 0.95f)) }
            var ttsPitch by remember { mutableStateOf(prefs.getFloat("tts_pitch", 1.0f)) }
            var selectedVoice by remember { mutableStateOf(prefs.getString("tts_voice", "") ?: "") }
            var autoSpeak by remember { mutableStateOf(prefs.getBoolean("auto_speak", false)) }
            val voices by voicesState
            val ttsReady by ttsReadyState
            val isFr = Locale.getDefault().language == "fr"

            val isDark = when (theme) {
                "light" -> false
                "system" -> {
                    val flags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    flags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
                else -> true
            }

            // Update status bar
            LaunchedEffect(isDark) {
                @Suppress("DEPRECATION")
                window.statusBarColor = if (isDark) android.graphics.Color.parseColor("#0D1F35")
                else android.graphics.Color.parseColor("#FFFFFF")
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDark
            }

            fun save() {
                prefs.edit()
                    .putString("theme", theme)
                    .putFloat("tts_speed", ttsSpeed)
                    .putFloat("tts_pitch", ttsPitch)
                    .putString("tts_voice", selectedVoice)
                    .putBoolean("auto_speak", autoSpeak)
                    .apply()
            }

            MedVoiceTheme(darkTheme = isDark) {
                val colors = LocalMedVoiceColors.current

                Column(modifier = Modifier.fillMaxSize().background(colors.bgPrimary)
                    .statusBarsPadding().navigationBarsPadding()) {

                    // Top bar
                    Row(
                        modifier = Modifier.fillMaxWidth().background(colors.bgTopBar)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { finish() }) {
                            Text("←", fontSize = 20.sp, color = if (isDark) Color(0xFF888780) else Color(0xFF444441))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(if (isFr) "Paramètres" else "Settings",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF1A1A2E))
                    }
                    if (isDark) HorizontalDivider(color = colors.divider, thickness = 0.5.dp)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // ── Apparence ────────────────────────────
                        item {
                            SLabel(if (isFr) "APPARENCE" else "APPEARANCE", colors.accent)
                            Spacer(Modifier.height(8.dp))
                            SCard(colors) {
                                SRow(colors) {
                                    Text(if (isFr) "Thème" else "Theme",
                                        color = colors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("dark" to (if (isFr) "🌙 Sombre" else "🌙 Dark"),
                                        "light" to (if (isFr) "☀️ Clair" else "☀️ Light"),
                                        "system" to (if (isFr) "📱 Système" else "📱 System"))
                                        .forEach { (value, label) ->
                                            val selected = theme == value
                                            Surface(
                                                color = if (selected) colors.accent else colors.bgInput,
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier.weight(1f).clickable { theme = value; save() }
                                            ) {
                                                Text(label, fontSize = 12.sp,
                                                    color = if (selected) Color.White else colors.textSecondary,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                                                        .fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally))
                                            }
                                        }
                                }
                            }
                        }

                        // ── Voix ─────────────────────────────────
                        item {
                            SLabel(if (isFr) "VOIX DE L'IA" else "AI VOICE", colors.accent)
                            Spacer(Modifier.height(8.dp))
                            SCard(colors) {
                                // Auto-speak
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (isFr) "Lecture automatique" else "Auto-read responses",
                                            color = colors.textPrimary, fontSize = 14.sp)
                                        Text(if (isFr) "Lire la réponse IA dès réception" else "Read AI response when received",
                                            color = colors.textSecondary, fontSize = 12.sp)
                                    }
                                    Switch(
                                        checked = autoSpeak, onCheckedChange = { autoSpeak = it; save() },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.accent,
                                            uncheckedThumbColor = colors.textSecondary, uncheckedTrackColor = colors.bgInput)
                                    )
                                }

                                SDivider(colors)

                                // Speed
                                Text(if (isFr) "Vitesse" else "Speed", color = colors.textSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🐢", fontSize = 14.sp)
                                    Slider(
                                        value = ttsSpeed, onValueChange = { ttsSpeed = it; save() },
                                        valueRange = 0.5f..2.0f,
                                        colors = SliderDefaults.colors(activeTrackColor = colors.accent, thumbColor = colors.accent,
                                            inactiveTrackColor = colors.bgInput),
                                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                                    )
                                    Text("🐇", fontSize = 14.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("${(ttsSpeed * 100).toInt()}%", color = colors.textSecondary, fontSize = 12.sp,
                                        modifier = Modifier.width(40.dp))
                                }

                                SDivider(colors)

                                // Pitch
                                Text(if (isFr) "Tonalité" else "Pitch", color = colors.textSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("↓", fontSize = 14.sp, color = colors.textSecondary)
                                    Slider(
                                        value = ttsPitch, onValueChange = { ttsPitch = it; save() },
                                        valueRange = 0.5f..2.0f,
                                        colors = SliderDefaults.colors(activeTrackColor = colors.accent, thumbColor = colors.accent,
                                            inactiveTrackColor = colors.bgInput),
                                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                                    )
                                    Text("↑", fontSize = 14.sp, color = colors.textSecondary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("${(ttsPitch * 100).toInt()}%", color = colors.textSecondary, fontSize = 12.sp,
                                        modifier = Modifier.width(40.dp))
                                }
                            }
                        }

                        // ── Voix disponibles ─────────────────────
                        item {
                            SLabel(if (isFr) "VOIX DISPONIBLES (FR / EN)" else "AVAILABLE VOICES (FR / EN)", colors.accent)
                            Spacer(Modifier.height(8.dp))
                            SCard(colors) {
                                if (!ttsReady) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(16.dp), colors.accent, 2.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(if (isFr) "Chargement des voix..." else "Loading voices...",
                                            color = colors.textSecondary, fontSize = 13.sp)
                                    }
                                } else if (voices.isEmpty()) {
                                    Text(
                                        if (isFr) "Aucune voix FR/EN offline installée.\nAllez dans Paramètres Android → Synthèse vocale → installer des voix."
                                        else "No FR/EN offline voices found.\nGo to Android Settings → Text-to-speech → install voices.",
                                        color = colors.textSecondary, fontSize = 13.sp, lineHeight = 20.sp
                                    )
                                } else {
                                    voices.forEach { voice ->
                                        val isSelected = selectedVoice == voice.name
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) colors.accent.copy(alpha = 0.1f) else Color.Transparent)
                                                .clickable { selectedVoice = voice.name; save() }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(Modifier.size(10.dp).background(
                                                if (isSelected) colors.accent else colors.textSecondary.copy(alpha = 0.3f),
                                                CircleShape))
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(voice.name.replace("_", " "), color = colors.textPrimary, fontSize = 13.sp)
                                                Text(
                                                    if (voice.locale.language == "fr") "🇫🇷 Français" else "🇬🇧 English",
                                                    color = colors.textSecondary, fontSize = 11.sp
                                                )
                                            }
                                            // Preview
                                            Surface(color = colors.bgInput, shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.clickable {
                                                    if (ttsReady) {
                                                        tts?.setVoice(voice)
                                                        tts?.speak(
                                                            if (voice.locale.language == "fr") "Bonjour, je suis MedVoice Africa"
                                                            else "Hello, I am MedVoice Africa",
                                                            TextToSpeech.QUEUE_FLUSH, null, "preview"
                                                        )
                                                    }
                                                }) {
                                                Text("▶ Test", fontSize = 11.sp, color = colors.accent,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── À propos ─────────────────────────────
                        item {
                            SLabel(if (isFr) "À PROPOS" else "ABOUT", colors.accent)
                            Spacer(Modifier.height(8.dp))
                            SCard(colors) {
                                listOf(
                                    "Version" to "1.0.0",
                                    "IA" to "Gemma 4 (Google DeepMind)",
                                    "RAG" to "Protocoles OMS — 10 modules",
                                    "Hackathon" to "Gemma 4 Good — Kaggle 2026",
                                    if (isFr) "Cible" to "Agents de santé ruraux, Bénin"
                                    else "Target" to "Rural health workers, Benin"
                                ).forEach { (label, value) ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                        Text(label, color = colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Text(value, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop(); tts?.shutdown()
    }
}

@Composable private fun SLabel(text: String, color: Color) =
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

@Composable private fun SCard(colors: MedVoiceColors, content: @Composable ColumnScope.() -> Unit) =
    Surface(color = colors.bgSecondary, shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), content = content)
    }

@Composable private fun SRow(colors: MedVoiceColors, content: @Composable RowScope.() -> Unit) =
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content)

@Composable private fun SDivider(colors: MedVoiceColors) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
    Spacer(Modifier.height(12.dp))
}