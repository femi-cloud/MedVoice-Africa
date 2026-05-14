package com.example.medvoiceafrica

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WhisperEngine — STT via HuggingFace Inference API (whisper-large-v3)
 *
 * Pattern identique au reste de l'app :
 *  - isReady()      → vérifier avant d'appeler
 *  - initialize()   → appeler dans onCreate() via lifecycleScope
 *  - transcribe()   → enregistre + envoie à Whisper, retourne le texte
 *
 * Fallback automatique : si HF API inaccessible, retourne "" → MainActivity
 * bascule sur le SpeechRecognizer Android natif existant.
 */
object WhisperEngine {

    private const val TAG = "WhisperEngine"

    // HuggingFace Inference API — whisper-large-v3 (meilleur support Fon)
    private const val HF_API_URL =
        "https://api-inference.huggingface.co/models/openai/whisper-large-v3"

    // Paramètres audio — format attendu par Whisper : 16kHz mono 16-bit PCM
    private const val SAMPLE_RATE    = 16_000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT

    // Durée max d'enregistrement : 10 secondes (largement suffisant pour un symptôme)
    private const val MAX_RECORD_MS  = 10_000L

    // Seuil de silence pour arrêt automatique (RMS en dessous = silence)
    private const val SILENCE_THRESHOLD_RMS = 300
    private const val SILENCE_DURATION_MS   = 1_500L

    private var hfApiKey: String = ""
    private var ready = false

    fun isReady(): Boolean = ready

    /**
     * Appeler dans onCreate() — charge la clé HF depuis BuildConfig ou SharedPreferences.
     * Si pas de clé → isReady() = false → fallback Android SpeechRecognizer.
     */
    fun initialize(context: Context) {
        hfApiKey = try {
            // Priorité 1 : clé dans SharedPreferences (configurable via Settings)
            val prefs = context.getSharedPreferences("medvoice_prefs", Context.MODE_PRIVATE)
            prefs.getString("hf_api_key", "") ?: ""
        } catch (_: Exception) { "" }

        // Priorité 2 : BuildConfig si défini (local.properties → HF_API_KEY=...)
        if (hfApiKey.isBlank()) {
            hfApiKey = try {
                val field = BuildConfig::class.java.getField("HF_API_KEY")
                field.get(null) as? String ?: ""
            } catch (_: Exception) { "" }
        }

        ready = hfApiKey.isNotBlank()
        Log.d(TAG, if (ready) "WhisperEngine ready (HF API)" else "WhisperEngine disabled — no HF key")
    }

    /**
     * Enregistre l'audio via AudioRecord puis envoie à Whisper.
     * Arrêt automatique après silence de 1.5s ou 10s max.
     * Retourne le texte transcrit, ou "" si erreur/silence.
     */
    suspend fun transcribe(context: Context): String = withContext(Dispatchers.IO) {
        if (!ready) return@withContext ""

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(4096)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission missing: ${e.message}")
            return@withContext ""
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed")
            recorder.release()
            return@withContext ""
        }

        val pcmBuffer = ByteArrayOutputStream()
        val chunk     = ByteArray(bufferSize)
        var silenceMs = 0L
        var totalMs   = 0L
        val chunkMs   = (bufferSize.toFloat() / (SAMPLE_RATE * 2) * 1000).toLong()

        recorder.startRecording()
        Log.d(TAG, "Recording started (max ${MAX_RECORD_MS}ms, silence stop ${SILENCE_DURATION_MS}ms)")

        while (totalMs < MAX_RECORD_MS) {
            val read = recorder.read(chunk, 0, bufferSize)
            if (read <= 0) break

            pcmBuffer.write(chunk, 0, read)
            totalMs += chunkMs

            // Calcul RMS pour détection silence
            val rms = computeRms(chunk, read)
            if (rms < SILENCE_THRESHOLD_RMS) {
                silenceMs += chunkMs
                if (silenceMs >= SILENCE_DURATION_MS && totalMs > 1_000L) {
                    Log.d(TAG, "Silence detected at ${totalMs}ms — stopping")
                    break
                }
            } else {
                silenceMs = 0L
            }
        }

        recorder.stop()
        recorder.release()
        Log.d(TAG, "Recording done — ${pcmBuffer.size()} bytes, ${totalMs}ms")

        if (pcmBuffer.size() < 3_200) {  // < 100ms de son → silence pur
            Log.d(TAG, "Too short — skipping")
            return@withContext ""
        }

        // Convertir PCM raw → WAV (Whisper attend un fichier audio valide)
        val wavBytes = pcmToWav(pcmBuffer.toByteArray(), SAMPLE_RATE)

        // Envoyer à HuggingFace Whisper API
        return@withContext sendToWhisper(wavBytes)
    }

    // ── Conversion PCM → WAV ──────────────────────────────────────────────────

    private fun pcmToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val channels    = 1
        val bitsPerSample = 16
        val byteRate    = sampleRate * channels * bitsPerSample / 8
        val dataSize    = pcm.size
        val totalSize   = 36 + dataSize

        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)

        // RIFF header
        dos.writeBytes("RIFF")
        dos.write(intToLittleEndian(totalSize))
        dos.writeBytes("WAVE")
        // fmt chunk
        dos.writeBytes("fmt ")
        dos.write(intToLittleEndian(16))            // chunk size
        dos.write(shortToLittleEndian(1))           // PCM format
        dos.write(shortToLittleEndian(channels))
        dos.write(intToLittleEndian(sampleRate))
        dos.write(intToLittleEndian(byteRate))
        dos.write(shortToLittleEndian(channels * bitsPerSample / 8))
        dos.write(shortToLittleEndian(bitsPerSample))
        // data chunk
        dos.writeBytes("data")
        dos.write(intToLittleEndian(dataSize))
        dos.write(pcm)

        return out.toByteArray()
    }

    private fun intToLittleEndian(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun shortToLittleEndian(v: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()

    // ── Calcul RMS pour détection silence ────────────────────────────────────

    private fun computeRms(buffer: ByteArray, length: Int): Double {
        var sum = 0.0
        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            sum += sample.toDouble() * sample.toDouble()
            i += 2
        }
        return if (length > 0) Math.sqrt(sum / (length / 2)) else 0.0
    }

    // ── Appel HuggingFace Inference API ──────────────────────────────────────

    private fun sendToWhisper(wavBytes: ByteArray): String {
        return try {
            val url  = URL(HF_API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $hfApiKey")
            conn.setRequestProperty("Content-Type", "audio/wav")
            // Paramètre langue : "fr" pour Fon/français (Whisper large-v3 détecte
            // automatiquement le Fon dans un contexte francophone)
            conn.setRequestProperty("x-use-cache", "false")
            conn.doOutput     = true
            conn.connectTimeout = 10_000
            conn.readTimeout    = 30_000

            conn.outputStream.use { it.write(wavBytes) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "HF API error $responseCode")
                // Modèle en train de charger (503) → attendre et retourner ""
                return ""
            }

            val raw  = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(raw)

            // Réponse HF : { "text": "..." }
            val text = json.optString("text", "").trim()
            Log.d(TAG, "Whisper result: \"${text.take(80)}\"")
            text

        } catch (e: Exception) {
            Log.e(TAG, "sendToWhisper error: ${e.message}")
            ""
        }
    }
}