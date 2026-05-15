package com.example.medvoiceafrica

// ═══════════════════════════════════════════════════════════════════
// 1. TRIAGE ACTIF — Vibration + Alerte vocale automatique
//
// Ce fichier contient :
//   A) TriageAlertHelper  → vibre + parle selon le niveau
//   B) Patch doSend()     → appeler l'alerte après runInference()
//   C) Patch ChatBubble() → carte visuelle améliorée (déjà en place,
//                           on ajoute juste le bouton SMS)

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech

// ── A) TriageAlertHelper ──────────────────────────────────────────
object TriageAlertHelper {

    /**
     * Appelé dès qu'on reçoit un niveau de triage ROUGE ou JAUNE.
     * - ROUGE : pattern long + message vocal urgent
     * - JAUNE : vibration courte + message vocal informatif
     * - VERT / UNKNOWN : rien (pas d'interruption)
     */
    fun trigger(
        context: Context,
        level: TriageLevel,
        tts: TextToSpeech?,
        isFr: Boolean = true
    ) {
        when (level) {
            TriageLevel.ROUGE -> {
                vibrate(context, longArrayOf(0, 600, 200, 600, 200, 600))
                speak(
                    tts,
                    if (isFr) "Urgence vitale détectée. Transfert immédiat requis."
                    else "Critical emergency detected. Immediate transfer required."
                )
            }
            TriageLevel.JAUNE -> {
                vibrate(context, longArrayOf(0, 300, 100, 300))
                speak(
                    tts,
                    if (isFr) "Cas modéré. Consultation médicale requise dans les 24 heures."
                    else "Moderate case. Medical consultation required within 24 hours."
                )
            }
            else -> { /* VERT et UNKNOWN : pas d'alerte automatique */ }
        }
    }

    private fun vibrate(context: Context, pattern: LongArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    ?: return
                mgr.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TriageAlert", "Vibration failed: ${e.message}")
        }
    }

    private fun speak(tts: TextToSpeech?, message: String) {
        if (tts == null) return
        tts.speak(message, TextToSpeech.QUEUE_ADD, null, "triage_alert")
    }
}
