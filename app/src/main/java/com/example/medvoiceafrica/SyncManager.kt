package com.example.medvoiceafrica

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════════
// SyncManager.kt — Synchronisation différée des ConsultationLogs
// Envoie des rapports ANONYMISÉS dès retour de connectivité
// ═══════════════════════════════════════════════════════════════════

object SyncManager {

    private const val TAG = "SyncManager"
    private const val SYNC_WORK_NAME = "medvoice_sync"

    // ── Planification via WorkManager ──────────────────────────────
    // À appeler dans MainActivity.onCreate() ou dès qu'une nouvelle
    // ConsultationLog est insérée en base Room
    fun schedulePendingSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
        Log.d(TAG, "Sync planifié (toutes les 6h si réseau dispo)")
    }

    // ── Sync immédiate (one-shot) — appeler quand réseau revient ───
    fun syncNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "${SYNC_WORK_NAME}_immediate",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        Log.d(TAG, "Sync immédiate lancée")
    }
}

// ═══════════════════════════════════════════════════════════════════
// SyncWorker — Exécuté par WorkManager en arrière-plan
// ═══════════════════════════════════════════════════════════════════
class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        // Endpoint de collecte anonymisée — à remplacer par ton URL réelle
        private const val SYNC_ENDPOINT = "https://api.medvoice-africa.org/v1/sync"
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)

        // Récupère uniquement les consultations non encore synchronisées
        val pendingLogs = db.consultationDao().getUnsyncedLogs()

        if (pendingLogs.isEmpty()) {
            Log.d(TAG, "Aucune donnée en attente de sync")
            return@withContext Result.success()
        }

        Log.d(TAG, "${pendingLogs.size} consultation(s) à synchroniser")

        return@withContext try {
            val anonymizedPayload = buildAnonymizedPayload(pendingLogs)
            val success = sendToServer(anonymizedPayload)

            if (success) {
                // Marquer comme synchronisé en base
                val ids = pendingLogs.map { it.id }
                db.consultationDao().markAsSynced(ids)
                Log.d(TAG, "✅ ${pendingLogs.size} consultation(s) synchronisées")

                context.getSharedPreferences("medvoice_sync", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("last_sync_ts", System.currentTimeMillis())
                    .putBoolean("last_sync_ok", true)
                    .apply()

                Result.success()
            } else {
                Log.w(TAG, "Échec sync serveur — retry dans 15min")

                context.getSharedPreferences("medvoice_sync", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("last_sync_ok", false)
                    .apply()

                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur sync: ${e.message}")

            context.getSharedPreferences("medvoice_sync", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("last_sync_ok", false)
                .apply()

            Result.retry()
        }
    }

    // ── Anonymisation stricte avant envoi ──────────────────────────
    // AUCUNE donnée identifiante : pas de texte, pas de sessionId brut
    private fun buildAnonymizedPayload(logs: List<ConsultationLog>): String {
        val array = JSONArray()
        logs.forEach { log ->
            array.put(JSONObject().apply {
                // Données épidémiologiques UNIQUEMENT
                put("triage", log.triage)           // "ROUGE" | "JAUNE" | "VERT"
                put("pathologie", log.pathologie)   // "Paludisme", "Fièvre"...
                put("is_offline", log.isOffline)
                put("timestamp_day", log.timestamp / 86_400_000)  // Jour (pas heure exacte)
                put("app_version", BuildConfig.VERSION_NAME)

                // Hash anonyme pour dédoublonner côté serveur (pas de sessionId brut)
                val raw = "${log.sessionId}${log.timestamp}${log.pathologie}"
                val hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.toByteArray())
                    .take(8)
                    .joinToString("") { "%02x".format(it) }
                put("hash", hash)
            })
        }
        return JSONObject().apply {
            put("records", array)
            put("count", logs.size)
            put("sent_at", System.currentTimeMillis())
        }.toString()
    }

    // ── Envoi HTTP POST ─────────────────────────────────────────────
    private fun sendToServer(jsonPayload: String): Boolean {
        return try {
            val url = URL(SYNC_ENDPOINT)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-App-Id", "medvoice-africa")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.outputStream.use { it.write(jsonPayload.toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "sendToServer error: ${e.message}")
            false
        }
    }
}