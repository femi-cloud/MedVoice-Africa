package com.example.medvoiceafrica

// ═══════════════════════════════════════════════════════════════════
// LlamaEngine.kt
// Intégration llama.cpp via llama-android (JNI bindings officiels)
// Gestion OOM + fallback automatique vers GemmaEngine/Mode Survie
// ═══════════════════════════════════════════════════════════════════

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.codeshipping.llamakotlin.LlamaModel
import org.codeshipping.llamakotlin.LlamaConfig
import kotlinx.coroutines.flow.collect
import java.io.File

// ── États possibles du moteur local ──────────────────────────────
sealed class LlamaState {
    object NotInitialized : LlamaState()
    object Loading        : LlamaState()
    object Ready          : LlamaState()
    data class Failed(val reason: FailReason) : LlamaState()
}

enum class FailReason {
    FILE_NOT_FOUND,   // GGUF absent du dossier Download
    OUT_OF_MEMORY,    // RAM insuffisante (typique sur 2 Go)
    LOAD_ERROR,       // Autre erreur d'initialisation JNI
    UNSUPPORTED_ABI   // Appareil 32-bit sans lib arm64
}

// ── Résultat de génération ────────────────────────────────────────
sealed class LlamaResult {
    data class Success(val text: String, val tokensPerSec: Float = 0f) : LlamaResult()
    data class Fallback(val reason: FailReason, val offlineText: String) : LlamaResult()
}

// ═══════════════════════════════════════════════════════════════════
// LlamaEngine Singleton
// ═══════════════════════════════════════════════════════════════════
object LlamaEngine {

    private const val TAG = "LlamaEngine"
    private const val MODEL_FILENAME = "medvoice_final_v2.gguf"
    // private const val MODEL_FILENAME = "medvoice_q2.gguf"
    private const val MODEL_PATH_DOWNLOAD = "/storage/emulated/0/Download/$MODEL_FILENAME"

    //private const val MODEL_PATH_DOWNLOAD = "/storage/0000-0000/$MODEL_FILENAME"

    // Chemin alternatif si l'user a mis le fichier dans un sous-dossier
    @SuppressLint("SdCardPath")
    private val FALLBACK_PATHS = listOf(
        "/storage/emulated/0/Download/$MODEL_FILENAME",
        "/storage/emulated/0/Documents/$MODEL_FILENAME",
        "/sdcard/Download/$MODEL_FILENAME",
        "/storage/0/Download/$MODEL_FILENAME",
        "/storage/0000-0000/$MODEL_FILENAME",

    )

    // ── Remplacement de getAvailableRamMb() ──────────────────────────
    private fun getAvailableNativeRamMb(): Long {
        return try {
            val lines = File("/proc/meminfo").readLines()
            // MemAvailable = RAM réellement disponible pour les allocs natives (JNI inclus)
            // C'est exactement ce que llama.cpp va consommer
            val available = lines
                .firstOrNull { it.startsWith("MemAvailable") }
                ?.split(Regex("\\s+"))
                ?.getOrNull(1)
                ?.toLongOrNull() ?: 0L
            available / 1024  // kB → MB
        } catch (e: Exception) {
            Log.w(TAG, "Impossible de lire /proc/meminfo : ${e.message}")
            // Si on ne peut pas lire, on suppose qu'il y a assez de RAM
            // plutôt que de bloquer le chargement à tort
            Long.MAX_VALUE
        }
    }

    // Paramètres du modèle — ajuste selon ta quantization
    private const val N_CTX        = 2048   // Context window
    private const val N_THREADS    = 4      // Threads CPU
    private const val N_GPU_LAYERS = 0      // 0 = CPU only (pas de GPU sur ONIX)
    private const val MAX_TOKENS   = 512    // Tokens max par réponse

    // État interne
    @Volatile var state: LlamaState = LlamaState.NotInitialized
        private set

    // Instance du modèle Llama
    private var model: LlamaModel? = null

    // ── Trouver le fichier GGUF ───────────────────────────────────
    private fun findModelFile(): File? {
        for (path in FALLBACK_PATHS) {
            val f = File(path)
            if (f.exists() && f.canRead() && f.length() > 100_000_000L) {
                Log.d(TAG, "Modèle trouvé : $path (${f.length() / 1024 / 1024} MB)")
                return f
            }
        }
        return null
    }

