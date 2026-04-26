package com.example.medvoiceafrica

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RagEngine(private val context: Context) {

    private val dao by lazy { AppDatabase.getInstance(context).omsProtocolDao() }

    companion object {
        private const val TAG = "RagEngine"
        private const val MAX_PROTOCOLS = 2
        private const val MIN_SCORE = 2  // raised from 1 to reduce false positives

        // Normalize: remove accents, lowercase, alphanum+space only
        fun normalize(s: String): String = s.lowercase()
            .replace(Regex("[àâä]"), "a").replace(Regex("[éèêë]"), "e")
            .replace(Regex("[îï]"), "i").replace(Regex("[ôö]"), "o")
            .replace(Regex("[ùûü]"), "u").replace(Regex("[ç]"), "c")
            .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

        // Beninese synonyms and common misspellings
        private val synonyms = mapOf(
            "chaud" to "fievre", "chaude" to "fievre", "temp" to "fievre",
            "ventre" to "diarrhee", "selle" to "diarrhee",
            "palu" to "paludisme", "palud" to "paludisme", "malaria" to "paludisme",
            "gonfle" to "oedeme", "gonflee" to "oedeme", "enfle" to "oedeme",
            "bebe" to "nourrisson", "bb" to "nourrisson",
            "nouveau ne" to "neonatal", "naissance" to "neonatal",
            "seche" to "deshydratation", "soif" to "deshydratation",
            "respir" to "pneumonie", "touss" to "pneumonie",
            "maigre" to "malnutrition", "kwashiorkor" to "malnutrition",
            "ta " to "hypertension", "tension" to "hypertension",
            "coupe" to "plaie", "blesse" to "plaie",
            "morsure" to "envenimation", "vipere" to "envenimation"
        )

        fun expandQuery(query: String): String {
            val norm = normalize(query)
            val expanded = StringBuilder(norm)
            synonyms.forEach { (word, expansion) -> if (norm.contains(word)) expanded.append(" $expansion") }
            return expanded.toString()
        }

        // Gemini fix: use \b word boundaries to avoid "eau" matching "beaucoup"
        fun score(query: String, p: OmsProtocol): Int {
            val q = expandQuery(query)
            val normCategory = normalize(p.category)
            var s = 0

            p.keywords.split(",").map { normalize(it.trim()) }.filter { it.isNotBlank() }.forEach { kw ->
                try {
                    // \b = word boundary — prevents partial matches
                    if (Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(q)) {
                        s += if (kw.length > 5) 3 else 2
                    }
                } catch (_: Exception) {
                    // fallback for special regex chars
                    if (q.contains(kw)) s += 1
                }
            }
            // Category exact word match
            try {
                if (Regex("\\b${Regex.escape(normCategory)}\\b").containsMatchIn(q)) s += 4
            } catch (_: Exception) {
                if (q.contains(normCategory)) s += 4
            }
            return s
        }

        fun buildContext(protocols: List<OmsProtocol>, lang: String): String {
            if (protocols.isEmpty()) return ""
            val header = if (lang == "fr")
                "=== PROTOCOLES OMS (Base locale — utilise ces informations OBLIGATOIREMENT pour ta réponse) ==="
            else
                "=== WHO PROTOCOLS (Local knowledge base — MANDATORY: use this information in your response) ==="
            val footer = if (lang == "fr") "=== FIN DES PROTOCOLES ===" else "=== END OF PROTOCOLS ==="
            return buildString {
                appendLine(header)
                protocols.forEach { p -> appendLine(); appendLine("--- ${p.title.uppercase()} ---"); appendLine(p.protocol.trim()) }
                appendLine(footer)
            }
        }
    }

    suspend fun findRelevant(query: String): List<OmsProtocol> = withContext(Dispatchers.IO) {
        try {
            dao.getAll()
                .map { it to score(query, it) }
                .filter { (_, s) -> s >= MIN_SCORE }
                .sortedByDescending { (_, s) -> s }
                .take(MAX_PROTOCOLS)
                .also { results ->
                    if (results.isEmpty()) Log.d(TAG, "RAG: no match for '$query'")
                    else results.forEach { (p, s) -> Log.d(TAG, "RAG ✅ '${p.title}' score=$s") }
                }
                .map { (p, _) -> p }
        } catch (e: Exception) { Log.e(TAG, "RAG error: ${e.message}"); emptyList() }
    }

    suspend fun populate() = withContext(Dispatchers.IO) {
        if (dao.count() > 0) return@withContext
        dao.insertAll(OmsProtocols.all)
        Log.d(TAG, "✅ RAG DB populated: ${OmsProtocols.all.size} protocols")
    }

    suspend fun isReady() = withContext(Dispatchers.IO) {
        try { dao.count() > 0 } catch (_: Exception) { false }
    }
}