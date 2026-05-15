package com.example.medvoiceafrica

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.ExperimentalPermissionsApi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

// ─── L'ANALYSEUR (Cerveau ML Kit) ────────────────────────────────
class PharmaAnalyzer(
    private val onResult: (String, String) -> Unit
) : ImageAnalysis.Analyzer {

    private val barcodeScanner = BarcodeScanning.getClient()
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastScanTime = 0L
    private val SCAN_COOLDOWN_MS = 2000L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastScanTime < SCAN_COOLDOWN_MS) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val code = barcodes.firstOrNull()?.displayValue ?: ""
                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text
                        if (code.isNotBlank() || text.length > 3) {
                            lastScanTime = System.currentTimeMillis()
                            onResult(code, text)
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
            }
            .addOnFailureListener { imageProxy.close() }
    }
}

// ─── DONNÉES BRUTES DU SCAN ───────────────────────────────────────
data class ScanData(val barcode: String, val ocrText: String)

// ─── L'INTERFACE PRINCIPALE ───────────────────────────────────────
@kotlin.OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PharmaScanScreen(
    gemmaEngine: GemmaEngine,
    currentMedications: List<String>,
    isOnline: Boolean,
    onInteractionFound: (InteractionResult, String) -> Unit,
    onDosageResult: (DosageResult) -> Unit,
    onScanDetected: (String, String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalMedVoiceColors.current
    val isFr = java.util.Locale.getDefault().language == "fr"

    var scanData by remember { mutableStateOf<ScanData?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var scanStatus by remember {
        mutableStateOf(if (isFr) "Placez le médicament dans le cadre" else "Place the medication in the frame")
    }
    var hasScanned by remember { mutableStateOf(false) }
    var scanAttempts by remember { mutableStateOf(0) }

    // ── Hint après 10 secondes sans scan ─────────────────────────
    LaunchedEffect(Unit) {
        while (!hasScanned) {
            delay(10000L)
            if (!hasScanned && !isAnalyzing) {
                scanAttempts++
                if (scanAttempts >= 2) {
                    scanStatus = if (isFr)
                        "Scan difficile ? Essayez de mieux éclairer le médicament."
                    else "Difficult scan? Try better lighting."
                }
            }
        }
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
            setImageAnalysisAnalyzer(
                Executors.newSingleThreadExecutor(),
                PharmaAnalyzer { code, text ->
                    if (!hasScanned && !isAnalyzing) {
                        scanData = ScanData(barcode = code, ocrText = text)
                    }
                }
            )
        }
    }

    LaunchedEffect(scanData) {
        val data = scanData ?: return@LaunchedEffect
        if (hasScanned || isAnalyzing) return@LaunchedEffect

        hasScanned = true
        isAnalyzing = true

        // ── ÉTAPE 1 : Confirmer la détection visuelle avant l'analyse ──
        scanStatus = if (isFr) "✓ Médicament détecté — identification..." else "✓ Medication detected — identifying..."

        Log.d("PharmaScan", "Scan reçu — Barcode: '${data.barcode}', OCR: '${data.ocrText.take(80)}'")

        withContext(Dispatchers.IO) {
            var interactionResult: InteractionResult? = null
            var shouldClose = false

            try {
                val moleculeName = DrugInteractionEngine.extractMoleculeName(
                    data.ocrText + " " + data.barcode
                )

                // ── ÉTAPE 2 : Afficher le nom identifié avant de continuer ──
                if (moleculeName.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        scanStatus = if (isFr)
                            "✓ Identifié : $moleculeName — vérification interactions..."
                        else "✓ Identified: $moleculeName — checking interactions..."
                    }
                    // Laisser l'utilisateur lire le nom identifié (~1.5s)
                    delay(1500L)
                } else {
                    withContext(Dispatchers.Main) {
                        scanStatus = if (isFr) "Analyse en cours..." else "Analyzing..."
                    }
                }

                // ── ÉTAPE 3 : Vérification interactions ──────────────────
                val result = DrugInteractionEngine.checkInteraction(
                    scannedText = data.ocrText,
                    scannedBarcode = data.barcode,
                    currentMeds = currentMedications,
                    gemmaEngine = gemmaEngine,
                    isOnline = isOnline,
                    isFr = isFr
                )
                interactionResult = result

                withContext(Dispatchers.Main) {
                    when (result.severity) {
                        InteractionSeverity.ROUGE -> {
                            scanStatus = if (isFr) "⚠️ INTERACTION DANGEREUSE" else "⚠️ DANGEROUS INTERACTION"
                            // Laisser l'utilisateur lire l'alerte avant de callback

                            onInteractionFound(interactionResult, moleculeName)
                            shouldClose = true
                        }
                        InteractionSeverity.JAUNE -> {
                            scanStatus = if (isFr) "⚠️ Précaution requise" else "⚠️ Caution required"

                            onInteractionFound(interactionResult, moleculeName)
                            shouldClose = true
                        }
                        InteractionSeverity.VERT -> {
                            scanStatus = if (isFr) "✓ Aucune interaction connue" else "✓ No known interaction"
                            onInteractionFound(interactionResult, moleculeName)
                            shouldClose = true
                        }
                        InteractionSeverity.UNKNOWN -> {
                            // Offline — ne pas fermer, renvoyer l'OCR au chat
                            scanStatus = if (isFr)
                                "Scan terminé — analyse hors-ligne"
                            else "Scan done — offline analysis"
                            withContext(Dispatchers.Main) {
                                onScanDetected(data.barcode, data.ocrText)
                            }
                            shouldClose = false
                        }
                    }
                }
                delay(1500L)

                // ── ÉTAPE 4 : Suggestion dosage automatique ───────────────
                val dosageParams = DosageFunctionCalling.extractDosageParams(
                    data.ocrText + " $moleculeName"
                )
                if (dosageParams != null && dosageParams.medicineName.isNotBlank()) {
                    val dosageResult = DosageFunctionCalling.calculateDosage(
                        params = dosageParams,
                        gemmaEngine = gemmaEngine,
                        isOnline = isOnline,
                        isFr = isFr,
                        currentMeds = currentMedications
                    )
                    dosageResult.let { r ->
                        if (r.source != DosageSource.INSUFFICIENT_DATA) {
                            withContext(Dispatchers.Main) { onDosageResult(r) }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("PharmaScan", "Erreur: ${e.message}")
                withContext(Dispatchers.Main) {
                    scanStatus = if (isFr) "Erreur d'analyse." else "Analysis error."
                    onScanDetected(data.barcode, data.ocrText)
                }
                shouldClose = false
            } finally {
                withContext(Dispatchers.Main) {
                    isAnalyzing = false
                    // Fermer uniquement si un résultat connu ET flag explicite
                    if (shouldClose) {
                        delay(2000L) // Laisser l'utilisateur voir le statut final
                        onClose()
                    }
                    // Si shouldClose = false → l'user peut rescanner via le bouton
                }
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermission.status.isGranted) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                        cameraController.bindToLifecycle(lifecycleOwner)
                        previewView.controller = cameraController
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Permission caméra requise", color = Color.White)
            }
        }

        // Overlay semi-transparent
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

        // Viseur
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.Center)
                .border(
                    width = if (isAnalyzing) 3.dp else 2.dp,
                    color = when {
                        isAnalyzing -> colors.accent
                        hasScanned  -> Color(0xFF4CAF50)
                        else        -> colors.accent
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Transparent)
        )

        if (isAnalyzing) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(56.dp),
                color = colors.accent,
                strokeWidth = 3.dp
            )
        }

        // Statut en bas
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = scanStatus, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (currentMedications.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isFr)
                        "Traitement actuel vérifié : ${currentMedications.joinToString(", ").take(50)}"
                    else
                        "Checking against: ${currentMedications.joinToString(", ").take(50)}",
                    color = Color.Yellow.copy(alpha = 0.85f),
                    fontSize = 10.sp
                )
            }
        }

        // Bouton fermeture
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopEnd)
                .background(colors.bgSecondary, RoundedCornerShape(8.dp))
                .padding(8.dp)
                .clickable { onClose() }
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Fermer",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // Bouton rescanner
        if (hasScanned && !isAnalyzing) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .background(colors.bgSecondary, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .clickable {
                        hasScanned = false
                        scanData = null
                        scanStatus = if (isFr) "Placez le médicament dans le cadre" else "Place medication in frame"
                    }
            ) {
                Text(
                    text = if (isFr) "Rescanner" else "Rescan",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}