    // ── Initialisation ────────────────────────────────────────────
    @RequiresApi(Build.VERSION_CODES.FROYO)
    suspend fun initialize(context: Context): LlamaState = withContext(Dispatchers.IO) {


        if (state == LlamaState.Ready) return@withContext state
        state = LlamaState.Loading

        // Dans ta fonction initialize()
        val downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val modelFile = File(downloadFolder, MODEL_FILENAME)

        // --- AJOUTE CE BLOC DE DEBUG ICI ---
        if (!modelFile.exists()) {
            Log.e("MEDVOICE_DEBUG", " ERREUR : Le fichier est introuvable à cette adresse : ${modelFile.absolutePath}")
            // Envoie un message sur l'écran pour être sûr de le voir
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Fichier GGUF introuvable dans Downloads", Toast.LENGTH_LONG).show()
            }
            return@withContext LlamaState.Failed(FailReason.FILE_NOT_FOUND)
        } else {
            Log.d("MEDVOICE_DEBUG", "Fichier trouvé ! Taille : ${modelFile.length() / 1024 / 1024} Mo")
        }

        val availRamMb = getAvailableNativeRamMb()
        val modelSizeMb = modelFile.length() / 1024 / 1024
        Log.d(TAG, "RAM native dispo: ${availRamMb}MB — Modèle: ${modelSizeMb}MB")

        // llama.cpp charge le GGUF + overhead de contexte (~10-15% supplémentaires)
        // On exige 110% de la taille du fichier en RAM libre
        //val requiredRamMb = (modelSizeMb * 1.1).toLong()
        //if (availRamMb < requiredRamMb) {
        //    Log.w(TAG, "RAM insuffisante : ${availRamMb}MB dispo, ${requiredRamMb}MB requis")
        //    state = LlamaState.Failed(FailReason.OUT_OF_MEMORY)
        //    return@withContext state
        //}

        try {
            // Chargement du GGUF avec le bloc de configuration de la librairie
            model = LlamaModel.load(modelFile.absolutePath) {
                contextSize = N_CTX
                threads = N_THREADS
                gpuLayers = N_GPU_LAYERS
                temperature = 0.65f
            }
            Log.d(TAG, "Modèle chargé avec succès")
            state = LlamaState.Ready

        } catch (unsupported: UnsatisfiedLinkError) {
            // La lib JNI n'est pas disponible pour cette ABI (ex: 32-bit)
            Log.e(TAG, "JNI introuvable (ABI incompatible ?) : ${unsupported.message}")
            cleanup()
            state = LlamaState.Failed(FailReason.UNSUPPORTED_ABI)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur chargement : ${e.javaClass.simpleName} — ${e.message}")
            cleanup()
            // On tente de classifier l'erreur
            state = if (e.message?.contains("memory", ignoreCase = true) == true) {
                LlamaState.Failed(FailReason.OUT_OF_MEMORY)
            } else {
                LlamaState.Failed(FailReason.LOAD_ERROR)
            }
        }

