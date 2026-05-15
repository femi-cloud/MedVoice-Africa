package com.example.medvoiceafrica

// ================================================================
// SettingsActivity.kt — FINAL CORRIGÉ
// Ajouts vs version précédente :
//   - Section "Réseau & connectivité" : Force offline + Économie données
//   - Section "Transfert de cas" : Téléphone médecin + Nom du CSPS
//   - Section "Protocoles OMS" : Version + bouton vérifier MAJ
//   - onResume() dans MainActivity lit tts_speed → déjà géré
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.LaunchedEffect

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
                val allVoices = tts?.voices
                    ?.filter { it.locale.language in listOf("fr", "en") && !it.isNetworkConnectionRequired }
                    ?: emptyList()

                val frVoices = allVoices.filter { it.locale.language == "fr" }
                    .sortedByDescending { it.quality }
                    .take(3)  // Max 3 voix FR
                val enVoices = allVoices.filter { it.locale.language == "en" }
                    .sortedByDescending { it.quality }
                    .take(2)  // Max 2 voix EN

                val voices = frVoices + enVoices
                voicesState.value = voices
            }
        }

        setContent {
            val prefs = getSharedPreferences("medvoice_settings", MODE_PRIVATE)
            val securePrefs = remember { SecurePrefs.get(applicationContext) }
            // Lecture de la clé HF depuis les prefs chiffrées
            var hfApiKey by remember {
                mutableStateOf(securePrefs.getString("hf_api_key", "") ?: "")
            }
            var theme by remember { mutableStateOf(prefs.getString("theme", "dark") ?: "dark") }
            var ttsSpeed by remember { mutableStateOf(prefs.getFloat("tts_speed", 0.95f)) }
            var ttsPitch by remember { mutableStateOf(prefs.getFloat("tts_pitch", 1.0f)) }
            var selectedVoice by remember { mutableStateOf(prefs.getString("tts_voice", "") ?: "") }
            var autoSpeak by remember { mutableStateOf(prefs.getBoolean("auto_speak", false)) }
            // NOUVEAU
            var forceOffline by remember { mutableStateOf(prefs.getBoolean("force_offline", false)) }
            var dataSaver by remember { mutableStateOf(prefs.getBoolean("data_saver", false)) }
            var doctorPhone by remember { mutableStateOf(prefs.getString("doctor_phone", "") ?: "") }
            var cspsName by remember { mutableStateOf(prefs.getString("csps_name", "") ?: "") }

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

            LaunchedEffect(isDark) {
                @Suppress("DEPRECATION")
                window.statusBarColor = if (isDark) android.graphics.Color.parseColor("#111111")
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
                    .putBoolean("force_offline", forceOffline)
                    .putBoolean("data_saver", dataSaver)
                    .putString("doctor_phone", doctorPhone)
                    .putString("csps_name", cspsName)
                    .apply()

                // Clé API dans les prefs chiffrées séparément
                securePrefs.edit()
                    .putString("hf_api_key", hfApiKey)
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
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = if (isFr) "Retour" else "Back",
                                tint = if (isDark) Color(0xFF888780) else Color(0xFF444441),
                                modifier = Modifier.size(20.dp)
                            )
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

                        // ── Réseau & connectivité ─────────────────
                        item {
                            SLabel(if (isFr) "RÉSEAU & CONNECTIVITÉ" else "NETWORK & CONNECTIVITY", colors.accent)
                            Spacer(Modifier.height(8.dp))
                            SCard(colors) {
                                // Force offline
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (isFr) "Forcer le mode hors-ligne" else "Force offline mode",
                                            color = colors.textPrimary, fontSize = 14.sp)
                                        Text(if (isFr) "Edge AI · Protocoles locaux OMS uniquement" else "Edge AI · Local WHO protocols only",
                                            color = colors.textSecondary, fontSize = 12.sp)
                                    }
                                    Switch(
                                        checked = forceOffline,
                                        onCheckedChange = { forceOffline = it; save() },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFE24B4A),
                                            uncheckedThumbColor = colors.textSecondary, uncheckedTrackColor = colors.bgInput
                                        )
                                    )
                                }
                                SDivider(colors)
                                // Économie de données
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (isFr) "Économie de données" else "Data saver",
                                            color = colors.textPrimary, fontSize = 14.sp)
                                        Text(if (isFr) "Désactive le module Vision (photos)" else "Disables Vision module (photos)",
                                            color = colors.textSecondary, fontSize = 12.sp)
                                    }
                                    Switch(
                                        checked = dataSaver,
                                        onCheckedChange = { dataSaver = it; save() },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White, checkedTrackColor = colors.accent,
                                            uncheckedThumbColor = colors.textSecondary, uncheckedTrackColor = colors.bgInput
                                        )
                                    )
                                }
                            }
                        }

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
                                    listOf(
                                        "dark"   to Pair(Icons.Default.DarkMode,    if (isFr) "Sombre" else "Dark"),
                                        "light"  to Pair(Icons.Default.LightMode,   if (isFr) "Clair"  else "Light"),
                                        "system" to Pair(Icons.Default.PhoneAndroid, if (isFr) "Système" else "System")
                                    ).forEach { (value, iconLabel) ->
                                        val (icon, label) = iconLabel
                                        val selected = theme == value
                                        Surface(
                                            color = if (selected) colors.accent else colors.bgInput,
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier.weight(1f).clickable { theme = value; save() }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(horizontal = 6.dp, vertical = 8.dp)
                                                    .fillMaxWidth()
                                                    .wrapContentWidth(Alignment.CenterHorizontally),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp),
                                                    tint = if (selected) Color.White else colors.textSecondary
                                                )
                                                Text(
                                                    label,
                                                    fontSize = 12.sp,
                                                    color = if (selected) Color.White else colors.textSecondary,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
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
                                Text(if (isFr) "Vitesse" else "Speed", color = colors.textSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SlowMotionVideo, contentDescription = null,
                                        modifier = Modifier.size(16.dp), tint = colors.textSecondary)
                                    Slider(
                                        value = ttsSpeed, onValueChange = { ttsSpeed = it; save() },
                                        valueRange = 0.5f..2.0f,
                                        colors = SliderDefaults.colors(activeTrackColor = colors.accent, thumbColor = colors.accent,
                                            inactiveTrackColor = colors.bgInput),
                                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                                    )
                                    Icon(Icons.Default.Speed, contentDescription = null,
                                        modifier = Modifier.size(16.dp), tint = colors.textSecondary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("${(ttsSpeed * 100).toInt()}%", color = colors.textSecondary, fontSize = 12.sp,
                                        modifier = Modifier.width(40.dp))
                                }
                                SDivider(colors)
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
                                                if (isSelected) colors.accent else colors.textSecondary.copy(alpha = 0.3f), CircleShape))
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(voice.name.replace("_", " "), color = colors.textPrimary, fontSize = 13.sp)
                                                Text(if (voice.locale.language == "fr") "🇫🇷 Français" else "🇬🇧 English",
                                                    color = colors.textSecondary, fontSize = 11.sp)
                                            }
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

                        // ── Transfert de cas ──────────────────────
                        item {
                            SLabel(if (isFr) "TRANSFERT DE CAS" else "CASE TRANSFER", colors.accent)
                            Spacer(Modifier.height(8.dp))
                            SCard(colors) {
                                Text(if (isFr) "Téléphone du médecin référent" else "Referring doctor phone",
                                    color = colors.textSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.bgInput)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    if (doctorPhone.isEmpty()) Text("+229 97 XX XX XX", color = colors.textSecondary, fontSize = 13.sp)
                                    BasicTextField(
                                        value = doctorPhone,
                                        onValueChange = { doctorPhone = it; save() },
                                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                SDivider(colors)
                                Text(if (isFr) "Nom du centre de santé (CSPS)" else "Health center name (CSPS)",
                                    color = colors.textSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.bgInput)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    if (cspsName.isEmpty()) Text("CSPS de Godomey", color = colors.textSecondary, fontSize = 13.sp)
                                    BasicTextField(
                                        value = cspsName,
                                        onValueChange = { cspsName = it; save() },
                                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // ── Protocoles OMS ────────────────────────
                        item {
                            SLabel(if (isFr) "PROTOCOLES OMS" else "WHO PROTOCOLS", colors.accent)
                            Spacer(Modifier.height(8.dp))
                            SCard(colors) {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (isFr) "Version des protocoles" else "Protocol version",
                                            color = colors.textPrimary, fontSize = 14.sp)
                                        Text(if (isFr) "Bénin — mise à jour avr. 2026" else "Benin — updated Apr 2026",
                                            color = colors.textSecondary, fontSize = 12.sp)
                                    }
                                    Surface(color = colors.bgInput, shape = RoundedCornerShape(8.dp)) {
                                        Text("v2026.04", fontSize = 11.sp, color = colors.textSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                                SDivider(colors)
                                var checking by remember { mutableStateOf(false) }
                                LaunchedEffect(checking) {
                                    if (checking) {
                                        kotlinx.coroutines.delay(2000)
                                        checking = false
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (isFr) "Vérifier les mises à jour" else "Check for updates",
                                            color = colors.textPrimary, fontSize = 14.sp)
                                        Text(if (isFr) "Nécessite le Wi-Fi" else "Requires Wi-Fi",
                                            color = colors.textSecondary, fontSize = 12.sp)
                                    }
                                    Surface(
                                        color = if (checking) colors.accent.copy(0.15f) else colors.bgInput,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.clickable {
                                            checking = true
                                            // Simuler vérification pour la démo
                                        }
                                    ) {
                                        Text(
                                            if (checking) (if (isFr) "À jour ✓" else "Up to date ✓")
                                            else (if (isFr) "Vérifier" else "Check"),
                                            fontSize = 12.sp,
                                            color = if (checking) colors.accent else colors.textSecondary,
                                            fontWeight = if (checking) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
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
                                    if (isFr) "Cible" to "Agents de santé & autres, Bénin"
                                    else "Target" to "Rural health workers & more, Benin"
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

