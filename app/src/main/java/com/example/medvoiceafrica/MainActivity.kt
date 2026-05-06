package com.example.medvoiceafrica

// ═══════════════════════════════════════════════════════════════════
// MainActivity.kt — FINAL CORRIGÉ
// Corrections vs version précédente :
//   - isOnline: collectAsStateWithLifecycle correctement appelé
//   - doSend: context retiré (n'existe pas dans la portée), tts passé en param
//   - topbar Option B: bouton ☰ + chip StatusChip dynamique + ✎ + ⚙
//   - QuickStartButtons intégré (visible quand messages.size == 1)
//   - TransferButton intégré dans ChatBubble pour ROUGE/JAUNE
//   - Badge source OMS intégré dans ChatBubble
//   - Parenthèse en trop ligne 674 corrigée
// ═══════════════════════════════════════════════════════════════════

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.animation.animateContentSize
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.medvoiceafrica.data.EmergencyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import com.example.medvoiceafrica.StatsScreen

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val triageLevel: TriageLevel = TriageLevel.UNKNOWN,
    val imageBitmap: Bitmap? = null
)

class MainActivity : ComponentActivity() {

    private lateinit var gemmaEngine: GemmaEngine
    private lateinit var db: AppDatabase

    private val pendingImageState      = mutableStateOf<Bitmap?>(null)
    private val isListeningState       = mutableStateOf(false)
    private val recognizedTextState    = mutableStateOf("")
    private val isSpeakingState        = mutableStateOf(false)
    private val speakingMessageIdState = mutableStateOf<String?>(null)
    private val themeModeState         = mutableStateOf("dark")
    private val ttsReadyState          = mutableStateOf(false)
    private val isGeneratingState      = mutableStateOf(false)
    private val isConsultationModeState = mutableStateOf(false)

    // FIX: public (pas private) pour accès depuis Compose via LocalContext
    val networkMonitor by lazy { NetworkMonitor(this) }

    private var photoUri: Uri? = null
    var tts: TextToSpeech? = null
    private var currentTtsLang = ""

    private var speechRecognizer: SpeechRecognizer? = null
    private var generationJob: Job? = null



    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.FRENCH); currentTtsLang = "fr"
                val prefs = getSharedPreferences("medvoice_settings", MODE_PRIVATE)
                tts?.setSpeechRate(prefs.getFloat("tts_speed", 0.92f))
                tts?.setPitch(prefs.getFloat("tts_pitch", 1.0f))
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(u: String?) = runOnUiThread { isSpeakingState.value = true }
                    override fun onDone(u: String?) = runOnUiThread { 
                        isSpeakingState.value = false; speakingMessageIdState.value = null 
                        if (isConsultationModeState.value && (u == "ai_response" || u == "ai_error" || u == "triage_rouge" || u == "triage_jaune")) {
                            launchSpeechRecognition()
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(u: String?) = runOnUiThread { 
                        isSpeakingState.value = false; speakingMessageIdState.value = null 
                        if (isConsultationModeState.value && (u == "ai_response" || u == "ai_error" || u == "triage_rouge" || u == "triage_jaune")) {
                            launchSpeechRecognition()
                        }
                    }
                })
                runOnUiThread { ttsReadyState.value = true }
            }
        }
    }

    fun speakText(text: String, messageId: String, lang: String = "fr") {
        val engine = tts
        if (!ttsReadyState.value || engine == null) {
            android.widget.Toast.makeText(this, if (lang == "fr") "Synthèse vocale non prête" else "TTS not ready", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // 1. On nettoie le texte de TOUS les astérisques et tags
        val cleanedText = text.replace("*", "")
            .replace(Regex("\\[TRIAGE:\\w+\\]"), "")
            .replace(Regex("\\p{So}|\\p{Cn}"), "")
            .trim()

        if (cleanedText.isBlank()) return

        // 2. Détection d'accent (Français vs Anglais/Fon)
        val isEnglish = cleanedText.contains(Regex("\\b(the|is|you|how|what)\\b", RegexOption.IGNORE_CASE))
        val targetLang = if (isEnglish) "en" else lang

        // 3. Application de l'accent
        if (targetLang != currentTtsLang) {
            engine.setLanguage(if (targetLang == "en") Locale.ENGLISH else Locale.FRENCH)
            currentTtsLang = targetLang
        }

        speakingMessageIdState.value = messageId
        engine.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, "mv_${messageId.take(8)}")
    }

    fun stopSpeaking() { tts?.stop(); isSpeakingState.value = false; speakingMessageIdState.value = null }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null)
            try { pendingImageState.value = contentResolver.openInputStream(photoUri!!)?.use { BitmapFactory.decodeStream(it) } }
            catch (e: Exception) { android.util.Log.e("Camera", "${e.message}") }
    }
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { try { pendingImageState.value = contentResolver.openInputStream(it)?.use { s -> BitmapFactory.decodeStream(s) } }
        catch (e: Exception) { android.util.Log.e("Gallery", "${e.message}") } }
    }
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isListeningState.value = false
        if (result.resultCode == RESULT_OK) {
            val r = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            if (r.isNotBlank()) recognizedTextState.value = r
        }
    }

    fun launchCamera() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 1001)
            return
        }
        try {
            val file = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            photoUri = uri
            takePhotoLauncher.launch(uri!!)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Erreur Caméra: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    fun launchGallery() = galleryLauncher.launch("image/*")
    fun launchSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { android.widget.Toast.makeText(this, "Reconnaissance vocale non disponible", android.widget.Toast.LENGTH_LONG).show(); return }
        val lang = Locale.getDefault().language
        
        if (isConsultationModeState.value) {
            startProgrammaticSpeechRecognition(lang)
            return
        }

        isListeningState.value = true
        try {
            speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == "fr") "fr-FR" else "en-US")
                putExtra(RecognizerIntent.EXTRA_PROMPT, if (lang == "fr") "Décrivez les symptômes..." else "Describe symptoms...")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: Exception) { isListeningState.value = false }
    }

    private fun startProgrammaticSpeechRecognition(lang: String) {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1002)
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == "fr") "fr-FR" else "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Optimal silence detection for hands-free mode
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }

        speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListeningState.value = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListeningState.value = false }
            override fun onError(error: Int) { 
                isListeningState.value = false 
                if (isConsultationModeState.value && (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH)) {
                    // Hands-free: restart listening if nothing heard
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (isConsultationModeState.value) launchSpeechRecognition()
                    }, 1000)
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                isListeningState.value = false
                if (!matches.isNullOrEmpty() && matches[0].isNotBlank()) {
                    recognizedTextState.value = matches[0]
                } else if (isConsultationModeState.value) {
                    launchSpeechRecognition()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun lancerLeMoteur() {
        lifecycleScope.launch {
            println("Tentative d'allumage de l'IA Locale...")

            // C'est ici qu'on lance la fonction initialize() qu'on vient de corriger
            val etat = LlamaEngine.initialize(applicationContext)

            when (etat) {
                is LlamaState.Ready -> {
                    println("Succès ! Le GGUF est chargé !")
                    // Ici, tu peux débloquer la barre de texte pour discuter avec Gemma
                }
                is LlamaState.Failed -> {
                    println("Echec: ${etat.reason}. Activation du Mode Survie (Lexique JSON).")
                    // Ici, tu affiches ton interface jaune "Mode Sécurité Hors-ligne"
                }
                else -> {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        gemmaEngine = GemmaEngine(this); db = AppDatabase.getInstance(this)
        initTts()
        val initResult = gemmaEngine.initialize()
        lifecycleScope.launch { gemmaEngine.initRag() }
        val prefs = getSharedPreferences("medvoice_settings", MODE_PRIVATE)
        themeModeState.value = prefs.getString("theme", "dark") ?: "dark"
        isConsultationModeState.value = prefs.getBoolean("consultation_mode", false)

        // Forcer transparence complète dès le départ
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // Pour Android 11+ (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS.inv(),
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }

        setContent {
            val themeMode by themeModeState
            val isDark = when (themeMode) { "light" -> false; "system" -> isSystemDark(); else -> true }

            // On observe l'état du réseau directement ici
            val isOnlineState by networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)

            MedVoiceTheme(darkTheme = isDark) {
                MedVoiceApp(
                    initSuccess = initResult.isSuccess, initError = initResult.exceptionOrNull()?.message,
                    db = db, pendingImageState = pendingImageState, isListeningState = isListeningState,
                    recognizedTextState = recognizedTextState, isSpeakingState = isSpeakingState,
                    speakingMessageIdState = speakingMessageIdState, ttsReadyState = ttsReadyState,
                    isGeneratingState = isGeneratingState,
                    isConsultationModeState = isConsultationModeState,
                    onLaunchCamera = { launchCamera() }, onLaunchGallery = { launchGallery() },
                    onLaunchSpeech = { launchSpeechRecognition() },
                    onSpeakText = { text, id, lang -> speakText(text, id, lang) },
                    onStopSpeaking = { stopSpeaking() },
                    onClearPendingImage = { pendingImageState.value = null },
                    onSendMessage = { userInput, bitmap, callback ->
                        generationJob = lifecycleScope.launch {
                            isGeneratingState.value = true
                            callback(gemmaEngine.runInference(userInput, bitmap))
                            isGeneratingState.value = false
                        }
                    },
                    gemmaEngine = gemmaEngine,
                    isOnline = isOnlineState,
                    tts = tts,
                    onStopGeneration = { generationJob?.cancel(); isGeneratingState.value = false },
                    onNewConversation = { gemmaEngine.clearHistory() },
                    onConsultationModeChanged = { enabled: Boolean -> 
                        isConsultationModeState.value = enabled
                        this@MainActivity.getSharedPreferences("medvoice_settings", Context.MODE_PRIVATE).edit().putBoolean("consultation_mode", enabled).apply()
                    }
                )
            }
        }

        // 1. Demande l'autorisation ultime de lire les fichiers (Android 11+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${packageName}")
                startActivity(intent)
                // L'utilisateur devra cocher la case pour ton app, puis revenir en arrière
            } else {
                lancerLeMoteur()
            }
        } else {
            lancerLeMoteur() // Pour les vieux Android, la permission du Manifest suffit souvent
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("medvoice_settings", MODE_PRIVATE)
        themeModeState.value = prefs.getString("theme", "dark") ?: "dark"
        // FIX: appliquer la vitesse TTS en revenant des paramètres
        tts?.setSpeechRate(prefs.getFloat("tts_speed", 0.92f))
        tts?.setPitch(prefs.getFloat("tts_pitch", 1.0f))
    }
    private fun isSystemDark(): Boolean {
        val f = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return f == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    override fun onDestroy() { 
        super.onDestroy()
        tts?.stop(); tts?.shutdown()
        speechRecognizer?.destroy()
        gemmaEngine.close() 
    }
}

// ── Design tokens ─────────────────────────────────────────────────
data class MedVoiceColors(
    val bgPrimary: Color, val bgSecondary: Color, val bgTopBar: Color, val bgInput: Color,
    val textPrimary: Color, val textSecondary: Color, val accent: Color,
    val inputBorder: Color, val divider: Color, val isDark: Boolean
)
val LocalMedVoiceColors = staticCompositionLocalOf {
    MedVoiceColors(Color(0xFF111111), Color(0xFF1C1C1C), Color(0xFF111111), Color(0xFF2A2A2A),
        Color(0xFFECECEC), Color(0xFF888888), Color(0xFF1D9E75), Color(0xFF333333), Color(0xFF2A2A2A), true)
}
@Composable
fun MedVoiceTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) MedVoiceColors(
        Color(0xFF111111), Color(0xFF1C1C1C), Color(0xFF111111), Color(0xFF2A2A2A),
        Color(0xFFECECEC), Color(0xFF888888), Color(0xFF1D9E75), Color(0xFF333333), Color(0xFF2A2A2A), true
    ) else MedVoiceColors(
        Color(0xFFF5F5F5), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFF0F0F0),
        Color(0xFF111111), Color(0xFF666666), Color(0xFF1D9E75), Color(0xFFDDDDDD), Color(0xFFEEEEEE), false
    )
    CompositionLocalProvider(LocalMedVoiceColors provides colors) { MaterialTheme(content = content) }
}