        state
    }

    // ── Génération de réponse ─────────────────────────────────────
    suspend fun generateResponse(
        prompt: String,
        systemPrompt: String = GemmaEngine.buildSystemPrompt(GemmaEngine.getDeviceLanguage(), true),
        ragContext: String = ""
    ): LlamaResult = withContext(Dispatchers.IO) {

        // Si le moteur n'est pas prêt → fallback immédiat
        if (state != LlamaState.Ready || model == null) {
            val failReason = (state as? LlamaState.Failed)?.reason ?: FailReason.LOAD_ERROR
            return@withContext LlamaResult.Fallback(
                reason = failReason,
                offlineText = buildOfflineFallback(prompt, failReason, ragContext)
            )
        }

        // Construire le prompt au format ChatML (compatible Gemma/Llama)
        val fullPrompt = buildChatMLPrompt(systemPrompt, ragContext, prompt)

        return@withContext try {
            val start = System.currentTimeMillis()

            val output = model?.generate(
                prompt = fullPrompt,
                configOverride = LlamaConfig {
                    maxTokens     = MAX_TOKENS
                    temperature   = 0.1f    // ✅ quasi-déterministe pour structured output médical
                    topK          = 1       // greedy : cohérent avec do_sample=False du training
                    topP          = 1.0f
                    repeatPenalty = 1.1f
                    // Stop tokens : <end_of_turn>=3, <eos>=1
                    // llama-android les passe via stopSequences
                    stopSequences = listOf("<end_of_turn>", "<eos>")
                }
            ) ?: ""

            val elapsed = (System.currentTimeMillis() - start) / 1000f
            val tokensPerSec = if (elapsed > 0) MAX_TOKENS / elapsed else 0f

            val cleaned = GemmaEngine.cleanResponse(output)
            Log.d(TAG, "✅ Réponse locale (${tokensPerSec.toInt()} tok/s): ${cleaned.take(60)}")

            LlamaResult.Success(text = cleaned, tokensPerSec = tokensPerSec)

        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "💥 OOM pendant la génération")
            state = LlamaState.Failed(FailReason.OUT_OF_MEMORY)
            cleanup()
            LlamaResult.Fallback(FailReason.OUT_OF_MEMORY, buildOfflineFallback(prompt, FailReason.OUT_OF_MEMORY, ragContext))

        } catch (e: Exception) {
            Log.e(TAG, "💥 Erreur génération : ${e.message}")
            LlamaResult.Fallback(FailReason.LOAD_ERROR, buildOfflineFallback(prompt, FailReason.LOAD_ERROR, ragContext))
        }
    }

    // ── Prompt au format Gemma (identique au training) ───────────
    // ⚠️ CRITIQUE : le modèle a été entraîné SANS system prompt et
    // avec le format <start_of_turn>, PAS ChatML (<|im_start|>).
    // Utiliser ChatML = le modèle ne reconnaît pas le format → hallucinations.
    // Le RAG context est injecté dans la question user si présent.
    private fun buildChatMLPrompt(system: String, rag: String, user: String): String {
        // system ignoré volontairement — absent du training dataset
        val userFull = if (rag.isNotBlank()) "$rag\n\n$user" else user
        return "<start_of_turn>user\n${userFull}<end_of_turn>\n<start_of_turn>model\n"
    }

    // ── Message de fallback offline ───────────────────────────────
    private fun buildOfflineFallback(prompt: String, reason: FailReason, ragContext: String): String {
        val isFr = GemmaEngine.getDeviceLanguage() == "fr"
        val reasonMsg = when (reason) {
            FailReason.FILE_NOT_FOUND -> if (isFr)
                "Le fichier `medvoice_final.gguf` est introuvable.\nPlacez-le dans `/storage/emulated/0/Download/`."
            else "File `medvoice_final.gguf` not found.\nPlace it in `/storage/emulated/0/Download/`."
            FailReason.OUT_OF_MEMORY -> if (isFr)
                "RAM insuffisante pour charger le modèle local (${Runtime.getRuntime().maxMemory()/1024/1024} MB disponibles).\nLe modèle de 3,3 Go nécessite au moins 3 Go de RAM libre."
            else "Insufficient RAM to load local model.\nThe 3.3GB model requires at least 3GB free RAM."
            FailReason.UNSUPPORTED_ABI -> if (isFr)
                "Appareil 32-bit détecté. llama.cpp nécessite une architecture arm64-v8a."
            else "32-bit device detected. llama.cpp requires arm64-v8a architecture."
            FailReason.LOAD_ERROR -> if (isFr)
                "Erreur lors du chargement du modèle local."
            else "Error loading local model."
        }

        val header = if (isFr) "⚠️ **Mode Sécurité Hors-ligne**\n\n$reasonMsg" else "⚠️ **Offline Safety Mode**\n\n$reasonMsg"

        return if (ragContext.isNotBlank()) {
            "$header\n\nProtocoles OMS disponibles localement :\n\n$ragContext\n[TRIAGE:INFO]"
        } else {
            "$header\n\nDécrivez un symptôme précis pour accéder aux protocoles OMS hors-ligne.\n[TRIAGE:INFO]"
        }
    }

    // ── Nettoyage mémoire ─────────────────────────────────────────
    fun cleanup() {
        model?.close()
        model = null
    }

    fun isReady() = state == LlamaState.Ready
    fun isFailed() = state is LlamaState.Failed
    fun failReason() = (state as? LlamaState.Failed)?.reason
}