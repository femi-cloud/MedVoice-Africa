package com.example.medvoiceafrica

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val triageLevel: TriageLevel = TriageLevel.UNKNOWN,
    val imageBitmap: Bitmap? = null
)

class MainActivity : ComponentActivity() {

    private lateinit var gemmaEngine: GemmaEngine
    private lateinit var db: AppDatabase
    private val pendingImageState = mutableStateOf<Bitmap?>(null)
    private val isListeningState = mutableStateOf(false)
    private val recognizedTextState = mutableStateOf("")
    private val isSpeakingState = mutableStateOf(false)
    private var photoUri: Uri? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Initialize TTS
    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val lang = if (Locale.getDefault().language == "fr") Locale.FRENCH else Locale.ENGLISH
                val result = tts?.setLanguage(lang)
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)
                // Listener to update speaking state
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { isSpeakingState.value = true }
                    override fun onDone(utteranceId: String?) { isSpeakingState.value = false }
                    override fun onError(utteranceId: String?) { isSpeakingState.value = false }
                })
            }
        }
    }

    fun speakText(text: String) {
        if (!ttsReady || tts == null) {
            android.widget.Toast.makeText(
                this,
                if (Locale.getDefault().language == "fr") "Synthèse vocale non disponible"
                else "Text-to-speech not available",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        // Strip emojis and triage tags for cleaner speech
        val cleaned = text
            .replace(Regex("\\[TRIAGE:\\w+\\]"), "")
            .replace(Regex("\\p{So}|\\p{Cn}"), "")
            .trim()
        tts?.speak(cleaned, TextToSpeech.QUEUE_FLUSH, null, "medvoice_tts")
    }

    fun stopSpeaking() {
        tts?.stop()
        isSpeakingState.value = false
    }

    // Speech recognition launcher — uses system dialog, no extra lib needed
    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListeningState.value = false
        if (result.resultCode == RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognized = matches?.firstOrNull() ?: ""
            if (recognized.isNotBlank()) recognizedTextState.value = recognized
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            try {
                pendingImageState.value = contentResolver.openInputStream(photoUri!!)
                    ?.use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                android.util.Log.e("Camera", "Error: ${e.message}")
            }
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                pendingImageState.value = contentResolver.openInputStream(it)
                    ?.use { stream -> BitmapFactory.decodeStream(stream) }
            } catch (e: Exception) {
                android.util.Log.e("Gallery", "Error: ${e.message}")
            }
        }
    }

    fun launchCamera() {
        try {
            val file = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            photoUri = uri
            takePhotoLauncher.launch(uri)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Erreur Caméra: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun launchGallery() = galleryLauncher.launch("image/*")

    fun launchSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            android.widget.Toast.makeText(
                this,
                if (Locale.getDefault().language == "fr")
                    "Reconnaissance vocale non disponible sur ce téléphone"
                else "Speech recognition not available",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        val lang = Locale.getDefault().language
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == "fr") "fr-FR" else "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, if (lang == "fr") "fr-FR" else "en-US")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                if (lang == "fr") "Décrivez les symptômes du patient..."
                else "Describe the patient's symptoms..."
            )
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        isListeningState.value = true
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            isListeningState.value = false
            android.widget.Toast.makeText(this, "Erreur micro: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Do NOT use enableEdgeToEdge() — it resets status bar color to transparent
        // Manually configure edge-to-edge + match status bar to our top bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.parseColor("#0D1F35")
        window.navigationBarColor = android.graphics.Color.parseColor("#0D1F35")
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        gemmaEngine = GemmaEngine(this)
        db = AppDatabase.getInstance(this)
        initTts()
        val initResult = gemmaEngine.initialize()

        lifecycleScope.launch {
            gemmaEngine.initRag()
        }

        setContent {
            MaterialTheme {
                MedVoiceApp(
                    initSuccess = initResult.isSuccess,
                    initError = initResult.exceptionOrNull()?.message,
                    db = db,
                    pendingImageState = pendingImageState,
                    isListeningState = isListeningState,
                    recognizedTextState = recognizedTextState,
                    isSpeakingState = isSpeakingState,
                    onLaunchCamera = { launchCamera() },
                    onLaunchGallery = { launchGallery() },
                    onLaunchSpeech = { launchSpeechRecognition() },
                    onSpeakText = { text -> speakText(text) },
                    onStopSpeaking = { stopSpeaking() },
                    onClearPendingImage = { pendingImageState.value = null },
                    onSendMessage = { userInput, bitmap, callback ->
                        lifecycleScope.launch {
                            callback(gemmaEngine.runInference(userInput, bitmap))
                        }
                    },
                    onNewConversation = { gemmaEngine.clearHistory() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        gemmaEngine.close()
    }
}

@Composable
fun MedVoiceApp(
    initSuccess: Boolean,
    initError: String?,
    db: AppDatabase,
    pendingImageState: MutableState<Bitmap?>,
    isListeningState: MutableState<Boolean>,
    recognizedTextState: MutableState<String>,
    isSpeakingState: MutableState<Boolean>,
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit,
    onLaunchSpeech: () -> Unit,
    onSpeakText: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onClearPendingImage: () -> Unit,
    onSendMessage: (String, Bitmap?, (Result<String>) -> Unit) -> Unit,
    onNewConversation: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val sessions by db.sessionDao().getAllSessions().collectAsState(initial = emptyList())
    var currentSessionId by remember { mutableStateOf<Long?>(null) }
    var preloadedMessages by remember { mutableStateOf<List<ChatMessage>?>(null) }
    val isFr = Locale.getDefault().language == "fr"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xFF0D1F35)) {
                Spacer(Modifier.height(16.dp))
                Text(
                    if (isFr) "Historique des soins" else "Care history",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                HorizontalDivider(color = Color(0xFF2A3B50))
                Spacer(Modifier.height(8.dp))
                if (sessions.isEmpty()) {
                    Text(
                        if (isFr) "Aucune conversation enregistrée" else "No conversations yet",
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFF888780), fontSize = 13.sp
                    )
                } else {
                    LazyColumn {
                        items(sessions) { session ->
                            val triageColor = when (session.triageLevel) {
                                "ROUGE" -> Color(0xFFE24B4A)
                                "JAUNE" -> Color(0xFFEF9F27)
                                "VERT"  -> Color(0xFF639922)
                                else    -> Color(0xFF888780)
                            }
                            NavigationDrawerItem(
                                label = {
                                    Column {
                                        Text(session.title, color = Color(0xFFD3D1C7), fontSize = 13.sp, maxLines = 2)
                                        Text(formatTimestamp(session.timestamp), color = Color(0xFF888780), fontSize = 11.sp)
                                    }
                                },
                                badge = {
                                    if (session.triageLevel != "UNKNOWN" && session.triageLevel != "INFO") {
                                        Box(Modifier.size(10.dp).background(triageColor, RoundedCornerShape(5.dp)))
                                    }
                                },
                                selected = currentSessionId == session.id,
                                onClick = {
                                    scope.launch {
                                        val msgs = db.messageDao().getMessagesForSession(session.id)
                                        currentSessionId = session.id
                                        preloadedMessages = msgs.map {
                                            ChatMessage(
                                                text = it.text, isUser = it.isUser,
                                                triageLevel = try { TriageLevel.valueOf(it.triageLevel) }
                                                catch (_: Exception) { TriageLevel.UNKNOWN }
                                            )
                                        }
                                        drawerState.close()
                                    }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    unselectedContainerColor = Color.Transparent,
                                    selectedContainerColor = Color(0xFF1A2B40)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) {
        MedVoiceChatScreen(
            initSuccess = initSuccess, initError = initError, db = db,
            currentSessionId = currentSessionId, preloadedMessages = preloadedMessages,
            pendingImageState = pendingImageState,
            isListeningState = isListeningState,
            recognizedTextState = recognizedTextState,
            isSpeakingState = isSpeakingState,
            onLaunchCamera = onLaunchCamera, onLaunchGallery = onLaunchGallery,
            onLaunchSpeech = onLaunchSpeech,
            onSpeakText = onSpeakText,
            onStopSpeaking = onStopSpeaking,
            onClearPendingImage = onClearPendingImage, onSendMessage = onSendMessage,
            onNewConversation = {
                onNewConversation()
                currentSessionId = null
                preloadedMessages = null
            },
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onSessionCreated = { id -> currentSessionId = id }
        )
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

@Composable
fun MedVoiceChatScreen(
    initSuccess: Boolean,
    initError: String?,
    db: AppDatabase,
    currentSessionId: Long?,
    preloadedMessages: List<ChatMessage>?,
    pendingImageState: MutableState<Bitmap?>,
    isListeningState: MutableState<Boolean>,
    recognizedTextState: MutableState<String>,
    isSpeakingState: MutableState<Boolean>,
    onLaunchCamera: () -> Unit,
    onLaunchGallery: () -> Unit,
    onLaunchSpeech: () -> Unit,
    onSpeakText: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onClearPendingImage: () -> Unit,
    onSendMessage: (String, Bitmap?, (Result<String>) -> Unit) -> Unit,
    onNewConversation: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSessionCreated: (Long) -> Unit
) {
    val bgColor     = Color(0xFF0A1628)
    val accentGreen = Color(0xFF1D9E75)
    val aiBubble    = Color(0xFF1A2B40)
    val isFr = Locale.getDefault().language == "fr"
    val scope = rememberCoroutineScope()

    val greeting = if (isFr)
        "Bonjour ! Je suis MedVoice Africa, votre assistant médical.\n\nJe peux vous aider avec le triage, les dosages, les interactions médicamenteuses et les protocoles WHO.\n\nDécrivez un patient, envoyez une photo ou utilisez le micro 🎤"
    else
        "Hello! I'm MedVoice Africa, your medical assistant.\n\nDescribe a patient, send a photo or use the microphone 🎤"

    val welcomeText = if (initSuccess) greeting else "Erreur : $initError"
    val messages = remember { mutableStateListOf(ChatMessage(text = welcomeText, isUser = false)) }
    val pendingImage by pendingImageState
    val isListening by isListeningState
    val isSpeaking by isSpeakingState
    var recognizedText by recognizedTextState

    // When speech returns text → inject into input field
    var inputText by remember { mutableStateOf("") }
    LaunchedEffect(recognizedText) {
        if (recognizedText.isNotBlank()) {
            inputText = recognizedText
            recognizedText = ""
        }
    }

    // Mic pulse animation
    val micScale by rememberInfiniteTransition(label = "mic").animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "micPulse"
    )

    LaunchedEffect(preloadedMessages) {
        if (!preloadedMessages.isNullOrEmpty()) {
            messages.clear()
            messages.addAll(preloadedMessages)
        }
    }

    var isLoading by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var activeSessionId by remember { mutableStateOf(currentSessionId) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Image source dialog
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            containerColor = aiBubble,
            title = {
                Text(
                    if (isFr) "Ajouter une image" else "Add an image",
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0A1628))
                            .clickable { showImageSourceDialog = false; onLaunchCamera() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📷", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(if (isFr) "Prendre une photo" else "Take a photo", color = Color.White, fontSize = 14.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0A1628))
                            .clickable { showImageSourceDialog = false; onLaunchGallery() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🖼️", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(if (isFr) "Choisir depuis la galerie" else "Choose from gallery", color = Color.White, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImageSourceDialog = false }) {
                    Text(if (isFr) "Annuler" else "Cancel", color = Color(0xFF888780))
                }
            }
        )
    }

    // FIX: use Column + weight instead of Scaffold to avoid the empty space bug
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .safeDrawingPadding()  // entire screen moves up with keyboard
    ) {
        // ── Top bar ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D1F35))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Text("☰", fontSize = 20.sp, color = Color(0xFF888780))
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier.size(36.dp).background(accentGreen, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("MedVoice Africa", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(7.dp).background(
                            if (initSuccess) accentGreen else Color(0xFFE24B4A),
                            RoundedCornerShape(4.dp)
                        )
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (initSuccess) "Gemma 4 · En ligne" else "Erreur API",
                        fontSize = 11.sp, color = Color(0xFF888780)
                    )
                }
            }
            // FIX: pencil button starts new conversation directly — no dialog
            IconButton(onClick = {
                onNewConversation()
                activeSessionId = null
                messages.clear()
                messages.add(ChatMessage(text = welcomeText, isUser = false))
                onClearPendingImage()
            }) {
                Text("✎", fontSize = 18.sp, color = Color(0xFF888780))
            }
        }

        // ── Messages — takes all remaining space ─────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)  // KEY: fills space between topbar and input
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { message ->
                ChatBubble(
                    message = message,
                    accentGreen = accentGreen,
                    aiBubble = aiBubble,
                    onSpeakText = onSpeakText,
                    isSpeaking = isSpeaking,
                    onStopSpeaking = onStopSpeaking
                )
            }
            if (isLoading) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Surface(
                            color = aiBubble,
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.size(14.dp), accentGreen, 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isFr) "Analyse en cours..." else "Analyzing...", fontSize = 13.sp, color = Color(0xFF888780))
                            }
                        }
                    }
                }
            }
        }

        // ── Bottom input — always just above keyboard ─────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D1F35))
        ) {
            // Pending image thumbnail
            if (pendingImage != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = pendingImage!!.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isFr) "Image prête" else "Image ready", fontSize = 12.sp, color = Color(0xFF5DCAA5), modifier = Modifier.weight(1f))
                    IconButton(onClick = onClearPendingImage) {
                        Text("✕", fontSize = 14.sp, color = Color(0xFF888780))
                    }
                }
            }
            // Listening indicator
            if (isListening) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎤", fontSize = 18.sp, modifier = Modifier.scale(micScale))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isFr) "Écoute en cours..." else "Listening...",
                        fontSize = 13.sp, color = Color(0xFF1D9E75)
                    )
                }
            }
            // Input row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier.size(48.dp).background(Color(0xFF1A2B40), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { showImageSourceDialog = true }) {
                        Text("📷", fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(if (isFr) "Écrivez ou dictez..." else "Type or dictate...", color = Color(0xFF5F5E5A), fontSize = 14.sp)
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = accentGreen, unfocusedBorderColor = Color(0xFF2A3B50),
                        cursorColor = accentGreen,
                        focusedContainerColor = Color(0xFF1A2B40), unfocusedContainerColor = Color(0xFF1A2B40)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    // FIX: ImeAction.Default allows newline, no send on Enter
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions(),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                // Mic button — pulses red when listening
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .scale(if (isListening) micScale else 1f)
                        .background(
                            if (isListening) Color(0xFFE24B4A) else Color(0xFF1A2B40),
                            RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { if (!isListening) onLaunchSpeech() }) {
                        Text("🎤", fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                val canSend = (inputText.isNotBlank() || pendingImage != null) && !isLoading
                Box(
                    modifier = Modifier.size(48.dp).background(
                        if (canSend) accentGreen else Color(0xFF2A3B50), RoundedCornerShape(24.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = {
                        if (canSend) {
                            doSend(inputText, pendingImage, messages, db, activeSessionId,
                                onSendMessage, { isLoading = it }, { inputText = "" },
                                onClearPendingImage,
                                { id -> activeSessionId = id; onSessionCreated(id) }, scope,
                                onSpeakText)
                        }
                    }) {
                        Text("→", fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    accentGreen: Color,
    aiBubble: Color,
    onSpeakText: ((String) -> Unit)? = null,
    isSpeaking: Boolean = false,
    onStopSpeaking: (() -> Unit)? = null
) {
    val triageColor = when (message.triageLevel) {
        TriageLevel.ROUGE   -> Color(0xFFE24B4A)
        TriageLevel.JAUNE   -> Color(0xFFEF9F27)
        TriageLevel.VERT    -> Color(0xFF639922)
        TriageLevel.UNKNOWN -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser && triageColor != null) {
            Box(Modifier.width(4.dp).heightIn(min = 20.dp).background(triageColor, RoundedCornerShape(2.dp)).align(Alignment.CenterVertically))
            Spacer(Modifier.width(6.dp))
        }
        Surface(
            color = if (message.isUser) accentGreen else aiBubble,
            shape = RoundedCornerShape(
                topStart = if (message.isUser) 16.dp else 4.dp,
                topEnd = if (message.isUser) 4.dp else 16.dp,
                bottomEnd = 16.dp, bottomStart = 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!message.isUser && triageColor != null) {
                    val label = when (message.triageLevel) {
                        TriageLevel.ROUGE -> "🔴 ROUGE — Urgence vitale"
                        TriageLevel.JAUNE -> "🟡 JAUNE — Consultation requise"
                        TriageLevel.VERT  -> "🟢 VERT — Surveillance"
                        else -> ""
                    }
                    Surface(color = triageColor.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
                        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = triageColor)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (message.imageBitmap != null) {
                    Image(
                        bitmap = message.imageBitmap.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    if (message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                }
                if (message.text.isNotBlank()) {
                    Text(message.text, fontSize = 14.sp, color = if (message.isUser) Color.White else Color(0xFFD3D1C7), lineHeight = 21.sp)
                }
                // Speaker button — only on AI messages
                if (!message.isUser && message.text.isNotBlank() && onSpeakText != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = {
                                if (isSpeaking) onStopSpeaking?.invoke()
                                else onSpeakText(message.text)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text(
                                if (isSpeaking) "⏹" else "🔊",
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun doSend(
    input: String, image: Bitmap?, messages: MutableList<ChatMessage>,
    db: AppDatabase, currentSessionId: Long?,
    onSendMessage: (String, Bitmap?, (Result<String>) -> Unit) -> Unit,
    setLoading: (Boolean) -> Unit, clearInput: () -> Unit, clearImage: () -> Unit,
    onSessionCreated: (Long) -> Unit, scope: CoroutineScope,
    onSpeakText: (String) -> Unit = {}
) {
    val text = input.trim()
    clearInput(); clearImage()
    messages.add(ChatMessage(text = text, isUser = true, imageBitmap = image))
    setLoading(true)
    val prompt = text.ifBlank { if (Locale.getDefault().language == "fr") "Analyse cette image médicalement." else "Analyze this image medically." }

    onSendMessage(prompt, image) { result ->
        setLoading(false)
        val rawResponse = if (result.isSuccess) result.getOrDefault("") else ""
        val triage = GemmaEngine.parseTriageLevelFromTag(rawResponse)
        val cleaned = GemmaEngine.cleanResponse(rawResponse)

        if (result.isSuccess) {
            messages.add(ChatMessage(text = cleaned, isUser = false, triageLevel = triage))
            // Auto-read the AI response
            onSpeakText(cleaned)
        } else {
            messages.add(ChatMessage(
                text = if (Locale.getDefault().language == "fr") "Erreur : ${result.exceptionOrNull()?.message}"
                else "Error: ${result.exceptionOrNull()?.message}",
                isUser = false
            ))
        }
        scope.launch {
            val sessionId = currentSessionId ?: run {
                db.sessionDao().insertSession(
                    ChatSession(title = text.take(50).ifBlank { "Consultation" }, triageLevel = triage.name)
                ).also { onSessionCreated(it) }
            }
            if (currentSessionId != null && triage != TriageLevel.UNKNOWN) {
                db.sessionDao().updateSession(currentSessionId, text.take(50).ifBlank { "Consultation" }, triage.name)
            }
            db.messageDao().insertMessage(ChatMessageEntity(sessionId = sessionId, text = text, isUser = true))
            if (result.isSuccess) {
                db.messageDao().insertMessage(ChatMessageEntity(sessionId = sessionId, text = cleaned, isUser = false, triageLevel = triage.name))
            }
        }
    }
}