// ── MedVoiceApp ───────────────────────────────────────────────────
@Composable
fun MedVoiceApp(
    initSuccess: Boolean, initError: String?, db: AppDatabase,
    pendingImageState: MutableState<Bitmap?>, isListeningState: MutableState<Boolean>,
    recognizedTextState: MutableState<String>, isSpeakingState: MutableState<Boolean>,
    speakingMessageIdState: MutableState<String?>, ttsReadyState: MutableState<Boolean>,
    isGeneratingState: MutableState<Boolean>, isConsultationModeState: MutableState<Boolean>,
    gemmaEngine: GemmaEngine,
    isOnline: Boolean,
    tts: TextToSpeech?,
    onLaunchCamera: () -> Unit, onLaunchGallery: () -> Unit, onLaunchSpeech: () -> Unit,
    onSpeakText: (String, String, String) -> Unit, onStopSpeaking: () -> Unit,
    onClearPendingImage: () -> Unit, onSendMessage: (String, Bitmap?, (Result<String>) -> Unit) -> Unit,
    onStopGeneration: () -> Unit, onNewConversation: () -> Unit,
    onConsultationModeChanged: (Boolean) -> Unit
) {

    val colors = LocalMedVoiceColors.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sessions by db.sessionDao().getAllSessions().collectAsState(initial = emptyList())
    var currentSessionId by remember { mutableStateOf<Long?>(null) }
    var preloadedMessages by remember { mutableStateOf<List<ChatMessage>?>(null) }
    val isFr = Locale.getDefault().language == "fr"
    var searchQuery by remember { mutableStateOf("") }
    var menuOpenFor by remember { mutableStateOf<Long?>(null) }
    var renameDialog by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var deleteDialog by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    // ── Stats screen toggle ───────────────────────────────────────
    var showStats by remember { mutableStateOf(false) }

    val medicationEntities by db.medicationDao().getAllMedications().collectAsState(initial = emptyList())
    val currentMedications = remember(medicationEntities) { medicationEntities.map { it.name } }

    val filteredSessions = remember(sessions, searchQuery) {
        if (searchQuery.isBlank()) sessions
        else sessions.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    // ── Rename dialog ─────────────────────────────────────────────
    renameDialog?.let { (sid, currentTitle) ->
        var newTitle by remember { mutableStateOf(currentTitle) }
        AlertDialog(
            onDismissRequest = { renameDialog = null },
            containerColor = colors.bgSecondary,
            title = { Text(if (isFr) "Renommer la conversation" else "Rename conversation", color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                OutlinedTextField(
                    value = newTitle, onValueChange = { newTitle = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.accent, unfocusedBorderColor = colors.inputBorder,
                        cursorColor = colors.accent, focusedContainerColor = colors.bgInput, unfocusedContainerColor = colors.bgInput
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTitle.isNotBlank()) scope.launch { db.sessionDao().renameSession(sid, newTitle.trim()) }
                    renameDialog = null
                }) { Text(if (isFr) "Enregistrer" else "Save", color = colors.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { renameDialog = null }) { Text(if (isFr) "Annuler" else "Cancel", color = colors.textSecondary) }
            }
        )
    }

    // ── Delete dialog ─────────────────────────────────────────────
    deleteDialog?.let { sid ->
        AlertDialog(
            onDismissRequest = { deleteDialog = null },
            containerColor = colors.bgSecondary,
            title = { Text(if (isFr) "Supprimer ?" else "Delete?", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(if (isFr) "Cette conversation sera supprimée définitivement." else "This will be permanently deleted.", color = colors.textSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        db.sessionDao().deleteSession(sid); db.sessionDao().deleteMessagesForSession(sid)
                        if (currentSessionId == sid) { currentSessionId = null; preloadedMessages = null }
                    }
                    deleteDialog = null
                }) { Text(if (isFr) "Supprimer" else "Delete", color = Color(0xFFE24B4A), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = null }) { Text(if (isFr) "Annuler" else "Cancel", color = colors.textSecondary) } }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = colors.bgSecondary, modifier = Modifier.width(290.dp)) {
                // ── Drawer header ──────────────────────────────────
                Column(Modifier
                    .fillMaxWidth()
                    .background(colors.bgTopBar)
                    .padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier
                            .size(40.dp)
                            .background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                            Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("MedVoice Africa", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Gemma 4 · Edge AI", color = colors.accent, fontSize = 11.sp)
                        }
                        // Bouton stats
                        IconButton(onClick = {
                            showStats = true
                            scope.launch { drawerState.close() }
                        }) {
                            Text("📊", fontSize = 16.sp, color = colors.textSecondary)
                        }
                        IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                            Text("⚙", fontSize = 18.sp, color = colors.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.accent.copy(alpha = 0.15f))
                            .clickable {
                                onNewConversation()
                                currentSessionId = null
                                preloadedMessages = emptyList()
                                showStats = false
                                scope.launch { drawerState.close() }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✎", fontSize = 15.sp, color = colors.accent)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isFr) "Nouvelle conversation" else "New conversation", color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.bgInput)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("🔍", fontSize = 13.sp); Spacer(Modifier.width(6.dp))
                        Box(Modifier.fillMaxWidth()) {
                            if (searchQuery.isEmpty()) Text(if (isFr) "Rechercher..." else "Search...", color = colors.textSecondary, fontSize = 13.sp)
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery, onValueChange = { searchQuery = it }, singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                if (filteredSessions.isEmpty()) {
                    Box(Modifier
                        .fillMaxWidth()
                        .padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(if (searchQuery.isNotBlank()) "Aucun résultat" else if (isFr) "Aucune conversation" else "No conversations",
                            color = colors.textSecondary, fontSize = 13.sp)
                    }
                } else {
                    Text(if (isFr) "Récents" else "Recent",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    LazyColumn {
                        items(filteredSessions, key = { it.id }) { session ->
                            val triageColor = when (session.triageLevel) {
                                "ROUGE" -> Color(0xFFE24B4A); "JAUNE" -> Color(0xFFEF9F27)
                                "VERT" -> Color(0xFF1D9E75); else -> null
                            }
                            val isSelected = currentSessionId == session.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 1.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable {
                                        scope.launch {
                                            val msgs =
                                                db.messageDao().getMessagesForSession(session.id)
                                            currentSessionId = session.id
                                            showStats = false
                                            preloadedMessages = msgs.map {
                                                ChatMessage(
                                                    text = it.text, isUser = it.isUser,
                                                    triageLevel = try {
                                                        TriageLevel.valueOf(it.triageLevel)
                                                    } catch (_: Exception) {
                                                        TriageLevel.UNKNOWN
                                                    }
                                                )
                                            }
                                            drawerState.close()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (triageColor != null) { Box(Modifier
                                    .size(7.dp)
                                    .background(triageColor, CircleShape)); Spacer(Modifier.width(8.dp)) }
                                else Spacer(Modifier.width(15.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(session.title, color = if (isSelected) colors.accent else colors.textPrimary,
                                        fontSize = 13.sp, maxLines = 1, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
                                    Text(formatTimestamp(session.timestamp), color = colors.textSecondary, fontSize = 11.sp)
                                }
                                Box {
                                    Text("⋯", fontSize = 16.sp, color = colors.textSecondary,
                                        modifier = Modifier
                                            .clickable { menuOpenFor = session.id }
                                            .padding(horizontal = 6.dp, vertical = 4.dp))
                                    DropdownMenu(
                                        expanded = menuOpenFor == session.id,
                                        onDismissRequest = { menuOpenFor = null },
                                        containerColor = colors.bgSecondary
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(if (isFr) "✎  Renommer" else "✎  Rename", color = colors.textPrimary, fontSize = 14.sp) },
                                            onClick = { menuOpenFor = null; renameDialog = Pair(session.id, session.title) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (isFr) "🗑  Supprimer" else "🗑  Delete", color = Color(0xFFE24B4A), fontSize = 14.sp) },
                                            onClick = { menuOpenFor = null; deleteDialog = session.id }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        // ── Affichage conditionnel : Stats ou Chat ────────────────
        if (showStats) {
            StatsScreen(
                db = db,
                colors = colors,
                onBack = { showStats = false }
            )
        } else {
            MedVoiceChatScreen(
                initSuccess = initSuccess, initError = initError, db = db,
                currentSessionId = currentSessionId, preloadedMessages = preloadedMessages,
                pendingImageState = pendingImageState, isListeningState = isListeningState,
                recognizedTextState = recognizedTextState, isSpeakingState = isSpeakingState,
                speakingMessageIdState = speakingMessageIdState, ttsReadyState = ttsReadyState,
                isGeneratingState = isGeneratingState,
                onLaunchCamera = onLaunchCamera, onLaunchGallery = onLaunchGallery,
                onLaunchSpeech = onLaunchSpeech, onSpeakText = onSpeakText, onStopSpeaking = onStopSpeaking,
                onClearPendingImage = onClearPendingImage, onSendMessage = onSendMessage,
                    onStopGeneration = onStopGeneration,
                    onNewConversation = {
                        onNewConversation()
                        currentSessionId = null
                        preloadedMessages = emptyList()
                    },
                    isConsultationModeState = isConsultationModeState,
                    onConsultationModeChanged = { enabled: Boolean -> 
                        isConsultationModeState.value = enabled
                        context.getSharedPreferences("medvoice_settings", Context.MODE_PRIVATE).edit().putBoolean("consultation_mode", enabled).apply()
                    },
                    gemmaEngine = gemmaEngine,
                isOnline = isOnline,
                tts = tts,
                currentMedications = currentMedications,
                onAddMedication = { name -> scope.launch { db.medicationDao().insert(Medication(name = name)) } },
                onRemoveMedication = { name ->
                    scope.launch {
                        val med = medicationEntities.find { it.name == name }
                        if (med != null) db.medicationDao().delete(med)
                    }
                },
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onSessionCreated = { id -> currentSessionId = id }
            )
        }
    }
}

fun formatTimestamp(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000     -> "À l'instant"
        diff < 3_600_000  -> "${diff / 60_000} min"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        else              -> "${diff / 86_400_000}j"
    }
}

// ── MedVoiceChatScreen ────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MedVoiceChatScreen(
    initSuccess: Boolean, initError: String?, db: AppDatabase,
    currentSessionId: Long?, preloadedMessages: List<ChatMessage>?,
    pendingImageState: MutableState<Bitmap?>, isListeningState: MutableState<Boolean>,
    recognizedTextState: MutableState<String>, isSpeakingState: MutableState<Boolean>,
    speakingMessageIdState: MutableState<String?>, ttsReadyState: MutableState<Boolean>,
    isGeneratingState: MutableState<Boolean>, isConsultationModeState: MutableState<Boolean>,
    gemmaEngine: GemmaEngine,
    isOnline: Boolean,
    tts: TextToSpeech?,
    currentMedications: List<String>,
    onAddMedication: (String) -> Unit,
    onRemoveMedication: (String) -> Unit,
    onLaunchCamera: () -> Unit, onLaunchGallery: () -> Unit, onLaunchSpeech: () -> Unit,
    onSpeakText: (String, String, String) -> Unit, onStopSpeaking: () -> Unit,
    onClearPendingImage: () -> Unit, onSendMessage: (String, Bitmap?, (Result<String>) -> Unit) -> Unit,
    onStopGeneration: () -> Unit, onNewConversation: () -> Unit,
    onOpenDrawer: () -> Unit, onSessionCreated: (Long) -> Unit,
    onConsultationModeChanged: (Boolean) -> Unit
) {

    val colors = LocalMedVoiceColors.current
    val isFr = Locale.getDefault().language == "fr"
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val pendingImage by pendingImageState
    val isListening by isListeningState
    val isConsultationMode by isConsultationModeState
    val speakingId by speakingMessageIdState
    val ttsReady by ttsReadyState
    val isGenerating by isGeneratingState
    var recognizedText by recognizedTextState
    var inputText by remember { mutableStateOf("") }
    var detectedDrugName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Auto-start listening when Consultation Mode is turned on
    LaunchedEffect(isConsultationMode) {
        if (isConsultationMode && !isListening && !isSpeakingState.value && !isGenerating && !isLoading) {
            onLaunchSpeech()
        }
    }

    // Lire force_offline depuis SharedPreferences
    val forceOffline = remember {
        context.getSharedPreferences("medvoice_settings", Context.MODE_PRIVATE)
            .getBoolean("force_offline", false)
    }
    val effectivelyOnline = isOnline && !forceOffline

    val greeting = if (isFr)
        "Bonjour ! Je suis MedVoice Africa, votre assistant médical.\n\nJe peux vous aider avec le triage, les dosages, les interactions médicamenteuses et les protocoles WHO.\n\nDécrivez un patient, envoyez une photo ou utilisez le micro 🎤"
    else "Hello! I'm MedVoice Africa, your medical assistant.\n\nDescribe a patient, send a photo or use the microphone 🎤"
    val welcomeText = if (initSuccess) greeting else "Erreur : $initError"

    var showScanner by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf(ChatMessage(text = welcomeText, isUser = false)) }

    var pendingDosageResult by remember { mutableStateOf<DosageResult?>(null) }
    var lastDosageParams by remember { mutableStateOf<DosageParams?>(null) }

    LaunchedEffect(inputText) {
        detectedDrugName = if (inputText.length > 3)
            DosageFunctionCalling.extractDosageParams(inputText)?.medicineName?.takeIf { it.isNotBlank() }
        else null
    }


    var activeSessionId by remember { mutableStateOf(currentSessionId) }
    LaunchedEffect(currentSessionId) { activeSessionId = currentSessionId }

    LaunchedEffect(preloadedMessages) {
        when {
            preloadedMessages == null -> { }
            preloadedMessages.isEmpty() -> {
                messages.clear()
                messages.add(ChatMessage(text = welcomeText, isUser = false))
            }
            else -> {
                messages.clear()
                messages.addAll(preloadedMessages)
            }
        }
    }

    LaunchedEffect(recognizedText) { 
        if (recognizedText.isNotBlank()) { 
            val textToSubmit = recognizedText
            inputText = textToSubmit
            recognizedText = "" 
            if (isConsultationMode) {
                doSend(textToSubmit, pendingImage, messages, db, activeSessionId,
                    onSendMessage, { b: Boolean -> isLoading = b }, { inputText = "" }, onClearPendingImage,
                    { id: Long -> activeSessionId = id; onSessionCreated(id) }, scope, tts = tts, context = context,
                    onDosageResult = { res: DosageResult -> pendingDosageResult = res },
                    onDosageParams = { pars: DosageParams -> lastDosageParams = pars },
                    currentMeds = currentMedications,
                    isOnline = effectivelyOnline,
                    isConsultationMode = true,
                    onRestartListening = { onLaunchSpeech() })
            }
        } 
    }

    // État pour contrôler l'affichage du dialogue
    var showMedicationsDialog by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    val micScale by rememberInfiniteTransition(label = "mic").animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "mic"
    )

    if (showImageSourceDialog) {
        AlertDialog(onDismissRequest = { showImageSourceDialog = false }, containerColor = colors.bgSecondary,
            title = { Text(if (isFr) "Ajouter une image" else "Add an image", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("📷" to (if (isFr) "Prendre une photo" else "Take a photo"),
                        "🖼️" to (if (isFr) "Choisir depuis la galerie" else "Choose from gallery"))
                        .forEachIndexed { i, (emoji, label) ->
                            Row(Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.bgInput)
                                .clickable {
                                    showImageSourceDialog =
                                        false; if (i == 0) onLaunchCamera() else onLaunchGallery()
                                }
                                .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(emoji, fontSize = 20.sp); Spacer(Modifier.width(12.dp))
                                Text(label, color = colors.textPrimary, fontSize = 14.sp)
                            }
                        }
                }
            }, confirmButton = {},
            dismissButton = { TextButton(onClick = { showImageSourceDialog = false }) { Text(if (isFr) "Annuler" else "Cancel", color = colors.textSecondary) } }
        )
    }

    // Si l'état est à true, on affiche le dialogue des médicaments
    if (showMedicationsDialog) {
        com.example.medvoiceafrica.MedicationsDialog(
            medications = currentMedications,
            onAddMedication = onAddMedication,
            onRemoveMedication = onRemoveMedication,
            onDismiss = { showMedicationsDialog = false }
        )
    }

    Column(Modifier
        .fillMaxSize()
        .background(colors.bgPrimary)
        .imePadding()
        .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
    ) {
        // ── Top bar Option B : ☰  chip dynamique  ✎  ⚙ ─────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPrimary)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Gauche : bouton drawer + chip statut réseau dynamique
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Gauche : Nom de l'app (complet)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.bgSecondary)
                            .clickable { onOpenDrawer() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("☰", fontSize = 14.sp, color = colors.textSecondary)
                    }
                    Column(modifier = Modifier.padding(end = 4.dp)) {
                        Text(
                            text = "MedVoice",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            lineHeight = 14.sp
                        )
                        Text(
                            text = "Africa",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accent,
                            lineHeight = 10.sp
                        )
                    }

                    // Milieu : Chip dynamique
                    val dotColor by animateColorAsState(
                        targetValue = if (effectivelyOnline) colors.accent else Color(0xFFE24B4A),
                        animationSpec = tween(600),
                        label = "dot"
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.bgSecondary,
                        border = BorderStroke(0.5.dp, colors.divider)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier
                                .size(6.dp)
                                .background(dotColor, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when {
                                    forceOffline -> if (isFr) "Mode Survie · Protocoles" else "Survival · Protocols"
                                    effectivelyOnline -> if (isFr) "En ligne · OMS v2026" else "Online · WHO v2026"
                                    else -> if (isFr) "Hors ligne · Survie" else "Offline · Survival"
                                },
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Droite : ✎ nouvelle conversation + icone pilule
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.bgSecondary)
                        .clickable { onNewConversation() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✎", fontSize = 14.sp, color = colors.textSecondary)
                }

                // Icône Pilule déplacée ici
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.bgSecondary)
                        .clickable { showMedicationsDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("💊", fontSize = 14.sp, color = colors.textSecondary)
                }
            }
        }

        // ── Messages ─────────────────────────────────────────────
        LazyColumn(state = listState, modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(
                    message = message, colors = colors,
                    isSpeaking = speakingId == message.id, ttsReady = ttsReady,
                    onSpeakText = { text ->
                        val lang = if (text.any { c -> c in "éèêëàâùûîïôœç" } || isFr) "fr" else "en"
                        onSpeakText(text, message.id, lang)
                    },
                    onStopSpeaking = onStopSpeaking,
                    onCopyText = { text ->
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("MedVoice", text))
                        android.widget.Toast.makeText(context, if (isFr) "Copié !" else "Copied!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onResend = { text ->
                        if (!isLoading && !isGenerating)
                            doSend(text, null, messages, db, activeSessionId, onSendMessage,
                                { isLoading = it }, {}, {}, { id -> activeSessionId = id; onSessionCreated(id) }, scope, tts = tts, context = context,
                                onDosageResult = { pendingDosageResult = it },
                                onDosageParams = { lastDosageParams = it },
                                currentMeds = currentMedications,
                                isOnline = effectivelyOnline,
                                isConsultationMode = isConsultationMode,
                                onRestartListening = { onLaunchSpeech() })

                    },
                    dosageResult = pendingDosageResult,
                    dosageParams = lastDosageParams
                )
            }
            if (isLoading || isGenerating) {
                item {
                    Row(Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = colors.bgSecondary,
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(13.dp), colors.accent, 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isFr) "Analyse en cours..." else "Analyzing...", fontSize = 13.sp, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }

        // ── QuickStart (visible uniquement au démarrage) ──────────
        if (messages.size == 1) {
            QuickStartButtons(
                colors = colors,
                onSuggestion = { text ->
                    if (!isLoading && !isGenerating)
                        doSend(text, null, messages, db, activeSessionId, onSendMessage,
                            { isLoading = it }, { inputText = "" }, onClearPendingImage,
                            { id -> activeSessionId = id; onSessionCreated(id) }, scope, tts = tts, context = context,
                            onDosageResult = { pendingDosageResult = it },
                            onDosageParams = { lastDosageParams = it },
                            currentMeds = currentMedications,
                            isOnline = effectivelyOnline,
                            isConsultationMode = isConsultationMode,
                            onRestartListening = { onLaunchSpeech() })

                },
                onPrefill = { text -> inputText = text; focusManager.clearFocus() }
            )
        }

        // ── Input bar ─────────────────────────────────────────────
        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
        Column(Modifier
            .fillMaxWidth()
            .background(colors.bgTopBar)
            .navigationBarsPadding()) {
            if (pendingImage != null) {
                Row(Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(bitmap = pendingImage!!.asImageBitmap(), contentDescription = null,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isFr) "Image prête" else "Image ready", fontSize = 12.sp, color = colors.accent, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClearPendingImage) { Text("✕", fontSize = 13.sp, color = colors.textSecondary) }
                }
            }
            if (isListening) {
                Row(Modifier
                    .fillMaxWidth()
                    .padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("🎤", fontSize = 16.sp, modifier = Modifier.scale(micScale)); Spacer(Modifier.width(6.dp))
                    Text(if (isFr) "Écoute..." else "Listening...", fontSize = 13.sp, color = colors.accent)
                }
            }

            // AJOUTER CE BLOC entre le bloc isListening et le Row principal :
            detectedDrugName?.let { drug ->
                val normalizedDetected = DrugInteractionEngine.normalizeDrugName(drug)
                val isAlreadyTaking = currentMedications.any { 
                    val norm = DrugInteractionEngine.normalizeDrugName(it)
                    norm == normalizedDetected || norm.contains(normalizedDetected) || normalizedDetected.contains(norm)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isAlreadyTaking) Color(0xFFE24B4A).copy(alpha = 0.15f) else colors.accent.copy(alpha = 0.15f),
                        modifier = Modifier.animateContentSize()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(if (isAlreadyTaking) "⚠️" else "💊", fontSize = 12.sp)
                            Text(
                                text = drug.replaceFirstChar { it.uppercase() } + if (isAlreadyTaking) (if (isFr) " (Déjà pris)" else " (Already taking)") else "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isAlreadyTaking) Color(0xFFE24B4A) else colors.accent
                            )
                            // Croix pour effacer si l'user veut
                            Text(
                                text = "×",
                                fontSize = 14.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.clickable { detectedDrugName = null }
                            )
                        }
                    }
                }
            }

            Row(Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {

                Box(Modifier
                    .size(42.dp)
                    .background(colors.bgInput, CircleShape), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { showImageSourceDialog = true }) { Text("📷", fontSize = 16.sp) }
                }
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = inputText, onValueChange = { inputText = it },
                    placeholder = { Text(if (isFr) "Écrivez ou dictez..." else "Type or dictate...", color = colors.textSecondary, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.accent, unfocusedBorderColor = colors.inputBorder,
                        cursorColor = colors.accent, focusedContainerColor = colors.bgInput, unfocusedContainerColor = colors.bgInput
                    ),
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(), maxLines = 4
                )
                Spacer(Modifier.width(6.dp))
                Box(Modifier
                    .size(42.dp)
                    .scale(if (isListening) micScale else 1f)
                    .background(if (isListening) Color(0xFFE24B4A) else colors.bgInput, CircleShape),
                    contentAlignment = Alignment.Center) {
                    IconButton(onClick = { if (!isListening) onLaunchSpeech() }) { 
                        Text(if (isConsultationMode) "👂" else "🎤", fontSize = 15.sp) 
                    }
                }
                Spacer(Modifier.width(6.dp))
                // Toggle pour le mode consultation
                Box(Modifier
                    .size(42.dp)
                    .background(if (isConsultationMode) colors.accent.copy(alpha = 0.2f) else colors.bgInput, CircleShape)
                    .clickable { onConsultationModeChanged(!isConsultationMode) },
                    contentAlignment = Alignment.Center) {
                    Text(if (isConsultationMode) "∞" else "1", 
                        color = if (isConsultationMode) colors.accent else colors.textSecondary,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                val canSend = (inputText.isNotBlank() || pendingImage != null) && !isLoading && !isGenerating
                Box(Modifier
                    .size(42.dp)
                    .background(
                        when {
                            isLoading || isGenerating -> Color(0xFF2A2A2A); canSend -> colors.accent; else -> colors.bgInput
                        }, CircleShape
                    ),
                    contentAlignment = Alignment.Center) {
                    if (isLoading || isGenerating) {
                        IconButton(onClick = { onStopGeneration(); isLoading = false }) {
                            Text("■", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(onClick = {
                            if (canSend) doSend(inputText, pendingImage, messages, db, activeSessionId,
                                onSendMessage, { isLoading = it }, { inputText = "" }, onClearPendingImage,
                                { id -> activeSessionId = id; onSessionCreated(id) }, scope, tts = tts, context = context,
                                onDosageResult = { pendingDosageResult = it },
                                onDosageParams = { lastDosageParams = it },
                                currentMeds = currentMedications,
                                isOnline = effectivelyOnline,
                                isConsultationMode = isConsultationMode,
                                onRestartListening = { onLaunchSpeech() })

                        }) { Text("→", fontSize = 18.sp, color = if (canSend) Color.White else colors.textSecondary, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    // 2. UTILISE UNE BOX pour superposer le scanner au-dessus de tout le reste
    Box(modifier = Modifier.fillMaxSize()) {

        // PharmaScanScreen (plein ecran, wired to DrugInteractionEngine + DosageFunctionCalling)
        if (showScanner) {
            PharmaScanScreen(
                gemmaEngine = gemmaEngine,
                currentMedications = currentMedications,
                isOnline = isOnline,
                onInteractionFound = { result, moleculeName ->
                    showScanner = false
                    val isFrL = Locale.getDefault().language == "fr"
                    val triage = when (result.severity) {
                        InteractionSeverity.ROUGE -> TriageLevel.ROUGE
                        InteractionSeverity.JAUNE -> TriageLevel.JAUNE
                        else -> TriageLevel.VERT
                    }
                    val triageTag = when (result.severity) {
                        InteractionSeverity.ROUGE -> "[TRIAGE:ROUGE]"
                        InteractionSeverity.JAUNE -> "[TRIAGE:JAUNE]"
                        else -> ""
                    }
                    val header = when (result.severity) {
                        InteractionSeverity.ROUGE -> if (isFrL) "INTERACTION DANGEREUSE" else "DANGEROUS INTERACTION"
                        InteractionSeverity.JAUNE -> if (isFrL) "Precaution requise" else "Caution required"
                        else -> if (isFrL) "Aucune interaction connue" else "No known interaction"
                    }
                    val alertText = "**$header** - $moleculeName\n\n${result.message}\n\n_Source: ${result.source.name}_$triageTag"
                    // messages.add(ChatMessage(text = alertText, isUser = false, triageLevel = triage))
                },
                onDosageResult = { dosageResult ->
                    pendingDosageResult = dosageResult
                    showScanner = false
                },
                onScanDetected = { code, text ->
                    showScanner = false
                    // Ne peut pas appeler doSend directement car 'messages' n'est pas accessible ici
                },
                onClose = { showScanner = false }
            )
        }

        // DosageCard overlay : affichee quand un resultat de dosage est disponible
        pendingDosageResult?.let { dosage ->
            if (dosage.source == DosageSource.INSUFFICIENT_DATA) {
                // Injecter directement dans le chat comme message IA (pas de card)
                // et vider le pending immédiatement
                LaunchedEffect(dosage) {
                    messages.add(ChatMessage(
                        text = dosage.specialInstructions,
                        isUser = false,
                        triageLevel = TriageLevel.UNKNOWN
                    ))
                    pendingDosageResult = null
                }
            } else {
                DosageCard(
                    result = dosage,
                    onDismiss = { pendingDosageResult = null },
                    onInjectToChat = { dosageText, triage ->
                        messages.add(ChatMessage(text = dosageText, isUser = false, triageLevel = triage))
                        pendingDosageResult = null
                    }
                )
            }
        }
    }
}

// ── DosageCard ───────────────────────────────
// Carte de dosage affichee en overlay sur le chat
@Composable
fun DosageCard(
    result: DosageResult,
    onDismiss: () -> Unit,
    onInjectToChat: (String, TriageLevel) -> Unit
) {
    val colors = LocalMedVoiceColors.current
    val isFr = java.util.Locale.getDefault().language == "fr"

    val _sourceLabel = when (result.source) {
        DosageSource.LOCAL_PROTOCOL -> if (isFr) "Protocole OMS local" else "Local WHO protocol"
        DosageSource.LLM_GEMINI    -> if (isFr) "Calcule par Gemini" else "Calculated by Gemini"
        DosageSource.LLM_LLAMA     -> if (isFr) "Calcule par IA locale" else "Calculated by local AI"
        DosageSource.INSUFFICIENT_DATA -> if (isFr) "Donnees insuffisantes" else "Insufficient data"
    }

    // Calcule dynamiquement le niveau de triage selon la dose ou les alertes
    val effectiveTriage = when {
        result.dosePerTake == "INTERDIT" || result.dosePerTake == "STOP" -> TriageLevel.ROUGE
        result.warningMessage.contains("⚠️") || result.warningMessage.contains("⛔") -> TriageLevel.JAUNE
        result.dosePerTake == "Inconnu" -> TriageLevel.JAUNE
        else -> TriageLevel.VERT
    }

    val chatText = if (result.dosePerTake == "INTERDIT" || result.dosePerTake == "STOP") {
        buildString {
            append(if (isFr) "⚠️ **Contre-indication : ${result.medicineName}**\n\n" else "⚠️ **Contraindication: ${result.medicineName}**\n\n")
            append(result.specialInstructions)
            if (result.warningMessage.isNotBlank()) append(" ${result.warningMessage}")
            append("\n\n_$_sourceLabel [TRIAGE:${effectiveTriage.name}]_")
        }
    } else {
        buildString {
            if (isFr) {
                append("La posologie calculée pour le **${result.medicineName}** est de **${result.dosePerTake}**, à prendre **${result.frequencyPerDay} fois par jour** pendant une durée de **${result.durationDays} jours**.")
                if (result.specialInstructions.isNotBlank()) append("\n\n**Instructions :** ${result.specialInstructions}")
            } else {
                append("The calculated dosage for **${result.medicineName}** is **${result.dosePerTake}**, to be taken **${result.frequencyPerDay} times a day** for **${result.durationDays} days**.")
                if (result.specialInstructions.isNotBlank()) append("\n\n**Instructions:** ${result.specialInstructions}")
            }
            if (result.warningMessage.isNotBlank()) append("\n\n**Attention :** ${result.warningMessage}")
            append("\n\n_$_sourceLabel [TRIAGE:${effectiveTriage.name}]_")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    colors.bgSecondary,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isFr) "Posologie calculée" else "Calculated dosage",
                    color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("X", color = colors.textSecondary, fontSize = 18.sp,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Nom du medicament
            Text(result.medicineName, color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))

            // Dose + frequence + duree
            if (result.dosePerTake != "INTERDIT" && result.dosePerTake != "STOP") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DosagePill(label = if (isFr) "Dose" else "Dose", value = result.dosePerTake, colors)
                    DosagePill(label = if (isFr) "Frequence" else "Frequence", value = "${result.frequencyPerDay}x/j", colors)
                    DosagePill(label = if (isFr) "Durée" else "Duration", value = "${result.durationDays}j", colors)
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Surface(
                    color = Color(0xFFE24B4A).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isFr) "USAGE INTERDIT / CONTRE-INDIQUÉ" else "USAGE FORBIDDEN / CONTRAINDICATED",
                        color = Color(0xFFE24B4A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Instructions
            if (result.specialInstructions.isNotBlank()) {
                Text(result.specialInstructions, color = colors.textSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Avertissement
            if (result.warningMessage.isNotBlank()) {
                Text(
                    text = result.warningMessage,
                    color = Color(0xFFFFA500), fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Source badge
            Text(_sourceLabel, color = colors.textSecondary, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(14.dp))

            // Bouton injecter dans le chat
            Button(
                onClick = { onInjectToChat(chatText, effectiveTriage) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isFr) "Envoyer dans le chat" else "Send to chat",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun detectIfFrench(text: String): Boolean {
    val frenchKeywords = listOf("combien", "donner", "poids", "enfant", "est-ce", "ordonnance")
    val englishKeywords = listOf("how", "give", "weight", "child", "should", "prescription")

    val frCount = frenchKeywords.count { text.contains(it, ignoreCase = true) }
    val enCount = englishKeywords.count { text.contains(it, ignoreCase = true) }

    return if (frCount == 0 && enCount == 0) {
        Locale.getDefault().language == "fr" // Fallback
    } else frCount >= enCount
}

@Composable
fun DosagePill(label: String, value: String, colors: MedVoiceColors) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(colors.bgPrimary, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(value, color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = colors.textSecondary, fontSize = 10.sp)
    }
}

// ── Markdown → AnnotatedString (gras **texte**) ───────────────────
@Composable
fun parseMarkdownToAnnotatedString(text: String): AnnotatedString {
    return buildAnnotatedString {
        val parts = text.split("**")
        parts.forEachIndexed { index, part ->
            if (index % 2 != 0) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
            } else {
                append(part)
            }
        }
    }
}

// ── ChatBubble ────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage, colors: MedVoiceColors,
    isSpeaking: Boolean, ttsReady: Boolean,
    onSpeakText: (String) -> Unit, onStopSpeaking: () -> Unit,
    onCopyText: (String) -> Unit, onResend: (String) -> Unit,
    dosageResult: DosageResult? = null,
    dosageParams: DosageParams? = null
) {
    val isFr = Locale.getDefault().language == "fr"
    val triageColor = when (message.triageLevel) {
        TriageLevel.ROUGE -> Color(0xFFE24B4A); TriageLevel.JAUNE -> Color(0xFFEF9F27)
        TriageLevel.VERT -> Color(0xFF1D9E75); TriageLevel.BLEU -> Color(0xFF2196F3); else -> null
    }
    Row(Modifier
        .fillMaxWidth()
        .padding(vertical = 1.dp), horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start) {
        if (!message.isUser && triageColor != null) {
            Box(Modifier
                .width(3.dp)
                .heightIn(min = 20.dp)
                .background(triageColor, RoundedCornerShape(2.dp))
                .align(Alignment.CenterVertically))
            Spacer(Modifier.width(6.dp))
        }
        Column(horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start) {
            Surface(
                color = if (message.isUser) colors.accent else colors.bgSecondary,
                shape = RoundedCornerShape(topStart = if (message.isUser) 18.dp else 4.dp, topEnd = if (message.isUser) 4.dp else 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(onLongClick = {
                        if (message.text.isNotBlank()) onCopyText(
                            message.text
                        )
                    }) {}
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // ── Carte triage améliorée ──────────────────────────
                    if (!message.isUser && triageColor != null) {
                        val (triageLabel, triageSub) = when (message.triageLevel) {
                            TriageLevel.ROUGE -> Pair(
                                if (isFr) "🔴 ROUGE — Urgence vitale" else "🔴 RED — Critical emergency",
                                if (isFr) "Transfert immédiat requis" else "Immediate transfer required"
                            )
                            TriageLevel.JAUNE -> Pair(
                                if (isFr) "🟡 JAUNE — Consultation requise" else "🟡 YELLOW — Consultation required",
                                if (isFr) "Dans les 24 heures" else "Within 24 hours"
                            )
                            TriageLevel.VERT -> Pair(
                                if (isFr) "🟢 VERT — Surveillance" else "🟢 GREEN — Home monitoring",
                                if (isFr) "Soins à domicile possibles" else "Home care possible"
                            )
                            TriageLevel.BLEU -> Pair(
                                if (isFr) "🔵 BLEU — Information Dosage" else "🔵 BLUE — Dosage Info",
                                if (isFr) "Suivre les instructions précisément" else "Follow instructions carefully"
                            )
                            else -> Pair("", "")
                        }
                        Surface(color = triageColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(triageLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = triageColor)
                                if (triageSub.isNotBlank())
                                    Text(triageSub, fontSize = 10.sp, color = triageColor.copy(alpha = 0.8f))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // ── Image ───────────────────────────────────────────
                    if (message.imageBitmap != null) {
                        Image(bitmap = message.imageBitmap.asImageBitmap(), contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        if (message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                    }
                    // ── Texte ───────────────────────────────────────────
                    if (message.text.isNotBlank()) {
                        if (!message.isUser) {
                            SelectionContainer {
                                Text(
                                    text = parseMarkdownToAnnotatedString(message.text),
                                    fontSize = 14.sp,
                                    color = colors.textPrimary,
                                    lineHeight = 21.sp
                                )
                            }
                        } else {
                            Text(message.text, fontSize = 14.sp, color = Color.White, lineHeight = 21.sp)
                        }
                    }
                }
            }

            // ── Badge source OMS ────────────────────────────────────
            if (!message.isUser && message.triageLevel != TriageLevel.UNKNOWN) {
                Spacer(Modifier.height(3.dp))
                val protocolName = extractProtocolNameFromText(message.text)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0f1f35))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("S", fontSize = 9.sp, color = Color(0xFF85B7EB), fontWeight = FontWeight.Bold)
                    Text("Sourcé : $protocolName", fontSize = 10.sp, color = Color(0xFF85B7EB))
                }
            }

            // ── Boutons d'action ────────────────────────────────────
            Spacer(Modifier.height(3.dp))
            if (message.isUser && message.text.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.End) {
                    BubbleBtn("⧉", colors.textSecondary.copy(0.5f)) { onCopyText(message.text) }
                    Spacer(Modifier.width(4.dp))
                    BubbleBtn("↺", colors.textSecondary.copy(0.5f)) { onResend(message.text) }
                }
            } else if (!message.isUser && message.text.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BubbleBtn("⧉", colors.textSecondary.copy(0.5f)) { onCopyText(message.text) }
                    Spacer(Modifier.width(4.dp))
                    BubbleBtn(if (isSpeaking) "⏹" else "🔊", if (isSpeaking) colors.accent else colors.textSecondary.copy(0.5f)) {
                        if (isSpeaking) onStopSpeaking() else onSpeakText(message.text)
                    }
                    // Bouton transfert SMS pour ROUGE et JAUNE
                    if (message.triageLevel == TriageLevel.ROUGE || message.triageLevel == TriageLevel.JAUNE) {
                        Spacer(Modifier.width(6.dp))
                        TransferButton(message = message, colors = colors, dosageResult = dosageResult, dosageParams = dosageParams )
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleBtn(icon: String, color: Color, onClick: () -> Unit) {
    Text(icon, fontSize = 13.sp, color = color,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(4.dp))
}

// ── Extraction du nom de protocole depuis le texte ────────────────
fun extractProtocolNameFromText(text: String): String {
    val t = text.lowercase()
    return when {
        t.contains("artésunate") || (t.contains("paludisme") && t.contains("grave")) -> "OMS — Paludisme grave"
        t.contains("paludisme") || t.contains("malaria") || t.contains("coartem") -> "OMS — Paludisme"
        t.contains("déshydratation") || t.contains("sro") || t.contains("ringer") -> "OMS — Déshydratation"
        t.contains("pneumonie") || t.contains("tirage") -> "OMS — Pneumonie"
        t.contains("malnutrition") || t.contains("atpe") -> "OMS — Malnutrition aiguë"
        t.contains("néonatal") || t.contains("nouveau-né") || t.contains("ampicilline") -> "OMS — Fièvre néonatale"
        t.contains("prééclampsie") || t.contains("sulfate de magnésium") -> "OMS — Hypertension / Prééclampsie"
        t.contains("antivenin") || t.contains("serpent") -> "OMS — Envenimation"
        t.contains("diarrhée") || t.contains("choléra") -> "OMS — Diarrhée aiguë"
        t.contains("plaie") || t.contains("tétanos") -> "OMS — Plaies et infections"
        else -> "Base OMS locale v2026"
    }
}

// ── doSend ────────────────────────────────────────────────────────
private fun doSend(
    input: String, image: Bitmap?, messages: MutableList<ChatMessage>,
    db: AppDatabase, currentSessionId: Long?,
    onSendMessage: (String, Bitmap?, (Result<String>) -> Unit) -> Unit,
    setLoading: (Boolean) -> Unit, clearInput: () -> Unit, clearImage: () -> Unit,
    onSessionCreated: (Long) -> Unit, scope: CoroutineScope,
    tts: TextToSpeech? = null,
    context: Context? = null,
    onDosageResult: (DosageResult) -> Unit = {},
    onDosageParams: (DosageParams) -> Unit = {},
    currentMeds: List<String> = emptyList(),
    isOnline: Boolean = false,
    isConsultationMode: Boolean = false,
    onRestartListening: () -> Unit = {}
) {
    val text = input.trim()
    if (text.isBlank() && image == null) return

    // =================================================================
    // 1. --- INTERCEPTION DOSAGE ---
    // =================================================================
    if (DosageFunctionCalling.detectDosageIntent(text) && context != null) {
        val dosageParams = DosageFunctionCalling.extractDosageParams(text)
        if (dosageParams != null && dosageParams.medicineName.isNotBlank()) {

            // CORRECTION 1 : On nettoie la barre de saisie et on affiche la question !
            clearInput()
            clearImage()
            messages.add(ChatMessage(text = text, isUser = true))

            // On peut mettre un petit loading le temps de calculer
            setLoading(true)

            scope.launch {
                val isFrD = Locale.getDefault().language == "fr"
                val dr = DosageFunctionCalling.calculateDosage(dosageParams, GemmaEngine(context!!), isOnline, isFrD, currentMeds)

                // CORRECTION 2 : On déclenche la carte et on arrête le chargement
                setLoading(false)
                onDosageParams(dosageParams)
                dr?.let { result ->
                    onDosageResult(result)
                    if (isConsultationMode && tts != null) {
                        val speechText = if (isFrD) {
                            "Posologie pour ${result.medicineName} : ${result.dosePerTake}, ${result.frequencyPerDay} fois par jour pendant ${result.durationDays} jours. ${result.specialInstructions}"
                        } else {
                            "Dosage for ${result.medicineName}: ${result.dosePerTake}, ${result.frequencyPerDay} times a day for ${result.durationDays} days. ${result.specialInstructions}"
                        }
                        tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "ai_response")
                    } else if (isConsultationMode) {
                        onRestartListening()
                    }
                } ?: run { if (isConsultationMode) onRestartListening() }

                // Optionnel : Sauvegarder ce message dans Room (comme pour le Fon)
                val sessionId: Long = currentSessionId ?: db.sessionDao().insertSession(
                    ChatSession(title = "Dosage: ${dosageParams.medicineName}", triageLevel = TriageLevel.BLEU.name)
                ).also { onSessionCreated(it) }
                db.messageDao().insertMessage(ChatMessageEntity(sessionId = sessionId, text = text, isUser = true))
            }

            // CORRECTION 3 (CRITIQUE) : On ARRÊTE la fonction ici !
            return
        }
    }

    // =================================================================
    // 2. --- INTERCEPTION FON ---
    // =================================================================
    if (context != null) {
        val fonRepository = EmergencyRepository(context)
        val urgenceFon = fonRepository.findMatch(text)

        if (urgenceFon != null) {
            clearInput()
            clearImage()

            messages.add(ChatMessage(text = text, isUser = true))
            messages.add(ChatMessage(
                text = urgenceFon.conseil_fon,
                isUser = false,
                triageLevel = TriageLevel.ROUGE
            ))

            if (isConsultationMode && tts != null) {
                tts.speak(urgenceFon.conseil_fon, TextToSpeech.QUEUE_FLUSH, null, "ai_response")
            } else if (isConsultationMode) {
                onRestartListening()
            }

            scope.launch {
                val isFrFon = Locale.getDefault().language == "fr"
                val titleFon = "🔴 ${urgenceFon.question_fr.take(35)}"
                val sessionId: Long = if (currentSessionId != null) {
                    currentSessionId
                } else {
                    db.sessionDao().insertSession(
                        ChatSession(title = titleFon, triageLevel = TriageLevel.ROUGE.name)
                    ).also { onSessionCreated(it) }
                }
                db.messageDao().insertMessage(ChatMessageEntity(sessionId = sessionId, text = text, isUser = true))
                db.messageDao().insertMessage(ChatMessageEntity(sessionId = sessionId,
                    text = urgenceFon.conseil_fon, isUser = false, triageLevel = TriageLevel.ROUGE.name))
                try {
                    db.consultationDao().insert(ConsultationLog(pathologie = urgenceFon.id,
                        triage = TriageLevel.ROUGE.name, sessionId = sessionId, isOffline = true))
                } catch (_: Exception) { }
            }

            return // C'est parfait, ici tu avais bien pensé au return !
        }
    }

    // =================================================================
    // 3. --- CHAT IA STANDARD ---
    // =================================================================
    clearInput(); clearImage()
    messages.add(ChatMessage(text = text, isUser = true, imageBitmap = image))
    setLoading(true)
    val isFr = detectIfFrench(text)
    val medsPrompt = if (currentMeds.isNotEmpty()) {
        if (isFr) "\n\nNote: Le patient prend déjà : ${currentMeds.joinToString(", ")}. Vérifie scrupuleusement les interactions avec ce nouveau traitement et réponds par un paragraphe clair et bien structuré."
        else "\n\nNote: Patient is already taking: ${currentMeds.joinToString(", ")}. Carefully check for interactions with this new treatment and respond with a clear and well-structured paragraph."
    } else ""
    
    val userLang = if (detectIfFrench(text)) "fr" else "en"
    
    val prompt = (text + medsPrompt).ifBlank { 
        if (isFr) "Analyse cette image médicalement en tenant compte des médicaments actuels : ${currentMeds.joinToString(", ")}." 
        else "Analyze this image medically considering current medications: ${currentMeds.joinToString(", ")}."
    }

    onSendMessage(prompt, image) { result ->
        setLoading(false)
        val rawResponse = if (result.isSuccess) result.getOrDefault("") else ""
        val triage = GemmaEngine.parseTriageLevelFromTag(rawResponse)

        val cleaned = GemmaEngine.cleanResponse(rawResponse)
        val cleanVoiceText = cleaned.replace("*", "").trim()
        val isEnglishResponse = cleanVoiceText.contains(Regex("\\b(the|is|you|how|what)\\b", RegexOption.IGNORE_CASE))

        val voicePrefix = when (triage) {
            TriageLevel.ROUGE -> if (isFr) "Urgence vitale détectée. Transfert immédiat requis. " else "Critical emergency. Immediate transfer required. "
            TriageLevel.JAUNE -> if (isFr) "Cas modéré. Consultation dans les 24 heures. " else "Moderate case. Consultation within 24 hours. "
            else -> ""
        }

        if (result.isSuccess) {
            // L'Orchestrateur vérifie si le LLM a sorti un JSON malgré tout
            val (finalText, dosageResult) = MedOrchestrator.extractAndProcess(cleaned)

            // On ajoute la réponse propre au chat
            messages.add(ChatMessage(text = finalText, isUser = false, triageLevel = triage))

            // Si l'IA standard a quand même sorti un dosage, on l'affiche
            dosageResult?.let { onDosageResult(it) }

            val fullVoiceText = (voicePrefix + cleanVoiceText).trim()
            if (fullVoiceText.isNotBlank() && tts != null) {
                tts.language = if (isEnglishResponse) Locale.ENGLISH else Locale.FRENCH
                tts.speak(fullVoiceText, TextToSpeech.QUEUE_ADD, null, "ai_response")
            } else if (isConsultationMode) {
                onRestartListening()
            }
        } else {
            val errorMsg = if (isFr) "Erreur : ${result.exceptionOrNull()?.message}" else "Error: ${result.exceptionOrNull()?.message}"
            messages.add(ChatMessage(text = errorMsg, isUser = false))
            if (tts != null) {
                tts.speak(errorMsg, TextToSpeech.QUEUE_ADD, null, "ai_error")
            } else if (isConsultationMode) {
                onRestartListening()
            }
        }

        scope.launch {
            val sessionId: Long = if (currentSessionId != null) {
                if (triage != TriageLevel.UNKNOWN) {
                    db.sessionDao().updateTriageOnly(
                        id = currentSessionId,
                        triageLevel = triage.name,
                        timestamp = System.currentTimeMillis()
                    )
                }
                currentSessionId
            } else {
                val smartTitle = generateSmartTitleAI(text, isFr, isOnline = false)
                db.sessionDao().insertSession(
                    ChatSession(title = smartTitle, triageLevel = triage.name)
                ).also { onSessionCreated(it) }
            }
            db.messageDao().insertMessage(ChatMessageEntity(sessionId = sessionId, text = text, isUser = true))
            if (result.isSuccess) {
                db.messageDao().insertMessage(ChatMessageEntity(sessionId = sessionId, text = cleaned, isUser = false, triageLevel = triage.name))
            }
            try {
                val pathologie = extractPathologie(null, text)
                db.consultationDao().insert(
                    ConsultationLog(
                        pathologie = pathologie,
                        triage = triage.name,
                        sessionId = sessionId,
                        isOffline = rawResponse.contains("[TRIAGE:INFO]")
                    )
                )
            } catch (_: Exception) { }
        }
    }
}

// ── Smart title generation ────────────────────────────────────────
// ══ Smart title generation (Gemini → Llama → Keywords) ══════
// Sync fallback — utilisé quand on ne peut pas suspendre (Fon path)
private fun generateSmartTitle(userMessage: String, isFr: Boolean): String {
    val msg = userMessage.trim()
    val medicalKeywords = mapOf(
        "fièvre" to (if (isFr) "Fièvre" else "Fever"),
        "paludisme" to (if (isFr) "Paludisme" else "Malaria"),
        "malaria" to (if (isFr) "Paludisme" else "Malaria"),
        "diarrhée" to (if (isFr) "Diarrhée" else "Diarrhea"),
        "toux" to (if (isFr) "Toux" else "Cough"),
        "vomiss" to (if (isFr) "Vomissements" else "Vomiting"),
        "convuls" to (if (isFr) "Convulsions" else "Seizures"),
        "tension" to (if (isFr) "Hypertension" else "Hypertension"),
        "grossesse" to (if (isFr) "Grossesse" else "Pregnancy"),
        "bébé" to (if (isFr) "Nourrisson" else "Infant"),
        "bebe" to (if (isFr) "Nourrisson" else "Infant"),
        "enfant" to (if (isFr) "Enfant" else "Child"),
        "nouveau-né" to (if (isFr) "Nouveau-né" else "Newborn"),
        "douleur" to (if (isFr) "Douleur" else "Pain"),
        "plaie" to (if (isFr) "Plaie" else "Wound"),
        "serpent" to (if (isFr) "Morsure serpent" else "Snake bite"),
        "morsure" to (if (isFr) "Morsure" else "Bite"),
        "œdème" to (if (isFr) "Œdème" else "Edema"),
        "malnutri" to (if (isFr) "Malnutrition" else "Malnutrition"),
        "déshydrat" to (if (isFr) "Déshydratation" else "Dehydration"),
        "fever" to (if (isFr) "Fièvre" else "Fever"),
        "cough" to (if (isFr) "Toux" else "Cough"),
        "diarrhea" to (if (isFr) "Diarrhée" else "Diarrhea"),
        "vomit" to (if (isFr) "Vomissements" else "Vomiting"),
        "baby" to (if (isFr) "Nourrisson" else "Baby"),
        "infant" to (if (isFr) "Nourrisson" else "Infant"),
        "child" to (if (isFr) "Enfant" else "Child"),
        "pain" to (if (isFr) "Douleur" else "Pain"),
        "wound" to (if (isFr) "Plaie" else "Wound"),
        "snake" to (if (isFr) "Serpent" else "Snake bite"),
        "dehydrat" to (if (isFr) "Déshydratation" else "Dehydration"),
        "seizure" to (if (isFr) "Convulsions" else "Seizures"),
        "pressure" to (if (isFr) "Tension artérielle" else "Blood pressure"),
        "pregnan" to (if (isFr) "Grossesse" else "Pregnancy"),
        "newborn" to (if (isFr) "Nouveau-né" else "Newborn"),
    )
    val msgLower = msg.lowercase()
    val foundKeyword = medicalKeywords.entries.firstOrNull { (kw, _) -> msgLower.contains(kw) }?.value
    return when {
        foundKeyword != null -> foundKeyword
        msg.length <= 40 -> msg.replaceFirstChar { it.uppercase() }
        else -> msg.take(37).replaceFirstChar { it.uppercase() } + "..."
    }
}

// Suspend version — utilisée dans le chemin normal (IA disponible)
// Hiérarchie : Gemini API (online) → LlamaEngine (local actif) → keywords (fallback)
private suspend fun generateSmartTitleAI(
    userMessage: String,
    isFr: Boolean,
    isOnline: Boolean
): String {
    val titlePrompt = if (isFr)
        "Génère un titre médical court (3-5 mots maximum, sans guillemets, sans ponctuation finale) pour cette consultation : \"$userMessage\""
    else
        "Generate a short medical title (3-5 words max, no quotes, no trailing punctuation) for this consultation: \"$userMessage\""

    // 1. Gemini API si online
    if (isOnline) {
        return try {
            val result = GemmaEngine.generateTitleOnly(titlePrompt)
            result.ifBlank { generateSmartTitle(userMessage, isFr) }
        } catch (_: Exception) {
            generateSmartTitle(userMessage, isFr) // fallback keywords
        }
    }

    // 2. LlamaEngine si actif hors-ligne
    if (LlamaEngine.isReady()) {
        return try {
            val llamaResult = LlamaEngine.generateResponse(
                prompt = titlePrompt,
                systemPrompt = if (isFr) "Tu es un assistant médical. Réponds UNIQUEMENT avec le titre demandé, rien d'autre."
                else "You are a medical assistant. Reply ONLY with the requested title, nothing else.",
                ragContext = ""
            )
            when (llamaResult) {
                is LlamaResult.Success -> llamaResult.text.lines().firstOrNull()?.trim()
                    ?.replace(Regex("[\"\'*#]"), "")
                    ?.take(50)
                    ?.ifBlank { generateSmartTitle(userMessage, isFr) }
                    ?: generateSmartTitle(userMessage, isFr)
                is LlamaResult.Fallback -> generateSmartTitle(userMessage, isFr)
            }
        } catch (_: Exception) {
            generateSmartTitle(userMessage, isFr)
        }
    }

    // 3. Fallback keywords (téléphone pas assez puissant, hors-ligne)
    return generateSmartTitle(userMessage, isFr)
}